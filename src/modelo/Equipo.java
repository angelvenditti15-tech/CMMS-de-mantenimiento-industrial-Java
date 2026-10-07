package modelo;

import interfaces.IMantenible;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Activo de la planta. Cada tipo concreto define su plan preventivo (intervalo, checklist y
 * especialidad que lo atiende): ese es el polimorfismo del sistema.
 */
public abstract class Equipo implements IMantenible, Serializable {

    private static final long serialVersionUID = 1L;

    private final String tag;
    private final String descripcion;
    private final String ubicacion;
    private final Criticidad criticidad;
    private final LocalDateTime puestaEnServicio;
    private EstadoEquipo estado = EstadoEquipo.OPERATIVO;
    private LocalDate ultimoPreventivo;

    protected Equipo(String tag, String descripcion, String ubicacion, Criticidad criticidad, LocalDateTime puestaEnServicio) {
        if (tag == null || tag.isBlank() || descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("Tag y descripcion no pueden estar vacios.");
        }
        if (criticidad == null || puestaEnServicio == null) {
            throw new IllegalArgumentException("Faltan la criticidad o la fecha de puesta en servicio.");
        }
        this.tag = tag.trim().toUpperCase();
        this.descripcion = descripcion.trim();
        this.ubicacion = ubicacion == null ? "" : ubicacion.trim();
        this.criticidad = criticidad;
        this.puestaEnServicio = puestaEnServicio;
    }

    public abstract String getTipo();

    /** Proximo preventivo: desde el ultimo realizado (o desde la puesta en servicio) mas el intervalo del plan. */
    @Override
    public LocalDate getProximoPreventivo() {
        LocalDate base = ultimoPreventivo != null ? ultimoPreventivo : puestaEnServicio.toLocalDate();
        return base.plusDays(getIntervaloPreventivoDias());
    }

    public void registrarPreventivo(LocalDate fecha) { this.ultimoPreventivo = fecha; }

    public String getTag()                      { return tag; }
    public String getDescripcion()              { return descripcion; }
    public String getUbicacion()                { return ubicacion; }
    public Criticidad getCriticidad()           { return criticidad; }
    public LocalDateTime getPuestaEnServicio()  { return puestaEnServicio; }
    public EstadoEquipo getEstado()             { return estado; }
    public LocalDate getUltimoPreventivo()      { return ultimoPreventivo; }
    public void setEstado(EstadoEquipo estado)  { this.estado = estado; }
}
