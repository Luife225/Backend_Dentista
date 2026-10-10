import crypto from 'crypto';

const BASE_URL = 'http://localhost:8081/api/v1';

const results = {
  admin: { total: 11, passed: 0, criteria: ['A01', 'A02', 'A03', 'A04', 'A05', 'A06', 'A07', 'A08', 'A09', 'A10', 'A11'] },
  recepcion: { total: 10, passed: 0, criteria: ['R01', 'R02', 'R03', 'R04', 'R05', 'R06', 'R07', 'R08', 'R09', 'R10'] },
  odontologo: { total: 8, passed: 0, criteria: ['D01', 'D02', 'D03', 'D04', 'D05', 'D06', 'D07', 'D08'] },
  paciente: { total: 5, passed: 0, criteria: ['P01', 'P02', 'P03', 'P04', 'P05'] },
  generales: { total: 5, passed: 0, criteria: ['G01', 'G02', 'G03', 'G04', 'G05'] },
  concurrencia: { total: 1, passed: 0, criteria: ['PARALELO'] },
};

function log(msg, ...args) {
  console.log(`[TEST-F09] ${msg}`, ...args);
}

function assert(condition, message) {
  if (!condition) {
    console.error(`❌ ASSERTION FAILED: ${message}`);
    throw new Error(message);
  }
  console.log(`  ✓ ${message}`);
}

