package pe.edu.utp.coronyx.backend.service.corovoz;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CoroVozHelp {
    public record Answer(String intent, String answer, String route, String targetLabel, boolean understood) {}
    private record Article(String answer, String route, String label, boolean doctorOnly) {}
    private final IntentClassifier classifier;
    private static final Map<String, Article> ARTICLES = Map.ofEntries(
            Map.entry("CITA_NUEVA", new Article("Abre Agenda y pulsa «+ Nueva cita». Selecciona paciente, odontólogo y horario, completa los datos requeridos y confirma el registro. Comprueba el resultado en la agenda.", "/app/agenda", "Ir a Agenda", false)),
            Map.entry("CITA_CAMBIO", new Article("Abre Agenda y selecciona la cita para consultar sus detalles y las acciones disponibles. Para cambiar el horario, utiliza la opción de reprogramación; para anularla, la de cancelación. Revisa los datos antes de confirmar.", "/app/agenda", "Ir a Agenda", false)),
            Map.entry("PACIENTE_NUEVO", new Article("Abre Pacientes, pulsa «+ Nuevo paciente», completa los datos obligatorios y selecciona «Registrar paciente». Revisa el documento de identidad para evitar duplicados.", "/app/pacientes", "Ir a Pacientes", false)),
            Map.entry("PACIENTE_BUSCAR", new Article("Abre Pacientes y usa el buscador de la lista. Selecciona al paciente para abrir su ficha. Las pestañas disponibles dependen de tu rol.", "/app/pacientes", "Ir a Pacientes", false)),
            Map.entry("ODONTOGRAMA", new Article("Abre Pacientes, selecciona al paciente y entra en Odontograma. Registra la condición en la pieza y cara correspondientes. «Guardar borrador» conserva los cambios; «Confirmar versión» cierra esa versión y la hace inmutable. También puedes usar el modo Odontograma de CoroVoz con esa pestaña abierta.", "/app/pacientes", "Ir a Pacientes", true)),
            Map.entry("HISTORIA", new Article("Abre Pacientes, selecciona al paciente y entra en Historia clínica. Revisa las atenciones del paciente y utiliza las acciones disponibles para registrar una atención. Esta información clínica está reservada al odontólogo.", "/app/pacientes", "Ir a Pacientes", true)),
            Map.entry("DOCUMENTOS", new Article("Abre Pacientes y selecciona al paciente. En Documentos puedes consultar los archivos disponibles y utilizar la carga de adjuntos. El odontólogo también dispone de la pestaña Radiografías. Revisa el tipo y el paciente antes de adjuntar un archivo.", "/app/pacientes", "Ir a Pacientes", false)),
            Map.entry("NOTIFICACIONES", new Article("Abre Notificaciones para revisar los avisos de tu sesión. Consulta el contenido del aviso antes de actuar; el número del menú indica los pendientes de lectura.", "/app/notificaciones", "Ir a Notificaciones", false)),
            Map.entry("COROVOZ", new Article("Puedes escribir o pulsar «Iniciar escucha». En General pregunta cómo usar el sistema. Para dictar un odontograma, abre primero el odontograma del paciente. Di una pieza y condición por vez, revisa lo interpretado y usa «CoroVoz correcto», «CoroVoz edita» o «CoroVoz elimina». «CoroVoz terminado» detiene la escucha. Guarda el borrador desde el odontograma para conservar los cambios.", "", "", false)),
            Map.entry("DASHBOARD", new Article("Abre Dashboard para consultar el resumen de actividad y los indicadores disponibles para tu sesión. Desde el menú puedes continuar a Agenda o Pacientes para ver los registros correspondientes.", "/app/dashboard", "Ir a Dashboard", false)),
            Map.entry("INVENTARIO", new Article("Abre Inventario para revisar materiales, existencias y alertas de stock bajo. Usa los filtros y el buscador para localizar un material y revisa su unidad antes de ajustar cantidades.", "/app/inventario", "Ir a Inventario", false)),
            Map.entry("CAJA", new Article("Abre Caja y reportes para consultar el resumen y los movimientos disponibles. Revisa el período y los datos mostrados antes de usar la información en un reporte.", "/app/caja", "Ir a Caja y reportes", true)),
            Map.entry("TELEODONTOLOGIA", new Article("Abre Teleodontología para consultar las consultas virtuales programadas. Selecciona la consulta correspondiente y utiliza «Iniciar llamada» cuando corresponda a la atención.", "/app/teleodontologia", "Ir a Teleodontología", true)));

    public CoroVozHelp() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("corovoz/training.tsv").getInputStream(), StandardCharsets.UTF_8))) {
            List<IntentClassifier.Example> examples = reader.lines().filter(l -> !l.isBlank() && !l.startsWith("#"))
                    .map(l -> l.split("\\|", 2))
                    .map(parts -> new IntentClassifier.Example(parts[0], parts[1])).toList();
            if (examples.stream().anyMatch(e -> !ARTICLES.containsKey(e.intent()))) {
                throw new IllegalStateException("El corpus de CoroVoz contiene categorías sin respuesta");
            }
            classifier = new IntentClassifier(examples);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar el entrenamiento de CoroVoz", e);
        }
    }

    public Answer answer(String text, String role) {
        if (!Set.of("ODONTOLOGO", "RECEPCIONISTA").contains(role)) {
            throw new org.springframework.security.access.AccessDeniedException("Rol no autorizado para CoroVoz");
        }
        IntentClassifier.Prediction prediction = classifier.predict(text);
        Article article = ARTICLES.get(prediction.intent());
        if (article == null || prediction.similarity() < 0.25 || prediction.margin() < 0.035 || prediction.coverage() < 0.45) {
            return new Answer("UNKNOWN", "No pude identificar la consulta. Puedo orientarte sobre los módulos de Coronyx disponibles para tu rol. Pregunta por una tarea a la vez, por ejemplo: «¿Cómo registro un paciente?».", "", "", false);
        }
        if ((article.doctorOnly() && !"ODONTOLOGO".equals(role)) || ("INVENTARIO".equals(prediction.intent()) && !"RECEPCIONISTA".equals(role))) {
            return new Answer(prediction.intent(), "Esa función está reservada a otro rol. Consulta los módulos disponibles en tu menú.", "", "", true);
        }
        return new Answer(prediction.intent(), article.answer(), article.route(), article.label(), true);
    }
}
