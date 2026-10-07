package servicio;

/** Una fila del ranking de "bad actors": los equipos que mas parada y costo generan. */
public record RankingEquipo(String tag, String tipo, int fallas, double horasParada, double costo) { }
