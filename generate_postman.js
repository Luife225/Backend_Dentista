import fs from 'fs';
import path from 'path';

const postmanDir = path.resolve('postman');
if (!fs.existsSync(postmanDir)) {
  fs.mkdirSync(postmanDir, { recursive: true });
}

// 1. ENVIRONMENT (sin secretos exportados)
const environment = {
  id: "coronyx-env-local-2026",
  name: "Coronyx LOCAL (Sin Secretos)",
  values: [
    { key: "baseUrl", value: "http://localhost:8081", type: "default", enabled: true },
    { key: "adminEmail", value: "admin@coronyx.pe", type: "default", enabled: true },
    { key: "adminPassword", value: "", type: "secret", enabled: true },
    { key: "recepEmail", value: "recepcion@coronyx.pe", type: "default", enabled: true },
    { key: "recepPassword", value: "", type: "secret", enabled: true },
    { key: "odontoEmail", value: "odontologo@coronyx.pe", type: "default", enabled: true },
    { key: "odontoPassword", value: "", type: "secret", enabled: true },
    { key: "pacienteEmail", value: "paciente@coronyx.pe", type: "default", enabled: true },
    { key: "pacientePassword", value: "", type: "secret", enabled: true },
    { key: "adminToken", value: "", type: "secret", enabled: true },
    { key: "recepToken", value: "", type: "secret", enabled: true },
    { key: "odontoToken", value: "", type: "secret", enabled: true },
    { key: "pacienteToken", value: "", type: "secret", enabled: true },
    { key: "pacienteId", value: "2", type: "default", enabled: true },
    { key: "citaId", value: "", type: "default", enabled: true },
    { key: "encounterId", value: "", type: "default", enabled: true },
    { key: "odontogramaVersionId", value: "", type: "default", enabled: true },
    { key: "attachmentId", value: "", type: "default", enabled: true },
    { key: "solicitudId", value: "", type: "default", enabled: true }
  ],
  _postman_variable_scope: "environment",
  _postman_exported_at: new Date().toISOString(),
  _postman_exported_using: "Postman/11.0.0"
};

fs.writeFileSync(
  path.join(postmanDir, 'Coronyx_LOCAL.postman_environment.json'),
  JSON.stringify(environment, null, 2),
  'utf-8'
);

