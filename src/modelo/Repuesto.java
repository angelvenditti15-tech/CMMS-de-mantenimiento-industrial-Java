package modelo;

import excepciones.StockInsuficienteException;

import java.io.Serializable;

public class Repuesto implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String codigo;
    private final String descripcion;
    private int stock;
    private final int stockMinimo;
    private final double costoUnitario;

    public Repuesto(String codigo, String descripcion, int stock, int stockMinimo, double costoUnitario) {
        if (codigo == null || codigo.isBlank() || descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("Codigo y descripcion no pueden estar vacios.");
        }
        if (stock < 0 || stockMinimo < 0 || costoUnitario < 0) {
            throw new IllegalArgumentException("Stock, stock minimo y costo no pueden ser negativos.");
        }
        this.codigo = codigo.trim().toUpperCase();
        this.descripcion = descripcion.trim();
        this.stock = stock;
        this.stockMinimo = stockMinimo;
        this.costoUnitario = costoUnitario;
    }

    public void consumir(int cantidad) throws StockInsuficienteException {
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser positiva.");
        if (cantidad > stock) {
            throw new StockInsuficienteException(String.format(
                "Stock insuficiente de %s: hay %d y se piden %d.", codigo, stock, cantidad));
        }
        stock -= cantidad;
    }

    public void reponer(int cantidad) {
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser positiva.");
        stock += cantidad;
    }

    public boolean bajoMinimo() { return stock < stockMinimo; }

    public String getCodigo()        { return codigo; }
    public String getDescripcion()   { return descripcion; }
    public int getStock()            { return stock; }
    public int getStockMinimo()      { return stockMinimo; }
    public double getCostoUnitario() { return costoUnitario; }
}
