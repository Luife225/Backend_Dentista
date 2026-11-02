package pe.edu.utp.coronyx.backend.service.corovoz;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import pe.edu.utp.coronyx.backend.dto.*;
import pe.edu.utp.coronyx.backend.security.UserPrincipal;
import pe.edu.utp.coronyx.backend.service.AttachmentService;
import pe.edu.utp.coronyx.backend.service.ClinicalAttentionService;
import pe.edu.utp.coronyx.backend.service.OdontogramService;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only, authorized data adapters. No records or attachments are sent to an external model. */
@Service
public class CoroVozClinicalQueries {
    public static final Set<String> CLINICAL = Set.of("allergies", "background", "medications", "radiographs",
            "history", "diagnosis", "treatment", "prescriptions", "procedures", "evolution", "indications",
            "examination", "vitals", "odontogram", "blood_type");
    private final AttachmentService attachments;
    private final ClinicalAttentionService history;
    private final OdontogramService odontograms;

    @Autowired public CoroVozClinicalQueries(AttachmentService attachments, ClinicalAttentionService history, OdontogramService odontograms) {
        this.attachments = attachments;
        this.history = history;
        this.odontograms = odontograms;
    }

    public String answer(String field, PatientDto patient, UserPrincipal principal) {
        if (CLINICAL.contains(field) && !"ODONTOLOGO".equals(principal.getRol()))
            throw new AccessDeniedException("La consulta clínica requiere el rol odontólogo");
        return switch (field) {
            case "allergies" -> recorded("Alergias", patient.getAlergias());
            case "background" -> recorded("Antecedentes médicos", patient.getAntecedentesMedicos());
            case "medications" -> recorded("Medicamentos", patient.getMedicamentos());
            case "blood_type" -> recorded("Tipo de sangre", patient.getTipoSangre());
            case "insurance" -> recorded("Seguro", patient.getSeguro());
            case "address" -> recorded("Dirección", patient.getDireccion()) + " " + recorded("Ciudad", patient.getCiudad());
            case "birth_date" -> recorded("Fecha de nacimiento", patient.getFechaNacimiento() == null ? null : patient.getFechaNacimiento().toString());
            case "documents", "radiographs" -> files(field, patient, principal);
            case "odontogram" -> odontogram(patient, principal);
            case "history", "diagnosis", "treatment", "prescriptions", "procedures", "evolution", "indications", "examination", "vitals" -> clinicalHistory(field, patient);
            default -> null;
        };
    }

    private String odontogram(PatientDto patient, UserPrincipal principal) {
        var versions = odontograms.getHistory(Long.parseLong(patient.getId()), principal);
        var confirmed = versions.stream().filter(v -> "CONFIRMADO".equals(v.getEstado()))
                .max(Comparator.comparing(OdontogramaVersionDto::getNumeroVersion));
        if (confirmed.isEmpty()) return "No hay un odontograma confirmado. Hay " + versions.size() + " versiones registradas; los borradores requieren revisión.";
        var version = confirmed.get();
        String findings = version.getHallazgos().stream().limit(20)
                .map(f -> "Pieza " + f.getCodigoPiezaDental() + ", " + f.getCodigoCondicion() + ", cara " + f.getCodigoSuperficieCara()
                        + (f.getNotaObservacion() == null || f.getNotaObservacion().isBlank() ? "" : ": " + clean(f.getNotaObservacion())))
                .collect(Collectors.joining("; "));
        return "Fuente: odontograma confirmado, versión " + version.getNumeroVersion() + ", fecha " + version.getFechaConfirmacion() + ". "
                + (findings.isEmpty() ? "No hay hallazgos registrados en esta versión." : findings)
                + (version.getHallazgos().size() > 20 ? ". Muestro los primeros 20 hallazgos." : "");
    }

    private String files(String field, PatientDto patient, UserPrincipal principal) {
        boolean xray = field.equals("radiographs");
        // AttachmentService filters clinical/admin scope and rechecks clinic membership.
        var records = attachments.listByPatient(Long.parseLong(patient.getId()), principal).stream()
                .filter(a -> !xray || "RADIOGRAFIA".equals(a.getTipoArchivo())).toList();
        String label = xray ? "radiografías" : "archivos";
        if (records.isEmpty()) return "No hay " + label + " registrados accesibles para tu sesión en esta clínica.";
        return "Sí, tiene " + records.size() + " " + label + " registrados accesibles para tu sesión. "
                + records.stream().limit(5).map(a -> clean(a.getNombreOriginal()) + " (" + clean(a.getTipoArchivo())
                        + ", registrado: " + clean(a.getFechaCreacion()) + ")").collect(Collectors.joining("; "))
                + (records.size() > 5 ? ". Muestro los primeros 5." : ".")
                + " Fuente: adjuntos del expediente. Se consulta el registro del archivo, no su contenido.";
    }

    private String clinicalHistory(String field, PatientDto patient) {
        var encounters = history.getEncountersByPatient(Long.parseLong(patient.getId()));
        // A newer draft must never be presented as a signed clinical fact.
        var confirmed = encounters.stream().flatMap(e -> e.getVersiones().stream())
                .filter(v -> Boolean.TRUE.equals(v.getEstaConfirmado()))
                .sorted(Comparator.comparing(ClinicalVersionResponseDto::getFechaConfirmacion,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed()).toList();
        if (confirmed.isEmpty()) return "No hay versiones clínicas confirmadas disponibles. Hay " + encounters.size()
                + " atenciones registradas; los borradores requieren revisión del odontólogo.";
        var version = confirmed.getFirst();
        Map<String, Function<ClinicalVersionResponseDto, String>> fields = Map.of(
                "diagnosis", ClinicalVersionResponseDto::getDiagnostico, "treatment", ClinicalVersionResponseDto::getPlanTratamiento,
                "prescriptions", ClinicalVersionResponseDto::getRecetasPrescripciones, "procedures", ClinicalVersionResponseDto::getProcedimientos,
                "evolution", ClinicalVersionResponseDto::getEvolucion, "indications", ClinicalVersionResponseDto::getIndicaciones,
                "examination", ClinicalVersionResponseDto::getExamenClinico, "vitals", ClinicalVersionResponseDto::getSignosVitales);
        String value = field.equals("history")
                ? recorded("Motivo", version.getMotivoConsulta()) + " " + recorded("Diagnóstico", version.getDiagnostico())
                    + " " + recorded("Plan de tratamiento", version.getPlanTratamiento())
                : recorded("Dato solicitado", fields.get(field).apply(version));
        return "Fuente: última versión clínica confirmada, atención " + version.getAtencionClinicaId() + ", versión "
                + version.getNumeroVersion() + ", fecha " + version.getFechaConfirmacion() + ". " + value;
    }

    private static String recorded(String label, String value) {
        return value == null || value.isBlank() ? label + ": no hay información registrada en esta fuente."
                : label + " (registrado): " + clean(value) + ".";
    }
    private static String clean(String text) {
        if (text == null || text.isBlank()) return "sin especificar";
        String value = text.strip().replaceAll("[\\p{Cntrl}&&[^\\n]]", " ");
        return value.length() > 1800 ? value.substring(0, 1800) + "… (registro abreviado)" : value;
    }
}
