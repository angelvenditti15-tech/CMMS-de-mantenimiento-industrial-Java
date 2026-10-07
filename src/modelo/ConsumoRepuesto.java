package modelo;

import java.io.Serializable;

/** Repuesto usado en una orden. Guarda el costo unitario del momento: el historial no cambia si luego sube el precio. */
public record ConsumoRepuesto(String codigo, int cantidad, double costoUnitario) implements Serializable {
    public double costoTotal() { return cantidad * costoUnitario; }
}
