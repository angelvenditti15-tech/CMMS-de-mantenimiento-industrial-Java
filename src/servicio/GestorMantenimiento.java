package servicio;

import excepciones.*;
import modelo.*;
import repositorio.RepositorioArchivo;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Logica de negocio del CMMS. No imprime ni lee de consola: lanza excepciones y la interfaz decide como mostrarlas.
 * Recibe un Clock para poder probar con el tiempo controlado.
 */
public class GestorMantenimiento {

    private static final int MAX_ORDENES_ACTIVAS_POR_TECNICO = 3;

    private final Clock reloj;
    private final Map<String, Equipo> equipos = new HashMap<>();
    private final Map<String, Tecnico> tecnicos = new HashMap<>();
    private final Map<String, Repuesto> repuestos = new HashMap<>();
    private final List<OrdenTrabajo> ordenes;
    private int proximoId;

    private final RepositorioArchivo<Equipo> repoEquipos;
    private final RepositorioArchivo<Tecnico> repoTecnicos;
    private final RepositorioArchivo<Repuesto> repoRepuestos;
    private final RepositorioArchivo<OrdenTrabajo> repoOrdenes;

    public GestorMantenimiento() {
        this("datos", Clock.systemDefaultZone());
    }

    public GestorMantenimiento(String carpetaDatos, Clock reloj) {
        this.reloj = reloj;
        repoEquipos = new RepositorioArchivo<>(carpetaDatos, "equipos.dat");
        repoTecnicos = new RepositorioArchivo<>(carpetaDatos, "tecnicos.dat");
        repoRepuestos = new RepositorioArchivo<>(carpetaDatos, "repuestos.dat");
        repoOrdenes = new RepositorioArchivo<>(carpetaDatos, "ordenes.dat");

        repoEquipos.cargar().forEach(e -> equipos.put(e.getTag(), e));
        repoTecnicos.cargar().forEach(t -> tecnicos.put(t.getLegajo(), t));
        repoRepuestos.cargar().forEach(r -> repuestos.put(r.getCodigo(), r));
        ordenes = repoOrdenes.cargar();
        proximoId = ordenes.stream().mapToInt(OrdenTrabajo::getId).max().orElse(0) + 1;
    }

    public LocalDateTime getAhora() { return LocalDateTime.now(reloj); }
    public LocalDate getHoy()       { return LocalDate.now(reloj); }

    // ---------- Altas y busquedas ----------

    public void registrarEquipo(Equipo e) throws EntidadDuplicadaException {
        if (equipos.putIfAbsent(e.getTag(), e) != null) {
            throw new EntidadDuplicadaException("Ya existe un equipo con tag " + e.getTag());
        }
    }

    public void registrarTecnico(Tecnico t) throws EntidadDuplicadaException {
        if (tecnicos.putIfAbsent(t.getLegajo(), t) != null) {
            throw new EntidadDuplicadaException("Ya existe un tecnico con legajo " + t.getLegajo());
        }
    }

    public void registrarRepuesto(Repuesto r) throws EntidadDuplicadaException {
        if (repuestos.putIfAbsent(r.getCodigo(), r) != null) {
            throw new EntidadDuplicadaException("Ya existe un repuesto con codigo " + r.getCodigo());
        }
    }

    public Equipo buscarEquipo(String tag) throws EntidadNoEncontradaException {
        return buscar(equipos, tag, "equipo");
    }

    public Tecnico buscarTecnico(String legajo) throws EntidadNoEncontradaException {
        return buscar(tecnicos, legajo, "tecnico");
    }

    public Repuesto buscarRepuesto(String codigo) throws EntidadNoEncontradaException {
        return buscar(repuestos, codigo, "repuesto");
    }

    public OrdenTrabajo buscarOrden(int id) throws EntidadNoEncontradaException {
        return ordenes.stream().filter(o -> o.getId() == id).findFirst()
            .orElseThrow(() -> new EntidadNoEncontradaException("No existe la orden #" + id));
    }

