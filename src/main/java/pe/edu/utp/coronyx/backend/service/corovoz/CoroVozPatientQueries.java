package pe.edu.utp.coronyx.backend.service.corovoz;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.coronyx.backend.dto.PatientDto;
import pe.edu.utp.coronyx.backend.model.Appointment;
import pe.edu.utp.coronyx.backend.repository.AppointmentRepository;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.PatientService;
import java.time.*;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static pe.edu.utp.coronyx.backend.service.corovoz.CoroVozHelp.*;

/** Consultas de registros, sin mutaciones; permisos revalidados en cada consulta. */
@Service
public class CoroVozPatientQueries {
    private final PatientService patients;
    private final AppointmentRepository appointments;
    private final PythonCoroVozClient client;
    private final CoroVozClinicalQueries clinical;
    private static final Set<String> ACTIVE_APPOINTMENTS = Set.of("PROGRAMADA", "CONFIRMADA", "EN_CURSO");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' uuuu 'a las' HH:mm", Locale.forLanguageTag("es-PE"));

    @Autowired public CoroVozPatientQueries(PatientService patients, AppointmentRepository appointments, PythonCoroVozClient client, CoroVozClinicalQueries clinical) {
        this.patients = patients; this.appointments = appointments; this.client = client; this.clinical = clinical;
    }
    public CoroVozPatientQueries(PatientService patients, AppointmentRepository appointments, PythonCoroVozClient client) { this(patients, appointments, client, null); }
    public CoroVozPatientQueries(PatientService patients, AppointmentRepository appointments) { this(patients, appointments, new PythonCoroVozClient(), null); }

    private JsonNode plan(String text) { return client.post("/patient/analyze", Map.of("text", text)); }

    public boolean handles(String text) {
        return plan(text).path("handles").asBoolean();
    }

    @Transactional(readOnly = true)
    public Answer suggestName(String text, UserPrincipal principal) {
        String query = plain(text);
        if (!query.matches("[a-z ]{3,80}")) return null;
        List<String> terms = List.of(query.split(" "));
        if (terms.size() > 4) return null;
        List<PatientDto> found = patients.getAllPatients(principal).stream().filter(p -> matches(p, terms)).limit(7).toList();
        if (found.isEmpty() || found.size() > 6) return null;
        return new Answer("PACIENTE_ELEGIR", "¿Estás buscando a un paciente con ese nombre o apellido? Elige una opción y conservaré el paciente para tus siguientes preguntas.",
                "", "", true, found.stream().map(p -> new PatientChoice(p.getId(), name(p), hint(p.getNumeroDocumento()))).toList(), null);
    }

    @Transactional(readOnly = true)
    public Answer answer(String text, String selectedId, UserPrincipal principal) {
        return answer(text, selectedId, principal, false);
    }

