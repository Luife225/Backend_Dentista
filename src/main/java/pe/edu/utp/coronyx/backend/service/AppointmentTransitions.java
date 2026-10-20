package pe.edu.utp.coronyx.backend.service;

import java.util.Map;
import java.util.Set;

/** Reglas compartidas: los estados terminales nunca se reabren implícitamente. */
public final class AppointmentTransitions {
    private AppointmentTransitions() {}

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "PROGRAMADA", Set.of("CONFIRMADA", "EN_ATENCION", "CANCELADA", "NO_ASISTIO"),
            "CONFIRMADA", Set.of("EN_ATENCION", "CANCELADA", "NO_ASISTIO"),
            "REPROGRAMADA", Set.of("CONFIRMADA", "EN_ATENCION", "CANCELADA", "NO_ASISTIO"),
            "EN_ATENCION", Set.of("FINALIZADA"));

    public static void requireTransition(String current, String next) {
        if (current.equals(next)) return;
        if (!ALLOWED.getOrDefault(current, Set.of()).contains(next)) {
            throw new IllegalStateException("No se puede cambiar una cita de " + current + " a " + next + ".");
        }
    }

    public static void requireReschedulable(String state) {
        if (!Set.of("PROGRAMADA", "CONFIRMADA", "REPROGRAMADA").contains(state)) {
            throw new IllegalStateException("No se puede reprogramar una cita en estado " + state + ".");
        }
    }
}
