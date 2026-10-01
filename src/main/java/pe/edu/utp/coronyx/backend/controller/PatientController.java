package pe.edu.utp.coronyx.backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.utp.coronyx.backend.dto.PatientDto;
import pe.edu.utp.coronyx.backend.service.PatientService;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    /** GET /api/v1/patients - Lista todos los pacientes ordenados por fecha de registro desc */
    @GetMapping
    public ResponseEntity<List<PatientDto>> getAllPatients() {
        return ResponseEntity.ok(patientService.getAllPatients());
    }

    /** GET /api/v1/patients/{id} - Ficha completa de un paciente por UUID */
    @GetMapping("/{id}")
    public ResponseEntity<PatientDto> getPatientById(@PathVariable UUID id) {
        return ResponseEntity.ok(patientService.getPatientById(id));
    }

    /** POST /api/v1/patients - Registra un nuevo paciente en la clínica */
    @PostMapping
    public ResponseEntity<PatientDto> createPatient(@Valid @RequestBody PatientDto dto) {
        PatientDto created = patientService.createPatient(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** PUT /api/v1/patients/{id} - Actualiza la ficha del paciente (solo campos presentes en el body) */
    @PutMapping("/{id}")
    public ResponseEntity<PatientDto> updatePatient(@PathVariable UUID id,
                                                    @RequestBody PatientDto dto) {
        return ResponseEntity.ok(patientService.updatePatient(id, dto));
    }

    /** PATCH /api/v1/patients/{id}/archive - Baja lógica: cambia estado a 'ARCHIVADO' */
    @PatchMapping("/{id}/archive")
    public ResponseEntity<PatientDto> archivePatient(@PathVariable UUID id) {
        return ResponseEntity.ok(patientService.archivePatient(id));
    }

    /** Manejo de paciente no encontrado → 404 */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    /** Manejo de estado inválido (ej: archivar un paciente ya archivado) → 409 */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage()));
    }
}