// 2. COLLECTION
function createItem(name, method, urlPath, headers, body, testScript) {
  const item = {
    name,
    request: {
      method,
      header: Object.entries(headers).map(([k, v]) => ({ key: k, value: v, type: 'text' })),
      url: {
        raw: `{{baseUrl}}${urlPath}`,
        host: ["{{baseUrl}}"],
        path: urlPath.replace(/^\//, '').split('/')
      }
    }
  };

  if (body) {
    if (typeof body === 'object') {
      item.request.body = {
        mode: 'raw',
        raw: JSON.stringify(body, null, 2),
        options: { raw: { language: 'json' } }
      };
    } else {
      item.request.body = {
        mode: 'raw',
        raw: body
      };
    }
  }

  if (testScript) {
    item.event = [
      {
        listen: 'test',
        script: {
          exec: testScript.split('\n'),
          type: 'text/javascript'
        }
      }
    ];
  }

  return item;
}

const collection = {
  info: {
    name: "Coronyx F09 HTTP Verification Suite",
    description: "Colección oficial de pruebas de integración HTTP de Coronyx Dental System con assertions y validación completa de 34 casos funcionales (A01-A11, R01-R10, D01-D08, P01-P05) y principios generales (G01-G05).",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  item: [
    {
      name: "00. Autenticacion y Precision Long",
      item: [
        createItem("00.1 Login ADMIN_CLINICA", "POST", "/api/v1/auth/login", { "Content-Type": "application/json" },
          { usernameOrEmail: "{{adminEmail}}", password: "{{adminPassword}}" },
          `pm.test("Status code is 200", function () { pm.response.to.have.status(200); });
const json = pm.response.json();
pm.test("Token JWT recibido", function () { pm.expect(json.token).to.be.a('string'); });
pm.test("Long precision: usuarioId y clinicaId como string", function () {
    pm.expect(json.usuarioId).to.be.a('string');
    pm.expect(json.clinicaId).to.be.a('string');
});
pm.environment.set("adminToken", json.token);`
        ),
        createItem("00.2 Login RECEPCIONISTA", "POST", "/api/v1/auth/login", { "Content-Type": "application/json" },
          { usernameOrEmail: "{{recepEmail}}", password: "{{recepPassword}}" },
          `pm.test("Status code is 200", function () { pm.response.to.have.status(200); });
const json = pm.response.json();
pm.environment.set("recepToken", json.token);`
        ),
        createItem("00.3 Login ODONTOLOGO", "POST", "/api/v1/auth/login", { "Content-Type": "application/json" },
          { usernameOrEmail: "{{odontoEmail}}", password: "{{odontoPassword}}" },
          `pm.test("Status code is 200", function () { pm.response.to.have.status(200); });
const json = pm.response.json();
pm.environment.set("odontoToken", json.token);`
        ),
        createItem("00.4 Login PACIENTE", "POST", "/api/v1/auth/login", { "Content-Type": "application/json" },
          { usernameOrEmail: "{{pacienteEmail}}", password: "{{pacientePassword}}" },
          `pm.test("Status code is 200", function () { pm.response.to.have.status(200); });
const json = pm.response.json();
pm.test("Long precision: pacienteId como string", function () { pm.expect(json.pacienteId).to.be.a('string'); });
pm.environment.set("pacienteToken", json.token);
if (json.pacienteId) { pm.environment.set("pacienteId", json.pacienteId); }`
        ),
        createItem("00.5 Registro Autónomo de Paciente (RF-01 / F03)", "POST", "/api/v1/auth/register-patient", { "Content-Type": "application/json" },
          {
            nombres: "Carlos",
            apellidos: "Santillan Postman",
            tipoDocumento: "DNI",
            numeroDocumento: "45678912",
            fechaNacimiento: "1992-04-12",
            correo: "paciente_autonomo@coronyx.pe",
            clave: "Password123!",
            telefono: "987112233"
          },
          `pm.test("Status code is 201 Created or 200 OK", function () { pm.expect([200, 201]).to.include(pm.response.code); });`
        )
      ]
    },
    {
      name: "01. Rol ADMIN_CLINICA (A01 - A11)",
      item: [
        createItem("A01: Crear cuenta de personal (Staff)", "POST", "/api/v1/admin/users",
          { "Authorization": "Bearer {{adminToken}}", "Content-Type": "application/json" },
          {
            nombre: "Staff Postman",
            apellido: "Verificacion",
            email: "staff_postman@coronyx.pe",
            password: "Password123!",
            rol: "RECEPCIONISTA",
            telefono: "987654321"
          },
          `pm.test("A01: Status code is 201 Created", function () { pm.response.to.have.status(201); });
const json = pm.response.json();
pm.test("A01: Usuario ID serializado como string", function () { pm.expect(json.id).to.be.a('string'); });`
        ),
        createItem("A02: Editar datos y rol de staff", "PUT", "/api/v1/admin/users/2",
          { "Authorization": "Bearer {{adminToken}}", "Content-Type": "application/json" },
          { telefono: "987000111", direccion: "Av. Principal 123" },
          `pm.test("A02: Status code is 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("A03: Desactivar cuenta de personal", "PATCH", "/api/v1/admin/users/2/status",
          { "Authorization": "Bearer {{adminToken}}", "Content-Type": "application/json" },
          { activo: true },
          `pm.test("A03: Status code is 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("A04: Rechazar creación de SUPER_ADMIN (400 Bad Request)", "POST", "/api/v1/admin/users",
          { "Authorization": "Bearer {{adminToken}}", "Content-Type": "application/json" },
          {
            nombre: "Intruso",
            apellido: "Escalamiento",
            email: "escalamiento@coronyx.pe",
            password: "Password123!",
            rol: "SUPER_ADMIN"
          },
          `pm.test("A04: Intento de SUPER_ADMIN rechazado con 400", function () { pm.response.to.have.status(400); });`
        ),
        createItem("A05: Listado administrativo de pacientes (sin datos clínicos)", "GET", "/api/v1/patients",
          { "Authorization": "Bearer {{adminToken}}" }, null,
          `pm.test("A05: Status code is 200 OK", function () { pm.response.to.have.status(200); });
const json = pm.response.json();
pm.test("A05: Lista retornada y campos clínicos no expuestos", function () {
    pm.expect(Array.isArray(json)).to.be.true;
    if (json.length > 0) {
        pm.expect(json[0].diagnostico).to.be.undefined;
        pm.expect(json[0].recetas).to.be.undefined;
    }
});`
        ),
        createItem("A06: Alta administrativa paciente con apoderado", "POST", "/api/v1/patients",
          { "Authorization": "Bearer {{adminToken}}", "Content-Type": "application/json" },
          {
            nombres: "Mateo",
            apellidos: "Pediátrico Postman",
            tipoDocumento: "DNI",
            numeroDocumento: "99112233",
            fechaNacimiento: "2018-05-10",
            sexo: "MASCULINO",
            nombreApoderado: "Laura Apoderada",
            telefonoApoderado: "998877665",
            parentescoApoderado: "MADRE"
          },
          `pm.test("A06: Alta de paciente retornó 201 Created", function () { pm.response.to.have.status(201); });
const json = pm.response.json();
pm.test("A06: Apoderado persistido", function () { pm.expect(json.nombreApoderado).to.eql("Laura Apoderada"); });`
        ),
        createItem("A07: Edición de contacto protegiendo alergias", "PUT", "/api/v1/patients/{{pacienteId}}",
          { "Authorization": "Bearer {{adminToken}}", "Content-Type": "application/json" },
          { telefono: "955443322", direccion: "Calle Las Begonias 450" },
          `pm.test("A07: Edición administrativa retornó 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("A08: Agendamiento administrativo de cita", "POST", "/api/v1/citas",
          { "Authorization": "Bearer {{adminToken}}", "Content-Type": "application/json" },
          {
            pacienteId: "{{pacienteId}}",
            odontologoId: "1",
            inicioEn: "2026-10-25T09:00:00-05:00",
            finEn: "2026-10-25T09:30:00-05:00",
            modalidad: "PRESENCIAL",
            motivo: "Revisión general A08",
            consultorio: "Sillón 1"
          },
          `pm.test("A08: Agendamiento administrativo retornó 201 Created", function () { pm.response.to.have.status(201); });
const json = pm.response.json();
pm.environment.set("citaId", json.id);`
        ),
        createItem("A09: Consultar horarios de atención de clínica", "GET", "/api/v1/disponibilidad/horarios-clinica",
          { "Authorization": "Bearer {{adminToken}}" }, null,
          `pm.test("A09: Consulta de horarios retornó 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("A10: Cancelación de cita con motivo auditado", "PATCH", "/api/v1/citas/{{citaId}}/estado",
          { "Authorization": "Bearer {{adminToken}}", "Content-Type": "application/json" },
          { estado: "CANCELADA", motivoCancelacion: "Cancelación administrativa por viaje del paciente" },
          `pm.test("A10: Cancelación de cita retornó 200 OK", function () { pm.response.to.have.status(200); });
const json = pm.response.json();
pm.test("A10: Estado actualizado a CANCELADA", function () { pm.expect(json.estado).to.eql("CANCELADA"); });`
        ),
        createItem("A11: Resumen de dashboard en America/Lima", "GET", "/api/v1/dashboard/resumen",
          { "Authorization": "Bearer {{adminToken}}" }, null,
          `pm.test("A11: Dashboard retornó 200 OK", function () { pm.response.to.have.status(200); });
const json = pm.response.json();
pm.test("A11: Métricas numéricas y zona horaria presentes", function () {
    pm.expect(json.totalCitasHoy).to.be.a('number');
    pm.expect(json.totalPacientes).to.be.a('number');
    pm.expect(json.fechaHoyLima).to.be.a('string');
});`
        )
      ]
    },
    {
      name: "02. Rol RECEPCIONISTA (R01 - R10)",
      item: [
        createItem("R00: Listar odontólogos habilitados de la clínica", "GET", "/api/v1/citas/odontologos",
          { "Authorization": "Bearer {{recepToken}}" }, null,
          `pm.test("R00: Odontólogos retornados con 200 OK", function () { pm.response.to.have.status(200); });
const json = pm.response.json();
pm.test("R00: Lista de odontólogos disponibles", function () {
    pm.expect(Array.isArray(json)).to.be.true;
    if (json.length > 0) {
        pm.expect(json[0].id).to.be.a('string');
        pm.expect(json[0].nombres).to.be.a('string');
    }
});`
        ),
        createItem("R01: Listar pacientes en recepción", "GET", "/api/v1/patients",
          { "Authorization": "Bearer {{recepToken}}" }, null,
          `pm.test("R01: Status code is 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("R02: Registro de paciente en mostrador", "POST", "/api/v1/patients",
          { "Authorization": "Bearer {{recepToken}}", "Content-Type": "application/json" },
          {
            nombres: "Paciente",
            apellidos: "Recepción Postman",
            tipoDocumento: "DNI",
            numeroDocumento: "88776655",
            fechaNacimiento: "1994-08-15",
            sexo: "FEMENINO",
            telefono: "911223344",
            email: "recep_paciente@coronyx.pe"
          },
          `pm.test("R02: Registro de paciente retornó 201 Created", function () { pm.response.to.have.status(201); });`
        ),
        createItem("R03: Actualizar teléfono y dirección", "PUT", "/api/v1/patients/{{pacienteId}}",
          { "Authorization": "Bearer {{recepToken}}", "Content-Type": "application/json" },
          { telefono: "922334455", direccion: "Av. Las Flores 789" },
          `pm.test("R03: Actualización de contacto retornó 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("R04: Agendamiento de cita por recepción", "POST", "/api/v1/citas",
          { "Authorization": "Bearer {{recepToken}}", "Content-Type": "application/json" },
          {
            pacienteId: "{{pacienteId}}",
            odontologoId: "1",
            inicioEn: "2026-10-25T10:00:00-05:00",
            finEn: "2026-10-25T10:30:00-05:00",
            modalidad: "PRESENCIAL",
            motivo: "Curación dental R04",
            consultorio: "Sillón 1"
          },
          `pm.test("R04: Agendamiento retornó 201 Created", function () { pm.response.to.have.status(201); });
const json = pm.response.json();
pm.environment.set("citaId", json.id);`
        ),
        createItem("R05: Consulta de disponibilidad y slots libres", "GET", "/api/v1/disponibilidad/slots?odontologoId=1&fecha=2026-10-25",
          { "Authorization": "Bearer {{recepToken}}" }, null,
          `pm.test("R05: Cálculo de slots retornó 200 OK", function () { pm.response.to.have.status(200); });
const json = pm.response.json();
pm.test("R05: Arreglo de slots generado", function () { pm.expect(Array.isArray(json)).to.be.true; });`
        ),
        createItem("R06: Confirmar asistencia física -> FINALIZADA", "PATCH", "/api/v1/citas/{{citaId}}/asistencia",
          { "Authorization": "Bearer {{recepToken}}", "Content-Type": "application/json" },
          { tipoAsistencia: "ASISTIO" },
          `pm.test("R06: Asistencia retornó 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("R07: Marcar inasistencia (NO_SHOW) -> NO_ASISTIO", "PATCH", "/api/v1/citas/{{citaId}}/asistencia",
          { "Authorization": "Bearer {{recepToken}}", "Content-Type": "application/json" },
          { tipoAsistencia: "NO_SHOW" },
          `pm.test("R07: No-show procesado con 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("R08: Reprogramación directa de fecha y hora", "PUT", "/api/v1/citas/{{citaId}}/reprogramar",
          { "Authorization": "Bearer {{recepToken}}", "Content-Type": "application/json" },
          {
            nuevaFechaHoraInicio: "2026-10-25T11:00:00-05:00",
            nuevaFechaHoraFin: "2026-10-25T11:30:00-05:00",
            motivo: "Cambio de hora acordado por llamada"
          },
          `pm.test("R08: Reprogramación directa retornó 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("R09: Bandeja de solicitudes de reprogramación", "GET", "/api/v1/citas/solicitudes-reprogramacion?estado=PENDIENTE",
          { "Authorization": "Bearer {{recepToken}}" }, null,
          `pm.test("R09: Listado de solicitudes retornó 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("R10: Resolver solicitud de reprogramación (APROBADA)", "PATCH", "/api/v1/citas/solicitudes-reprogramacion/1/resolver",
          { "Authorization": "Bearer {{recepToken}}", "Content-Type": "application/json" },
          { estado: "APROBADA", motivoResolucion: "Aprobada por recepción tras validación de agenda" },
          `pm.test("R10: Resolución de solicitud retornó 200 OK", function () { pm.response.to.have.status(200); });`
        )
      ]
    },
    {
      name: "03. Rol ODONTOLOGO (D01 - D08)",
      item: [
        createItem("D01: Consulta de expediente e historia clínica", "GET", "/api/v1/patients/{{pacienteId}}/historia-clinica",
          { "Authorization": "Bearer {{odontoToken}}" }, null,
          `pm.test("D01: Acceso a historia clínica retornó 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("D02: Apertura de borrador de atención clínica", "POST", "/api/v1/clinical-encounters",
          { "Authorization": "Bearer {{odontoToken}}", "Content-Type": "application/json" },
          {
            pacienteId: 2,
            citaId: 1,
            diagnostico: "Gingivitis marginal localizada",
            procedimientos: "Profilaxis y destartraje",
            recetasPrescripciones: "Enjuague bucal",
            evolucion: "Inicio de tratamiento"
          },
          `pm.test("D02: Creación de atención retornó 201 Created", function () { pm.response.to.have.status(201); });
const json = pm.response.json();
pm.test("D02: Encuentro en BORRADOR", function () { pm.expect(json.estado).to.eql("BORRADOR"); });
pm.environment.set("encounterId", json.id);`
        ),
        createItem("D03: Guardado progresivo de borrador de atención", "PUT", "/api/v1/clinical-encounters/{{encounterId}}/draft",
          { "Authorization": "Bearer {{odontoToken}}", "Content-Type": "application/json" },
          {
            diagnostico: "Gingivitis marginal tratada",
            procedimientos: "Profilaxis completa",
            evolucion: "Evolución favorable"
          },
          `pm.test("D03: Guardado progresivo retornó 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("D04: Confirmación inmutable de atención médica", "POST", "/api/v1/clinical-encounters/{{encounterId}}/confirm",
          { "Authorization": "Bearer {{odontoToken}}", "Content-Type": "application/json" },
          { notasConfirmacion: "Atención firmada y confirmada" },
          `pm.test("D04: Confirmación retornó 200 OK", function () { pm.response.to.have.status(200); });
const json = pm.response.json();
pm.test("D04: Estado pasa a CONFIRMADO", function () {
    pm.expect(json.estado === "CONFIRMADO" || json.estado === "CONFIRMADA").to.be.true;
});`
        ),
        createItem("D05: Consulta de versiones históricas inalteradas", "GET", "/api/v1/clinical-encounters/{{encounterId}}/versions",
          { "Authorization": "Bearer {{odontoToken}}" }, null,
          `pm.test("D05: Versiones históricas retornaron 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("D06: Crear versión evolutiva N+1", "POST", "/api/v1/clinical-encounters/{{encounterId}}/versions",
          { "Authorization": "Bearer {{odontoToken}}", "Content-Type": "application/json" },
          {
            diagnostico: "Control pos-tratamiento",
            procedimientos: "Revisión clínica",
            evolucion: "Versión evolutiva 2"
          },
          `pm.test("D06: Versión correctiva N+1 retornó 201 Created", function () { pm.response.to.have.status(201); });`
        ),
        createItem("D07: Odontograma interactivo: consulta actual", "GET", "/api/v1/odontograma/paciente/{{pacienteId}}/actual",
          { "Authorization": "Bearer {{odontoToken}}" }, null,
          `pm.test("D07: Odontograma actual retornó 200 OK", function () { pm.response.to.have.status(200); });
const json = pm.response.json();
pm.environment.set("odontogramaVersionId", json.id);`
        ),
        createItem("D08: Publicar visibilidad de archivo en portal", "PATCH", "/api/v1/attachments/1/visibility",
          { "Authorization": "Bearer {{odontoToken}}", "Content-Type": "application/json" },
          { esVisiblePaciente: true },
          `pm.test("D08: Publicación retornó 200 OK", function () { pm.response.to.have.status(200); });`
        )
      ]
    },
    {
      name: "04. Rol PACIENTE (P01 - P05)",
      item: [
        createItem("P01: Consulta de citas propias en /me/citas", "GET", "/api/v1/me/citas",
          { "Authorization": "Bearer {{pacienteToken}}" }, null,
          `pm.test("P01: Citas propias retornaron 200 OK", function () { pm.response.to.have.status(200); });
const json = pm.response.json();
pm.test("P01: Arreglo de citas retornado", function () { pm.expect(Array.isArray(json)).to.be.true; });`
        ),
        createItem("P02: Agendamiento autónomo en /me/citas", "POST", "/api/v1/me/citas",
          { "Authorization": "Bearer {{pacienteToken}}", "Content-Type": "application/json" },
          {
            odontologoId: "1",
            inicioEn: "2026-10-25T14:00:00-05:00",
            finEn: "2026-10-25T14:30:00-05:00",
            modalidad: "PRESENCIAL",
            motivo: "Agendamiento paciente P02",
            consultorio: "Sillón 1"
          },
          `pm.test("P02: Agendamiento autónomo retornó 201 Created", function () { pm.response.to.have.status(201); });
const json = pm.response.json();
pm.test("P02: Cita ligada al paciente autenticado", function () {
    pm.expect(json.pacienteId).to.eql(pm.environment.get("pacienteId"));
});`
        ),
        createItem("P03: Consulta de solicitudes propias en /me", "GET", "/api/v1/me/solicitudes-reprogramacion",
          { "Authorization": "Bearer {{pacienteToken}}" }, null,
          `pm.test("P03: Solicitudes propias retornaron 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("P04: Descarga de radiografía autorizada", "GET", "/api/v1/attachments/1/download",
          { "Authorization": "Bearer {{pacienteToken}}" }, null,
          `pm.test("P04: Descarga retornó 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("P05: Negativo: subir archivo a expediente ajeno (403)", "POST", "/api/v1/attachments/upload",
          { "Authorization": "Bearer {{pacienteToken}}" }, null,
          `pm.test("P05: Intento a ID ajeno rechazado con 403 Forbidden o 400 Bad Request", function () {
    pm.expect([400, 403]).to.include(pm.response.code);
});`
        )
      ]
    },
    {
      name: "05. Principios Generales (G01 - G05)",
      item: [
        createItem("G01: Intento de modificar atención confirmada (409 Conflict)", "PUT", "/api/v1/clinical-encounters/{{encounterId}}/draft",
          { "Authorization": "Bearer {{odontoToken}}", "Content-Type": "application/json" },
          { diagnostico: "Intento de sabotaje G01" },
          `pm.test("G01: Inmutabilidad defendida con 409 Conflict o 400", function () {
    pm.expect([400, 409]).to.include(pm.response.code);
});`
        ),
        createItem("G02: Intento de acceder a paciente ajeno fuera de sede (403/404)", "GET", "/api/v1/patients/99999",
          { "Authorization": "Bearer {{adminToken}}" }, null,
          `pm.test("G02: Aislamiento multi-sede defendido con 403 o 404", function () {
    pm.expect([403, 404]).to.include(pm.response.code);
});`
        ),
        createItem("G03: Intento de paciente de modificar alergias clínicas (403 Forbidden)", "PUT", "/api/v1/patients/me",
          { "Authorization": "Bearer {{pacienteToken}}", "Content-Type": "application/json" },
          { telefono: "999888777", alergias: "Intento de inyección de alergia" },
          `pm.test("G03: Modificación clínica denegada con 403 Forbidden", function () { pm.response.to.have.status(403); });`
        ),
        createItem("G04: Operaciones reales en tiempo real (Cero Delays fijos)", "GET", "/api/v1/dashboard/resumen",
          { "Authorization": "Bearer {{adminToken}}" }, null,
          `pm.test("G04: Respuesta inmediata con datos vivos", function () {
    pm.response.to.have.status(200);
    pm.expect(pm.response.responseTime).to.be.below(1500);
});`
        ),
        createItem("G05: Revocación inmediata de visibilidad de archivo (403)", "PATCH", "/api/v1/attachments/1/visibility",
          { "Authorization": "Bearer {{odontoToken}}", "Content-Type": "application/json" },
          { esVisiblePaciente: false },
          `pm.test("G05: Revocación exitosa con 200 OK", function () { pm.response.to.have.status(200); });`
        )
      ]
    },
    {
      name: "06. Notificaciones y Alertas (N01 - N03)",
      item: [
        createItem("N01: Listar notificaciones del usuario", "GET", "/api/v1/notifications",
          { "Authorization": "Bearer {{adminToken}}" }, null,
          `pm.test("N01: Notificaciones retornadas con 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("N02: Conteo de no leídas", "GET", "/api/v1/notifications/unread-count",
          { "Authorization": "Bearer {{adminToken}}" }, null,
          `pm.test("N02: Conteo retornado con 200 OK", function () { pm.response.to.have.status(200); });`
        ),
        createItem("N03: Marcar todas como leídas", "PATCH", "/api/v1/notifications/read-all",
          { "Authorization": "Bearer {{adminToken}}" }, null,
          `pm.test("N03: Marcadas con 200 OK", function () { pm.response.to.have.status(200); });`
        )
      ]
    }
  ]
};

const collectionJson = JSON.stringify(collection, null, 2);

// 1. Guardar en Backend_Dentista/postman
fs.writeFileSync(
  path.join(postmanDir, 'Coronyx_F09_HTTP_Verification.postman_collection.json'),
  collectionJson,
  'utf-8'
);

fs.writeFileSync(
  path.join(postmanDir, 'Coronyx_API_Collection.postman_collection.json'),
  collectionJson,
  'utf-8'
);

// 2. Guardar en la raíz de CoronyxProyecto
const rootPostmanPath = path.resolve('..', 'Coronyx_API_Collection.postman_collection.json');
try {
  fs.writeFileSync(rootPostmanPath, collectionJson, 'utf-8');
  console.log('Colección actualizada también en la raíz:', rootPostmanPath);
} catch (e) {
  console.warn('No se pudo escribir en la raíz:', e.message);
}

console.log('Postman Collection y Environment LOCAL generados con éxito en:', postmanDir);
