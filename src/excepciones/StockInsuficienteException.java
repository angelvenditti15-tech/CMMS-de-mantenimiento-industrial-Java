package excepciones;

/** No hay stock suficiente del repuesto pedido. */
public class StockInsuficienteException extends Exception {
    private static final long serialVersionUID = 1L;

    public StockInsuficienteException(String mensaje) {
        super(mensaje);
    }
}