    private static <T> T buscar(Map<String, T> mapa, String clave, String nombre) throws EntidadNoEncontradaException {
        T valor = mapa.get(clave == null ? "" : clave.trim().toUpperCase());
        if (valor == null) throw new EntidadNoEncontradaException("No existe el " + nombre + " '" + clave + "'");
        return valor;
    }

    // ---------- Flujo de ordenes de trabajo ----------

    /** Reporta una falla: abre una orden correctiva y pone el equipo EN_FALLA. */
    public OrdenTrabajo reportarFalla(String tag, String descripcion, LocalDateTime fechaFalla)
            throws EntidadNoEncontradaException, OperacionInvalidaException {
        Equipo e = buscarEquipo(tag);
        if (fechaFalla.isAfter(getAhora())) {
            throw new OperacionInvalidaException("La fecha de la falla no puede estar en el futuro.");
        }
        if (e.getEstado() == EstadoEquipo.FUERA_DE_SERVICIO) {
            throw new OperacionInvalidaException(e.getTag() + " esta fuera de servicio.");
        }
        if (hayOrdenAbierta(e.getTag(), TipoOrden.CORRECTIVO)) {
            throw new OperacionInvalidaException("Ya hay una falla abierta para " + e.getTag() + ".");
        }
        OrdenTrabajo o = nuevaOrden(e, TipoOrden.CORRECTIVO, "Falla: " + descripcion, fechaFalla);
        e.setEstado(EstadoEquipo.EN_FALLA);
        return o;
    }

    public OrdenTrabajo crearPreventiva(String tag) throws EntidadNoEncontradaException, OperacionInvalidaException {
        Equipo e = buscarEquipo(tag);
        if (hayOrdenAbierta(e.getTag(), TipoOrden.PREVENTIVO)) {
            throw new OperacionInvalidaException("Ya hay un preventivo abierto para " + e.getTag() + ".");
        }
        return nuevaOrden(e, TipoOrden.PREVENTIVO, "Preventivo: " + String.join("; ", e.getChecklist()), null);
    }

    /** Scheduler: abre un preventivo para cada equipo cuyo plan vence dentro de 'diasAnticipacion' dias (o ya vencio). */
    public List<OrdenTrabajo> generarPreventivosPendientes(int diasAnticipacion) {
        LocalDate limite = getHoy().plusDays(diasAnticipacion);
        List<OrdenTrabajo> nuevas = new ArrayList<>();
        for (Equipo e : equipos.values().stream().sorted(Comparator.comparing(Equipo::getTag)).toList()) {
            boolean corresponde = e.getEstado() != EstadoEquipo.FUERA_DE_SERVICIO
                && !e.getProximoPreventivo().isAfter(limite)
                && !hayOrdenAbierta(e.getTag(), TipoOrden.PREVENTIVO);
            if (corresponde) {
                nuevas.add(nuevaOrden(e, TipoOrden.PREVENTIVO, "Preventivo: " + String.join("; ", e.getChecklist()), null));
            }
        }
        return nuevas;
    }

    public void asignarOrden(int idOrden, String legajo)
            throws EntidadNoEncontradaException, OperacionInvalidaException {
        OrdenTrabajo o = buscarOrden(idOrden);
        Tecnico t = buscarTecnico(legajo);
        if (t.getEspecialidad() != o.getEspecialidadRequerida()) {
            throw new OperacionInvalidaException(String.format("%s es %s y la orden #%d requiere %s.",
                t.getNombre(), t.getEspecialidad(), o.getId(), o.getEspecialidadRequerida()));
        }
        long activas = ordenes.stream().filter(x -> x.isAbierta() && t.getLegajo().equals(x.getLegajoTecnico())
            && x.getId() != o.getId()).count();
        if (activas >= MAX_ORDENES_ACTIVAS_POR_TECNICO) {
            throw new OperacionInvalidaException(t.getNombre() + " ya tiene " + activas + " ordenes activas (maximo "
                + MAX_ORDENES_ACTIVAS_POR_TECNICO + ").");
        }
        o.asignar(t.getLegajo());
    }

