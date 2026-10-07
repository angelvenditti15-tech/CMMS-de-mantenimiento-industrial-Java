package modelo;

import java.time.LocalDateTime;
import java.util.List;

public class TransmisorCampo extends Equipo {

    private static final long serialVersionUID = 1L;

    private final String variable;

    public TransmisorCampo(String tag, String descripcion, String ubicacion, Criticidad criticidad,
                           LocalDateTime puestaEnServicio, String variable) {
        super(tag, descripcion, ubicacion, criticidad, puestaEnServicio);
        this.variable = variable == null ? "" : variable.trim();
    }

    @Override public String getTipo()                    { return "Transmisor"; }
    @Override public int getIntervaloPreventivoDias()    { return 180; }
    @Override public Especialidad getEspecialidadRequerida() { return Especialidad.INSTRUMENTISTA; }

    @Override
    public List<String> getChecklist() {
        return List.of("Verificar cero y span contra patron", "Revisar lazo 4-20 mA",
                       "Controlar conexion a proceso (linea de impulso)", "Registrar error as-found / as-left");
    }

    public String getVariable() { return variable; }
}
