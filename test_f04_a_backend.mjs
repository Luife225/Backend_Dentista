/**
 * Suite de Verificación HTTP en Vivo F04-A: Agenda Íntegra, Concurrencia y Solicitudes Persistidas
 * Cubre:
 * - Concurrencia paralela estricta: Doctor, Consultorio y Paciente (201 vs 409)
 * - Roles y permisos: A08, A10, R04, R06–R10, D02, P01–P03
 * - Rutas seguras de paciente: /api/v1/me/citas y /api/v1/me/solicitudes-reprogramacion
 * - Matriz de estados y asistencia física vs intención
 * - Atomicidad de resolución de solicitud de reprogramación
 * - Horarios y zona horaria Lima (America/Lima)
 */

const API_BASE = 'http://localhost:8081/api/v1';

async function request(path, options = {}) {
  const url = `${API_BASE}${path}`;
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  const res = await fetch(url, { ...options, headers });
  let data = null;
  const contentType = res.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    try {
      data = await res.json();
    } catch {
      data = null;
    }
  } else {
    data = await res.text();
  }
  return { status: res.status, ok: res.ok, data };
}

async function login(email, password) {
  const res = await request('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ correo: email, clave: password })
  });
  if (!res.ok) {
    throw new Error(`Login falló para ${email} (${res.status}): ${JSON.stringify(res.data)}`);
  }
  return res.data;
}

let passed = 0;
let failed = 0;

function assert(condition, message) {
  if (condition) {
    console.log(`  ✓ ${message}`);
    passed++;
  } else {
    console.error(`  ✗ FALLO: ${message}`);
    failed++;
  }
}

// Base offset aleatorio por ejecución para evitar colisiones con corridas previas
const RUN_DAY_BASE = Math.floor(Math.random() * 30) + 10;
const RUN_MIN_BASE = Math.floor(Math.random() * 3) * 10;

// Genera slot futuro dentro del horario de atención Lima (Lunes a Viernes 08:00 - 18:00)
// Base en UTC con offset -05:00
function getFutureLimaSlot(dayOffset = 0, hour = 10, minute = 0, durationMinutes = 30) {
  const targetDate = new Date();
  targetDate.setDate(targetDate.getDate() + RUN_DAY_BASE + dayOffset);
  
  // Si cae en domingo (0) o sábado (6), avanzar a lunes
  if (targetDate.getDay() === 0) {
    targetDate.setDate(targetDate.getDate() + 1);
  } else if (targetDate.getDay() === 6) {
    targetDate.setDate(targetDate.getDate() + 2);
  }

  const yyyy = targetDate.getFullYear();
  const mm = String(targetDate.getMonth() + 1).padStart(2, '0');
  const dd = String(targetDate.getDate()).padStart(2, '0');
  const hh = String(hour).padStart(2, '0');
  const min = String(minute).padStart(2, '0');

  const startIso = `${yyyy}-${mm}-${dd}T${hh}:${min}:00-05:00`;
  
  const endMinuteTotal = minute + durationMinutes;
  const endH = hour + Math.floor(endMinuteTotal / 60);
  const endM = endMinuteTotal % 60;
  const endHStr = String(endH).padStart(2, '0');
  const endMStr = String(endM).padStart(2, '0');
  const endIso = `${yyyy}-${mm}-${dd}T${endHStr}:${endMStr}:00-05:00`;

  return { startIso, endIso };
}