    public void iniciarOrden(int idOrden) throws EntidadNoEncontradaException, OperacionInvalidaException {
        OrdenTrabajo o = buscarOrden(idOrden);
        o.iniciar(getAhora());
        if (o.getTipo() == TipoOrden.PREVENTIVO) {
            buscarEquipo(o.getTagEquipo()).setEstado(EstadoEquipo.EN_MANTENIMIENTO);
        }
    }

    /** Descuenta del stock y lo registra en la orden. Si algo falla, no se modifica ninguna de las dos cosas. */
    public void consumirRepuesto(int idOrden, String codigo, int cantidad)
            throws EntidadNoEncontradaException, OperacionInvalidaException, StockInsuficienteException {
        OrdenTrabajo o = buscarOrden(idOrden);
        Repuesto r = buscarRepuesto(codigo);
        if (o.getEstado() != EstadoOrden.EN_EJECUCION) {
            throw new OperacionInvalidaException("La orden #" + o.getId() + " esta " + o.getEstado()
                + ": solo se registran repuestos en una orden EN_EJECUCION.");
        }
        r.consumir(cantidad);
        o.registrarConsumo(new ConsumoRepuesto(r.getCodigo(), cantidad, r.getCostoUnitario()));
    }

    public void cerrarOrden(int idOrden, double horasHombre, String causaRaiz, String acciones)
            throws EntidadNoEncontradaException, OperacionInvalidaException {
        OrdenTrabajo o = buscarOrden(idOrden);
        Tecnico t = buscarTecnico(o.getLegajoTecnico());
        o.cerrar(horasHombre, t.getTarifaHora(), causaRaiz, acciones, getAhora());
        Equipo e = buscarEquipo(o.getTagEquipo());
        e.setEstado(EstadoEquipo.OPERATIVO);
        if (o.getTipo() == TipoOrden.PREVENTIVO) {
            e.registrarPreventivo(getHoy());
        }
    }

    public void cancelarOrden(int idOrden, String motivo) throws EntidadNoEncontradaException, OperacionInvalidaException {
        OrdenTrabajo o = buscarOrden(idOrden);
        o.cancelar(motivo, getAhora());
        if (o.getTipo() == TipoOrden.CORRECTIVO) {          // falsa alarma: el equipo no estaba fallado
            buscarEquipo(o.getTagEquipo()).setEstado(EstadoEquipo.OPERATIVO);
        }
    }

    public void reponerStock(String codigo, int cantidad) throws EntidadNoEncontradaException {
        buscarRepuesto(codigo).reponer(cantidad);
    }

    private OrdenTrabajo nuevaOrden(Equipo e, TipoOrden tipo, String descripcion, LocalDateTime fechaFalla) {
        OrdenTrabajo o = new OrdenTrabajo(proximoId++, e.getTag(), tipo, Prioridad.calcular(e.getCriticidad(), tipo),
            e.getEspecialidadRequerida(), descripcion, getAhora(), fechaFalla);
        ordenes.add(o);
        return o;
    }

    private boolean hayOrdenAbierta(String tag, TipoOrden tipo) {
        return ordenes.stream().anyMatch(o -> o.isAbierta() && o.getTipo() == tipo && o.getTagEquipo().equals(tag));
    }

    // ---------- Indicadores y reportes ----------

