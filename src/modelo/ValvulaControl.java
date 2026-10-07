package modelo;

import java.time.LocalDateTime;
import java.util.List;

public class ValvulaControl extends Equipo {

    private static final long serialVersionUID = 1L;

    private final String actuador;

    public ValvulaControl(String tag, String descripcion, String ubicacion, Criticidad criticidad,
                          LocalDateTime puestaEnServicio, String actuador) {
        super(tag, descripcion, ubicacion, criticidad, puestaEnServicio);
        this.actuador = actuador == null ? "" : actuador.trim();
    }

    @Override public String getTipo()                    { return "Valvula de control"; }
    @Override public int getIntervaloPreventivoDias()    { return 365; }
    @Override public Especialidad getEspecialidadRequerida() { return Especialidad.INSTRUMENTISTA; }

    @Override
    public List<String> getChecklist() {
        return List.of("Verificar carrera completa (0-100 %)", "Revisar posicionador y aire de instrumentos",
                       "Controlar fugas en prensaestopas", "Verificar posicion de falla", "Registrar banda muerta");
    }

    public String getActuador() { return actuador; }
}
