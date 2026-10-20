package pe.edu.utp.coronyx.backend.service.corovoz;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.PatientDto;
import pe.edu.utp.coronyx.backend.model.Appointment;
import pe.edu.utp.coronyx.backend.repository.AppointmentRepository;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.PatientService;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import static pe.edu.utp.coronyx.backend.service.corovoz.CoroVozHelp.*;

/** Consultas administrativas, sin mutaciones ni acceso a historia clínica. */
@Service
public class CoroVozPatientQueries {
    private final PatientService patients;
    private final AppointmentRepository appointments;
    private static final Set<String> ACTIVE_APPOINTMENTS = Set.of("PROGRAMADA", "CONFIRMADA", "EN_CURSO");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' uuuu 'a las' HH:mm", Locale.forLanguageTag("es-PE"));
    private static final Set<String> FILLERS = Set.of(
            "a", "al", "con", "cual", "cuales", "cuando", "de", "del", "el", "en", "es", "esta", "la", "las", "lo", "los",
            "me", "mi", "para", "por", "que", "quiero", "su", "sus", "tiene", "un", "una", "y", "sera", "seria", "fue",
            "proxima", "proximo", "siguiente", "ultima", "ultimo", "cita", "citas", "consulta", "consultas", "paciente",
            "telefono", "telefonico", "celular", "numero", "correo", "email", "contacto", "estado", "activo", "activa",
            "informacion", "general", "datos", "dato", "resumen", "administrativo", "administrativa", "dame", "dime", "puedes",
            "saber", "necesito", "ver", "buscar", "busca", "consultar", "muestra", "muestrame", "favor", "corovoz", "dni",
            "documento", "identidad", "llama", "llamado", "llamada", "persona", "ese", "esa", "este", "seleccionado",
            "acerca", "sobre", "quisiera", "podrias", "dar", "darme", "mostrar", "obtener", "hora", "fecha", "dia");

    public CoroVozPatientQueries(PatientService patients, AppointmentRepository appointments) {
        this.patients = patients; this.appointments = appointments;
    }

    public boolean handles(String text) {
        String q = IntentClassifier.plain(text);
        if (!has(q, "paciente") && has(q, "odontograma|version|versiones|historia|clinica|consultorio|usuario|usuarios|cuenta|cuentas")) return false;
        if (has(q, "como|donde|ejemplo|ejemplos|puedo|puedes") && !has(q, "dame|dime|muestra|muestrame|sera|telefono|correo|proxima|siguiente|ultima")) return false;
        if (has(q, "crear|registrar|registro|editar|edito|cambiar|cambio|actualizar|modificar|agendar|agenda|reprogramar|cancelar|activar|desactivar|recuperar|vincular")) return false;
        if (has(q, "usuarios|cuentas|coronyx|corovoz") && !has(q, "paciente|cita|telefono|correo")) return false;
        return has(q, "telefono|celular|correo|email|contacto|resumen")
                || has(q, "proxima|proximo|siguiente|ultima|ultimo") && has(q, "cita|consulta")
                || has(q, "cuando") && has(q, "cita|consulta")
                || has(q, "su") && has(q, "estado")
                || has(q, "paciente") && has(q, "datos|informacion|estado|activo|activa|alergias|antecedentes|medicamentos|diagnostico");
    }