    public Indicadores indicadores(String tag) throws EntidadNoEncontradaException {
        Equipo e = buscarEquipo(tag);
        LocalDateTime ahora = getAhora();
        List<OrdenTrabajo> fallas = ordenes.stream()
            .filter(o -> o.getTagEquipo().equals(e.getTag()) && o.getTipo() == TipoOrden.CORRECTIVO
                && o.getEstado() != EstadoOrden.CANCELADA)
            .toList();
        List<OrdenTrabajo> cerradas = fallas.stream().filter(o -> o.getEstado() == EstadoOrden.CERRADA).toList();

        double paradaTotal = fallas.stream().mapToDouble(o -> horas(o.getTiempoParada(ahora))).sum();
        double paradaCerradas = cerradas.stream().mapToDouble(o -> horas(o.getTiempoParada(ahora))).sum();
        double horasTotales = horas(Duration.between(e.getPuestaEnServicio(), ahora));

        double mttr = cerradas.isEmpty() ? Double.NaN : paradaCerradas / cerradas.size();
        if (horasTotales <= 0) {
            return new Indicadores(fallas.size(), cerradas.size(), paradaTotal, mttr, Double.NaN, Double.NaN, Double.NaN);
        }
        double operativas = horasTotales - paradaTotal;
        double mtbf = fallas.isEmpty() ? Double.NaN : operativas / fallas.size();
        return new Indicadores(fallas.size(), cerradas.size(), paradaTotal, mttr, operativas, mtbf, operativas / horasTotales * 100.0);
    }

    /** Equipos ordenados por horas de parada (los "bad actors" que conviene atacar primero). */
    public List<RankingEquipo> getBadActors(int cantidad) {
        LocalDateTime ahora = getAhora();
        List<RankingEquipo> ranking = new ArrayList<>();
        for (Equipo e : equipos.values()) {
            List<OrdenTrabajo> deEquipo = ordenes.stream().filter(o -> o.getTagEquipo().equals(e.getTag())).toList();
            List<OrdenTrabajo> fallas = deEquipo.stream()
                .filter(o -> o.getTipo() == TipoOrden.CORRECTIVO && o.getEstado() != EstadoOrden.CANCELADA).toList();
            double parada = fallas.stream().mapToDouble(o -> horas(o.getTiempoParada(ahora))).sum();
            double costo = deEquipo.stream().filter(o -> o.getEstado() == EstadoOrden.CERRADA)
                .mapToDouble(OrdenTrabajo::getCostoTotal).sum();
            if (!fallas.isEmpty()) ranking.add(new RankingEquipo(e.getTag(), e.getTipo(), fallas.size(), parada, costo));
        }
        return ranking.stream()
            .sorted(Comparator.comparingDouble(RankingEquipo::horasParada).reversed().thenComparing(RankingEquipo::tag))
            .limit(cantidad).collect(Collectors.toList());
    }

    /** Costo de las ordenes cerradas agrupado por tipo de equipo. */
    public Map<String, Double> getCostosPorTipoEquipo() {
        Map<String, String> tipoPorTag = equipos.values().stream().collect(Collectors.toMap(Equipo::getTag, Equipo::getTipo));
        return ordenes.stream().filter(o -> o.getEstado() == EstadoOrden.CERRADA)
            .collect(Collectors.groupingBy(o -> tipoPorTag.get(o.getTagEquipo()), TreeMap::new,
                Collectors.summingDouble(OrdenTrabajo::getCostoTotal)));
    }

    /** % de ordenes cerradas que fueron preventivas. Un valor alto indica una planta mas planificada que "apagando incendios". */
    public double getPorcentajePreventivo() {
        List<OrdenTrabajo> cerradas = ordenes.stream().filter(o -> o.getEstado() == EstadoOrden.CERRADA).toList();
        if (cerradas.isEmpty()) return Double.NaN;
        return 100.0 * cerradas.stream().filter(o -> o.getTipo() == TipoOrden.PREVENTIVO).count() / cerradas.size();
    }

    /** Ordenes abiertas: primero las de mayor prioridad y, a igual prioridad, las mas antiguas. */
    public List<OrdenTrabajo> getBacklog() {
        return ordenes.stream().filter(OrdenTrabajo::isAbierta)
            .sorted(Comparator.comparing(OrdenTrabajo::getPrioridad).thenComparing(OrdenTrabajo::getFechaApertura))
            .collect(Collectors.toList());
    }

