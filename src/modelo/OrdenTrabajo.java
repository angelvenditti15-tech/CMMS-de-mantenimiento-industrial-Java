package modelo;

import excepciones.OperacionInvalidaException;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orden de trabajo con flujo de estados controlado:
 *
 *   ABIERTA -> ASIGNADA -> EN_EJECUCION -> CERRADA
 *      \___________\______________________> CANCELADA  (solo antes de empezar a ejecutarse)
 *
 * Cada transicion valida el estado actual y lanza OperacionInvalidaException si no corresponde.
 */
public class OrdenTrabajo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int id;
    private final String tagEquipo;
    private final TipoOrden tipo;
    private final Prioridad prioridad;
    private final Especialidad especialidadRequerida;
    private final String descripcion;
    private final LocalDateTime fechaApertura;
    private final LocalDateTime fechaFalla;       // solo correctivas: cuando dejo de funcionar el equipo

    private EstadoOrden estado = EstadoOrden.ABIERTA;
    private String legajoTecnico;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaCierre;
    private double horasHombre;
    private double costoManoObra;
    private String causaRaiz = "";
    private String acciones = "";
    private String motivoCancelacion = "";
    private final ArrayList<ConsumoRepuesto> consumos = new ArrayList<>();

    public OrdenTrabajo(int id, String tagEquipo, TipoOrden tipo, Prioridad prioridad, Especialidad especialidad,
                        String descripcion, LocalDateTime fechaApertura, LocalDateTime fechaFalla) {
        if (tipo == TipoOrden.CORRECTIVO && fechaFalla == null) {
            throw new IllegalArgumentException("Una orden correctiva necesita la fecha de la falla.");
        }
        this.id = id;
        this.tagEquipo = tagEquipo;
        this.tipo = tipo;
        this.prioridad = prioridad;
        this.especialidadRequerida = especialidad;
        this.descripcion = descripcion;
        this.fechaApertura = fechaApertura;
        this.fechaFalla = fechaFalla;
    }

    public void asignar(String legajo) throws OperacionInvalidaException {
        if (estado != EstadoOrden.ABIERTA && estado != EstadoOrden.ASIGNADA) {
            throw new OperacionInvalidaException("La orden #" + id + " esta " + estado + ": ya no se puede asignar.");
        }
        this.legajoTecnico = legajo;
        this.estado = EstadoOrden.ASIGNADA;
    }

    public void iniciar(LocalDateTime ahora) throws OperacionInvalidaException {
        if (estado != EstadoOrden.ASIGNADA) {
            throw new OperacionInvalidaException("La orden #" + id + " esta " + estado + ": solo se inicia una orden ASIGNADA.");
        }
        this.fechaInicio = ahora;
        this.estado = EstadoOrden.EN_EJECUCION;
    }

    public void registrarConsumo(ConsumoRepuesto consumo) throws OperacionInvalidaException {
        exigirEnEjecucion("registrar repuestos");
        consumos.add(consumo);
    }

    public void cerrar(double horas, double tarifaHora, String causa, String accionesRealizadas, LocalDateTime ahora)
            throws OperacionInvalidaException {
        exigirEnEjecucion("cerrar la orden");
        if (horas <= 0) throw new OperacionInvalidaException("Las horas-hombre deben ser mayores a 0.");
        if (ahora.isBefore(fechaInicio)) throw new OperacionInvalidaException("El cierre no puede ser anterior al inicio.");
        if (tipo == TipoOrden.CORRECTIVO && (causa == null || causa.isBlank())) {
            throw new OperacionInvalidaException("Una orden correctiva no se cierra sin indicar la causa raiz.");
        }
        this.horasHombre = horas;
        this.costoManoObra = horas * tarifaHora;
        this.causaRaiz = causa == null ? "" : causa.trim();
        this.acciones = accionesRealizadas == null ? "" : accionesRealizadas.trim();
        this.fechaCierre = ahora;
        this.estado = EstadoOrden.CERRADA;
    }

    public void cancelar(String motivo, LocalDateTime ahora) throws OperacionInvalidaException {
        if (estado != EstadoOrden.ABIERTA && estado != EstadoOrden.ASIGNADA) {
            throw new OperacionInvalidaException("La orden #" + id + " esta " + estado + ": no se puede cancelar.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new OperacionInvalidaException("Indica el motivo de la cancelacion.");
        }
        this.motivoCancelacion = motivo.trim();
        this.fechaCierre = ahora;
        this.estado = EstadoOrden.CANCELADA;
    }

    private void exigirEnEjecucion(String accion) throws OperacionInvalidaException {
        if (estado != EstadoOrden.EN_EJECUCION) {
            throw new OperacionInvalidaException("La orden #" + id + " esta " + estado + ": solo se puede " + accion + " cuando esta EN_EJECUCION.");
        }
    }

    /** Tiempo que el equipo estuvo (o esta) parado por esta falla. Solo cuentan las correctivas no canceladas. */
    public Duration getTiempoParada(LocalDateTime ahora) {
        if (tipo != TipoOrden.CORRECTIVO || estado == EstadoOrden.CANCELADA) return Duration.ZERO;
        LocalDateTime fin = estado == EstadoOrden.CERRADA ? fechaCierre : ahora;
        Duration d = Duration.between(fechaFalla, fin);
        return d.isNegative() ? Duration.ZERO : d;
    }

    public double getCostoRepuestos() {
        return consumos.stream().mapToDouble(ConsumoRepuesto::costoTotal).sum();
    }

    public double getCostoTotal() { return costoManoObra + getCostoRepuestos(); }

    public boolean isAbierta() {
        return estado == EstadoOrden.ABIERTA || estado == EstadoOrden.ASIGNADA || estado == EstadoOrden.EN_EJECUCION;
    }

    public int getId()                              { return id; }
    public String getTagEquipo()                    { return tagEquipo; }
    public TipoOrden getTipo()                      { return tipo; }
    public Prioridad getPrioridad()                 { return prioridad; }
    public Especialidad getEspecialidadRequerida()  { return especialidadRequerida; }
    public String getDescripcion()                  { return descripcion; }
    public LocalDateTime getFechaApertura()         { return fechaApertura; }
    public LocalDateTime getFechaFalla()            { return fechaFalla; }
    public EstadoOrden getEstado()                  { return estado; }
    public String getLegajoTecnico()                { return legajoTecnico; }
    public LocalDateTime getFechaInicio()           { return fechaInicio; }
    public LocalDateTime getFechaCierre()           { return fechaCierre; }
    public double getHorasHombre()                  { return horasHombre; }
    public double getCostoManoObra()                { return costoManoObra; }
    public String getCausaRaiz()                    { return causaRaiz; }
    public String getAcciones()                     { return acciones; }
    public String getMotivoCancelacion()            { return motivoCancelacion; }
    public List<ConsumoRepuesto> getConsumos()      { return Collections.unmodifiableList(consumos); }
}
