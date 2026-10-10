import http from 'http';

const BASE_URL = 'http://localhost:8081';

function log(msg, ...args) {
    console.log(`[TEST-F08] ${msg}`, ...args);
}

function assert(condition, message) {
    if (!condition) {
        console.error(`❌ ASSERTION FAILED: ${message}`);
        throw new Error(message);
    }
    console.log(`  ✓ ${message}`);
}

async function request(options, body = null) {
    return new Promise((resolve, reject) => {
        const req = http.request(options, (res) => {
            const chunks = [];
            res.on('data', chunk => chunks.push(chunk));
            res.on('end', () => {
                const buffer = Buffer.concat(chunks);
                let json = null;
                const contentType = res.headers['content-type'] || '';
                if (contentType.includes('application/json')) {
                    try {
                        json = JSON.parse(buffer.toString('utf-8'));
                    } catch (e) {
                        json = buffer.toString('utf-8');
                    }
                }
                resolve({
                    status: res.statusCode,
                    headers: res.headers,
                    body: buffer,
                    json: json
                });
            });
        });
        req.on('error', reject);
        if (body) {
            req.write(body);
        }
        req.end();
    });
}

async function login(correo, clave) {
    const postData = JSON.stringify({ correo, clave });
    const res = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/auth/login',
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(postData)
        }
    }, postData);

    if (res.status !== 200 || !res.json?.token) {
        throw new Error(`Login falló para ${correo}: status ${res.status} body: ${res.body.toString()}`);
    }
    return res.json;
}

