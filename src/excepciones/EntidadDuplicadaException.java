package excepciones;

/** Se intenta registrar algo que ya existe (mismo tag, legajo o codigo). */
public class EntidadDuplicadaException extends Exception {
    private static final long serialVersionUID = 1L;

    public EntidadDuplicadaException(String mensaje) {
        super(mensaje);
    }
}
