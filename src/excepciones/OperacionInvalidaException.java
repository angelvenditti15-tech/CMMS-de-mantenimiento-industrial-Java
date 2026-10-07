package excepciones;

/** La operacion no corresponde al estado actual (transicion de orden invalida, regla de negocio incumplida). */
public class OperacionInvalidaException extends Exception {
    private static final long serialVersionUID = 1L;

    public OperacionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