    public List<Equipo> getPreventivosVencidos() {
        return equipos.values().stream()
            .filter(e -> e.getProximoPreventivo().isBefore(getHoy()) && e.getEstado() != EstadoEquipo.FUERA_DE_SERVICIO)
            .sorted(Comparator.comparing(Equipo::getTag)).collect(Collectors.toList());
    }

    public List<Repuesto> getRepuestosBajoMinimo() {
        return repuestos.values().stream().filter(Repuesto::bajoMinimo)
            .sorted(Comparator.comparing(Repuesto::getCodigo)).collect(Collectors.toList());
    }

    public List<Equipo> getEquipos() {
        return equipos.values().stream().sorted(Comparator.comparing(Equipo::getTag)).collect(Collectors.toList());
    }

    public List<Tecnico> getTecnicos() {
        return tecnicos.values().stream().sorted(Comparator.comparing(Tecnico::getLegajo)).collect(Collectors.toList());
    }

    public List<Repuesto> getRepuestos() {
        return repuestos.values().stream().sorted(Comparator.comparing(Repuesto::getCodigo)).collect(Collectors.toList());
    }

    public List<OrdenTrabajo> getOrdenes() { return Collections.unmodifiableList(ordenes); }

    public List<OrdenTrabajo> getOrdenesDeEquipo(String tag) {
        String t = tag == null ? "" : tag.trim().toUpperCase();
        return ordenes.stream().filter(o -> o.getTagEquipo().equals(t)).collect(Collectors.toList());
    }

    private static double horas(Duration d) { return d.toSeconds() / 3600.0; }

    // ---------- Persistencia y datos de ejemplo ----------

    public void guardarDatos() {
        repoEquipos.guardar(new ArrayList<>(equipos.values()));
        repoTecnicos.guardar(new ArrayList<>(tecnicos.values()));
        repoRepuestos.guardar(new ArrayList<>(repuestos.values()));
        repoOrdenes.guardar(ordenes);
    }

    public boolean estaVacio() { return equipos.isEmpty(); }

