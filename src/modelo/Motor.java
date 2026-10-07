package modelo;

import java.time.LocalDateTime;
import java.util.List;

public class Motor extends Equipo {

    private static final long serialVersionUID = 1L;

    private final double potenciaKw;

    public Motor(String tag, String descripcion, String ubicacion, Criticidad criticidad,
                 LocalDateTime puestaEnServicio, double potenciaKw) {
        super(tag, descripcion, ubicacion, criticidad, puestaEnServicio);
        this.potenciaKw = potenciaKw;
    }

    @Override public String getTipo()                    { return "Motor"; }
    @Override public int getIntervaloPreventivoDias()    { return 180; }
    @Override public Especialidad getEspecialidadRequerida() { return Especialidad.ELECTRICISTA; }

    @Override
    public List<String> getChecklist() {
        return List.of("Medir corriente por fase", "Medir aislacion (megger)",
                       "Verificar temperatura de bobinados", "Revisar bornera y puesta a tierra");
    }

    public double getPotenciaKw() { return potenciaKw; }
}
