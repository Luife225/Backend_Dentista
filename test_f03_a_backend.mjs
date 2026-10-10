/**
 * Suite de Verificación HTTP en Vivo F03-A: Cuentas y Pacientes con Permisos
 * Valida A01–A07, R01–R03, desactivación afectando sesión previa,
 * eliminación de regla 5–100, vinculación de usuario sin equiparar IDs, y negativos.
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

async function run() {
  console.log('======================================================');
  console.log('--- INICIO DE PRUEBAS F03-A: CUENTAS Y PACIENTES API ---');
  console.log('======================================================\n');

  console.log('--- 1. Autenticación y obtención de credenciales de prueba ---');
  const sessionAdmin = await login('admin@coronyx.pe', '123456');
  const sessionOdonto = await login('odontologo@coronyx.pe', '123456');
  const sessionRecepcion = await login('recepcion@coronyx.pe', '123456');
  const sessionPaciente = await login('paciente@coronyx.pe', '123456');

  assert(sessionAdmin.rol === 'ADMIN_CLINICA', 'Sesión de Admin obtenida con rol ADMIN_CLINICA');
  assert(sessionOdonto.rol === 'ODONTOLOGO', 'Sesión de Odontólogo obtenida con rol ODONTOLOGO');
  assert(sessionRecepcion.rol === 'RECEPCIONISTA', 'Sesión de Recepcionista obtenida con rol RECEPCIONISTA');
  assert(sessionPaciente.rol === 'PACIENTE', 'Sesión de Paciente obtenida con rol PACIENTE');

  // --- A01: Crear cuenta de su clínica con ADMIN_CLINICA ---
  console.log('\n--- 2. A01: ADMIN_CLINICA crea cuenta y membresía con nueva sesión real ---');
  const suffix = Math.floor(Math.random() * 899999 + 100000);
  const newEmail = `doctor.${suffix}@coronyx.pe`;
  const createRes = await request('/clinic/users', {
    method: 'POST',
    headers: { Authorization: `Bearer ${sessionAdmin.token}` },
    body: JSON.stringify({
      correo: newEmail,
      clave: '123456',
      nombres: 'Esteban',
      apellidos: 'García',
      rol: 'ODONTOLOGO'
    })
  });

  assert(createRes.status === 201, `Creación de cuenta responde HTTP 201 Created (recibido ${createRes.status})`);
  assert(createRes.data.id && /^[1-9]\d*$/.test(createRes.data.id), `ID de membresía devuelto en decimal string: ${createRes.data.id}`);
  assert(createRes.data.usuarioId && /^[1-9]\d*$/.test(createRes.data.usuarioId), `usuarioId devuelto en decimal string: ${createRes.data.usuarioId}`);
  assert(createRes.data.clinicaId === '1', 'clinicaId devuelto como "1"');
  assert(createRes.data.rol === 'ODONTOLOGO', 'rol asignado devuelto como "ODONTOLOGO"');

  // Probar que la nueva cuenta puede iniciar sesión real con su contraseña
  const newSession = await login(newEmail, '123456');
  assert(newSession.token != null, 'Nueva cuenta inicia sesión real con token JWT');
  assert(newSession.usuarioId === createRes.data.usuarioId, 'usuarioId en sesión coincide con la cuenta creada');

  // --- A02: Editar cuenta con ADMIN_CLINICA ---
  console.log('\n--- 3. A02: ADMIN_CLINICA edita cuenta ---');
  const editRes = await request(`/clinic/users/${createRes.data.id}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${sessionAdmin.token}` },
    body: JSON.stringify({
      nombres: 'Esteban Editado',
      apellidos: 'García Actualizado',
      rol: 'RECEPCIONISTA'
    })
  });

  assert(editRes.status === 200, `Edición de cuenta responde HTTP 200 OK (recibido ${editRes.status})`);
  assert(editRes.data.nombres === 'Esteban Editado', 'Nombre actualizado en respuesta');
  assert(editRes.data.rol === 'RECEPCIONISTA', 'Rol actualizado a RECEPCIONISTA');

  // --- A03: Activar / Desactivar cuenta afectando sesión previa ---
  console.log('\n--- 4. A03: Desactivar membresía invalida sesión previa con token emitido ---');
  // La sesión del nuevo usuario estaba activa
  const checkBefore = await request('/patients', {
    headers: { Authorization: `Bearer ${newSession.token}` }
  });
  assert(checkBefore.status === 200, 'Sesión previa del usuario consulta API exitosamente antes de desactivar');

  // ADMIN_CLINICA desactiva la membresía
  const deactivateRes = await request(`/clinic/users/${createRes.data.id}/status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${sessionAdmin.token}` },
    body: JSON.stringify({ estado: 'INACTIVO' })
  });
  assert(deactivateRes.status === 200, `Desactivación responde HTTP 200 OK (recibido ${deactivateRes.status})`);
  assert(deactivateRes.data.estado === 'INACTIVO', 'Estado actualizado a INACTIVO');

  // Petición inmediata con el token anterior DEBE FALLAR con 401 Unauthorized
  const checkAfter = await request('/patients', {
    headers: { Authorization: `Bearer ${newSession.token}` }
  });
  assert(checkAfter.status === 401, `Sesión previa rechazada con HTTP 401 Unauthorized en tiempo real (recibido ${checkAfter.status})`);

  // Reactivar membresía
  const reactivateRes = await request(`/clinic/users/${createRes.data.id}/status`, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${sessionAdmin.token}` },
    body: JSON.stringify({ estado: 'ACTIVO' })
  });
  assert(reactivateRes.status === 200, 'Reactivación de membresía responde HTTP 200 OK');

  // Nueva sesión tras reactivar funciona
  const reSession = await login(newEmail, '123456');
  const checkReactivated = await request('/patients', {
    headers: { Authorization: `Bearer ${reSession.token}` }
  });
  assert(checkReactivated.status === 200, 'Nueva sesión tras reactivar accede con HTTP 200 OK');

  // --- A04: Asignar rol autorizado (Rechazar SUPER_ADMIN y auto-degradación) ---
  console.log('\n--- 5. A04: Rechazar asignación de SUPER_ADMIN y auto-desactivación ---');
  const rejectSuperRes = await request('/clinic/users', {
    method: 'POST',
    headers: { Authorization: `Bearer ${sessionAdmin.token}` },
    body: JSON.stringify({
      correo: 'super.fake@coronyx.pe',
      clave: '123456',
      nombres: 'Falso',
      apellidos: 'Super',
      rol: 'SUPER_ADMIN'
    })
  });
  assert(rejectSuperRes.status === 400, `Asignar SUPER_ADMIN es rechazado con HTTP 400 Bad Request (recibido ${rejectSuperRes.status})`);

  const rejectSelfRes = await request('/clinic/users/3/status', {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${sessionAdmin.token}` },
    body: JSON.stringify({ estado: 'INACTIVO' })
  });
  assert(rejectSelfRes.status === 400, `Auto-desactivación del propio admin es rechazada con HTTP 400 Bad Request (recibido ${rejectSelfRes.status})`);

  // --- A05: Listar pacientes administrativos con exclusión de datos clínicos ---
  console.log('\n--- 6. A05: ADMIN_CLINICA lista pacientes sin datos clínicos (Control G02) ---');
  const listAdminRes = await request('/patients', {
    headers: { Authorization: `Bearer ${sessionAdmin.token}` }
  });
  assert(listAdminRes.status === 200, 'Consulta /patients responde HTTP 200 OK');
  assert(Array.isArray(listAdminRes.data), 'Respuesta es un arreglo');
  const samplePatient = listAdminRes.data[0];
  assert(samplePatient.alergias === null, 'alergias es estrictamente null para rol administrativo');
  assert(samplePatient.antecedentesMedicos === null, 'antecedentesMedicos es estrictamente null');
  assert(samplePatient.medicamentos === null, 'medicamentos es estrictamente null');

  // Proyección administrativa explícita
  const listAdminProj = await request('/patients/admin', {
    headers: { Authorization: `Bearer ${sessionAdmin.token}` }
  });
  assert(listAdminProj.status === 200, 'GET /patients/admin responde HTTP 200 OK');

  // --- A06: Registrar paciente administrativo (Eliminación de regla rígida 5-100) ---
  console.log('\n--- 7. A06: Registrar paciente (menor de 5 años aceptado) ---');
  const dniPediatrico = String(Math.floor(Math.random() * 89999999 + 10000000));
  const twoYearsAgo = new Date();
  twoYearsAgo.setFullYear(twoYearsAgo.getFullYear() - 2);
  const birthDateStr = twoYearsAgo.toISOString().split('T')[0];

  const createPatientRes = await request('/patients', {
    method: 'POST',
    headers: { Authorization: `Bearer ${sessionAdmin.token}` },
    body: JSON.stringify({
      nombres: 'Joaquín',
      apellidos: 'Menor',
      tipoDocumento: 'DNI',
      numeroDocumento: dniPediatrico,
      fechaNacimiento: birthDateStr,
      telefono: '+51 987654321',
      correo: `joaquin.${dniPediatrico}@gmail.com`
    })
  });
  assert(createPatientRes.status === 201, `Alta de paciente de 2 años exitosa (HTTP 201 Created) - Regla 5-100 eliminada (recibido ${createPatientRes.status})`);
  assert(createPatientRes.data.id && /^[1-9]\d*$/.test(createPatientRes.data.id), `ID de paciente es decimal string: ${createPatientRes.data.id}`);

  // --- A07: Editar paciente administrativo sin tocar campos clínicos ---
  console.log('\n--- 8. A07: ADMIN_CLINICA edita paciente administrativo sin alterar clínica ---');
  const updatePatientRes = await request(`/patients/${createPatientRes.data.id}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${sessionAdmin.token}` },
    body: JSON.stringify({
      telefono: '+51 912345678',
      seguro: 'RIMAC',
      alergias: 'Intento de inyección clínica' // No debe persistir
    })
  });
  assert(updatePatientRes.status === 200, 'Actualización demográfica responde HTTP 200 OK');

  // Odontólogo consulta el paciente: alergias no fueron modificadas por el admin
  const checkOdonto = await request(`/patients/${createPatientRes.data.id}`, {
    headers: { Authorization: `Bearer ${sessionOdonto.token}` }
  });
  assert(checkOdonto.status === 200, 'Odontólogo consulta paciente creado');
  assert(checkOdonto.data.alergias == null, 'Alergias no fueron alteradas por el rol administrativo');

  // --- R01, R02, R03: Operación de Recepcionista ---
  console.log('\n--- 9. R01–R03: Operación de Pacientes por RECEPCIONISTA ---');
  const r01Res = await request('/patients', {
    headers: { Authorization: `Bearer ${sessionRecepcion.token}` }
  });
  assert(r01Res.status === 200, 'R01: Recepcionista lista pacientes con HTTP 200 OK');
  assert(r01Res.data[0].alergias === null, 'R01: Campos clínicos excluidos para recepcionista');

  const dniRecepcion = String(Math.floor(Math.random() * 89999999 + 10000000));
  const r02Res = await request('/patients', {
    method: 'POST',
    headers: { Authorization: `Bearer ${sessionRecepcion.token}` },
    body: JSON.stringify({
      nombres: 'Claudia',
      apellidos: 'Vásquez',
      tipoDocumento: 'DNI',
      numeroDocumento: dniRecepcion,
      fechaNacimiento: '1996-04-12',
      telefono: '+51 944556677',
      correo: `claudia.${dniRecepcion}@gmail.com`
    })
  });
  assert(r02Res.status === 201, 'R02: Recepcionista registra paciente con HTTP 201 Created');

  const r03Res = await request(`/patients/${r02Res.data.id}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${sessionRecepcion.token}` },
    body: JSON.stringify({
      telefono: '+51 977889900',
      ciudad: 'Trujillo'
    })
  });
  assert(r03Res.status === 200, 'R03: Recepcionista actualiza paciente con HTTP 200 OK');
  assert(r03Res.data.ciudad === 'Trujillo', 'R03: Campo demográfico actualizado');

  // --- Vinculación de cuenta de paciente sin equiparar paciente.id a usuario.id ---
  console.log('\n--- 10. Vinculación de cuenta paciente sin equiparar IDs ---');
  const linkRes = await request('/patients/2/link-user', {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${sessionAdmin.token}` },
    body: JSON.stringify({ usuarioId: '4' })
  });
  assert(linkRes.status === 200, 'Vinculación responde HTTP 200 OK');
  assert(linkRes.data.id === '2', `paciente.id se conserva como "2" (recibido ${linkRes.data.id})`);
  assert(linkRes.data.usuarioId === '4', `usuarioId vinculado es "4" (recibido ${linkRes.data.usuarioId})`);
  assert(linkRes.data.id !== linkRes.data.usuarioId, 'paciente.id (2) != usuario.id (4) estricto');

  // --- Negativos y Control RBAC ---
  console.log('\n--- 11. Negativos: Control RBAC y validación de fecha futura ---');
  // Odontólogo intentando acceder a /clinic/users
  const odontoClinicUsers = await request('/clinic/users', {
    headers: { Authorization: `Bearer ${sessionOdonto.token}` }
  });
  assert(odontoClinicUsers.status === 403, `ODONTOLOGO en /clinic/users rechazado con HTTP 403 Forbidden (recibido ${odontoClinicUsers.status})`);

  // Recepcionista intentando acceder a /clinic/users
  const recepcionClinicUsers = await request('/clinic/users', {
    headers: { Authorization: `Bearer ${sessionRecepcion.token}` }
  });
  assert(recepcionClinicUsers.status === 403, `RECEPCIONISTA en /clinic/users rechazado con HTTP 403 Forbidden (recibido ${recepcionClinicUsers.status})`);

  // Paciente intentando acceder a /clinic/users
  const pacienteClinicUsers = await request('/clinic/users', {
    headers: { Authorization: `Bearer ${sessionPaciente.token}` }
  });
  assert(pacienteClinicUsers.status === 403, `PACIENTE en /clinic/users rechazado con HTTP 403 Forbidden (recibido ${pacienteClinicUsers.status})`);

  // Sin token a /clinic/users
  const unauthClinicUsers = await request('/clinic/users');
  assert(unauthClinicUsers.status === 401, `Sin token en /clinic/users rechazado con HTTP 401 Unauthorized (recibido ${unauthClinicUsers.status})`);

  // Fecha futura (mañana)
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const futureDateStr = tomorrow.toISOString().split('T')[0];
  const futurePatientRes = await request('/patients', {
    method: 'POST',
    headers: { Authorization: `Bearer ${sessionAdmin.token}` },
    body: JSON.stringify({
      nombres: 'Futurista',
      apellidos: 'Inválido',
      tipoDocumento: 'DNI',
      numeroDocumento: '77665544',
      fechaNacimiento: futureDateStr
    })
  });
  assert(futurePatientRes.status === 400, `Fecha futura rechazada con HTTP 400 Bad Request (recibido ${futurePatientRes.status})`);

  console.log('\n======================================================');
  console.log(`--- RESUMEN F03-A: ${passed} pruebas pasadas, ${failed} fallos ---`);
  console.log('======================================================');

  if (failed > 0) {
    process.exit(1);
  }
}

run().catch(err => {
  console.error('Error fatal en ejecución de pruebas:', err);
  process.exit(1);
});