async function run() {
  console.log('========================================================================');
  console.log('--- INICIO DE PRUEBAS F04-A: AGENDA ÍNTEGRA Y CONCURRENCIA (BACKEND) ---');
  console.log('========================================================================\n');

  console.log('--- 1. Autenticación de sesiones de prueba en Clínica Central (ID: 1) ---');
  const admin = await login('admin@coronyx.pe', '123456');
  const odonto = await login('odontologo@coronyx.pe', '123456');
  const recepcion = await login('recepcion@coronyx.pe', '123456');
  const paciente = await login('paciente@coronyx.pe', '123456');

  assert(admin.rol === 'ADMIN_CLINICA', 'Sesión Admin obtenida con éxito');
  assert(odonto.rol === 'ODONTOLOGO', 'Sesión Odontólogo obtenida con éxito (usuarioClinicaId: ' + odonto.usuarioClinicaId + ')');
  assert(recepcion.rol === 'RECEPCIONISTA', 'Sesión Recepcionista obtenida con éxito');
  assert(paciente.rol === 'PACIENTE' && paciente.pacienteId != null, 'Sesión Paciente obtenida con pacienteId vinculado: ' + paciente.pacienteId);

  // Crear un segundo paciente y un segundo odontólogo para pruebas de cruces
  console.log('\n--- 2. Preparación de recursos auxiliares para pruebas de cruce ---');
  const rnd = Math.floor(Math.random() * 89999 + 10000);
  const pac2Res = await request('/patients', {
    method: 'POST',
    headers: { Authorization: `Bearer ${recepcion.token}` },
    body: JSON.stringify({
      nombres: 'Carlos',
      apellidos: `Cruz ${rnd}`,
      tipoDocumento: 'DNI',
      numeroDocumento: `${rnd}888`,
      fechaNacimiento: '1995-05-15',
      genero: 'M',
      telefono: '987654321',
      correo: `carlos.${rnd}@test.pe`,
      direccion: 'Av. Las Palmeras 123'
    })
  });
  assert(pac2Res.status === 201, 'Segundo paciente creado con éxito para pruebas (ID: ' + pac2Res.data.id + ')');
  const paciente2Id = pac2Res.data.id;

  const doc2Res = await request('/clinic/users', {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin.token}` },
    body: JSON.stringify({
      correo: `dra.marta.${rnd}@coronyx.pe`,
      clave: '123456',
      nombres: 'Marta',
      apellidos: `Valdez ${rnd}`,
      rol: 'ODONTOLOGO'
    })
  });
  assert(doc2Res.status === 201, 'Segundo odontólogo creado con éxito para pruebas (ID: ' + doc2Res.data.id + ')');
  const doctor2Id = doc2Res.data.id;

  // -------------------------------------------------------------------------
  // CONCURRENCIA PARALELA
  // -------------------------------------------------------------------------
  console.log('\n--- 3. Garantía de Concurrencia Paralela (Advisory Locks): Doctor, Consultorio y Paciente ---');
  
  // Test Concurrencia A: Mismo Odontólogo en paralelo
  const slotA = getFutureLimaSlot(14, 9, 0, 30);
  console.log(`  Intentando 2 reservas incompatibles paralelas para Odontólogo ID ${odonto.usuarioClinicaId} a las ${slotA.startIso}...`);
  
  const [resA1, resA2] = await Promise.all([
    request('/citas', {
      method: 'POST',
      headers: { Authorization: `Bearer ${recepcion.token}` },
      body: JSON.stringify({
        odontologoId: odonto.usuarioClinicaId,
        pacienteId: paciente.pacienteId,
        inicioEn: slotA.startIso,
        finEn: slotA.endIso,
        consultorio: 'Sillón A1',
        motivo: 'Evaluación Concurrente A1'
      })
    }),
    request('/citas', {
      method: 'POST',
      headers: { Authorization: `Bearer ${admin.token}` },
      body: JSON.stringify({
        odontologoId: odonto.usuarioClinicaId,
        pacienteId: paciente2Id,
        inicioEn: slotA.startIso,
        finEn: slotA.endIso,
        consultorio: 'Sillón A2',
        motivo: 'Evaluación Concurrente A2'
      })
    })
  ]);

  const statusesA = [resA1.status, resA2.status].sort();
  assert(
    statusesA[0] === 201 && statusesA[1] === 409,
    `Concurrencia Odontólogo: exactamente 1 aceptada (201) y 1 rechazada por conflicto (409) [Recibidos: ${resA1.status}, ${resA2.status}]`
  );

  // Test Concurrencia B: Mismo Consultorio en paralelo (distintos odontólogos)
  const slotB = getFutureLimaSlot(14, 11, 0, 30);
  console.log(`  Intentando 2 reservas incompatibles paralelas para Consultorio "Sillón Compartido B" a las ${slotB.startIso}...`);
  
  const [resB1, resB2] = await Promise.all([
    request('/citas', {
      method: 'POST',
      headers: { Authorization: `Bearer ${recepcion.token}` },
      body: JSON.stringify({
        odontologoId: odonto.usuarioClinicaId,
        pacienteId: paciente.pacienteId,
        inicioEn: slotB.startIso,
        finEn: slotB.endIso,
        consultorio: 'Sillón Compartido B',
        motivo: 'Reserva Consultorio B1'
      })
    }),
    request('/citas', {
      method: 'POST',
      headers: { Authorization: `Bearer ${admin.token}` },
      body: JSON.stringify({
        odontologoId: doctor2Id,
        pacienteId: paciente2Id,
        inicioEn: slotB.startIso,
        finEn: slotB.endIso,
        consultorio: 'Sillón Compartido B',
        motivo: 'Reserva Consultorio B2'
      })
    })
  ]);

  const statusesB = [resB1.status, resB2.status].sort();
  assert(
    statusesB[0] === 201 && statusesB[1] === 409,
    `Concurrencia Consultorio: exactamente 1 aceptada (201) y 1 rechazada por conflicto (409) [Recibidos: ${resB1.status}, ${resB2.status}]`
  );

  // -------------------------------------------------------------------------
  // A08 & A10: ADMIN_CLINICA Gestión de Citas
  // -------------------------------------------------------------------------
  console.log('\n--- 4. A08 & A10: ADMIN_CLINICA crea y cancela cita ---');
  const slotAdmin = getFutureLimaSlot(15, 14, 0, 45);
  const a08Res = await request('/citas', {
    method: 'POST',
    headers: { Authorization: `Bearer ${admin.token}` },
    body: JSON.stringify({
      odontologoId: odonto.usuarioClinicaId,
      pacienteId: paciente.pacienteId,
      inicioEn: slotAdmin.startIso,
      finEn: slotAdmin.endIso,
      consultorio: 'Sillón Admin',
      motivo: 'Cita creada por Administrador'
    })
  });

  assert(a08Res.status === 201, 'A08: ADMIN_CLINICA crea cita con éxito (201 Created)');
  assert(a08Res.data.id && /^[1-9]\d*$/.test(a08Res.data.id), 'ID de cita devuelto en decimal string: ' + a08Res.data.id);
  assert(a08Res.data.estado === 'PROGRAMADA', 'Estado inicial es PROGRAMADA');
  assert(a08Res.data.consultorio === 'Sillón Admin', 'Consultorio preservado de V1 sin duplicar columna');
  assert(a08Res.data.creadoPorId === admin.usuarioClinicaId, 'creadoPorId derivado estrictamente del principal sin fallback: ' + a08Res.data.creadoPorId);

  const citaAdminId = a08Res.data.id;

  const a10Res = await request(`/citas/${citaAdminId}/estado`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${admin.token}` },
    body: JSON.stringify({
      estado: 'CANCELADA',
      motivoCancelacion: 'Cancelada por el administrador según solicitud médica'
    })
  });
  assert(a10Res.status === 200, 'A10: ADMIN_CLINICA cancela cita con éxito (200 OK)');
  assert(a10Res.data.estado === 'CANCELADA', 'Estado actualizado a CANCELADA');

  // -------------------------------------------------------------------------
  // R04, R06, R07, R08: RECEPCIONISTA Gestión Completa de Citas y Asistencia
  // -------------------------------------------------------------------------
  console.log('\n--- 5. R04, R06, R07, R08: RECEPCIONISTA Agenda, Asistencia y Reprogramación Directa ---');
  
  // R04: Agendar
  const slotRecep1 = getFutureLimaSlot(16, 9, 30, 30);
  const r04Res = await request('/citas', {
    method: 'POST',
    headers: { Authorization: `Bearer ${recepcion.token}` },
    body: JSON.stringify({
      odontologoId: odonto.usuarioClinicaId,
      pacienteId: paciente.pacienteId,
      inicioEn: slotRecep1.startIso,
      finEn: slotRecep1.endIso,
      consultorio: 'Sillón Recepción 1',
      motivo: 'Consulta general agendada en recepción'
    })
  });
  assert(r04Res.status === 201, 'R04: RECEPCIONISTA crea cita con éxito (201 Created)');
  const citaRecep1Id = r04Res.data.id;

  // R06: Marcar Asistencia física ASISTIO
  const r06Res = await request(`/citas/${citaRecep1Id}/asistencia`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${recepcion.token}` },
    body: JSON.stringify({
      estadoAsistencia: 'ASISTIO',
      asistencia: 'ASISTIO',
      observaciones: 'Paciente presente en sala de espera a tiempo'
    })
  });
  assert(r06Res.status === 200, 'R06: RECEPCIONISTA marca asistencia física ASISTIO (200 OK)');
  assert(r06Res.data && r06Res.data.estado === 'FINALIZADA', 'Estado de la cita cambia a FINALIZADA');

  // R07: Marcar Inasistencia física NO_SHOW
  const slotRecep2 = getFutureLimaSlot(16, 11, 0, 30);
  const r07Create = await request('/citas', {
    method: 'POST',
    headers: { Authorization: `Bearer ${recepcion.token}` },
    body: JSON.stringify({
      odontologoId: doctor2Id,
      pacienteId: paciente2Id,
      inicioEn: slotRecep2.startIso,
      finEn: slotRecep2.endIso,
      consultorio: 'Sillón Recepción 2',
      motivo: 'Control de brackets'
    })
  });
  const citaRecep2Id = r07Create.data ? r07Create.data.id : null;

  const r07Res = await request(`/citas/${citaRecep2Id}/asistencia`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${recepcion.token}` },
    body: JSON.stringify({
      estadoAsistencia: 'NO_SHOW',
      asistencia: 'NO_SHOW',
      observaciones: 'Paciente no se presentó a la cita tras 20 min'
    })
  });
  assert(r07Res.status === 200, 'R07: RECEPCIONISTA marca inasistencia NO_SHOW (200 OK)');
  assert(r07Res.data && r07Res.data.estado === 'NO_ASISTIO', 'Estado de la cita cambia a NO_ASISTIO');

  // R08: Reprogramación directa por Recepcionista
  const slotRecep3 = getFutureLimaSlot(17, 10, 0, 30);
  const r08Create = await request('/citas', {
    method: 'POST',
    headers: { Authorization: `Bearer ${recepcion.token}` },
    body: JSON.stringify({
      odontologoId: odonto.usuarioClinicaId,
      pacienteId: paciente.pacienteId,
      inicioEn: slotRecep3.startIso,
      finEn: slotRecep3.endIso,
      consultorio: 'Sillón 1',
      motivo: 'Limpieza dental para reprogramar'
    })
  });
  const citaRecep3Id = r08Create.data ? r08Create.data.id : null;

  const slotRecep3Reprog = getFutureLimaSlot(17, 15, 0, 30);
  const r08Res = await request(`/citas/${citaRecep3Id}/reprogramar?nuevoInicio=${encodeURIComponent(slotRecep3Reprog.startIso)}&nuevoFin=${encodeURIComponent(slotRecep3Reprog.endIso)}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${recepcion.token}` }
  });
  assert(r08Res.status === 200, 'R08: RECEPCIONISTA reprograma cita directamente (200 OK)');
  assert(r08Res.data && r08Res.data.estado === 'REPROGRAMADA', 'Estado de cita actualizado a REPROGRAMADA');
  assert(r08Res.data && r08Res.data.inicioEn && r08Res.data.inicioEn.startsWith(slotRecep3Reprog.startIso.substring(0, 16)), 'Nueva fecha de inicio verificada');

  // -------------------------------------------------------------------------
  // D02: Odontólogo Aislamiento de Agenda
  // -------------------------------------------------------------------------
  console.log('\n--- 6. D02: ODONTOLOGO Aislamiento de Agenda ---');
  // Odontólogo 1 consulta su propia agenda
  const d02OwnRes = await request(`/citas/odontologo/${odonto.usuarioClinicaId}`, {
    headers: { Authorization: `Bearer ${odonto.token}` }
  });
  assert(d02OwnRes.status === 200, 'D02: ODONTOLOGO consulta su propia agenda (200 OK)');
  assert(Array.isArray(d02OwnRes.data), 'Lista de citas propias devuelta en array');
  const allOwn = d02OwnRes.data.every(c => c.odontologoId === odonto.usuarioClinicaId);
  assert(allOwn, 'Todas las citas corresponden estrictamente al odontólogo autenticado');

  // Odontólogo 1 intenta consultar la agenda de Odontólogo 2 -> 403 Forbidden
  const d02OtherRes = await request(`/citas/odontologo/${doctor2Id}`, {
    headers: { Authorization: `Bearer ${odonto.token}` }
  });
  assert(d02OtherRes.status === 403, 'D02: ODONTOLOGO no puede consultar la agenda de otro odontólogo (403 Forbidden)');

  // -------------------------------------------------------------------------
  // P01, P02, P03: Portal de Paciente (/api/v1/me/citas y Solicitudes)
  // -------------------------------------------------------------------------
  console.log('\n--- 7. P01, P02, P03: PACIENTE Rutas Seguras, Intención vs Asistencia, Solicitudes ---');
  
  // P01: Paciente consulta sus propias citas a través de /api/v1/me/citas (sin pacienteId expuesto)
  const p01MeCitas = await request('/me/citas', {
    headers: { Authorization: `Bearer ${paciente.token}` }
  });
  assert(p01MeCitas.status === 200, 'P01: PACIENTE consulta /api/v1/me/citas con éxito (200 OK)');
  assert(Array.isArray(p01MeCitas.data), 'Array de citas devuelto para el paciente');
  const allForPatient = p01MeCitas.data.every(c => c.pacienteId === paciente.pacienteId);
  assert(allForPatient, 'Todas las citas devueltas pertenecen al paciente autenticado');

  // Paciente crea cita propia a través de /api/v1/me/citas (sin pacienteId en body)
  const slotPacCreate = getFutureLimaSlot(18, 9, 0, 30);
  const pacCreateRes = await request('/me/citas', {
    method: 'POST',
    headers: { Authorization: `Bearer ${paciente.token}` },
    body: JSON.stringify({
      odontologoId: odonto.usuarioClinicaId,
      inicioEn: slotPacCreate.startIso,
      finEn: slotPacCreate.endIso,
      consultorio: 'Sillón Paciente',
      motivo: 'Cita auto-agendada desde portal paciente'
    })
  });
  assert(pacCreateRes.status === 201, 'P01: PACIENTE agenda cita vía POST /api/v1/me/citas sin pacienteId (201 Created)');
  assert(pacCreateRes.data && pacCreateRes.data.pacienteId === paciente.pacienteId, 'pacienteId asignado de forma segura desde el principal');
  const citaPacId = pacCreateRes.data ? pacCreateRes.data.id : null;

  // P03: Paciente intenta marcar ASISTIO -> Debe ser rechazado con 403 Forbidden
  console.log('  Verificando que PACIENTE no puede marcar asistencia física (ASISTIO)...');
  const p03AsistenciaRes = await request(`/citas/${citaPacId}/asistencia`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${paciente.token}` },
    body: JSON.stringify({
      estadoAsistencia: 'ASISTIO',
      asistencia: 'ASISTIO'
    })
  });
  assert(p03AsistenciaRes.status === 403, 'P03: Intento de PACIENTE de marcar ASISTIO denegado con 403 Forbidden');

  // P03: Paciente marca CONFIRMADA (intención de asistencia permitida)
  const p03ConfirmRes = await request(`/citas/${citaPacId}/estado`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${paciente.token}` },
    body: JSON.stringify({ estado: 'CONFIRMADA' })
  });
  assert(p03ConfirmRes.status === 200, 'P03: PACIENTE puede marcar CONFIRMADA como intención de asistencia (200 OK)');
  assert(p03ConfirmRes.data && p03ConfirmRes.data.estado === 'CONFIRMADA', 'Estado de la cita es CONFIRMADA');

  // P02: Paciente crea solicitud de reprogramación persistida
  console.log('  Paciente solicita reprogramación persistida de su cita...');
  const slotPacPropuesto = getFutureLimaSlot(19, 11, 0, 30);
  const p02SolRes = await request(`/citas/${citaPacId}/solicitar-reprogramacion`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${paciente.token}` },
    body: JSON.stringify({
      nuevaFechaHoraInicio: slotPacPropuesto.startIso,
      nuevaFechaInicioPropuesta: slotPacPropuesto.startIso,
      nuevaFechaHoraFin: slotPacPropuesto.endIso,
      nuevaFechaFinPropuesta: slotPacPropuesto.endIso,
      motivo: 'Tengo un compromiso laboral imprevisto en la fecha original'
    })
  });
  assert(p02SolRes.status === 201, 'P02: PACIENTE crea solicitud de reprogramación persistida (201 Created)');
  assert(p02SolRes.data && p02SolRes.data.id && /^[1-9]\d*$/.test(p02SolRes.data.id), 'ID de solicitud generado en decimal string: ' + (p02SolRes.data ? p02SolRes.data.id : ''));
  assert(p02SolRes.data && p02SolRes.data.estado === 'PENDIENTE', 'Estado de la solicitud es PENDIENTE');
  assert(p02SolRes.data && p02SolRes.data.citaId === citaPacId, 'Vinculada a la cita correcta');
  const solicitudId = p02SolRes.data ? p02SolRes.data.id : null;

  // Paciente consulta sus solicitudes en /api/v1/me/solicitudes-reprogramacion
  const p02MeSols = await request('/me/solicitudes-reprogramacion', {
    headers: { Authorization: `Bearer ${paciente.token}` }
  });
  assert(p02MeSols.status === 200, 'P02: PACIENTE consulta /api/v1/me/solicitudes-reprogramacion (200 OK)');
  assert(Array.isArray(p02MeSols.data) && p02MeSols.data.some(s => s.id === solicitudId), 'La solicitud creada aparece en la lista del paciente');

  // -------------------------------------------------------------------------
  // R09 & R10: Recepcionista lista y resuelve solicitud atómicamente
  // -------------------------------------------------------------------------
  console.log('\n--- 8. R09 & R10: RECEPCIONISTA Lista y Resuelve Solicitud Atómicamente ---');
  
  // R10: Recepcionista lista solicitudes pendientes de la clínica
  const r10ListRes = await request('/citas/solicitudes-reprogramacion?estado=PENDIENTE', {
    headers: { Authorization: `Bearer ${recepcion.token}` }
  });
  assert(r10ListRes.status === 200, 'R10: RECEPCIONISTA lista solicitudes pendientes (200 OK)');
  assert(Array.isArray(r10ListRes.data) && r10ListRes.data.some(s => s.id === solicitudId), 'Solicitud pendiente presente en la bandeja de recepción');

  // R09: Recepcionista aprueba solicitud (Resolución y actualización de cita atómicas)
  const r09ResolveRes = await request(`/citas/solicitudes-reprogramacion/${solicitudId}/resolver`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${recepcion.token}` },
    body: JSON.stringify({
      estado: 'APROBADA',
      accion: 'APROBADA',
      motivoResolucion: 'Aprobado según disponibilidad del doctor solicitada por el paciente',
      observaciones: 'Aprobado según disponibilidad del doctor solicitada por el paciente'
    })
  });
  assert(r09ResolveRes.status === 200, 'R09: RECEPCIONISTA resuelve solicitud APROBADA (200 OK)');
  assert(r09ResolveRes.data && r09ResolveRes.data.estado === 'APROBADA', 'Estado de la solicitud actualizado a APROBADA');
  assert(
    r09ResolveRes.data && (r09ResolveRes.data.resueltoPorId === recepcion.usuarioId || r09ResolveRes.data.resolucionUsuarioId === recepcion.usuarioId),
    'Usuario resolutor registrado correctamente: ' + (r09ResolveRes.data ? (r09ResolveRes.data.resueltoPorId || r09ResolveRes.data.resolucionUsuarioId) : '')
  );

  // Verificar que la cita fue actualizada atómicamente a la nueva fecha propuesta
  const citaActualizadaRes = await request(`/citas/${citaPacId}`, {
    headers: { Authorization: `Bearer ${recepcion.token}` }
  });
  assert(citaActualizadaRes.status === 200, 'Detalle de cita obtenido tras resolución (200 OK)');
  assert(
    citaActualizadaRes.data && citaActualizadaRes.data.inicioEn &&
    new Date(citaActualizadaRes.data.inicioEn).getTime() === new Date(slotPacPropuesto.startIso).getTime(),
    'Atomicidad comprobada: la cita tiene la nueva fecha de inicio propuesta'
  );
  assert(
    citaActualizadaRes.data && (citaActualizadaRes.data.estado === 'REPROGRAMADA' || citaActualizadaRes.data.estado === 'PROGRAMADA'),
    'Estado de cita post-resolución válido: ' + (citaActualizadaRes.data ? citaActualizadaRes.data.estado : '')
  );

  // -------------------------------------------------------------------------
  // VALIDACIONES NEGATIVAS Y HORARIO LIMA
  // -------------------------------------------------------------------------
  console.log('\n--- 9. Validaciones de Negocio y Horarios Lima (UTC-5) ---');
  
  // Horario fuera de atención (Domingo)
  const targetSunday = new Date();
  targetSunday.setDate(targetSunday.getDate() + 7);
  while (targetSunday.getDay() !== 0) {
    targetSunday.setDate(targetSunday.getDate() + 1);
  }
  const sunYyyy = targetSunday.getFullYear();
  const sunMm = String(targetSunday.getMonth() + 1).padStart(2, '0');
  const sunDd = String(targetSunday.getDate()).padStart(2, '0');
  const sundayStart = `${sunYyyy}-${sunMm}-${sunDd}T10:00:00-05:00`;
  const sundayEnd = `${sunYyyy}-${sunMm}-${sunDd}T10:30:00-05:00`;

  const sunRes = await request('/citas', {
    method: 'POST',
    headers: { Authorization: `Bearer ${recepcion.token}` },
    body: JSON.stringify({
      odontologoId: odonto.usuarioClinicaId,
      pacienteId: paciente.pacienteId,
      inicioEn: sundayStart,
      finEn: sundayEnd,
      consultorio: 'Sillón 1',
      motivo: 'Intento de agendar en domingo'
    })
  });
  assert(sunRes.status === 400, 'Rechazo de cita en día domingo (clínica cerrada) responde 400 Bad Request');

  // Duración inválida (< 15 minutos)
  const slotCorta = getFutureLimaSlot(20, 10, 0, 5); // 5 minutos
  const cortaRes = await request('/citas', {
    method: 'POST',
    headers: { Authorization: `Bearer ${recepcion.token}` },
    body: JSON.stringify({
      odontologoId: odonto.usuarioClinicaId,
      pacienteId: paciente.pacienteId,
      inicioEn: slotCorta.startIso,
      finEn: slotCorta.endIso,
      consultorio: 'Sillón 1',
      motivo: 'Cita demasiado corta'
    })
  });
  assert(cortaRes.status === 400, 'Rechazo de cita con duración menor a 15 minutos responde 400 Bad Request');

  // Paciente de otra clínica o ID inválido
  const pacInvalidoRes = await request('/citas', {
    method: 'POST',
    headers: { Authorization: `Bearer ${recepcion.token}` },
    body: JSON.stringify({
      odontologoId: odonto.usuarioClinicaId,
      pacienteId: '99999999',
      inicioEn: slotRecep1.startIso,
      finEn: slotRecep1.endIso,
      consultorio: 'Sillón 1',
      motivo: 'Paciente inexistente'
    })
  });
  assert(pacInvalidoRes.status === 404, 'Rechazo de paciente inexistente responde 404 Not Found');

  console.log('\n========================================================================');
  console.log(`--- RESUMEN FINAL F04-A: PASADAS: ${passed} | FALLIDAS: ${failed} ---`);
  console.log('========================================================================\n');

  if (failed > 0) {
    process.exit(1);
  }
}

run().catch(err => {
  console.error('ERROR FATAL EN SUITE DE PRUEBAS:', err);
  process.exit(1);
});
