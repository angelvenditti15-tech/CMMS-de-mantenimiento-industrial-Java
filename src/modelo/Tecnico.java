package modelo;

import java.io.Serializable;
import java.util.Objects;

public class Tecnico implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String legajo;
    private final String nombre;
    private final Especialidad especialidad;
    private final double tarifaHora;

    public Tecnico(String legajo, String nombre, Especialidad especialidad, double tarifaHora) {
        if (legajo == null || legajo.isBlank() || nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Legajo y nombre no pueden estar vacios.");
        }
        if (especialidad == null) throw new IllegalArgumentException("Falta la especialidad.");
        if (tarifaHora <= 0) throw new IllegalArgumentException("La tarifa por hora debe ser positiva.");
        this.legajo = legajo.trim().toUpperCase();
        this.nombre = nombre.trim();
        this.especialidad = especialidad;
        this.tarifaHora = tarifaHora;
    }

    public String getLegajo()           { return legajo; }
    public String getNombre()           { return nombre; }
    public Especialidad getEspecialidad() { return especialidad; }
    public double getTarifaHora()       { return tarifaHora; }

    @Override public boolean equals(Object o) { return o instanceof Tecnico t && t.legajo.equals(legajo); }
    @Override public int hashCode()           { return Objects.hash(legajo); }
    @Override public String toString()        { return legajo + " " + nombre + " (" + especialidad + ")"; }
}