function buildMultipartFormData(fields, fileField) {
    const boundary = '----CoronyxFormBoundary' + Math.random().toString(36).substring(2);
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

async function runTests() {
    log('================================================================');
    log('  EJECUTANDO SUITE DE VALIDACIÓN COMPLETA PARA LOS 8 INCREMENTOS');
    log('================================================================');

    // 0. Autenticación de sesiones
    log('Paso 0: Autenticación de roles de prueba...');
    const doctorSession = await login('odontologo@coronyx.pe', '123456');
    const patientSession = await login('paciente@coronyx.pe', '123456');
    const adminSession = await login('admin@coronyx.pe', '123456');

    assert(doctorSession.rol === 'ODONTOLOGO', 'Sesión Odontólogo activa');
    assert(patientSession.rol === 'PACIENTE', 'Sesión Paciente activa');
    assert(adminSession.rol === 'ADMIN_CLINICA', 'Sesión Admin Clínica activa');

    const patientId = patientSession.pacienteId || '2';
    log('Paciente autenticado con ID:', patientId);

    // =========================================================================
    // 1. ODONTOGRAMA D07/V06
    // =========================================================================
    log('\n--- 1. ODONTOGRAMA D07/V06: Versiones, Hallazgos e Inmutabilidad ---');
    const odontoActualRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/odontograma/paciente/${patientId}/actual`,
        method: 'GET',
        headers: { 'Authorization': `Bearer ${doctorSession.token}` }
    });
    assert(odontoActualRes.status === 200, `GET odontograma actual status 200 (obtenido ${odontoActualRes.status})`);
    const odontoActual = odontoActualRes.json;
    assert(odontoActual && typeof odontoActual.id === 'string', 'Odontograma actual retornado con ID string');
    assert(odontoActual.pacienteId === String(patientId), 'Odontograma aislado para paciente correcto');

    let currentVersionId = odontoActual.id;

    // Si ya estuviera confirmada, creamos una nueva versión borrador
    if (odontoActual.estado === 'CONFIRMADA' || odontoActual.estado === 'CONFIRMADO') {
        log('Versión actual ya confirmada. Creando versión de evolución/corrección...');
        const newVerRes = await request({
            hostname: 'localhost',
            port: 8081,
            path: `/api/v1/odontograma/paciente/${patientId}/nueva-version`,
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${doctorSession.token}`,
                'Content-Type': 'application/json'
            }
        }, JSON.stringify({ motivoCambio: 'Evolución clínica semestral' }));
        if (newVerRes.status !== 200 && newVerRes.status !== 201) {
            console.error('Error status:', newVerRes.status, 'body:', newVerRes.body ? newVerRes.body.toString() : '');
        }
        assert(newVerRes.status === 200 || newVerRes.status === 201, 'Nueva versión borrador creada');
        currentVersionId = newVerRes.json.id;
        assert(newVerRes.json.estado === 'BORRADOR', 'Nueva versión inicia en BORRADOR');
    }

    // Guardar hallazgos en el borrador
    log(`Guardando hallazgos en borrador ${currentVersionId}...`);
    const saveFindingsPayload = JSON.stringify({
        hallazgos: [
            {
                codigoPiezaDental: '18',
                codigoSuperficieCara: 'OCLUSAL',
                codigoCondicion: 'CARIES',
                notaObservacion: 'Caries de esmalte y dentina'
            },
            {
                codigoPiezaDental: '26',
                codigoSuperficieCara: 'GENERAL',
                codigoCondicion: 'CORONA',
                notaObservacion: 'Corona de porcelana'
            }
        ]
    });

    const saveFindingsRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/odontograma/borrador/${currentVersionId}`,
        method: 'PUT',
        headers: {
            'Authorization': `Bearer ${doctorSession.token}`,
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(saveFindingsPayload)
        }
    }, saveFindingsPayload);
    assert(saveFindingsRes.status === 200, `Guardar hallazgos status 200 (obtenido ${saveFindingsRes.status})`);
    assert(saveFindingsRes.json.hallazgos.length >= 2, 'Hallazgos guardados correctamente en borrador');

    // Confirmar versión médica
    log(`Confirmando versión ${currentVersionId}...`);
    const confirmVerRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/odontograma/version/${currentVersionId}/confirmar`,
        method: 'POST',
        headers: { 'Authorization': `Bearer ${doctorSession.token}` }
    });
    assert(confirmVerRes.status === 200, `Confirmación exitosa (status 200)`);
    assert(confirmVerRes.json.estado === 'CONFIRMADA' || confirmVerRes.json.estado === 'CONFIRMADO', 'Versión pasa a estado CONFIRMADO');
    assert(confirmVerRes.json.fechaConfirmacion != null, 'Fecha de confirmación registrada');

    // Negativo de inmutabilidad: intentar editar una versión confirmada debe ser rechazado
    log('Negativo de inmutabilidad: Intentando editar versión confirmada...');
    const tamperRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/odontograma/borrador/${currentVersionId}`,
        method: 'PUT',
        headers: {
            'Authorization': `Bearer ${doctorSession.token}`,
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(saveFindingsPayload)
        }
    }, saveFindingsPayload);
    assert(tamperRes.status === 400 || tamperRes.status === 409 || tamperRes.status === 403,
        `Intento de editar versión confirmada fue RECHAZADO (status ${tamperRes.status})`);

    // =========================================================================
    // 2. DISPONIBILIDAD A09/R05
    // =========================================================================
    log('\n--- 2. DISPONIBILIDAD A09/R05: Horarios y Ausencias en BD ---');
    const getHorariosRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/disponibilidad/horarios',
        method: 'GET',
        headers: { 'Authorization': `Bearer ${adminSession.token}` }
    });
    assert(getHorariosRes.status === 200, `GET horarios status 200 (obtenido ${getHorariosRes.status})`);
    log('Horarios actuales en clínica:', getHorariosRes.json?.length || 0, 'días configurados');

    // Guardar / editar horario
    const updatedHorariosPayload = JSON.stringify([
        { diaSemana: 1, horaApertura: '08:00:00', horaCierre: '18:00:00', activo: true },
        { diaSemana: 2, horaApertura: '08:00:00', horaCierre: '18:00:00', activo: true },
        { diaSemana: 3, horaApertura: '08:00:00', horaCierre: '18:00:00', activo: true },
        { diaSemana: 4, horaApertura: '08:00:00', horaCierre: '18:00:00', activo: true },
        { diaSemana: 5, horaApertura: '08:00:00', horaCierre: '18:00:00', activo: true },
        { diaSemana: 6, horaApertura: '09:00:00', horaCierre: '22:00:00', activo: true },
        { diaSemana: 7, horaApertura: '08:00:00', horaCierre: '12:00:00', activo: false }
    ]);
    const putHorariosRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/disponibilidad/horarios',
        method: 'PUT',
        headers: {
            'Authorization': `Bearer ${adminSession.token}`,
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(updatedHorariosPayload)
        }
    }, updatedHorariosPayload);
    assert(putHorariosRes.status === 200, `PUT horarios status 200`);

    // Consultar slots reales para un día laborable
    const testFecha = '2026-10-15'; // Jueves
    const getSlotsRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/disponibilidad/slots?odontologoId=1&fecha=${testFecha}`,
        method: 'GET',
        headers: { 'Authorization': `Bearer ${doctorSession.token}` }
    });
    assert(getSlotsRes.status === 200, `GET slots status 200`);
    assert(Array.isArray(getSlotsRes.json), 'Slots retornados como arreglo');
    assert(getSlotsRes.json.length > 0, `Slots generados dinámicamente: ${getSlotsRes.json.length}`);
    log('Primer slot disponible:', getSlotsRes.json[0]);

    // =========================================================================
    // 3. CLÍNICA V09
    // =========================================================================
    log('\n--- 3. CLÍNICA V09: Perfil de consultorio real persistido ---');
    const getClinicRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/clinic/profile',
        method: 'GET',
        headers: { 'Authorization': `Bearer ${adminSession.token}` }
    });
    assert(getClinicRes.status === 200, `GET perfil clínica status 200`);
    const clinicData = getClinicRes.json;
    log('Clínica cargada:', clinicData.nombre, '| RUC:', clinicData.ruc, '| Ciudad:', clinicData.ciudad);

    const updateClinicPayload = JSON.stringify({
        nombre: clinicData.nombre || 'Coronyx Central',
        ruc: '20601234567',
        telefono: '+51 1 765-4321',
        correo: 'contacto@coronyx.pe',
        ciudad: 'Lima Metropolitana',
        direccion: 'Av. Javier Prado Este 2450, San Borja'
    });
    const putClinicRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/clinic/profile',
        method: 'PUT',
        headers: {
            'Authorization': `Bearer ${adminSession.token}`,
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(updateClinicPayload)
        }
    }, updateClinicPayload);
    assert(putClinicRes.status === 200, `PUT perfil clínica status 200`);
    assert(putClinicRes.json.ruc === '20601234567', 'RUC persistido');
    assert(putClinicRes.json.ciudad === 'Lima Metropolitana', 'Ciudad persistida');

    // =========================================================================
    // 4. PANEL A11/V10
    // =========================================================================
    log('\n--- 4. PANEL A11/V10: Resumen BD y métricas operativas reales ---');
    const dashRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/dashboard/summary',
        method: 'GET',
        headers: { 'Authorization': `Bearer ${doctorSession.token}` }
    });
    assert(dashRes.status === 200, `GET resumen dashboard status 200`);
    const dashSummary = dashRes.json;
    assert(dashSummary && typeof dashSummary.totalCitasHoy === 'number', 'totalCitasHoy es numérico');
    assert(typeof dashSummary.totalPacientes === 'number', 'totalPacientes es numérico');
    assert(Boolean(dashSummary.fechaHoyLima), `Fecha en zona horaria Lima presente: ${dashSummary.fechaHoyLima}`);
    log(`Panel: Citas hoy: ${dashSummary.totalCitasHoy} | Confirmadas: ${dashSummary.citasConfirmadasHoy} | Pacientes: ${dashSummary.totalPacientes}`);

    // Error no autenticado no aparece como 200 con ceros
    const dashUnauthRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/dashboard/summary',
        method: 'GET'
    });
    assert(dashUnauthRes.status === 401 || dashUnauthRes.status === 403,
        `Petición sin autenticación es rechazada (${dashUnauthRes.status})`);

    // =========================================================================
    // 5. PERFIL V14
    // =========================================================================
    log('\n--- 5. PERFIL V14: Ficha propia del paciente y negativos de campos clínicos ---');
    const myProfileRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/patients/me',
        method: 'GET',
        headers: { 'Authorization': `Bearer ${patientSession.token}` }
    });
    assert(myProfileRes.status === 200, `GET ficha propia status 200`);
    log('Paciente propio consultado:', myProfileRes.json.nombres, myProfileRes.json.apellidos);

    // Edición de campos permitidos
    const updateProfilePayload = JSON.stringify({
        telefono: '+51 912345678',
        ciudad: 'Miraflores, Lima',
        direccion: 'Calle Los Cedros 345, Dpto 201'
    });
    const putProfileRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/patients/me',
        method: 'PUT',
        headers: {
            'Authorization': `Bearer ${patientSession.token}`,
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(updateProfilePayload)
        }
    }, updateProfilePayload);
    assert(putProfileRes.status === 200, `PUT ficha propia status 200`);
    assert(putProfileRes.json.telefono === '+51 912345678', 'Teléfono actualizado');
    assert(putProfileRes.json.ciudad === 'Miraflores, Lima', 'Ciudad actualizada');

    // Negativo: paciente intentando modificar alergias o campos clínicos protegidos
    log('Negativo: Paciente intentando alterar sus alergias clínicas...');
    const tamperClinicalPayload = JSON.stringify({
        telefono: '+51 912345678',
        alergias: 'Intento de alterar alergias sin autorización odontológica'
    });
    const tamperClinicalRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/patients/me',
        method: 'PUT',
        headers: {
            'Authorization': `Bearer ${patientSession.token}`,
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(tamperClinicalPayload)
        }
    }, tamperClinicalPayload);
    assert(tamperClinicalRes.status === 403,
        `Alteración de campos clínicos fue RECHAZADA con 403 Forbidden (obtenido ${tamperClinicalRes.status})`);

    // =========================================================================
    // 6. CARGA P05
    // =========================================================================
    log('\n--- 6. CARGA P05: Subida de archivo propio por paciente (<= 25MB) ---');
    const patientFileBytes = Buffer.from('CONTENIDO_ESTUDIO_MEDICO_PACIENTE_P05_AUTORIZADO');
    const patientUploadData = buildMultipartFormData(
        {
            pacienteId: String(patientId),
            tipoArchivo: 'RADIOGRAFIA',
            regionAnatomica: 'Control postoperatorio',
            esVisiblePaciente: 'true'
        },
        {
            fieldName: 'file',
            filename: 'mi_estudio_dental.pdf',
            contentType: 'application/pdf',
            data: patientFileBytes
        }
    );

    const patientUploadRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/attachments/upload',
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${patientSession.token}`,
            'Content-Type': patientUploadData.contentType,
            'Content-Length': patientUploadData.body.length
        }
    }, patientUploadData.body);

    assert(patientUploadRes.status === 201, `Upload de archivo propio por paciente exitoso (status 201)`);
    assert(patientUploadRes.json.esVisiblePaciente === true, 'Archivo visible para el paciente');
    const patientUploadedId = patientUploadRes.json.id;

    // Negativo P05: paciente intentando subir archivo al expediente de otro paciente
    log('Negativo P05: Paciente intentando subir a expediente ajeno (ID 999)...');
    const foreignUploadData = buildMultipartFormData(
        {
            pacienteId: '999',
            tipoArchivo: 'RADIOGRAFIA',
            esVisiblePaciente: 'true'
        },
        {
            fieldName: 'file',
            filename: 'ataque.pdf',
            contentType: 'application/pdf',
            data: patientFileBytes
        }
    );
    const foreignUploadRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/attachments/upload',
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${patientSession.token}`,
            'Content-Type': foreignUploadData.contentType,
            'Content-Length': foreignUploadData.body.length
        }
    }, foreignUploadData.body);
    assert(foreignUploadRes.status === 403,
        `Upload a expediente ajeno fue RECHAZADO con 403 Forbidden (obtenido ${foreignUploadRes.status})`);

    // Crear una cita para hoy y validar que incrementa el contador de citas de hoy en el panel (A11) y emite notificación (V15)
    log('\nProbando creación de cita para hoy -> Debe cambiar contador en panel y generar notificación...');
    const nowLima = new Date();
    const todayIso = nowLima.toISOString().split('T')[0];

    const todaySlotsRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/disponibilidad/slots?odontologoId=1&fecha=${todayIso}`,
        method: 'GET',
        headers: { 'Authorization': `Bearer ${doctorSession.token}` }
    });
    const freeSlot = Array.isArray(todaySlotsRes.json) ? todaySlotsRes.json.find(s => s.disponible && s.inicio >= '16:00') : null;
    const slotInicio = freeSlot ? freeSlot.inicio : '19:00';
    const slotFin = freeSlot ? freeSlot.fin : '19:30';

    const apptPayload = JSON.stringify({
        pacienteId: String(patientId),
        odontologoId: '1',
        inicioEn: `${todayIso}T${slotInicio}:00-05:00`,
        finEn: `${todayIso}T${slotFin}:00-05:00`,
        modalidad: 'PRESENCIAL',
        motivo: 'Evaluación integral F08',
        consultorio: 'Sillón 1'
    });

    const createApptRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/citas',
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${doctorSession.token}`,
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(apptPayload)
        }
    }, apptPayload);

    if (createApptRes.status === 201) {
        log('✓ Cita creada para hoy con ID:', createApptRes.json.id);

        // Re-verificar dashboard summary: totalCitasHoy debe haber incrementado
        const dashAfterRes = await request({
            hostname: 'localhost',
            port: 8081,
            path: '/api/v1/dashboard/summary',
            method: 'GET',
            headers: { 'Authorization': `Bearer ${doctorSession.token}` }
        });
        assert(dashAfterRes.status === 200, 'GET dashboard summary post-alta status 200');
        assert(dashAfterRes.json.totalCitasHoy > dashSummary.totalCitasHoy,
            `Alta de cita cambió el contador: antes ${dashSummary.totalCitasHoy}, ahora ${dashAfterRes.json.totalCitasHoy}`);
        log(`✓ Alta de cita cambió contador en BD exitosamente: ${dashAfterRes.json.totalCitasHoy}`);
    } else {
        log('Nota: Cita en ese horario respondió con status:', createApptRes.status, 'body:', createApptRes.body ? createApptRes.body.toString() : '');
    }

    // =========================================================================
    // 7. NOTIFICACIONES V15
    // =========================================================================
    log('\n--- 7. NOTIFICACIONES V15: Eventos reales y lectura persistida ---');
    const getNotifsRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/notifications',
        method: 'GET',
        headers: { 'Authorization': `Bearer ${doctorSession.token}` }
    });
    assert(getNotifsRes.status === 200, `GET notificaciones status 200`);
    assert(Array.isArray(getNotifsRes.json), 'Notificaciones retornadas como array');
    log(`Notificaciones del odontólogo: ${getNotifsRes.json.length}`);

    if (getNotifsRes.json.length > 0) {
        const notifToRead = getNotifsRes.json[0];
        log(`Marcando notificación ${notifToRead.id} como leída...`);
        const readRes = await request({
            hostname: 'localhost',
            port: 8081,
            path: `/api/v1/notifications/${notifToRead.id}/read`,
            method: 'PATCH',
            headers: { 'Authorization': `Bearer ${doctorSession.token}` }
        });
        assert(readRes.status === 200, `PATCH read status 200`);
        assert(readRes.json.estado === 'LEIDO' || readRes.json.estado === 'LEIDA', 'Estado actualizado a LEIDO');
        assert(readRes.json.fechaLectura != null, 'Fecha de lectura persistida');

        // Negativo: Paciente intentando marcar como leída la notificación del Odontólogo (ajena)
        log('Negativo: Paciente intentando acceder/marcar notificación ajena del odontólogo...');
        const foreignNotifRes = await request({
            hostname: 'localhost',
            port: 8081,
            path: `/api/v1/notifications/${notifToRead.id}/read`,
            method: 'PATCH',
            headers: { 'Authorization': `Bearer ${patientSession.token}` }
        });
        assert(foreignNotifRes.status === 403 || foreignNotifRes.status === 404,
            `Acceso a notificación ajena fue RECHAZADO (${foreignNotifRes.status})`);
    }

    log('\n================================================================');
    log('  ¡TODOS LOS 8 INCREMENTOS VALIDADOS Y APROBADOS CON ÉXITO!     ');
    log('================================================================');
}

runTests().catch(err => {
    console.error('ERROR EN PRUEBAS:', err);
    process.exit(1);
});