    @Transactional(readOnly = true)
    public Answer answer(String text, String selectedId, UserPrincipal principal, boolean confirmed) {
        if (principal == null || !principal.hasOperativeClinic() || !Set.of("ODONTOLOGO", "RECEPCIONISTA").contains(principal.getRol()))
            throw new AccessDeniedException("Sesión no autorizada para consultar pacientes");
        JsonNode plan = plan(text);
        if (plan.path("restricted").asBoolean() && !"ODONTOLOGO".equals(principal.getRol()))
            return new Answer("PACIENTE_CONSULTA", "Los datos clínicos requieren el rol odontólogo. Con tu sesión puedo consultar datos administrativos y documentos autorizados.", "", "", true);
        List<String> search = new ArrayList<>();
        plan.path("search").forEach(term -> search.add(term.asText()));
        boolean draft = plan.path("field").asText().equals("appointment_draft");
        if (draft && !plan.path("appointment").path("clarification").asText().isBlank())
            return new Answer("CITA_ACLARAR", plan.path("appointment").path("clarification").asText(), "", "", false);
        PatientDto patient = null;
        if (selectedId != null) {
            try {
                var candidate = patients.getPatientById(Long.parseLong(selectedId), principal);
                if (confirmed || search.isEmpty() || matches(candidate, search)) patient = candidate;
            } catch (NoSuchElementException ignored) {
                return missing();
            }
        }
        if (patient == null) {
            if (draft && search.isEmpty() && selectedId == null) return appointmentDraft(plan, null);
            if (search.isEmpty()) return new Answer("PACIENTE_ELEGIR", "Indica el nombre o documento del paciente, por ejemplo «Próxima cita del paciente Ana Pérez». No seleccionaré a una persona por suposición.", "", "", true);
            var available = patients.getAllPatients(principal);
            List<PatientDto> found = available.stream().filter(p -> matches(p, search)).limit(7).toList();
            boolean approximate = false;
            if (found.isEmpty()) {
                // Speech may lose a name or add filler. Partial matches only offer choices.
                found = available.stream().filter(p -> partialMatch(p, search)).limit(7).toList();
                approximate = true;
            }
            if (found.isEmpty()) return missing();
            if (found.size() > 6) return new Answer("PACIENTE_ELEGIR", "Hay demasiadas coincidencias. Incluye ambos apellidos o escribe el documento para precisar el paciente.", "", "", true);
            if (found.size() > 1 || approximate || search.size() == 1 && !search.getFirst().matches(".*\\d.*")) return new Answer("PACIENTE_ELEGIR", "¿Buscas a alguna de estas personas? Elige por nombre o número" + (found.size() == 1 ? ", o di «sí»" : "") + ". Conservaré tu pregunta para responderla al elegir.", "", "", true,
                    found.stream().map(p -> new PatientChoice(p.getId(), name(p), hint(p.getNumeroDocumento()))).toList(), null);
            patient = found.getFirst();
        }
        // Se revalida el ID incluso cuando proviene de una coincidencia o del navegador.
        patient = patients.getPatientById(Long.parseLong(patient.getId()), principal);
        if (draft) return appointmentDraft(plan, patient);
        String prefix = name(patient) + ". ";
        String info;
        String field = plan.path("field").asText();
        List<String> responses = new ArrayList<>();
        if (clinical != null) {
            var fields = plan.path("fields");
            if (fields.isArray()) for (var requested : fields) {
                String response = clinical.answer(requested.asText(), patient, principal);
                if (response != null) responses.add(response);
            }
        }
        String clinicalAnswer = responses.isEmpty() ? null : String.join("\n", responses);
        if (clinicalAnswer != null) info = clinicalAnswer;
        else if (CoroVozClinicalQueries.CLINICAL.contains(field) || Set.of("documents", "insurance", "address", "birth_date").contains(field))
            return new Answer("PACIENTE_DATOS", prefix + "No pude consultar esa fuente. No puedo confirmar si hay información registrada.", "", "", false, List.of(), patient.getId(), name(patient));
        else if (field.equals("phone")) info = "Teléfono registrado: " + value(patient.getTelefono()) + ".";
        else if (field.equals("email")) info = "Correo registrado: " + value(patient.getCorreo()) + ".";
        else if (field.equals("status")) info = "Estado de la ficha: " + value(patient.getEstado()) + ". El estado de su cuenta de acceso es independiente.";
        else if (field.equals("contact")) info = "Teléfono: " + value(patient.getTelefono()) + ". Correo: " + value(patient.getCorreo()) + ".";
        else {
            List<Appointment> records = appointments.findByPacienteIdAndClinicaIdOrderByInicioEnDesc(Long.parseLong(patient.getId()), principal.getClinicaId());
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            if (field.equals("last_appointment")) {
                var last = records.stream().filter(a -> a.getInicioEn() != null && a.getInicioEn().isBefore(now)
                        && !Set.of("CANCELADA", "REPROGRAMADA").contains(a.getEstado())).max(Comparator.comparing(Appointment::getInicioEn));
                info = last.map(a -> "Última cita registrada: " + describe(a) + ". Esto no confirma que haya asistido.").orElse("No hay una cita pasada registrada.");
            } else {
                var next = records.stream().filter(a -> a.getInicioEn() != null && a.getInicioEn().isAfter(now) && ACTIVE_APPOINTMENTS.contains(a.getEstado()))
                        .min(Comparator.comparing(Appointment::getInicioEn));
                String nextText = next.map(a -> "Próxima cita: " + describe(a) + ".").orElse("No tiene una próxima cita activa registrada.");
                info = field.equals("next_appointment") ? nextText :
                        "Estado: " + value(patient.getEstado()) + ". Teléfono: " + value(patient.getTelefono()) + ". Correo: " + value(patient.getCorreo()) + ". " + nextText;
            }
        }
        boolean agenda = plan.path("agenda").asBoolean();
        return new Answer("PACIENTE_DATOS", prefix + info, agenda ? "/app/agenda" : "/app/pacientes", agenda ? "Ir a Agenda" : "Ir a Pacientes", true, List.of(), patient.getId(), name(patient));
    }

