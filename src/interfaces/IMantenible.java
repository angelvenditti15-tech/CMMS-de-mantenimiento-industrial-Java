package interfaces;

import modelo.Especialidad;

import java.time.LocalDate;
import java.util.List;

/** Todo activo que tiene un plan de mantenimiento preventivo. */
public interface IMantenible {
    int getIntervaloPreventivoDias();
    Especialidad getEspecialidadRequerida();
    List<String> getChecklist();
    LocalDate getProximoPreventivo();
}
