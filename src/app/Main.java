package app;

import excepciones.*;
import modelo.*;
import servicio.GestorMantenimiento;
import servicio.Indicadores;
import servicio.RankingEquipo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;

/** Interfaz de consola del CMMS. Solo pide datos y muestra resultados. */
public class Main {

    private static final GestorMantenimiento gestor = new GestorMantenimiento();
    private static final Scanner scanner = new Scanner(System.in);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static void main(String[] args) {
        System.out.println("=== GESTION DE MANTENIMIENTO (CMMS) ===");
        if (gestor.estaVacio()) {
            System.out.println("Sin datos. Usa la opcion 15 para cargar una planta de ejemplo.");
        }
        int opcion = -1;
        try {
            do {
                mostrarMenu();
                opcion = leerEntero("");
                try {
                    procesar(opcion);
                } catch (EntidadNoEncontradaException | EntidadDuplicadaException
                         | OperacionInvalidaException | StockInsuficienteException e) {
                    System.out.println("Error: " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    System.out.println("Dato invalido: " + e.getMessage());
                }
            } while (opcion != 0);
        } catch (NoSuchElementException e) {
            System.out.println("\nEntrada finalizada.");
        }
        gestor.guardarDatos();
        System.out.println("Datos guardados. Hasta luego!");
        scanner.close();
    }

    private static void mostrarMenu() {
        System.out.println("\n======= MENU  (ordenes abiertas: " + gestor.getBacklog().size() + ") =======");
        System.out.println(" 1. Tablero de equipos");
        System.out.println(" 2. Reportar falla (orden correctiva)");
        System.out.println(" 3. Generar preventivos pendientes");
        System.out.println(" 4. Backlog de ordenes abiertas");
        System.out.println(" 5. Asignar tecnico a una orden");
        System.out.println(" 6. Iniciar orden");
        System.out.println(" 7. Registrar repuesto usado");
        System.out.println(" 8. Cerrar orden");
        System.out.println(" 9. Cancelar orden");
        System.out.println("10. Indicadores de un equipo (MTBF / MTTR / disponibilidad)");
        System.out.println("11. Reporte de planta");
        System.out.println("12. Detalle de una orden");
        System.out.println("13. Registrar equipo / tecnico / repuesto");
        System.out.println("14. Stock de repuestos y reposicion");
        System.out.println("15. Cargar planta de ejemplo");
        System.out.println(" 0. Salir y guardar");
        System.out.print("Opcion: ");
    }

    private static void procesar(int opcion) throws EntidadNoEncontradaException, EntidadDuplicadaException,
            OperacionInvalidaException, StockInsuficienteException {
        switch (opcion) {
            case 1 -> tablero();
            case 2 -> reportarFalla();
            case 3 -> generarPreventivos();
            case 4 -> backlog();
            case 5 -> { gestor.asignarOrden(leerEntero("Id de orden: "), leerTexto("Legajo del tecnico: ")); System.out.println("Orden asignada."); }
            case 6 -> { gestor.iniciarOrden(leerEntero("Id de orden: ")); System.out.println("Orden en ejecucion."); }
            case 7 -> { gestor.consumirRepuesto(leerEntero("Id de orden: "), leerTexto("Codigo de repuesto: "), leerEntero("Cantidad: ")); System.out.println("Repuesto registrado y descontado del stock."); }
            case 8 -> cerrar();
            case 9 -> { gestor.cancelarOrden(leerEntero("Id de orden: "), leerTexto("Motivo: ")); System.out.println("Orden cancelada."); }
            case 10 -> indicadores();
            case 11 -> reporte();
            case 12 -> detalle();
            case 13 -> registrar();
            case 14 -> stock();
            case 15 -> { gestor.cargarDatosDeEjemplo(); System.out.println("Planta de ejemplo cargada."); }
            case 0 -> { }
            default -> System.out.println("Opcion no valida.");
        }
    }

    // ---------- Pantallas ----------

    private static void tablero() {
        List<Equipo> lista = gestor.getEquipos();
        if (lista.isEmpty()) { System.out.println("(sin equipos)"); return; }
        System.out.printf("%n%-8s %-18s %-36s %-4s %-17s %s%n", "TAG", "TIPO", "DESCRIPCION", "CRIT", "ESTADO", "PROXIMO PREVENTIVO");
        System.out.println("-".repeat(104));
        for (Equipo e : lista) {
            long dias = java.time.temporal.ChronoUnit.DAYS.between(gestor.getHoy(), e.getProximoPreventivo());
            String pm = dias < 0 ? e.getProximoPreventivo() + " (VENCIDO " + (-dias) + "d)" : e.getProximoPreventivo() + " (en " + dias + "d)";
            System.out.printf("%-8s %-18s %-36s %-4s %-17s %s%n", e.getTag(), e.getTipo(), recortar(e.getDescripcion(), 36),
                e.getCriticidad(), e.getEstado(), pm);
        }
    }

    private static void reportarFalla() throws EntidadNoEncontradaException, OperacionInvalidaException {
        String tag = leerTexto("Tag del equipo: ");
        String desc = leerTexto("Que paso?: ");
        double horasAtras = leerDecimal("Hace cuantas horas fallo? (0 = ahora): ");
        LocalDateTime fecha = gestor.getAhora().minusMinutes(Math.round(horasAtras * 60));
        OrdenTrabajo o = gestor.reportarFalla(tag, desc, fecha);
        System.out.printf("Orden #%d abierta | prioridad %s | requiere %s%n", o.getId(), o.getPrioridad(), o.getEspecialidadRequerida());
    }

    private static void generarPreventivos() {
        List<OrdenTrabajo> nuevas = gestor.generarPreventivosPendientes(leerEntero("Anticipacion en dias (0 = solo vencidos): "));
        if (nuevas.isEmpty()) System.out.println("No hay preventivos para generar.");
        for (OrdenTrabajo o : nuevas) {
            System.out.printf("Orden #%d (%s) preventivo de %s | prioridad %s%n", o.getId(), o.getEspecialidadRequerida(), o.getTagEquipo(), o.getPrioridad());
        }
    }

    private static void backlog() {
        List<OrdenTrabajo> lista = gestor.getBacklog();
        if (lista.isEmpty()) { System.out.println("(no hay ordenes abiertas)"); return; }
        System.out.printf("%n%-5s %-11s %-9s %-12s %-13s %-8s %s%n", "ID", "PRIORIDAD", "EQUIPO", "TIPO", "ESTADO", "TECNICO", "ABIERTA");
        System.out.println("-".repeat(80));
        for (OrdenTrabajo o : lista) {
            System.out.printf("#%-4d %-11s %-9s %-12s %-13s %-8s %s%n", o.getId(), o.getPrioridad(), o.getTagEquipo(), o.getTipo(),
                o.getEstado(), o.getLegajoTecnico() == null ? "-" : o.getLegajoTecnico(), o.getFechaApertura().format(FECHA));
        }
    }

    private static void cerrar() throws EntidadNoEncontradaException, OperacionInvalidaException {
        int id = leerEntero("Id de orden: ");
        double horas = leerDecimal("Horas-hombre trabajadas: ");
        String causa = leerTextoOpcional("Causa raiz (obligatoria en correctivas): ");
        String acciones = leerTextoOpcional("Acciones realizadas: ");
        gestor.cerrarOrden(id, horas, causa, acciones);
        OrdenTrabajo o = gestor.buscarOrden(id);
        System.out.printf("Orden cerrada. Costo total: $%,.0f (mano de obra $%,.0f + repuestos $%,.0f)%n",
            o.getCostoTotal(), o.getCostoManoObra(), o.getCostoRepuestos());
    }

    private static void indicadores() throws EntidadNoEncontradaException {
        String tag = leerTexto("Tag del equipo: ");
        Equipo e = gestor.buscarEquipo(tag);
        Indicadores i = gestor.indicadores(tag);
        System.out.println(e.getTag() + " - " + e.getDescripcion());
        System.out.println(i);
        if (i.fallas() == 0) System.out.println("Sin fallas registradas: el MTBF no se puede calcular.");
    }

    private static void reporte() {
        System.out.println("\n--- Equipos con mas horas de parada (bad actors) ---");
        List<RankingEquipo> ranking = gestor.getBadActors(5);
        if (ranking.isEmpty()) System.out.println("(sin fallas registradas)");
        int puesto = 1;
        for (RankingEquipo r : ranking) {
            System.out.printf("%d. %-8s %-18s fallas=%d  parada=%.1f h  costo=$%,.0f%n", puesto++, r.tag(), r.tipo(), r.fallas(), r.horasParada(), r.costo());
        }
        System.out.println("\n--- Costo de ordenes cerradas por tipo de equipo ---");
        gestor.getCostosPorTipoEquipo().forEach((tipo, costo) -> System.out.printf("%-18s $%,.0f%n", tipo, costo));
        double pm = gestor.getPorcentajePreventivo();
        System.out.println("\nTrabajo planificado (preventivo sobre ordenes cerradas): " + (Double.isNaN(pm) ? "n/a" : String.format(Locale.US, "%.1f %%", pm)));
        System.out.println("\n--- Preventivos vencidos ---");
        List<Equipo> vencidos = gestor.getPreventivosVencidos();
        if (vencidos.isEmpty()) System.out.println("(ninguno)");
        vencidos.forEach(e -> System.out.println(e.getTag() + " debio hacerse el " + e.getProximoPreventivo()));
        System.out.println("\n--- Repuestos bajo el minimo ---");
        List<Repuesto> bajos = gestor.getRepuestosBajoMinimo();
        if (bajos.isEmpty()) System.out.println("(ninguno)");
        bajos.forEach(r -> System.out.printf("%s: hay %d, minimo %d%n", r.getCodigo(), r.getStock(), r.getStockMinimo()));
    }

    private static void detalle() throws EntidadNoEncontradaException {
        OrdenTrabajo o = gestor.buscarOrden(leerEntero("Id de orden: "));
        System.out.printf("Orden #%d | %s | %s | %s%n", o.getId(), o.getTipo(), o.getPrioridad(), o.getEstado());
        System.out.println("Equipo: " + o.getTagEquipo() + " | requiere " + o.getEspecialidadRequerida() + " | tecnico: " + (o.getLegajoTecnico() == null ? "sin asignar" : o.getLegajoTecnico()));
        System.out.println("Descripcion: " + o.getDescripcion());
        System.out.println("Abierta: " + o.getFechaApertura().format(FECHA) + (o.getFechaCierre() != null ? " | Cerrada: " + o.getFechaCierre().format(FECHA) : ""));
        if (!o.getCausaRaiz().isEmpty()) System.out.println("Causa raiz: " + o.getCausaRaiz());
        for (ConsumoRepuesto c : o.getConsumos()) System.out.printf("Repuesto: %s x%d ($%,.0f)%n", c.codigo(), c.cantidad(), c.costoTotal());
        if (o.getEstado() == EstadoOrden.CERRADA) System.out.printf("Costo total: $%,.0f%n", o.getCostoTotal());
        if (o.getTipo() == TipoOrden.PREVENTIVO && o.isAbierta()) {
            System.out.println("Checklist:");
            gestor.buscarEquipo(o.getTagEquipo()).getChecklist().forEach(t -> System.out.println("  [ ] " + t));
        }
    }

    private static void registrar() throws EntidadDuplicadaException {
        switch (leerEntero("1) Equipo  2) Tecnico  3) Repuesto -> ")) {
            case 1 -> {
                int tipo = leerEntero("Tipo: 1) Bomba  2) Motor  3) Valvula de control  4) Transmisor -> ");
                String tag = leerTexto("Tag: "), desc = leerTexto("Descripcion: "), ubic = leerTexto("Ubicacion: ");
                Criticidad crit = leerCriticidad();
                LocalDateTime puesta = gestor.getAhora().minusDays(leerEntero("Dias en servicio: "));
                Equipo e = switch (tipo) {
                    case 1 -> new Bomba(tag, desc, ubic, crit, puesta, leerDecimal("Potencia (kW): "));
                    case 2 -> new Motor(tag, desc, ubic, crit, puesta, leerDecimal("Potencia (kW): "));
                    case 3 -> new ValvulaControl(tag, desc, ubic, crit, puesta, leerTexto("Actuador: "));
                    case 4 -> new TransmisorCampo(tag, desc, ubic, crit, puesta, leerTexto("Variable medida: "));
                    default -> throw new IllegalArgumentException("Tipo de equipo no valido.");
                };
                gestor.registrarEquipo(e);
                System.out.println("Equipo registrado. Preventivo cada " + e.getIntervaloPreventivoDias() + " dias, lo atiende: " + e.getEspecialidadRequerida());
            }
            case 2 -> {
                String legajo = leerTexto("Legajo: "), nombre = leerTexto("Nombre: ");
                int esp = leerEntero("Especialidad: 1) Mecanico  2) Electricista  3) Instrumentista -> ");
                if (esp < 1 || esp > 3) throw new IllegalArgumentException("Especialidad no valida.");
                gestor.registrarTecnico(new Tecnico(legajo, nombre, Especialidad.values()[esp - 1], leerDecimal("Tarifa por hora: $")));
                System.out.println("Tecnico registrado.");
            }
            case 3 -> {
                gestor.registrarRepuesto(new Repuesto(leerTexto("Codigo: "), leerTexto("Descripcion: "),
                    leerEntero("Stock: "), leerEntero("Stock minimo: "), leerDecimal("Costo unitario: $")));
                System.out.println("Repuesto registrado.");
            }
            default -> System.out.println("Opcion no valida.");
        }
    }

    private static void stock() throws EntidadNoEncontradaException {
        gestor.getRepuestos().forEach(r -> System.out.printf("%-14s %-34s stock=%-4d min=%-3d $%,.0f %s%n", r.getCodigo(),
            recortar(r.getDescripcion(), 34), r.getStock(), r.getStockMinimo(), r.getCostoUnitario(), r.bajoMinimo() ? "<-- REPONER" : ""));
        if (leerTexto("Reponer algo? (s/n): ").equalsIgnoreCase("s")) {
            gestor.reponerStock(leerTexto("Codigo: "), leerEntero("Cantidad: "));
            System.out.println("Stock actualizado.");
        }
    }

    private static Criticidad leerCriticidad() {
        String c = leerTexto("Criticidad (A critico / B importante / C auxiliar): ").toUpperCase();
        try {
            return Criticidad.valueOf(c);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("La criticidad debe ser A, B o C.");
        }
    }

    // ---------- Lectura de datos con validacion ----------

    private static String recortar(String s, int max) { return s.length() <= max ? s : s.substring(0, max - 2) + ".."; }

    private static String leerTexto(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            String linea = scanner.nextLine().trim();
            if (!linea.isEmpty()) return linea;
            System.out.println("No puede estar vacio.");
        }
    }

    private static String leerTextoOpcional(String mensaje) {
        System.out.print(mensaje);
        return scanner.nextLine().trim();
    }

    private static int leerEntero(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Ingresa un numero entero valido.");
            }
        }
    }

    private static double leerDecimal(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            try {
                return Double.parseDouble(scanner.nextLine().trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                System.out.println("Ingresa un numero valido (ej: 12.5).");
            }
        }
    }
}
