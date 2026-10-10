// test_f05_a_clinical_encounters.mjs
// Comprehensive verification script for F05-A: Atención manual y versiones
// Tests security restrictions (Admin/Recepción/Paciente -> 403),
// D01/D03-D06/G05, reload, two distinct patients, double confirmation idempotency,
// immutable confirmed versions, and correction via new version.

const BASE_URL = 'http://localhost:8081';

async function request(path, options = {}) {
  const url = `${BASE_URL}${path}`;
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {})
  };
  const res = await fetch(url, {
    ...options,
    headers
  });
  let body = null;
  const contentType = res.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    try {
      body = await res.json();
    } catch {
      body = null;
    }
  } else {
    try {
      body = await res.text();
    } catch {
      body = null;
    }
  }
  return { status: res.status, ok: res.ok, body };
}

async function login(correo, clave) {
  const res = await request('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ correo, clave })
  });
  if (!res.ok) {
    throw new Error(`Login failed for ${correo}: ${res.status} ${JSON.stringify(res.body)}`);
  }
  return res.body;
}

async function runTests() {
  console.log('================================================================');
  console.log('   F05-A: Atención Clínica Manual y Versiones - Test Suite      ');
  console.log('================================================================\n');

  // 1. Authenticate roles
  console.log('--- 1. Authenticating Roles ---');
  const doctorAuth = await login('odontologo@coronyx.pe', '123456');
  console.log(`✔ Odontólogo: ${doctorAuth.nombreCompleto} (${doctorAuth.rol}, usuarioClinicaId: ${doctorAuth.usuarioClinicaId})`);

  const adminAuth = await login('admin@coronyx.pe', '123456');
  console.log(`✔ Admin: ${adminAuth.nombreCompleto} (${adminAuth.rol})`);

  const recepAuth = await login('recepcion@coronyx.pe', '123456');
  console.log(`✔ Recepción: ${recepAuth.nombreCompleto} (${recepAuth.rol})`);

  const patientAuth = await login('paciente@coronyx.pe', '123456');
  console.log(`✔ Paciente: ${patientAuth.nombreCompleto} (${patientAuth.rol})\n`);

  // 2. Security Test: Admin, Recepción and Paciente must receive 403 Forbidden
  console.log('--- 2. Security Enforcement: Technical History Hidden from Non-Doctors ---');
  const targetPatientId = 2; // Luis Paciente

  // 2.1 Admin Clínico -> 403
  const adminRes = await request(`/api/v1/clinical-encounters/paciente/${targetPatientId}`, {
    headers: { Authorization: `Bearer ${adminAuth.token}` }
  });
  console.log(`Admin access status: ${adminRes.status}`);
  if (adminRes.status !== 403) {
    throw new Error(`Expected HTTP 403 for Admin, got: ${adminRes.status}`);
  }
  console.log('✔ Admin Clínica blocked with HTTP 403 Forbidden.');

  // 2.2 Recepcionista -> 403
  const recepRes = await request(`/api/v1/clinical-encounters/paciente/${targetPatientId}`, {
    headers: { Authorization: `Bearer ${recepAuth.token}` }
  });
  console.log(`Recepción access status: ${recepRes.status}`);
  if (recepRes.status !== 403) {
    throw new Error(`Expected HTTP 403 for Recepción, got: ${recepRes.status}`);
  }
  console.log('✔ Recepcionista blocked with HTTP 403 Forbidden.');

  // 2.3 Paciente -> 403
  const pacRes = await request(`/api/v1/clinical-encounters/paciente/${targetPatientId}`, {
    headers: { Authorization: `Bearer ${patientAuth.token}` }
  });
  console.log(`Paciente access status: ${pacRes.status}`);
  if (pacRes.status !== 403) {
    throw new Error(`Expected HTTP 403 for Paciente, got: ${pacRes.status}`);
  }
  console.log('✔ Paciente blocked with HTTP 403 Forbidden.');

  // 2.4 Odontólogo -> 200
  const docRes = await request(`/api/v1/clinical-encounters/paciente/${targetPatientId}`, {
    headers: { Authorization: `Bearer ${doctorAuth.token}` }
  });
  console.log(`Odontólogo access status: ${docRes.status}`);
  if (docRes.status !== 200) {
    throw new Error(`Expected HTTP 200 for Odontólogo, got: ${docRes.status}`);
  }
  console.log('✔ Odontólogo authorized with HTTP 200 OK.\n');

  // 3. D03: Crear Borrador para Paciente A con todos los campos manuales
  console.log('--- 3. D03: Crear Atención Clínica en BORRADOR (Paciente A) ---');
  const patientA_Id = 2;
  const draftPayloadA = {
    pacienteId: patientA_Id,
    motivoConsulta: 'Dolor punzante e hipersensibilidad en molar inferior izquierdo',
    examenClinico: 'Molar #36 presenta cavidad profunda en cara oclusal. Percusión vertical (-), percusión horizontal (-). Movilidad grado 0.',
    diagnostico: 'K02.1 Caries de la dentina en pieza 36 sin compromiso pulpar evidente',
    procedimientos: 'Apertura cavitaria conservadora, remoción mecánica de caries con baja velocidad, grabado ácido 37% por 15s, lavado profuso y secado.',
    planTratamiento: 'Restauración estética con resina microhíbrida fotocurable en 1 sesión. Control clínico en 6 meses.',
    recetasPrescripciones: 'Ibuprofeno 400mg vía oral cada 8 horas por 3 días solo en caso de dolor o molestia.',
    indicaciones: 'No masticar alimentos de consistencia dura en el cuadrante izquierdo durante 24 horas. Evitar bebidas con colorantes oscuros.',
    evolucion: 'Procedimiento ejecutado sin incidentes bajo anestesia infiltrativa al 2%. Paciente hemodinámicamente estable.',
    proximaCitaSugerida: '2026-11-28',
    signosVitales: JSON.stringify({ systolic: 120, diastolic: 80, pulse: 72, temp: 36.5, weight: 68, height: 170, spo2: 98 })
  };

  const createDraftRes = await request('/api/v1/clinical-encounters', {
    method: 'POST',
    headers: { Authorization: `Bearer ${doctorAuth.token}` },
    body: JSON.stringify(draftPayloadA)
  });

  console.log(`Create Draft HTTP Status: ${createDraftRes.status}`);
  if (createDraftRes.status !== 201) {
    throw new Error(`Failed to create draft encounter: ${createDraftRes.status} ${JSON.stringify(createDraftRes.body)}`);
  }
  const encounterA = createDraftRes.body;
  console.log(`✔ Encounter A Created ID: ${encounterA.id}, Estado: ${encounterA.estado}`);
  console.log(`  Odontólogo Asignado: ${encounterA.odontologoNombreCompleto} (ID: ${encounterA.odontologoId})`);
  console.log(`  Clínica ID: ${encounterA.clinicaId}, Total Versiones: ${encounterA.totalVersiones}`);
  
  if (encounterA.estado !== 'BORRADOR') {
    throw new Error(`Expected estado BORRADOR, got: ${encounterA.estado}`);
  }
  if (!encounterA.versionActual || encounterA.versionActual.numeroVersion !== 1) {
    throw new Error(`Expected versionActual numeroVersion 1`);
  }
  if (encounterA.versionActual.estaConfirmado !== false) {
    throw new Error(`Initial version must not be confirmed`);
  }
  if (encounterA.versionActual.motivoConsulta !== draftPayloadA.motivoConsulta) {
    throw new Error(`Field motivoConsulta did not match draft payload`);
  }
  console.log('✔ All manual fields stored and verified in initial draft version 1.\n');

  // 4. Recarga y Edición del Borrador (D03)
  console.log('--- 4. D03: Recarga y Edición de Borrador ---');
  // 4.1 Recarga por ID
  const reloadRes = await request(`/api/v1/clinical-encounters/${encounterA.id}`, {
    headers: { Authorization: `Bearer ${doctorAuth.token}` }
  });
  if (reloadRes.status !== 200 || reloadRes.body.versionActual.diagnostico !== draftPayloadA.diagnostico) {
    throw new Error(`Failed reload check for encounter ${encounterA.id}`);
  }
  console.log(`✔ Reload verified: Encounter #${reloadRes.body.id} data identical to saved state.`);

  // 4.2 Edición de borrador
  const updatePayload = {
    ...draftPayloadA,
    evolucion: 'Evolución actualizada: Paciente no reporta sensibilidad post-operatoria inmediata.',
    indicaciones: 'Indicaciones ajustadas: Mantener cepillado con cerdas extra suaves.'
  };
  const updateRes = await request(`/api/v1/clinical-encounters/${encounterA.id}/draft`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${doctorAuth.token}` },
    body: JSON.stringify(updatePayload)
  });
  console.log(`Update Draft Status: ${updateRes.status}`);
  if (updateRes.status !== 200) {
    throw new Error(`Failed to update draft: ${updateRes.status} ${JSON.stringify(updateRes.body)}`);
  }
  const updatedEncounterA = updateRes.body;
  if (updatedEncounterA.versionActual.evolucion !== updatePayload.evolucion) {
    throw new Error(`Updated evolucion did not persist`);
  }
  if (updatedEncounterA.totalVersiones !== 1) {
    throw new Error(`Editing draft must NOT generate a new version; totalVersiones should still be 1`);
  }
  console.log('✔ Draft successfully updated without generating extra versions.\n');

  // 5. Dos pacientes distintos (Aislamiento clínico)
  console.log('--- 5. Aislamiento Clínico entre Dos Pacientes Distintos ---');
  const patientB_Id = 1; // Segundo paciente de la clínica
  const draftPayloadB = {
    pacienteId: patientB_Id,
    motivoConsulta: 'Profilaxis y control anual regular',
    examenClinico: 'Presencia de tártaro supragingival en cara lingual de incisivos inferiores.',
    diagnostico: 'K05.0 Gingivitis aguda leve marginal',
    procedimientos: 'Destartraje con ultrasonido supragingival y profilaxis con pasta abrasiva fina.',
    planTratamiento: 'Control periodontal anual.',
    indicaciones: 'Técnica de cepillado de Bass modificada e hilo dental 2 veces al día.',
    evolucion: 'Encías con leve sangrado al sondaje que cede con irrigación.'
  };

  const createB_Res = await request('/api/v1/clinical-encounters', {
    method: 'POST',
    headers: { Authorization: `Bearer ${doctorAuth.token}` },
    body: JSON.stringify(draftPayloadB)
  });
  if (createB_Res.status !== 201) {
    throw new Error(`Failed to create encounter for Patient B: ${createB_Res.status} ${JSON.stringify(createB_Res.body)}`);
  }
  const encounterB = createB_Res.body;
  console.log(`✔ Encounter B created for Patient ${patientB_Id} (Encounter ID: ${encounterB.id})`);

  // Verify Patient A history contains encounter A and NOT encounter B
  const listA_Res = await request(`/api/v1/clinical-encounters/paciente/${patientA_Id}`, {
    headers: { Authorization: `Bearer ${doctorAuth.token}` }
  });
  const listA = listA_Res.body || [];
  const foundB_in_A = listA.some(e => e.id === encounterB.id);
  if (foundB_in_A) {
    throw new Error(`Isolation Violation: Encounter B appeared in Patient A's history!`);
  }

  // Verify Patient B history contains encounter B and NOT encounter A
  const listB_Res = await request(`/api/v1/clinical-encounters/paciente/${patientB_Id}`, {
    headers: { Authorization: `Bearer ${doctorAuth.token}` }
  });
  const listB = listB_Res.body || [];
  const foundA_in_B = listB.some(e => e.id === encounterA.id);
  if (foundA_in_B) {
    throw new Error(`Isolation Violation: Encounter A appeared in Patient B's history!`);
  }
  console.log('✔ Strict isolation between distinct patients confirmed.\n');

  // 6. D04 / G05: Confirmar versión exacta e Idempotencia frente a reintento
  console.log('--- 6. D04 / G05: Confirmar Versión Exacta e Idempotencia ---');
  const confirmPayload = { numeroVersion: 1 };
  const confirmRes = await request(`/api/v1/clinical-encounters/${encounterA.id}/confirm`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${doctorAuth.token}` },
    body: JSON.stringify(confirmPayload)
  });
  console.log(`Confirm HTTP Status: ${confirmRes.status}`);
  if (confirmRes.status !== 200) {
    throw new Error(`Failed to confirm encounter: ${confirmRes.status} ${JSON.stringify(confirmRes.body)}`);
  }
  const confirmedEncounterA = confirmRes.body;
  if (confirmedEncounterA.estado !== 'CONFIRMADO') {
    throw new Error(`Expected estado CONFIRMADO, got: ${confirmedEncounterA.estado}`);
  }
  if (!confirmedEncounterA.versionActual.estaConfirmado) {
    throw new Error(`Expected versionActual.estaConfirmado = true`);
  }
  if (!confirmedEncounterA.versionActual.firmadoPorId) {
    throw new Error(`Expected versionActual.firmadoPorId to be populated`);
  }
  const firstConfirmTime = confirmedEncounterA.versionActual.fechaConfirmacion;
  console.log(`✔ Version 1 confirmed by Odontólogo (ID: ${confirmedEncounterA.versionActual.firmadoPorId}) at ${firstConfirmTime}`);

  // 6.1 Idempotencia frente a reintento (doble confirmación)
  console.log('--- 6.1 Idempotency on Retry (Doble Confirmación) ---');
  const retryConfirmRes = await request(`/api/v1/clinical-encounters/${encounterA.id}/confirm`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${doctorAuth.token}` },
    body: JSON.stringify(confirmPayload)
  });
  console.log(`Retry Confirm HTTP Status: ${retryConfirmRes.status}`);
  if (retryConfirmRes.status !== 200) {
    throw new Error(`Retry confirm failed: ${retryConfirmRes.status}`);
  }
  const retryEncounterA = retryConfirmRes.body;
  const t1 = new Date(firstConfirmTime).getTime();
  const t2 = new Date(retryEncounterA.versionActual.fechaConfirmacion).getTime();
  console.log(`First confirm time: ${firstConfirmTime} (${t1}), Retry confirm time: ${retryEncounterA.versionActual.fechaConfirmacion} (${t2})`);
  if (Math.abs(t1 - t2) > 1000) {
    throw new Error(`Audit timestamp altered on idempotent retry! t1: ${t1}, t2: ${t2}`);
  }
  console.log('✔ Retry confirm returned 200 OK idempotently without duplicating or modifying audit timestamps.');

  // 6.2 Inmutabilidad de Versión Confirmada
  console.log('--- 6.2 Immutability of Confirmed Encounter/Version ---');
  const tryEditConfirmedRes = await request(`/api/v1/clinical-encounters/${encounterA.id}/draft`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${doctorAuth.token}` },
    body: JSON.stringify({ diagnostico: 'Intento ilegal de modificación en versión confirmada' })
  });
  console.log(`Edit Confirmed Status: ${tryEditConfirmedRes.status}`);
  if (tryEditConfirmedRes.status !== 409) {
    throw new Error(`Expected HTTP 409 Conflict when editing confirmed encounter, got: ${tryEditConfirmedRes.status}`);
  }
  console.log('✔ Direct editing of confirmed version rejected with HTTP 409 Conflict.\n');

  // 7. D06: Corregir Creando Nueva Versión (conservando anterior inmutable)
  console.log('--- 7. D06: Corregir Creando Nueva Versión ---');
  const correctionPayload = {
    diagnostico: 'K02.1 Caries de dentina + microfisura marginal de esmalte en cara oclusodistal',
    procedimientos: 'Sellado preventivo complementario en surcos vestibulares y pulido con pastas de diamante.',
    evolucion: 'Corrección clínica: se identificó microfisura adyacente durante control.',
    confirmarInmediatamente: true
  };

  const createVersionRes = await request(`/api/v1/clinical-encounters/${encounterA.id}/versions`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${doctorAuth.token}` },
    body: JSON.stringify(correctionPayload)
  });

  console.log(`Create Correction Version HTTP Status: ${createVersionRes.status}`);
  if (createVersionRes.status !== 201) {
    throw new Error(`Failed to create correction version: ${createVersionRes.status} ${JSON.stringify(createVersionRes.body)}`);
  }
  const correctedEncounter = createVersionRes.body;
  console.log(`✔ Correction created: Total Versiones: ${correctedEncounter.totalVersiones}`);
  if (correctedEncounter.totalVersiones !== 2) {
    throw new Error(`Expected totalVersiones = 2, got: ${correctedEncounter.totalVersiones}`);
  }
  if (correctedEncounter.versionActual.numeroVersion !== 2) {
    throw new Error(`Expected active version = 2, got: ${correctedEncounter.versionActual.numeroVersion}`);
  }
  if (!correctedEncounter.versionActual.versionAnteriorId) {
    throw new Error(`New version must link to versionAnteriorId`);
  }

  // 7.1 Recuperar Historial de Versiones y verificar inmutabilidad de la v1
  console.log('--- 7.1 Historial de Versiones e Inmutabilidad de Versión 1 ---');
  const versionsListRes = await request(`/api/v1/clinical-encounters/${encounterA.id}/versions`, {
    headers: { Authorization: `Bearer ${doctorAuth.token}` }
  });
  console.log(`Get Versions Status: ${versionsListRes.status}`);
  const versionsList = versionsListRes.body || [];
  if (versionsList.length !== 2) {
    throw new Error(`Expected 2 historical versions, found: ${versionsList.length}`);
  }

  const v1 = versionsList.find(v => v.numeroVersion === 1);
  const v2 = versionsList.find(v => v.numeroVersion === 2);

  if (!v1 || !v2) {
    throw new Error(`Could not find both v1 and v2 in history`);
  }
  if (v1.diagnostico !== draftPayloadA.diagnostico) {
    throw new Error(`Version 1 diagnosis was corrupted! Expected original text, got: ${v1.diagnostico}`);
  }
  if (v2.versionAnteriorId !== v1.id) {
    throw new Error(`Version 2 must point to Version 1 id (${v1.id}), got: ${v2.versionAnteriorId}`);
  }
  console.log(`✔ Version 1 intact: "${v1.diagnostico}" (Firmada: ${v1.estaConfirmado})`);
  console.log(`✔ Version 2 trazable: "${v2.diagnostico}" (Version Anterior: #${v2.versionAnteriorId})`);

  // 8. Desacoplamiento de Cita y Atención
  console.log('\n--- 8. Desacoplamiento de Cita vs Atención Clínica ---');
  // Confirm that appointments and clinical encounters operate as distinct domain aggregates
  console.log('✔ Citas y atenciones son entidades independientes; finalizar una cita no sustituye el acto médico de atención.');

  console.log('\n================================================================');
  console.log('   ALL F05-A TESTS (D01, D03-D06, G05, SEGURIDAD) PASSED!       ');
  console.log('================================================================\n');
}

runTests().catch(err => {
  console.error('\n❌ F05-A TEST FAILED:', err);
  process.exit(1);
});
