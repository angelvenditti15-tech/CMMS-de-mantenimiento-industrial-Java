package servicio;

import java.util.Locale;

/**
 * Indicadores de confiabilidad de un equipo. NaN significa "no calculable" (por ejemplo, MTBF sin fallas).
 *  - MTTR: tiempo medio de reparacion = parada total de fallas cerradas / cantidad de fallas cerradas.
 *  - MTBF: tiempo medio entre fallas = tiempo operativo / cantidad de fallas.
 *  - Disponibilidad: tiempo operativo / tiempo total desde la puesta en servicio.
 */
public record Indicadores(int fallas, int reparaciones, double horasParada, double mttrHoras,
                          double horasOperativas, double mtbfHoras, double disponibilidadPct) {

    private static String f(double v, String formato) {
        return Double.isNaN(v) ? "n/a" : String.format(Locale.US, formato, v);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "fallas=%d  parada=%.1f h  MTTR=%s  MTBF=%s  disponibilidad=%s",
            fallas, horasParada, f(mttrHoras, "%.1f h"), f(mtbfHoras, "%.1f h"), f(disponibilidadPct, "%.2f %%"));
    }
}
