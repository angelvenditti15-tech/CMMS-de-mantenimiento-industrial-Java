package excepciones;

/** Se busca un equipo, tecnico, repuesto u orden que no existe. */
public class EntidadNoEncontradaException extends Exception {
    private static final long serialVersionUID = 1L;

    public EntidadNoEncontradaException(String mensaje) {
        super(mensaje);
    }
}