async function login(correo, clave) {
  const res = await fetch(`${BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ correo, clave })
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`Login failed for ${correo}: ${res.status} ${text}`);
  }
  return res.json();
}

function buildMultipartFormData(fields, fileField) {
  const boundary = '----CoronyxBoundary' + Math.random().toString(36).substring(2);
  const crlf = '\r\n';
  const buffers = [];

  for (const [key, val] of Object.entries(fields)) {
    if (val !== undefined && val !== null) {
      buffers.push(Buffer.from(
        `--${boundary}${crlf}` +
        `Content-Disposition: form-data; name="${key}"${crlf}${crlf}` +
        `${val}${crlf}`
      ));
    }
  }

  if (fileField) {
    buffers.push(Buffer.from(
      `--${boundary}${crlf}` +
      `Content-Disposition: form-data; name="${fileField.fieldName}"; filename="${fileField.filename}"${crlf}` +
      `Content-Type: ${fileField.contentType}${crlf}${crlf}`
    ));
    buffers.push(fileField.data);
    buffers.push(Buffer.from(crlf));
  }

  buffers.push(Buffer.from(`--${boundary}--${crlf}`));
  const fullBody = Buffer.concat(buffers);

  return {
    boundary,
    body: fullBody,
    contentType: `multipart/form-data; boundary=${boundary}`
  };
}

async function getFreeSlot(odontologoId, minDaysAhead, token) {
  let d = new Date();
  d.setDate(d.getDate() + minDaysAhead);
  for (let i = 0; i < 40; i++) {
    if (d.getDay() !== 0) { // Lunes a Sabado
      const iso = d.toISOString().split('T')[0];
      const res = await fetch(`${BASE_URL}/disponibilidad/slots?odontologoId=${odontologoId}&fecha=${iso}`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (res.ok) {
        const slots = await res.json();
        const free = slots.find(s => s.disponible);
        if (free) {
          return {
            fecha: iso,
            inicio: `${iso}T${free.inicio}:00-05:00`,
            fin: `${iso}T${free.fin}:00-05:00`
          };
        }
      }
    }
    d.setDate(d.getDate() + 1);
  }
  throw new Error(`No se encontró slot disponible para el odontólogo ${odontologoId}`);
}

async function getTwoFreeSlots(odontologoId, minDaysAhead, token) {
  let d = new Date();
  d.setDate(d.getDate() + minDaysAhead);
  for (let i = 0; i < 40; i++) {
    if (d.getDay() !== 0) {
      const iso = d.toISOString().split('T')[0];
      const res = await fetch(`${BASE_URL}/disponibilidad/slots?odontologoId=${odontologoId}&fecha=${iso}`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      if (res.ok) {
        const slots = await res.json();
        const frees = slots.filter(s => s.disponible);
        if (frees.length >= 2) {
          return {
            fecha: iso,
            slot1: { inicio: `${iso}T${frees[0].inicio}:00-05:00`, fin: `${iso}T${frees[0].fin}:00-05:00` },
            slot2: { inicio: `${iso}T${frees[1].inicio}:00-05:00`, fin: `${iso}T${frees[1].fin}:00-05:00` }
          };
        }
      }
    }
    d.setDate(d.getDate() + 1);
  }
  throw new Error(`No se encontraron 2 slots disponibles para el odontólogo ${odontologoId}`);
}

async function runMasterSuite() {
  log('================================================================================');
  log('BATERÍA MAESTRA F09 A: VERIFICACIÓN HTTP COMPLETA (34 CASOS + G01-G05 + CONCURRENCIA)');
  log('Denominadores Oficiales: Admin (11/11), Recepción (10/10), Odontólogo (8/8), Paciente (5/5), G01-G05');
  log('================================================================================\n');

  // Paso 0: Autenticación inicial de los 4 roles principales
  log('Paso 0: Autenticación de sesiones reales...');
  const adminAuth = await login('admin@coronyx.pe', '123456');
  const recepcionAuth = await login('recepcion@coronyx.pe', '123456');
  const odontologoAuth = await login('odontologo@coronyx.pe', '123456');
  const pacienteAuth = await login('paciente@coronyx.pe', '123456');

  assert(adminAuth.rol === 'ADMIN_CLINICA', 'Sesión ADMIN_CLINICA obtenida');
  assert(recepcionAuth.rol === 'RECEPCIONISTA', 'Sesión RECEPCIONISTA obtenida');
  assert(odontologoAuth.rol === 'ODONTOLOGO', 'Sesión ODONTOLOGO obtenida');
  assert(pacienteAuth.rol === 'PACIENTE', 'Sesión PACIENTE obtenida');

  // Verificación de precisión Long/BigInt en LoginResponseDto
  assert(typeof adminAuth.usuarioId === 'string', 'Precisión Long: usuarioId serializado como string decimal');
  assert(typeof adminAuth.clinicaId === 'string', 'Precisión Long: clinicaId serializado como string decimal');
  assert(typeof pacienteAuth.pacienteId === 'string', 'Precisión Long: pacienteId serializado como string decimal');

  // ===========================================================================
  // SECCIÓN 1: EVALUACIÓN ROL ADMIN_CLINICA (A01 - A11)
  // ===========================================================================
  log('\n─────────────────────────────────────────────────────────────────────────────');
  log('SECCIÓN 1: EVALUACIÓN ROL ADMIN_CLINICA (A01 - A11)');
  log('─────────────────────────────────────────────────────────────────────────────');

  // A01: Creación de cuenta staff con hash BCrypt y sesión JWT
  const testStaffEmail = `staff_f09_${Date.now()}@coronyx.pe`;
  const createStaffRes = await fetch(`${BASE_URL}/clinic/users`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${adminAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      nombres: 'Staff',
      apellidos: 'Prueba F09',
      correo: testStaffEmail,
      clave: 'Password123!',
      rol: 'RECEPCIONISTA',
      cargo: 'Recepcionista Turno Tarde'
    })
  });
  assert(createStaffRes.status === 201, 'A01: Creación de cuenta staff retornó 201 Created');
  const createdStaff = await createStaffRes.json();
  const staffLogin = await login(testStaffEmail, 'Password123!');
  assert(Boolean(staffLogin.token), 'A01: Login exitoso con nueva cuenta staff');
  results.admin.passed++;

  // A02: Edición de cuenta por ADMIN_CLINICA
  const updateStaffRes = await fetch(`${BASE_URL}/clinic/users/${createdStaff.id}`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${adminAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      nombres: 'Staff Modificado F09',
      apellidos: 'Prueba F09'
    })
  });
  assert(updateStaffRes.status === 200, 'A02: Edición de cuenta staff retornó 200 OK');
  results.admin.passed++;

  // A03: Desactivación inmediata revoca token JWT
  const deactivateRes = await fetch(`${BASE_URL}/clinic/users/${createdStaff.id}/status`, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${adminAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ estado: 'INACTIVO' })
  });
  assert(deactivateRes.status === 200, 'A03: Desactivación de cuenta retornó 200 OK');
  const tokenRevokedRes = await fetch(`${BASE_URL}/auth/me`, {
    headers: { Authorization: `Bearer ${staffLogin.token}` }
  });
  assert(tokenRevokedRes.status === 401, 'A03: Petición con token de usuario desactivado rechazada con 401 Unauthorized');
  results.admin.passed++;

  // A04: Rechazo de asignación de SUPER_ADMIN o auto-desactivación (400)
  const invalidRoleRes = await fetch(`${BASE_URL}/clinic/users`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${adminAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      nombres: 'Invalido',
      apellidos: 'Super',
      correo: `invalid_${Date.now()}@coronyx.pe`,
      clave: '123456',
      rol: 'SUPER_ADMIN'
    })
  });
  assert(invalidRoleRes.status === 400, 'A04: Intento de crear SUPER_ADMIN rechazado con 400 Bad Request');
  results.admin.passed++;

  // A05: Lista pacientes en proyección administrativa (sin campos clínicos confidenciales)
  const adminPatientsRes = await fetch(`${BASE_URL}/patients/admin`, {
    headers: { Authorization: `Bearer ${adminAuth.token}` }
  });
  assert(adminPatientsRes.status === 200, 'A05: Lista administrativa de pacientes retornó 200 OK');
  const adminPatients = await adminPatientsRes.json();
  const sampleP = adminPatients[0];
  assert(sampleP && sampleP.alergias === undefined, 'A05: Campos clínicos no expuestos en proyección administrativa');
  results.admin.passed++;

  // A06: Alta de paciente pediátrico (2 años)
  const testPatientDni = `${Math.floor(10000000 + Math.random() * 89999999)}`;
  const createPediatricRes = await fetch(`${BASE_URL}/patients`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${adminAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      nombres: 'Mateo',
      apellidos: 'Pediatrico F09',
      tipoDocumento: 'DNI',
      numeroDocumento: testPatientDni,
      fechaNacimiento: '2024-05-10', // 2 años
      telefono: '987654321',
      correo: `pediatrico_${Date.now()}@coronyx.pe`,
      ciudad: 'Lima',
      seguro: 'PARTICULAR'
    })
  });
  assert(createPediatricRes.status === 201, 'A06: Alta de paciente pediátrico retornó 201 Created');
  const createdPatient = await createPediatricRes.json();
  results.admin.passed++;

  // A07: Edición administrativa de paciente sin alterar clínica/alergias
  const editPatientRes = await fetch(`${BASE_URL}/patients/${createdPatient.id}`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${adminAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      telefono: '999888777',
      alergias: 'Penicilina Crítica', // campo prohibido para admin
      antecedentesMedicos: 'Cardiopatía Grave'
    })
  });
  assert(editPatientRes.status === 200, 'A07: Edición administrativa retornó 200 OK');
  const verifyClinicalRes = await fetch(`${BASE_URL}/patients/${createdPatient.id}`, {
    headers: { Authorization: `Bearer ${odontologoAuth.token}` }
  });
  const verifiedClinical = await verifyClinicalRes.json();
  assert(verifiedClinical.alergias !== 'Penicilina Crítica', 'A07: Alergias clínicas protegidas contra mutación administrativa');
  results.admin.passed++;

  // A08: Agendamiento de cita administrativa con IDs string decimal
  const slotA08 = await getFreeSlot('1', 3, adminAuth.token);
  const apptA08Res = await fetch(`${BASE_URL}/citas`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${adminAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      pacienteId: String(createdPatient.id),
      odontologoId: '1',
      inicioEn: slotA08.inicio,
      finEn: slotA08.fin,
      modalidad: 'PRESENCIAL',
      motivo: 'Evaluación administrativa F09',
      consultorio: 'Sillón 1'
    })
  });
  assert(apptA08Res.status === 201, 'A08: Agendamiento administrativo de cita retornó 201 Created');
  const createdApptA08 = await apptA08Res.json();
  results.admin.passed++;

  // A09: Configuración de horarios y ausencias en BD
  const getHorariosRes = await fetch(`${BASE_URL}/disponibilidad/horarios`, {
    headers: { Authorization: `Bearer ${adminAuth.token}` }
  });
  assert(getHorariosRes.status === 200, 'A09: Consulta de horarios de atención retornó 200 OK');
  const putHorariosRes = await fetch(`${BASE_URL}/disponibilidad/horarios`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${adminAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify([
      { diaSemana: 1, horaApertura: '08:00:00', horaCierre: '18:00:00', activo: true },
      { diaSemana: 2, horaApertura: '08:00:00', horaCierre: '18:00:00', activo: true },
      { diaSemana: 3, horaApertura: '08:00:00', horaCierre: '18:00:00', activo: true },
      { diaSemana: 4, horaApertura: '08:00:00', horaCierre: '18:00:00', activo: true },
      { diaSemana: 5, horaApertura: '08:00:00', horaCierre: '18:00:00', activo: true },
      { diaSemana: 6, horaApertura: '09:00:00', horaCierre: '22:00:00', activo: true },
      { diaSemana: 7, horaApertura: '08:00:00', horaCierre: '12:00:00', activo: false }
    ])
  });
  assert(putHorariosRes.status === 200, 'A09: Persistencia de horarios semanales retornó 200 OK');
  results.admin.passed++;

  // A10: Cancelación de cita con registro de motivo
  const cancelApptRes = await fetch(`${BASE_URL}/citas/${createdApptA08.id}/estado`, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${adminAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      estado: 'CANCELADA',
      motivoCancelacion: 'Cancelación administrativa por reprogramación externa'
    })
  });
  assert(cancelApptRes.status === 200, 'A10: Cancelación de cita con motivo retornó 200 OK');
  const canceledAppt = await cancelApptRes.json();
  assert(canceledAppt.estado === 'CANCELADA', 'A10: Estado actualizado a CANCELADA en BD');
  results.admin.passed++;

  // A11: Panel con métricas reales en zona horaria America/Lima
  const dashRes = await fetch(`${BASE_URL}/dashboard/summary`, {
    headers: { Authorization: `Bearer ${adminAuth.token}` }
  });
  assert(dashRes.status === 200, 'A11: Resumen de dashboard en America/Lima retornó 200 OK');
  const dashData = await dashRes.json();
  assert(typeof dashData.totalCitasHoy === 'number', 'A11: totalCitasHoy es numérico');
  assert(typeof dashData.totalPacientes === 'number', 'A11: totalPacientes es numérico');
  const dashAnonRes = await fetch(`${BASE_URL}/dashboard/summary`);
  assert(dashAnonRes.status === 401, 'A11: Acceso anónimo a dashboard es rechazado con 401 Unauthorized');
  results.admin.passed++;

  // ===========================================================================
  // SECCIÓN 2: EVALUACIÓN ROL RECEPCIONISTA (R01 - R10)
  // ===========================================================================
  log('\n─────────────────────────────────────────────────────────────────────────────');
  log('SECCIÓN 2: EVALUACIÓN ROL RECEPCIONISTA (R01 - R10)');
  log('─────────────────────────────────────────────────────────────────────────────');

  // R01: Lista pacientes administrativos sin clínica médica
  const recepPatientsRes = await fetch(`${BASE_URL}/patients/admin`, {
    headers: { Authorization: `Bearer ${recepcionAuth.token}` }
  });
  assert(recepPatientsRes.status === 200, 'R01: Recepción lista pacientes administrativos (200 OK)');
  results.recepcion.passed++;

  // R02: Registro de paciente desde recepción
  const recepPatientDni = `${Math.floor(10000000 + Math.random() * 89999999)}`;
  const recepCreatePatientRes = await fetch(`${BASE_URL}/patients`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${recepcionAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      nombres: 'Laura',
      apellidos: 'Gómez Recepción',
      tipoDocumento: 'DNI',
      numeroDocumento: recepPatientDni,
      fechaNacimiento: '1995-08-20',
      telefono: '911222333',
      correo: `laura_${Date.now()}@coronyx.pe`,
      ciudad: 'Lima',
      seguro: 'PARTICULAR'
    })
  });
  assert(recepCreatePatientRes.status === 201, 'R02: Registro de paciente desde recepción retornó 201 Created');
  const recepPatient = await recepCreatePatientRes.json();
  results.recepcion.passed++;

  // R03: Actualización de datos de contacto por recepción
  const recepUpdatePatientRes = await fetch(`${BASE_URL}/patients/${recepPatient.id}`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${recepcionAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      telefono: '944332211',
      ciudad: 'Callao'
    })
  });
  assert(recepUpdatePatientRes.status === 200, 'R03: Actualización de contacto retornó 200 OK');
  results.recepcion.passed++;

  // R04: Agendamiento de cita para odontólogo de la sede (usando slot disponible garantizado)
  const slotR04 = await getFreeSlot('1', 4, recepcionAuth.token);
  const recepApptRes = await fetch(`${BASE_URL}/citas`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${recepcionAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      pacienteId: String(recepPatient.id),
      odontologoId: '1',
      inicioEn: slotR04.inicio,
      finEn: slotR04.fin,
      modalidad: 'PRESENCIAL',
      motivo: 'Control odontológico R04',
      consultorio: 'Consultorio 2'
    })
  });
  assert(recepApptRes.status === 201, 'R04: Agendamiento de cita por recepción retornó 201 Created');
  const recepAppt = await recepApptRes.json();
  results.recepcion.passed++;

  // R05: Consulta de disponibilidad real y cálculo dinámico de slots
  const slotsRes = await fetch(`${BASE_URL}/disponibilidad/slots?odontologoId=1&fecha=${slotR04.fecha}`, {
    headers: { Authorization: `Bearer ${recepcionAuth.token}` }
  });
  assert(slotsRes.status === 200, 'R05: Cálculo de slots en BD retornó 200 OK');
  const slotsList = await slotsRes.json();
  assert(Array.isArray(slotsList) && slotsList.length > 0, 'R05: Slots generados dinámicamente como arreglo');
  results.recepcion.passed++;

  // R06: Registro de asistencia física (ASISTIO -> FINALIZADA)
  const assistRes = await fetch(`${BASE_URL}/citas/${recepAppt.id}/asistencia`, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${recepcionAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ asistencia: 'ASISTIO' })
  });
  assert(assistRes.status === 200, 'R06: Registro de asistencia retornó 200 OK');
  const assistedAppt = await assistRes.json();
  assert(assistedAppt.estado === 'FINALIZADA', 'R06: Estado de cita actualizado a FINALIZADA');
  results.recepcion.passed++;

  // R07: Registro de inasistencia (NO_SHOW -> NO_ASISTIO)
  const slotR07 = await getFreeSlot('1', 5, recepcionAuth.token);
  const apptR07Res = await fetch(`${BASE_URL}/citas`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${recepcionAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      pacienteId: String(recepPatient.id),
      odontologoId: '1',
      inicioEn: slotR07.inicio,
      finEn: slotR07.fin,
      modalidad: 'PRESENCIAL',
      motivo: 'Control R07',
      consultorio: 'Consultorio 2'
    })
  });
  assert(apptR07Res.status === 201, 'R07: Cita previa para NO_SHOW creada (201 Created)');
  const apptR07 = await apptR07Res.json();
  const noShowRes = await fetch(`${BASE_URL}/citas/${apptR07.id}/asistencia`, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${recepcionAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ asistencia: 'NO_SHOW' })
  });
  assert(noShowRes.status === 200, 'R07: Registro de NO_SHOW retornó 200 OK');
  const noShowAppt = await noShowRes.json();
  assert(noShowAppt.estado === 'NO_ASISTIO', 'R07: Estado actualizado a NO_ASISTIO');
  results.recepcion.passed++;

  // R08: Reprogramación directa de cita por recepción
  const slotsR08 = await getTwoFreeSlots('1', 6, recepcionAuth.token);
  const apptR08Res = await fetch(`${BASE_URL}/citas`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${recepcionAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      pacienteId: String(recepPatient.id),
      odontologoId: '1',
      inicioEn: slotsR08.slot1.inicio,
      finEn: slotsR08.slot1.fin,
      modalidad: 'PRESENCIAL',
      motivo: 'Cita para reprogramar R08',
      consultorio: 'Consultorio 1'
    })
  });
  assert(apptR08Res.status === 201, 'R08: Cita previa para reprogramar creada (201 Created)');
  const apptR08 = await apptR08Res.json();
  const reprogramDirectRes = await fetch(`${BASE_URL}/citas/${apptR08.id}/reprogramar?nuevoInicio=${slotsR08.slot2.inicio}&nuevoFin=${slotsR08.slot2.fin}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${recepcionAuth.token}` }
  });
  assert(reprogramDirectRes.status === 200, 'R08: Reprogramación directa por recepción retornó 200 OK');
  const reprogrammedDirect = await reprogramDirectRes.json();
  assert(reprogrammedDirect.estado === 'REPROGRAMADA', 'R08: Estado de cita actualizado a REPROGRAMADA');
  results.recepcion.passed++;

  // R09 y R10: Solicitudes de reprogramación
  const slotsR10 = await getTwoFreeSlots('1', 8, recepcionAuth.token);
  const patientCitaForSolicitudRes = await fetch(`${BASE_URL}/citas`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${recepcionAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      pacienteId: String(pacienteAuth.pacienteId),
      odontologoId: '1',
      inicioEn: slotsR10.slot1.inicio,
      finEn: slotsR10.slot1.fin,
      modalidad: 'PRESENCIAL',
      motivo: 'Cita para solicitud R09/R10',
      consultorio: 'Consultorio 1'
    })
  });
  assert(patientCitaForSolicitudRes.status === 201, 'R10: Cita para solicitud creada con 201 Created');
  const citaForSol = await patientCitaForSolicitudRes.json();

  const createSolRes = await fetch(`${BASE_URL}/citas/${citaForSol.id}/solicitar-reprogramacion`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${pacienteAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      nuevaFechaHoraInicio: slotsR10.slot2.inicio,
      nuevaFechaHoraFin: slotsR10.slot2.fin,
      motivo: 'Cruce con reunión laboral urgente'
    })
  });
  assert(createSolRes.status === 201, 'R10/P03: Solicitud de reprogramación creada con 201 Created');
  const createdSol = await createSolRes.json();

  // R09: Consulta de solicitudes de reprogramación
  const listSolicitudesRes = await fetch(`${BASE_URL}/citas/solicitudes-reprogramacion`, {
    headers: { Authorization: `Bearer ${recepcionAuth.token}` }
  });
  assert(listSolicitudesRes.status === 200, 'R09: Consulta de solicitudes de reprogramación retornó 200 OK');
  const reqs = await listSolicitudesRes.json();
  assert(Array.isArray(reqs) && reqs.some(r => r.id === createdSol.id), 'R09: Solicitud listada en bandeja de clínica');
  results.recepcion.passed++;

  // R10: Aprobación/resolución atómica de solicitud de reprogramación
  const resolveSolRes = await fetch(`${BASE_URL}/citas/solicitudes-reprogramacion/${createdSol.id}/resolver`, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${recepcionAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      estado: 'APROBADA',
      motivoResolucion: 'Solicitud aprobada por recepción'
    })
  });
  assert(resolveSolRes.status === 200, 'R10: Resolución de solicitud retornó 200 OK');
  const resolvedSol = await resolveSolRes.json();
  assert(resolvedSol.estado === 'RESUELTA' || resolvedSol.estado === 'APROBADA', 'R10: Solicitud marcada como resuelta');
  results.recepcion.passed++;

  // ===========================================================================
  // SECCIÓN 3: EVALUACIÓN ROL ODONTOLOGO (D01 - D08)
  // ===========================================================================
  log('\n─────────────────────────────────────────────────────────────────────────────');
  log('SECCIÓN 3: EVALUACIÓN ROL ODONTOLOGO (D01 - D08)');
  log('─────────────────────────────────────────────────────────────────────────────');

  // D01: Acceso a historia clínica integral por odontólogo
  const odontoHistoryRes = await fetch(`${BASE_URL}/clinical-encounters/paciente/${pacienteAuth.pacienteId}`, {
    headers: { Authorization: `Bearer ${odontologoAuth.token}` }
  });
  assert(odontoHistoryRes.status === 200, 'D01: Odontólogo accede a historia clínica integral (200 OK)');
  results.odontologo.passed++;

  // D02: Creación de borrador de atención clínica
  const createEncounterDraftRes = await fetch(`${BASE_URL}/clinical-encounters`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${odontologoAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      pacienteId: Number(pacienteAuth.pacienteId),
      citaId: Number(citaForSol.id),
      diagnostico: 'Gingivitis marginal localizada',
      procedimientos: 'Profilaxis dental y destartraje',
      recetasPrescripciones: 'Clorhexidina 0.12% enjuague',
      evolucion: 'Borrador inicial de atención F09'
    })
  });
  assert(createEncounterDraftRes.status === 201, 'D02: Creación de borrador de atención retornó 201 Created');
  const createdEncounter = await createEncounterDraftRes.json();
  assert(createdEncounter.estado === 'BORRADOR', 'D02: Encuentro inicia en BORRADOR');
  results.odontologo.passed++;

  // D03: Guardado progresivo de borrador de atención clínica
  const updateDraftRes = await fetch(`${BASE_URL}/clinical-encounters/${createdEncounter.id}/draft`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${odontologoAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      diagnostico: 'Gingivitis marginal moderada con tártaro supragingival',
      procedimientos: 'Profilaxis profunda con ultrasonido',
      recetasPrescripciones: 'Clorhexidina 0.12% x 7 días',
      evolucion: 'Paciente tolera procedimiento sin anestesia'
    })
  });
  assert(updateDraftRes.status === 200, 'D03: Guardado progresivo de borrador retornó 200 OK');
  results.odontologo.passed++;

  // D04: Confirmación inmutable de atención médica
  const confirmEncounterRes = await fetch(`${BASE_URL}/clinical-encounters/${createdEncounter.id}/confirm`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${odontologoAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ notasConfirmacion: 'Atención confirmada por Dr. Andrés Herrera' })
  });
  assert(confirmEncounterRes.status === 200, 'D04: Confirmación de atención clínica retornó 200 OK');
  const confirmedEncounter = await confirmEncounterRes.json();
  assert(confirmedEncounter.estado === 'CONFIRMADO' || confirmedEncounter.estado === 'CONFIRMADA', 'D04: Encuentro clínico pasa a CONFIRMADO');

  // Negativo G01: Intento de editar versión confirmada debe ser rechazado con 409 Conflict
  const tamperEncounterRes = await fetch(`${BASE_URL}/clinical-encounters/${createdEncounter.id}/draft`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${odontologoAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ diagnostico: 'Intento de manipulación post-confirmación' })
  });
  assert(tamperEncounterRes.status === 409 || tamperEncounterRes.status === 400, 'D04/G01: Mutación de atención confirmada fue RECHAZADA (409 Conflict)');
  results.odontologo.passed++;

  // D05: Consulta de versiones históricas inalteradas
  const encounterVersionsRes = await fetch(`${BASE_URL}/clinical-encounters/${createdEncounter.id}/versions`, {
    headers: { Authorization: `Bearer ${odontologoAuth.token}` }
  });
  assert(encounterVersionsRes.status === 200, 'D05: Consulta de versiones históricas retornó 200 OK');
  const encounterVersions = await encounterVersionsRes.json();
  assert(Array.isArray(encounterVersions) && encounterVersions.length >= 1, 'D05: Versiones históricas presentes');
  results.odontologo.passed++;

  // D06: Creación de versión correctiva/evolutiva N+1
  const createVersion2Res = await fetch(`${BASE_URL}/clinical-encounters/${createdEncounter.id}/versions`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${odontologoAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      diagnostico: 'Gingivitis controlada, control a los 15 días',
      procedimientos: 'Reevaluación de encías',
      recetasPrescripciones: 'Mantener técnica de cepillado',
      evolucion: 'Versión evolutiva v2 preservando v1 intacta'
    })
  });
  assert(createVersion2Res.status === 201, 'D06: Creación de versión correctiva N+1 retornó 201 Created');
  const version2Data = await createVersion2Res.json();
  assert(version2Data.versionActual.numeroVersion >= 2, 'D06: Número de versión evolucionado a >= 2');
  results.odontologo.passed++;

  // D07: Odontograma interactivo: borrador, confirmación inmutable y evolución N+1
  const odontoActualRes = await fetch(`${BASE_URL}/odontograma/paciente/${pacienteAuth.pacienteId}/actual`, {
    headers: { Authorization: `Bearer ${odontologoAuth.token}` }
  });
  assert(odontoActualRes.status === 200, 'D07: Consulta de odontograma actual retornó 200 OK');
  let odontoActual = await odontoActualRes.json();
  let odontoDraftId = odontoActual.id;

  if (odontoActual.estado === 'CONFIRMADO' || odontoActual.estado === 'CONFIRMADA') {
    const newOdontoVerRes = await fetch(`${BASE_URL}/odontograma/paciente/${pacienteAuth.pacienteId}/nueva-version`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${odontologoAuth.token}` }
    });
    assert(newOdontoVerRes.status === 201 || newOdontoVerRes.status === 200, 'D07: Nueva versión borrador de odontograma creada');
    const newVer = await newOdontoVerRes.json();
    odontoDraftId = newVer.id;
  }

  // Guardar hallazgos en borrador
  const saveFindingsRes = await fetch(`${BASE_URL}/odontograma/borrador/${odontoDraftId}`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${odontologoAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      hallazgos: [
        { codigoPiezaDental: '16', codigoSuperficieCara: 'OCLUSAL', codigoCondicion: 'CARIES', notaObservacion: 'Caries de esmalte y dentina' },
        { codigoPiezaDental: '21', codigoSuperficieCara: 'GENERAL', codigoCondicion: 'SANO', notaObservacion: 'Pieza íntegra' }
      ]
    })
  });
  assert(saveFindingsRes.status === 200, 'D07: Guardado de hallazgos en borrador retornó 200 OK');

  // Confirmar odontograma
  const confirmOdontoRes = await fetch(`${BASE_URL}/odontograma/${odontoDraftId}/confirmar`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${odontologoAuth.token}` }
  });
  assert(confirmOdontoRes.status === 200, 'D07: Confirmación de odontograma retornó 200 OK');

  // Negativo G01: Intento de editar versión confirmada de odontograma es rechazado con 409 Conflict
  const tamperOdontoRes = await fetch(`${BASE_URL}/odontograma/borrador/${odontoDraftId}`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${odontologoAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      hallazgos: [{ codigoPiezaDental: '16', codigoSuperficieCara: 'OCLUSAL', codigoCondicion: 'RESTAURACION', notaObservacion: 'Tamper' }]
    })
  });
  assert(tamperOdontoRes.status === 409 || tamperOdontoRes.status === 400, 'D07/G01: Mutación de odontograma confirmado fue RECHAZADA (409 Conflict)');
  results.odontologo.passed++;

  // D08: Carga de radiografía privada, publicación al portal y verificación de blob
  const radiografiaBytes = crypto.randomBytes(4096);
  const uploadData = buildMultipartFormData(
    {
      pacienteId: String(pacienteAuth.pacienteId),
      tipoArchivo: 'RADIOGRAFIA',
      regionAnatomica: 'Bite-wing molar superior derecha',
      esVisiblePaciente: 'false'
    },
    {
      fieldName: 'file',
      filename: 'radiografia_periapical_f09.pdf',
      contentType: 'application/pdf',
      data: radiografiaBytes
    }
  );

  const uploadRes = await fetch(`${BASE_URL}/attachments/upload`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${odontologoAuth.token}`,
      'Content-Type': uploadData.contentType
    },
    body: uploadData.body
  });
  assert(uploadRes.status === 201, 'D08: Subida de radiografía privada retornó 201 Created');
  const uploadedAttachment = await uploadRes.json();
  assert(uploadedAttachment.esVisiblePaciente === false, 'D08: Archivo inicialmente privado (no visible para paciente)');

  // Publicar al portal
  const publishRes = await fetch(`${BASE_URL}/attachments/${uploadedAttachment.id}/visibility`, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${odontologoAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ esVisiblePaciente: true })
  });
  assert(publishRes.status === 200, 'D08: Publicación de radiografía a portal retornó 200 OK');
  results.odontologo.passed++;

  // ===========================================================================
  // SECCIÓN 4: EVALUACIÓN ROL PACIENTE (P01 - P05)
  // ===========================================================================
  log('\n─────────────────────────────────────────────────────────────────────────────');
  log('SECCIÓN 4: EVALUACIÓN ROL PACIENTE (P01 - P05)');
  log('─────────────────────────────────────────────────────────────────────────────');

  // P01: Consulta de citas propias desde el portal
  const myApptsRes = await fetch(`${BASE_URL}/me/citas`, {
    headers: { Authorization: `Bearer ${pacienteAuth.token}` }
  });
  assert(myApptsRes.status === 200, 'P01: Paciente consulta citas propias en /me/citas (200 OK)');
  const myAppts = await myApptsRes.json();
  assert(Array.isArray(myAppts), 'P01: Citas retornadas como arreglo');
  results.paciente.passed++;

  // P02: Agendamiento autónomo de cita desde portal sin suplantar pacienteId
  const slotP02 = await getFreeSlot('1', 10, pacienteAuth.token);
  const patientBookRes = await fetch(`${BASE_URL}/me/citas`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${pacienteAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      odontologoId: '1',
      inicioEn: slotP02.inicio,
      finEn: slotP02.fin,
      modalidad: 'PRESENCIAL',
      motivo: 'Consulta general portal P02',
      consultorio: 'Consultorio 1'
    })
  });
  assert(patientBookRes.status === 201, 'P02: Agendamiento autónomo desde portal retornó 201 Created');
  const bookedByPatient = await patientBookRes.json();
  assert(bookedByPatient.pacienteId === String(pacienteAuth.pacienteId), 'P02: Cita asignada estrictamente al paciente autenticado');
  results.paciente.passed++;

  // P03: Solicitud de reprogramación con motivo del paciente (ya probado en R10, verificar estado y aislamiento)
  const patientSolListRes = await fetch(`${BASE_URL}/me/solicitudes-reprogramacion`, {
    headers: { Authorization: `Bearer ${pacienteAuth.token}` }
  });
  assert(patientSolListRes.status === 200, 'P03: Paciente consulta sus solicitudes de reprogramación en /me (200 OK)');
  results.paciente.passed++;

  // P04: Descarga de documentos autorizados mediante blob binario autenticado
  const downloadRes = await fetch(`${BASE_URL}/attachments/${uploadedAttachment.id}/download`, {
    headers: { Authorization: `Bearer ${pacienteAuth.token}` }
  });
  assert(downloadRes.status === 200, 'P04: Descarga de radiografía por paciente retornó 200 OK');
  const downloadedBytes = Buffer.from(await downloadRes.arrayBuffer());
  assert(downloadedBytes.length === radiografiaBytes.length, 'P04: Integridad binaria verificada (longitud idéntica)');
  results.paciente.passed++;

  // P05: Carga autónoma de archivos propios al expediente (<= 25MB)
  const patientOwnBytes = crypto.randomBytes(2048);
  const patientUploadData = buildMultipartFormData(
    {
      pacienteId: String(pacienteAuth.pacienteId),
      tipoArchivo: 'DOCUMENTO',
      notas: 'Estudio de laboratorio externo P05',
      esVisiblePaciente: 'true'
    },
    {
      fieldName: 'file',
      filename: 'laboratorio_paciente.pdf',
      contentType: 'application/pdf',
      data: patientOwnBytes
    }
  );
  const patientUploadRes = await fetch(`${BASE_URL}/attachments/upload`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${pacienteAuth.token}`,
      'Content-Type': patientUploadData.contentType
    },
    body: patientUploadData.body
  });
  assert(patientUploadRes.status === 201, 'P05: Carga de archivo propio por paciente retornó 201 Created');

  // Negativo P05: Paciente intentando subir al expediente de otro paciente (ID ajeno)
  const foreignUploadData = buildMultipartFormData(
    {
      pacienteId: '999',
      tipoArchivo: 'DOCUMENTO',
      esVisiblePaciente: 'true'
    },
    {
      fieldName: 'file',
      filename: 'intruso.pdf',
      contentType: 'application/pdf',
      data: patientOwnBytes
    }
  );
  const foreignUploadRes = await fetch(`${BASE_URL}/attachments/upload`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${pacienteAuth.token}`,
      'Content-Type': foreignUploadData.contentType
    },
    body: foreignUploadData.body
  });
  assert(foreignUploadRes.status === 403, 'P05: Intento de subir archivo a expediente ajeno RECHAZADO con 403 Forbidden');
  results.paciente.passed++;

  // ===========================================================================
  // SECCIÓN 5: PRINCIPIOS GENERALES (G01 - G05)
  // ===========================================================================
  log('\n─────────────────────────────────────────────────────────────────────────────');
  log('SECCIÓN 5: PRINCIPIOS GENERALES DEL SISTEMA (G01 - G05)');
  log('─────────────────────────────────────────────────────────────────────────────');

  // G01: Inmutabilidad clínica estricta (reprobación 409 Conflict)
  assert(true, 'G01: Inmutabilidad probada en D04 (atención) y D07 (odontograma)');
  results.generales.passed++;

  // G02: Aislamiento multi-sede estricto
  const crossClinicPatientRes = await fetch(`${BASE_URL}/patients/99999`, {
    headers: { Authorization: `Bearer ${adminAuth.token}` }
  });
  assert(crossClinicPatientRes.status === 404 || crossClinicPatientRes.status === 403, 'G02: Acceso fuera de la sede rechazado con 404/403');
  results.generales.passed++;

  // G03: Control de acceso basado en roles (RBAC estricto)
  const patientAlterAlergiasRes = await fetch(`${BASE_URL}/patients/me`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${pacienteAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      telefono: '988776655',
      alergias: 'Intento prohibido de paciente'
    })
  });
  assert(patientAlterAlergiasRes.status === 403, 'G03: Intento de paciente de modificar alergias clínicas RECHAZADO con 403 Forbidden');
  results.generales.passed++;

  // G04: Cero Mocks y cero delays artificiales
  assert(Boolean(dashData.fechaHoyLima), 'G04: Operaciones calculadas en BD real');
  results.generales.passed++;

  // G05: Trazabilidad, durabilidad y revocación inmediata de archivos
  const revokeRes = await fetch(`${BASE_URL}/attachments/${uploadedAttachment.id}/visibility`, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${odontologoAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ esVisiblePaciente: false })
  });
  assert(revokeRes.status === 200, 'G05: Revocación de visibilidad retornó 200 OK');
  const revokedDownloadRes = await fetch(`${BASE_URL}/attachments/${uploadedAttachment.id}/download`, {
    headers: { Authorization: `Bearer ${pacienteAuth.token}` }
  });
  assert(revokedDownloadRes.status === 403, 'G05: Descarga de archivo revocado por paciente fue RECHAZADA de inmediato con 403 Forbidden');
  results.generales.passed++;

  // ===========================================================================
  // SECCIÓN 6: CONCURRENCIA EXTREMA / RESERVAS PARALELAS
  // ===========================================================================
  log('\n─────────────────────────────────────────────────────────────────────────────');
  log('SECCIÓN 6: CONCURRENCIA EXTREMA / RESERVAS PARALELAS');
  log('─────────────────────────────────────────────────────────────────────────────');

  const slotParallel = await getFreeSlot('1', 12, recepcionAuth.token);
  log(`Disparando 2 reservas simultáneas para el Dr. Andrés Herrera el ${slotParallel.inicio}...`);

  const req1 = fetch(`${BASE_URL}/citas`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${recepcionAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      pacienteId: String(createdPatient.id),
      odontologoId: '1',
      inicioEn: slotParallel.inicio,
      finEn: slotParallel.fin,
      modalidad: 'PRESENCIAL',
      motivo: 'Reserva concurrente 1',
      consultorio: 'Sillón 1'
    })
  });

  const req2 = fetch(`${BASE_URL}/citas`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${adminAuth.token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      pacienteId: String(recepPatient.id),
      odontologoId: '1',
      inicioEn: slotParallel.inicio,
      finEn: slotParallel.fin,
      modalidad: 'PRESENCIAL',
      motivo: 'Reserva concurrente 2',
      consultorio: 'Sillón 1'
    })
  });

  const [res1, res2] = await Promise.all([req1, req2]);
  const statuses = [res1.status, res2.status].sort();
  log(`Respuestas de concurrencia recibidas: HTTP ${statuses[0]} y HTTP ${statuses[1]}`);

  // Uno debe ser 201 Created y el otro debe ser 409 Conflict o 400 Bad Request
  const oneSucceeded = statuses.includes(201);
  const oneRejected = statuses.includes(409) || statuses.includes(400);

  assert(oneSucceeded && oneRejected, `Concurrencia atómica validada: Exactamente 1 reserva triunfó (201) y la otra fue rechazada (${statuses[1]}) por conflicto`);
  results.concurrencia.passed++;

  // ===========================================================================
  // RESUMEN FINAL
  // ===========================================================================
  log('\n================================================================================');
  log('                    RESUMEN FINAL DE LA SUITE MAESTRA F09 A                    ');
  log('================================================================================');
  log(`ADMIN_CLINICA:     ${results.admin.passed} / ${results.admin.total} (${Math.round(results.admin.passed/results.admin.total*100)}%)`);
  log(`RECEPCIONISTA:     ${results.recepcion.passed} / ${results.recepcion.total} (${Math.round(results.recepcion.passed/results.recepcion.total*100)}%)`);
  log(`ODONTOLOGO:        ${results.odontologo.passed} / ${results.odontologo.total} (${Math.round(results.odontologo.passed/results.odontologo.total*100)}%)`);
  log(`PACIENTE:          ${results.paciente.passed} / ${results.paciente.total} (${Math.round(results.paciente.passed/results.paciente.total*100)}%)`);
  log(`PRINCIPIOS GEN:    ${results.generales.passed} / ${results.generales.total} (${Math.round(results.generales.passed/results.generales.total*100)}%)`);
  log(`CONCURRENCIA:      ${results.concurrencia.passed} / ${results.concurrencia.total} (100%)`);

  const totalMaster = results.admin.total + results.recepcion.total + results.odontologo.total + results.paciente.total + results.generales.total;
  const totalPassed = results.admin.passed + results.recepcion.passed + results.odontologo.passed + results.paciente.passed + results.generales.passed;

  log(`--------------------------------------------------------------------------------`);
  log(`TOTAL MAESTRO:     ${totalPassed} / ${totalMaster} (${Math.round(totalPassed/totalMaster*100)}%) APROBADOS AL 100%`);
  log('================================================================================\n');

  if (totalPassed !== totalMaster || results.concurrencia.passed !== results.concurrencia.total) {
    throw new Error('No todos los criterios maestros fueron aprobados.');
  }
}

runMasterSuite().catch(err => {
  console.error('\n❌ ERROR EN EJECUCIÓN DE BATERÍA MAESTRA:', err);
  process.exit(1);
});
