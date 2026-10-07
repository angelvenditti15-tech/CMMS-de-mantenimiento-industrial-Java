package modelo;

/** Prioridad de una orden, derivada de la criticidad del equipo y del tipo de trabajo. */
public enum Prioridad {
    EMERGENCIA, ALTA, MEDIA, BAJA;

    /** Una falla en un equipo critico es emergencia; el preventivo nunca supera la prioridad media. */
    public static Prioridad calcular(Criticidad criticidad, TipoOrden tipo) {
        if (tipo == TipoOrden.CORRECTIVO) {
            return switch (criticidad) {
                case A -> EMERGENCIA;
                case B -> ALTA;
                case C -> MEDIA;
            };
        }
        return criticidad == Criticidad.A ? MEDIA : BAJA;
    }
}