    @Transactional(readOnly = true)
    public Answer answer(String text, String selectedId, UserPrincipal principal) {
        if (principal == null || !principal.hasOperativeClinic() || !Set.of("ODONTOLOGO", "RECEPCIONISTA").contains(principal.getRol()))
            throw new AccessDeniedException("Sesión no autorizada para consultar pacientes");
        String q = IntentClassifier.plain(text);
        if (has(q, "alergias|antecedentes|medicamentos|diagnostico|historia|tratamiento"))
            return new Answer("PACIENTE_CONSULTA", "En General solo consulto información administrativa. La historia clínica y los datos de salud se revisan en la ficha con el rol odontólogo.", "", "", true);
        List<String> search = Arrays.stream(q.split(" ")).filter(s -> !FILLERS.contains(s) && s.length() > 1).toList();
        PatientDto patient = null;
        if (selectedId != null) {
            try {
                var candidate = patients.getPatientById(Long.parseLong(selectedId), principal);
                if (search.isEmpty() || matches(candidate, search)) patient = candidate;
            } catch (NoSuchElementException ignored) {
                return missing();
            }
        }
        if (patient == null) {
            if (search.isEmpty()) return new Answer("PACIENTE_ELEGIR", "Indica el nombre o documento del paciente, por ejemplo «Próxima cita del paciente Ana Pérez». No seleccionaré a una persona por suposición.", "", "", true);
            List<PatientDto> found = patients.getAllPatients(principal).stream().filter(p -> matches(p, search)).limit(7).toList();
            if (found.isEmpty()) return missing();
            if (found.size() > 6) return new Answer("PACIENTE_ELEGIR", "Hay demasiadas coincidencias. Incluye ambos apellidos o escribe el documento para precisar el paciente.", "", "", true);
            if (found.size() > 1) return new Answer("PACIENTE_ELEGIR", "Encontré varias personas en tu clínica. Elige el paciente antes de consultar sus datos.", "", "", true,
                    found.stream().map(p -> new PatientChoice(p.getId(), name(p), hint(p.getNumeroDocumento()))).toList(), null);
            patient = found.getFirst();
        }
        // Se revalida el ID incluso cuando proviene de una coincidencia o del navegador.
        patient = patients.getPatientById(Long.parseLong(patient.getId()), principal);
        String prefix = name(patient) + ". ";
        String info;
        if (has(q, "telefono|celular")) info = "Teléfono registrado: " + value(patient.getTelefono()) + ".";
        else if (has(q, "correo|email")) info = "Correo registrado: " + value(patient.getCorreo()) + ".";
        else if (has(q, "estado|activo|activa")) info = "Estado de la ficha: " + value(patient.getEstado()) + ". El estado de su cuenta de acceso es independiente.";
        else if (has(q, "contacto")) info = "Teléfono: " + value(patient.getTelefono()) + ". Correo: " + value(patient.getCorreo()) + ".";
        else {
            List<Appointment> records = appointments.findByPacienteIdAndClinicaIdOrderByInicioEnDesc(Long.parseLong(patient.getId()), principal.getClinicaId());
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            if (has(q, "ultima|ultimo")) {
                var last = records.stream().filter(a -> a.getInicioEn() != null && a.getInicioEn().isBefore(now)
                        && !Set.of("CANCELADA", "REPROGRAMADA").contains(a.getEstado())).max(Comparator.comparing(Appointment::getInicioEn));
                info = last.map(a -> "Última cita registrada: " + describe(a) + ". Esto no confirma que haya asistido.").orElse("No hay una cita pasada registrada.");
            } else {
                var next = records.stream().filter(a -> a.getInicioEn() != null && a.getInicioEn().isAfter(now) && ACTIVE_APPOINTMENTS.contains(a.getEstado()))
                        .min(Comparator.comparing(Appointment::getInicioEn));
                String nextText = next.map(a -> "Próxima cita: " + describe(a) + ".").orElse("No tiene una próxima cita activa registrada.");
                info = has(q, "cita|consulta|cuando|proxima|siguiente") ? nextText :
                        "Estado: " + value(patient.getEstado()) + ". Teléfono: " + value(patient.getTelefono()) + ". Correo: " + value(patient.getCorreo()) + ". " + nextText;
            }
        }
        boolean agenda = has(q, "cita|consulta|cuando|proxima|siguiente|ultima");
        return new Answer("PACIENTE_DATOS", prefix + info, agenda ? "/app/agenda" : "/app/pacientes", agenda ? "Ir a Agenda" : "Ir a Pacientes", true, List.of(), patient.getId(), name(patient));
    }

    private boolean matches(PatientDto p, List<String> terms) {
        String name = " " + IntentClassifier.plain(name(p)) + " ";
        String document = IntentClassifier.plain(value(p.getNumeroDocumento()));
        return terms.stream().allMatch(t -> t.matches(".*\\d.*") ? document.equals(t) : name.contains(" " + t + " "));
    }
    private String describe(Appointment a) {
        return a.getInicioEn().atZoneSameInstant(ZoneId.of("America/Lima")).format(DATE)
                + " (hora de Perú), modalidad " + value(a.getModalidad()) + ", estado " + value(a.getEstado())
                + (a.getConsultorio() == null || a.getConsultorio().isBlank() ? "" : ", consultorio " + a.getConsultorio());
    }
    private static boolean has(String q, String alternatives) { return Pattern.compile("\\b(?:" + alternatives + ")\\b").matcher(q).find(); }
    private static String name(PatientDto p) { return (value(p.getNombres()) + " " + value(p.getApellidos())).trim(); }
    private static String hint(String document) { return document == null || document.length() < 4 ? "Sin documento" : "Documento termina en " + document.substring(document.length() - 4); }
    private static String value(String text) { return text == null || text.isBlank() ? "no registrado" : text; }
    private Answer missing() { return new Answer("PACIENTE_ELEGIR", "No encontré ese paciente en tu clínica. Revisa nombre y apellidos o escribe el documento exacto. No se consultan otras clínicas.", "", "", true); }
}