    private boolean matches(PatientDto p, List<String> terms) {
        String name = " " + plain(name(p)) + " ";
        String document = plain(value(p.getNumeroDocumento()));
        return terms.stream().allMatch(t -> t.matches(".*\\d.*") ? document.equals(t) : name.contains(" " + t + " "));
    }
    private Answer appointmentDraft(JsonNode plan, PatientDto patient) {
        var data = plan.path("appointment");
        var draft = new AppointmentDraft(data.path("date").asText(), data.path("time").asText(), patient == null ? null : patient.getId(),
                data.path("reason").asText(), data.path("modality").asText());
        return new Answer("CITA_BORRADOR", "Prepararé una cita para " + draft.date() + " a las " + draft.time()
                + " (hora de Perú)" + (patient == null ? "" : " para " + name(patient))
                + ". Pulsa Redirigir para revisar el formulario, elegir un horario disponible y confirmar la cita.",
                "/app/agenda", "Redirigir a la cita", true, List.of(), patient == null ? null : patient.getId(),
                patient == null ? null : name(patient), List.of(), draft);
    }
    private boolean partialMatch(PatientDto p, List<String> terms) {
        if (terms.stream().anyMatch(t -> t.matches(".*\\d.*"))) return false;
        var names = Arrays.asList(plain(name(p)).split(" "));
        return terms.stream().anyMatch(t -> t.length() >= 3 && names.stream().anyMatch(n -> n.equals(t)
                || t.length() >= 5 && oneEdit(n, t)));
    }
    private boolean oneEdit(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) return false;
        int i = 0, j = 0, edits = 0;
        while (i < a.length() && j < b.length()) {
            if (a.charAt(i) == b.charAt(j)) { i++; j++; }
            else { if (++edits > 1) return false; if (a.length() >= b.length()) i++; if (b.length() >= a.length()) j++; }
        }
        return edits + a.length() - i + b.length() - j <= 1;
    }
    private String describe(Appointment a) {
        return a.getInicioEn().atZoneSameInstant(ZoneId.of("America/Lima")).format(DATE)
                + " (hora de Perú), modalidad " + value(a.getModalidad()) + ", estado " + value(a.getEstado())
                + (a.getConsultorio() == null || a.getConsultorio().isBlank() ? "" : ", consultorio " + a.getConsultorio());
    }
    private static String plain(String text) { return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "").replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim(); }
    private static String name(PatientDto p) { return (value(p.getNombres()) + " " + value(p.getApellidos())).trim(); }
    private static String hint(String document) { return document == null || document.length() < 4 ? "Sin documento" : "Documento termina en " + document.substring(document.length() - 4); }
    private static String value(String text) { return text == null || text.isBlank() ? "no registrado" : text; }
    private Answer missing() { return new Answer("PACIENTE_ELEGIR", "No encontré ese paciente en tu clínica. Revisa nombre y apellidos o escribe el documento exacto. No se consultan otras clínicas.", "", "", true); }
}
