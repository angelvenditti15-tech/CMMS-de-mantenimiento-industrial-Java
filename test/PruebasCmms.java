import excepciones.*;
import modelo.*;
import servicio.*;

import java.nio.file.*;
import java.time.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Pruebas sin dependencias externas. Ejecutar con:  java -ea -cp bin PruebasCmms */
public class PruebasCmms {

    /** Reloj que se puede avanzar a mano: permite probar MTBF / MTTR con horas exactas. */
    static class RelojTest extends Clock {
        private Instant ahora;
        RelojTest(Instant inicio) { this.ahora = inicio; }
        void avanzarHoras(long h) { ahora = ahora.plus(Duration.ofHours(h)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    private static int ok = 0;
    private static final Instant INICIO = Instant.parse("2026-01-01T00:00:00Z");

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("cmms-test");
        try {
            planesPreventivos();
            workflowDeOrdenes(temp);
            reglasDeAsignacion(temp);
            repuestos(temp);
            mtbfMttr(temp);
            preventivos(temp);
            reportes(temp);
            persistencia(temp);
            System.out.println("\nTodas las pruebas pasaron (" + ok + " verificaciones).");
        } finally {
            try (Stream<Path> s = Files.walk(temp)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static void check(boolean condicion, String descripcion) {
        if (!condicion) throw new AssertionError("FALLO: " + descripcion);
        ok++;
        System.out.println("  OK  " + descripcion);
    }

    private static boolean cerca(double a, double b, double tol) { return Math.abs(a - b) <= tol; }

    private static LocalDateTime t0() { return LocalDateTime.ofInstant(INICIO, ZoneOffset.UTC); }

    /** Gestor con un reloj controlable y datos basicos. Los equipos entran en servicio en el instante inicial. */
    private static GestorMantenimiento gestor(Path temp, String carpeta, RelojTest reloj) throws Exception {
        GestorMantenimiento g = new GestorMantenimiento(temp.resolve(carpeta).toString(), reloj);
        g.registrarEquipo(new Bomba("P-101", "Bomba de carga", "Bateria A", Criticidad.A, t0(), 55));
        g.registrarEquipo(new Motor("M-101", "Motor de P-101", "Bateria A", Criticidad.B, t0(), 55));
        g.registrarEquipo(new ValvulaControl("LV-301", "Valvula de nivel", "Tanques", Criticidad.C, t0(), "neumatico"));
        g.registrarTecnico(new Tecnico("T-01", "Carlos Medina", Especialidad.MECANICO, 1000));
        g.registrarTecnico(new Tecnico("T-02", "Lucia Paz", Especialidad.ELECTRICISTA, 2000));
        g.registrarTecnico(new Tecnico("T-03", "Marcos Rios", Especialidad.INSTRUMENTISTA, 3000));
        g.registrarRepuesto(new Repuesto("SELLO", "Sello mecanico", 3, 2, 1000));
        return g;
    }

    private static OrdenTrabajo falla(GestorMantenimiento g, String tag) throws Exception {
        return g.reportarFalla(tag, "no arranca", g.getAhora());
    }

    // ------------------------------------------------------------------

    private static void planesPreventivos() {
        System.out.println("Planes preventivos (polimorfismo)");
        Equipo b = new Bomba("P-1", "x", "", Criticidad.A, t0(), 10);
        Equipo m = new Motor("M-1", "x", "", Criticidad.A, t0(), 10);
        Equipo v = new ValvulaControl("LV-1", "x", "", Criticidad.A, t0(), "");
        Equipo t = new TransmisorCampo("PT-1", "x", "", Criticidad.A, t0(), "presion");
        check(b.getIntervaloPreventivoDias() == 90 && b.getEspecialidadRequerida() == Especialidad.MECANICO, "bomba: cada 90 dias, la atiende un mecanico");
        check(m.getIntervaloPreventivoDias() == 180 && m.getEspecialidadRequerida() == Especialidad.ELECTRICISTA, "motor: cada 180 dias, lo atiende un electricista");
        check(v.getIntervaloPreventivoDias() == 365 && v.getEspecialidadRequerida() == Especialidad.INSTRUMENTISTA, "valvula de control: anual, instrumentista");
        check(t.getEspecialidadRequerida() == Especialidad.INSTRUMENTISTA && !t.getChecklist().isEmpty(), "transmisor: instrumentista, con checklist propio");
        check(b.getProximoPreventivo().equals(LocalDate.of(2026, 4, 1)), "proximo preventivo desde la puesta en servicio + 90 dias");
        b.registrarPreventivo(LocalDate.of(2026, 3, 1));
        check(b.getProximoPreventivo().equals(LocalDate.of(2026, 5, 30)), "tras un preventivo, el plan se cuenta desde esa fecha");
        check(Prioridad.calcular(Criticidad.A, TipoOrden.CORRECTIVO) == Prioridad.EMERGENCIA
            && Prioridad.calcular(Criticidad.C, TipoOrden.CORRECTIVO) == Prioridad.MEDIA
            && Prioridad.calcular(Criticidad.A, TipoOrden.PREVENTIVO) == Prioridad.MEDIA
            && Prioridad.calcular(Criticidad.B, TipoOrden.PREVENTIVO) == Prioridad.BAJA, "prioridad segun criticidad y tipo de trabajo");
        try { new Tecnico("T", "x", Especialidad.MECANICO, 0); check(false, "tarifa"); }
        catch (IllegalArgumentException e) { check(true, "rechaza tecnico con tarifa 0"); }
    }

    private static void workflowDeOrdenes(Path temp) throws Exception {
        System.out.println("Flujo de la orden de trabajo");
        RelojTest reloj = new RelojTest(INICIO);
        GestorMantenimiento g = gestor(temp, "flujo", reloj);
        reloj.avanzarHoras(10);
        OrdenTrabajo o = falla(g, "p-101");
        check(o.getEstado() == EstadoOrden.ABIERTA && o.getPrioridad() == Prioridad.EMERGENCIA
            && g.buscarEquipo("P-101").getEstado() == EstadoEquipo.EN_FALLA, "reportar una falla abre la orden y pone el equipo EN_FALLA");

        try { g.iniciarOrden(o.getId()); check(false, "sin asignar"); }
        catch (OperacionInvalidaException e) { check(true, "no se puede iniciar una orden sin asignar"); }
        g.asignarOrden(o.getId(), "t-01");
        g.iniciarOrden(o.getId());
        check(o.getEstado() == EstadoOrden.EN_EJECUCION, "asignar + iniciar -> EN_EJECUCION");
        try { g.cancelarOrden(o.getId(), "x"); check(false, "cancelar en ejecucion"); }
        catch (OperacionInvalidaException e) { check(true, "no se puede cancelar una orden en ejecucion"); }
        try { g.cerrarOrden(o.getId(), 0, "causa", ""); check(false, "horas"); }
        catch (OperacionInvalidaException e) { check(true, "no se cierra con 0 horas-hombre"); }
        try { g.cerrarOrden(o.getId(), 2, "  ", ""); check(false, "causa"); }
        catch (OperacionInvalidaException e) { check(true, "una correctiva no se cierra sin causa raiz"); }
        reloj.avanzarHoras(4);
        g.cerrarOrden(o.getId(), 3, "Sello danado", "Cambio de sello");
        check(o.getEstado() == EstadoOrden.CERRADA && g.buscarEquipo("P-101").getEstado() == EstadoEquipo.OPERATIVO, "al cerrar, el equipo vuelve a OPERATIVO");
        check(cerca(o.getCostoManoObra(), 3000, 1e-9), "mano de obra = horas x tarifa del tecnico (3 h x 1000)");
        try { g.asignarOrden(o.getId(), "T-01"); check(false, "asignar cerrada"); }
        catch (OperacionInvalidaException e) { check(true, "una orden cerrada ya no se reasigna"); }

        OrdenTrabajo falsa = falla(g, "M-101");
        g.cancelarOrden(falsa.getId(), "Falsa alarma, era un fusible del tablero");
        check(falsa.getEstado() == EstadoOrden.CANCELADA && g.buscarEquipo("M-101").getEstado() == EstadoEquipo.OPERATIVO,
              "cancelar una falla falsa devuelve el equipo a OPERATIVO");
        try { g.reportarFalla("P-101", "x", g.getAhora().plusHours(1)); check(false, "futuro"); }
        catch (OperacionInvalidaException e) { check(true, "la falla no puede estar en el futuro"); }
        falla(g, "P-101");
        try { falla(g, "P-101"); check(false, "duplicada"); }
        catch (OperacionInvalidaException e) { check(true, "no se reporta dos veces la misma falla abierta"); }
        try { falla(g, "XX-999"); check(false, "no existe"); }
        catch (EntidadNoEncontradaException e) { check(true, "equipo inexistente lanza excepcion"); }
    }

    private static void reglasDeAsignacion(Path temp) throws Exception {
        System.out.println("Reglas de asignacion");
        RelojTest reloj = new RelojTest(INICIO);
        GestorMantenimiento g = gestor(temp, "asignacion", reloj);
        reloj.avanzarHoras(1);
        OrdenTrabajo o = falla(g, "P-101");
        try { g.asignarOrden(o.getId(), "T-02"); check(false, "especialidad"); }
        catch (OperacionInvalidaException e) { check(true, "un electricista no puede tomar una orden mecanica"); }
        g.asignarOrden(o.getId(), "T-01");
        g.asignarOrden(o.getId(), "T-01");
        check(o.getEstado() == EstadoOrden.ASIGNADA, "reasignar una orden ASIGNADA esta permitido");

        g.registrarEquipo(new Bomba("P-102", "x", "", Criticidad.B, t0(), 1));
        g.registrarEquipo(new Bomba("P-103", "x", "", Criticidad.B, t0(), 1));
        g.registrarEquipo(new Bomba("P-104", "x", "", Criticidad.B, t0(), 1));
        g.asignarOrden(falla(g, "P-102").getId(), "T-01");
        g.asignarOrden(falla(g, "P-103").getId(), "T-01");
        OrdenTrabajo cuarta = falla(g, "P-104");
        try { g.asignarOrden(cuarta.getId(), "T-01"); check(false, "limite"); }
        catch (OperacionInvalidaException e) { check(true, "un tecnico no puede tener mas de 3 ordenes activas"); }
        try { g.asignarOrden(cuarta.getId(), "T-99"); check(false, "tecnico"); }
        catch (EntidadNoEncontradaException e) { check(true, "tecnico inexistente lanza excepcion"); }
    }

    private static void repuestos(Path temp) throws Exception {
        System.out.println("Repuestos y costos");
        RelojTest reloj = new RelojTest(INICIO);
        GestorMantenimiento g = gestor(temp, "repuestos", reloj);
        reloj.avanzarHoras(1);
        OrdenTrabajo o = falla(g, "P-101");
        try { g.consumirRepuesto(o.getId(), "SELLO", 1); check(false, "no en ejecucion"); }
        catch (OperacionInvalidaException e) { check(true, "no se registran repuestos en una orden que no esta en ejecucion"); }
        check(g.buscarRepuesto("SELLO").getStock() == 3, "el intento fallido no toco el stock");

        g.asignarOrden(o.getId(), "T-01");
        g.iniciarOrden(o.getId());
        g.consumirRepuesto(o.getId(), "sello", 2);
        check(g.buscarRepuesto("SELLO").getStock() == 1 && o.getConsumos().size() == 1, "consumir descuenta del stock y queda en la orden");
        try { g.consumirRepuesto(o.getId(), "SELLO", 5); check(false, "stock"); }
        catch (StockInsuficienteException e) { check(true, "no se puede consumir mas de lo que hay"); }
        check(g.buscarRepuesto("SELLO").getStock() == 1 && o.getConsumos().size() == 1, "tras el error, ni el stock ni la orden cambiaron");
        check(g.getRepuestosBajoMinimo().size() == 1, "con stock 1 y minimo 2, el repuesto aparece para reponer");
        g.reponerStock("SELLO", 10);
        check(g.getRepuestosBajoMinimo().isEmpty(), "reponer lo saca de la lista");

        reloj.avanzarHoras(2);
        g.cerrarOrden(o.getId(), 2, "Sello danado", "Cambio de sello");
        check(cerca(o.getCostoRepuestos(), 2000, 1e-9) && cerca(o.getCostoTotal(), 4000, 1e-9), "costo total = mano de obra 2000 + repuestos 2000");
    }

    private static void mtbfMttr(Path temp) throws Exception {
        System.out.println("MTBF, MTTR y disponibilidad");
        RelojTest reloj = new RelojTest(INICIO);
        GestorMantenimiento g = gestor(temp, "mtbf", reloj);

        check(Double.isNaN(g.indicadores("P-101").mtbfHoras()), "sin tiempo transcurrido no hay indicadores");
        reloj.avanzarHoras(300);
        check(Double.isNaN(g.indicadores("P-101").mtbfHoras()) && g.indicadores("P-101").disponibilidadPct() == 100.0,
              "sin fallas: MTBF no calculable y disponibilidad 100 %");

        OrdenTrabajo f1 = falla(g, "P-101");            // falla a las 300 h
        reloj.avanzarHoras(4);
        g.asignarOrden(f1.getId(), "T-01"); g.iniciarOrden(f1.getId());
        reloj.avanzarHoras(6);
        g.cerrarOrden(f1.getId(), 5, "Sello", "");       // cerrada a las 310 h: 10 h de parada
        reloj.avanzarHoras(490);
        OrdenTrabajo f2 = falla(g, "P-101");            // falla a las 800 h
        g.asignarOrden(f2.getId(), "T-01"); g.iniciarOrden(f2.getId());
        reloj.avanzarHoras(20);
        g.cerrarOrden(f2.getId(), 5, "Rodamiento", ""); // cerrada a las 820 h: 20 h de parada
        reloj.avanzarHoras(180);                        // ahora = 1000 h

        Indicadores i = g.indicadores("P-101");
        check(i.fallas() == 2 && cerca(i.horasParada(), 30, 1e-9), "2 fallas y 30 h de parada en total");
        check(cerca(i.mttrHoras(), 15.0, 1e-9), "MTTR = 30 h / 2 reparaciones = 15 h");
        check(cerca(i.horasOperativas(), 970, 1e-9) && cerca(i.mtbfHoras(), 485.0, 1e-9), "MTBF = 970 h operativas / 2 fallas = 485 h");
        check(cerca(i.disponibilidadPct(), 97.0, 1e-9), "disponibilidad = 970 / 1000 = 97 %");

        falla(g, "P-101");                               // tercera falla, queda abierta
        reloj.avanzarHoras(5);
        Indicadores abierta = g.indicadores("P-101");
        check(abierta.fallas() == 3 && cerca(abierta.horasParada(), 35, 1e-9), "una falla abierta suma parada hasta ahora (5 h)");
        check(cerca(abierta.mttrHoras(), 15.0, 1e-9), "el MTTR solo cuenta reparaciones terminadas");
        check(cerca(abierta.disponibilidadPct(), 970.0 / 1005.0 * 100, 1e-9), "la disponibilidad baja mientras el equipo sigue parado");
        check(g.indicadores("M-101").fallas() == 0, "otros equipos no se ven afectados");
    }

    private static void preventivos(Path temp) throws Exception {
        System.out.println("Preventivos");
        RelojTest reloj = new RelojTest(INICIO);
        GestorMantenimiento g = gestor(temp, "preventivos", reloj);
        reloj.avanzarHoras(24L * 85);                    // dia 85: la bomba vence a los 90
        check(g.generarPreventivosPendientes(0).isEmpty(), "dia 85 sin anticipacion: nada vencio todavia");
        List<OrdenTrabajo> nuevas = g.generarPreventivosPendientes(7);
        check(nuevas.size() == 1 && nuevas.get(0).getTagEquipo().equals("P-101") && nuevas.get(0).getTipo() == TipoOrden.PREVENTIVO,
              "con 7 dias de anticipacion se abre el preventivo de la bomba (y solo el de la bomba)");
        check(g.generarPreventivosPendientes(7).isEmpty(), "no se duplica un preventivo ya abierto");

        OrdenTrabajo pm = nuevas.get(0);
        g.asignarOrden(pm.getId(), "T-01");
        g.iniciarOrden(pm.getId());
        check(g.buscarEquipo("P-101").getEstado() == EstadoEquipo.EN_MANTENIMIENTO, "durante el preventivo el equipo esta EN_MANTENIMIENTO");
        reloj.avanzarHoras(5);
        g.cerrarOrden(pm.getId(), 2, "", "Se siguio el checklist");
        check(g.buscarEquipo("P-101").getEstado() == EstadoEquipo.OPERATIVO, "al cerrar vuelve a OPERATIVO");
        check(pm.getTiempoParada(g.getAhora()).isZero() && g.indicadores("P-101").fallas() == 0, "un preventivo no cuenta como falla ni como parada");
        check(g.buscarEquipo("P-101").getProximoPreventivo().isAfter(g.getHoy().plusDays(85)), "el plan se reprograma desde la fecha del preventivo");
        check(g.generarPreventivosPendientes(0).isEmpty(), "no vuelve a generarse hasta que corresponda");

        g.buscarEquipo("M-101").setEstado(EstadoEquipo.FUERA_DE_SERVICIO);
        reloj.avanzarHoras(24L * 200);
        check(g.generarPreventivosPendientes(0).stream().noneMatch(o -> o.getTagEquipo().equals("M-101")), "un equipo fuera de servicio no genera preventivos");
        try { g.crearPreventiva("LV-301"); g.crearPreventiva("LV-301"); check(false, "duplicado"); }
        catch (OperacionInvalidaException e) { check(true, "no se abren dos preventivos para el mismo equipo"); }
    }

    private static void reportes(Path temp) throws Exception {
        System.out.println("Reportes");
        RelojTest reloj = new RelojTest(INICIO);
        GestorMantenimiento g = gestor(temp, "reportes", reloj);
        reloj.avanzarHoras(100);

        OrdenTrabajo a = falla(g, "LV-301");             // 2 h de parada, costo 3000*1 = 3000
        g.asignarOrden(a.getId(), "T-03"); g.iniciarOrden(a.getId());
        reloj.avanzarHoras(2);
        g.cerrarOrden(a.getId(), 1, "Diafragma", "");
        OrdenTrabajo b = falla(g, "P-101");              // 8 h de parada, costo 8*... 
        g.asignarOrden(b.getId(), "T-01"); g.iniciarOrden(b.getId());
        reloj.avanzarHoras(8);
        g.cerrarOrden(b.getId(), 4, "Sello", "");
        OrdenTrabajo c = falla(g, "M-101");              // 5 h de parada
        g.asignarOrden(c.getId(), "T-02"); g.iniciarOrden(c.getId());
        reloj.avanzarHoras(5);
        g.cerrarOrden(c.getId(), 1, "Bornera", "");

        List<RankingEquipo> ranking = g.getBadActors(2);
        check(ranking.size() == 2 && ranking.get(0).tag().equals("P-101") && ranking.get(1).tag().equals("M-101"),
              "bad actors: ordenados por horas de parada (P-101 8 h, M-101 5 h)");
        check(cerca(ranking.get(0).costo(), 4000, 1e-9), "el ranking incluye el costo de las ordenes cerradas");
        check(g.getCostosPorTipoEquipo().get("Bomba") == 4000.0 && g.getCostosPorTipoEquipo().get("Valvula de control") == 3000.0,
              "costos agrupados por tipo de equipo");
        check(cerca(g.getPorcentajePreventivo(), 0.0, 1e-9), "sin preventivos cerrados, el trabajo planificado es 0 %");

        g.crearPreventiva("LV-301");                     // BAJA
        falla(g, "P-101");                               // EMERGENCIA
        g.crearPreventiva("P-101").getId();              // MEDIA (criticidad A)
        List<OrdenTrabajo> backlog = g.getBacklog();
        check(backlog.size() == 3 && backlog.get(0).getPrioridad() == Prioridad.EMERGENCIA
            && backlog.get(1).getPrioridad() == Prioridad.MEDIA && backlog.get(2).getPrioridad() == Prioridad.BAJA,
              "backlog ordenado por prioridad: EMERGENCIA, MEDIA, BAJA");
    }

    private static void persistencia(Path temp) throws Exception {
        System.out.println("Persistencia");
        RelojTest reloj = new RelojTest(INICIO);
        GestorMantenimiento a = gestor(temp, "persist", reloj);
        reloj.avanzarHoras(50);
        OrdenTrabajo o = falla(a, "P-101");
        a.asignarOrden(o.getId(), "T-01"); a.iniciarOrden(o.getId());
        a.consumirRepuesto(o.getId(), "SELLO", 1);
        a.guardarDatos();

        GestorMantenimiento b = new GestorMantenimiento(temp.resolve("persist").toString(), reloj);
        check(b.getEquipos().size() == 3 && b.getTecnicos().size() == 3 && b.getRepuestos().size() == 1, "se recuperan equipos, tecnicos y repuestos");
        OrdenTrabajo r = b.buscarOrden(o.getId());
        check(r.getEstado() == EstadoOrden.EN_EJECUCION && r.getConsumos().size() == 1, "se recupera la orden con su estado y sus consumos");
        check(b.buscarRepuesto("SELLO").getStock() == 2, "se recupera el stock descontado");
        check(b.buscarEquipo("P-101").getEstado() == EstadoEquipo.EN_FALLA, "el equipo conserva su estado");
        reloj.avanzarHoras(3);
        b.cerrarOrden(r.getId(), 2, "Sello", "");
        check(b.buscarEquipo("P-101").getEstado() == EstadoEquipo.OPERATIVO && cerca(b.indicadores("P-101").horasParada(), 3, 1e-9),
              "la orden recuperada se puede seguir operando");
        check(falla(b, "M-101").getId() == o.getId() + 1, "los ids de orden continuan desde el ultimo guardado");
    }
}