    /** Carga una planta de ejemplo con equipos, tecnicos, stock e historial de fallas para ver MTBF / MTTR. */
    public void cargarDatosDeEjemplo() {
        LocalDateTime ahora = getAhora();
        try {
            registrarEquipo(new Bomba("P-101", "Bomba de carga de crudo", "Bateria A", Criticidad.A, ahora.minusDays(900), 55));
            registrarEquipo(new Bomba("P-102", "Bomba de reserva", "Bateria A", Criticidad.B, ahora.minusDays(700), 55));
            registrarEquipo(new Motor("M-101", "Motor de P-101", "Bateria A", Criticidad.A, ahora.minusDays(800), 55));
            registrarEquipo(new ValvulaControl("LV-301", "Valvula de control de nivel TK-301", "Tanques", Criticidad.B,
                ahora.minusDays(500), "neumatico, aire para abrir"));
            registrarEquipo(new TransmisorCampo("PT-101", "Transmisor de presion descarga P-101", "Bateria A", Criticidad.B,
                ahora.minusDays(600), "presion"));
            registrarEquipo(new TransmisorCampo("TT-201", "Transmisor de temperatura horno H-200", "Horno", Criticidad.C,
                ahora.minusDays(300), "temperatura"));

            registrarTecnico(new Tecnico("T-01", "Carlos Medina", Especialidad.MECANICO, 4500));
            registrarTecnico(new Tecnico("T-02", "Lucia Paz", Especialidad.ELECTRICISTA, 4800));
            registrarTecnico(new Tecnico("T-03", "Marcos Rios", Especialidad.INSTRUMENTISTA, 5200));

            registrarRepuesto(new Repuesto("SELLO-MEC-01", "Sello mecanico para bomba", 3, 2, 85000));
            registrarRepuesto(new Repuesto("RODAM-6309", "Rodamiento 6309", 8, 4, 12000));
            registrarRepuesto(new Repuesto("ACEITE-ISO68", "Aceite ISO 68 (litro)", 40, 20, 2500));
            registrarRepuesto(new Repuesto("DIAFR-LV", "Diafragma de actuador neumatico", 1, 2, 38000));
            registrarRepuesto(new Repuesto("EMPAQ-KIT", "Kit de empaquetadura de valvula", 5, 2, 9000));

            historico("P-101", 700, 8, 6, "Desgaste de rodamiento lado acople", new ConsumoRepuesto("RODAM-6309", 2, 12000));
            historico("P-101", 520, 12, 9, "Falla de sello mecanico", new ConsumoRepuesto("SELLO-MEC-01", 1, 85000));
            historico("P-101", 300, 6, 4, "Cavitacion por baja succion", new ConsumoRepuesto("ACEITE-ISO68", 4, 2500));
            historico("P-101", 120, 30, 16, "Falla de sello mecanico", new ConsumoRepuesto("SELLO-MEC-01", 1, 85000));
            historico("P-102", 400, 10, 7, "Desgaste de rodamiento lado acople", new ConsumoRepuesto("RODAM-6309", 2, 12000));
            historico("M-101", 350, 14, 8, "Aislacion degradada en bobinado");
            historico("M-101", 90, 5, 3, "Falso contacto en bornera");
            historico("LV-301", 450, 4, 3, "Fuga en prensaestopas", new ConsumoRepuesto("EMPAQ-KIT", 1, 9000));
            historico("LV-301", 260, 6, 4, "Diafragma de actuador roto", new ConsumoRepuesto("DIAFR-LV", 1, 38000));
            historico("LV-301", 60, 5, 3, "Diafragma de actuador roto", new ConsumoRepuesto("DIAFR-LV", 1, 38000));
            historico("PT-101", 200, 3, 2, "Deriva de cero por golpe de ariete");

            buscarEquipo("P-101").registrarPreventivo(getHoy().minusDays(100));
            buscarEquipo("P-102").registrarPreventivo(getHoy().minusDays(60));
            buscarEquipo("M-101").registrarPreventivo(getHoy().minusDays(50));
            buscarEquipo("LV-301").registrarPreventivo(getHoy().minusDays(400));
            buscarEquipo("PT-101").registrarPreventivo(getHoy().minusDays(20));
        } catch (EntidadDuplicadaException | EntidadNoEncontradaException e) {
            System.out.println("Los datos de ejemplo ya estaban cargados: " + e.getMessage());
        }
    }

    /** Crea una falla ya resuelta en el pasado (para el historial de ejemplo). */
    private void historico(String tag, int diasAtras, int horasParada, double horasHombre, String causa,
                           ConsumoRepuesto... consumos) throws EntidadNoEncontradaException {
        Equipo e = buscarEquipo(tag);
        Tecnico t = tecnicos.values().stream().filter(x -> x.getEspecialidad() == e.getEspecialidadRequerida())
            .findFirst().orElseThrow();
        LocalDateTime falla = getAhora().minusDays(diasAtras);
        OrdenTrabajo o = new OrdenTrabajo(proximoId++, tag, TipoOrden.CORRECTIVO,
            Prioridad.calcular(e.getCriticidad(), TipoOrden.CORRECTIVO), e.getEspecialidadRequerida(),
            "Falla: " + causa, falla.plusMinutes(20), falla);
        try {
            o.asignar(t.getLegajo());
            o.iniciar(falla.plusHours(1));
            for (ConsumoRepuesto c : consumos) o.registrarConsumo(c);
            o.cerrar(horasHombre, t.getTarifaHora(), causa, "Reparacion realizada", falla.plusHours(horasParada));
        } catch (OperacionInvalidaException ex) {
            throw new IllegalStateException("Historial de ejemplo invalido: " + ex.getMessage(), ex);
        }
        ordenes.add(o);
    }
}
