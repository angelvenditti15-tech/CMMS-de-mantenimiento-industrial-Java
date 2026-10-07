package modelo;

import java.time.LocalDateTime;
import java.util.List;

public class Bomba extends Equipo {

    private static final long serialVersionUID = 1L;

    private final double potenciaKw;

    public Bomba(String tag, String descripcion, String ubicacion, Criticidad criticidad,
                 LocalDateTime puestaEnServicio, double potenciaKw) {
        super(tag, descripcion, ubicacion, criticidad, puestaEnServicio);
        this.potenciaKw = potenciaKw;
    }

    @Override public String getTipo()                    { return "Bomba"; }
    @Override public int getIntervaloPreventivoDias()    { return 90; }
    @Override public Especialidad getEspecialidadRequerida() { return Especialidad.MECANICO; }

    @Override
    public List<String> getChecklist() {
        return List.of("Medir vibracion y temperatura de rodamientos", "Revisar sello mecanico (fugas)",
                       "Controlar presion de descarga", "Lubricar rodamientos", "Verificar alineacion del acople");
    }

    public double getPotenciaKw() { return potenciaKw; }
}
