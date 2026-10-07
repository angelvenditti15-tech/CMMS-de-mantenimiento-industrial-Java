package modelo;

/** Criticidad del equipo para el proceso: A = una falla frena o pone en riesgo la planta. */
public enum Criticidad {
    A("Critico"), B("Importante"), C("Auxiliar");

    private final String descripcion;

    Criticidad(String descripcion) { this.descripcion = descripcion; }

    public String getDescripcion() { return descripcion; }
}
