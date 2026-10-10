/**
 * Test de Integración Automatizado F06-A: Archivos Duraderos y Publicación
 * 
 * Valida:
 * 1. Autenticación de roles (ODONTOLOGO, ADMIN_CLINICA, PACIENTE)
 * 2. Carga de archivo privado (D08) con almacenamiento durable fuera de directorios efímeros
 * 3. Descarga privada por proxy autenticado con verificación byte a byte
 * 4. Listado clínico completo vs listado proyectado de paciente
 * 5. Bloqueo inmediato de descarga y listado para paciente mientras es privado (P04)
 * 6. Publicación (es_visible_paciente = true) y acceso permitido para el paciente
 * 7. Revocación inmediata (es_visible_paciente = false) y bloqueo en tiempo real de nuevas descargas proxy
 * 8. Negativos de aislamiento: paciente ajeno (403), clínica ajena (404), paciente intentando subir/cambiar visibilidad (403)
 * 9. Validación de tamaño real (> 0 bytes) y tipo de archivo
 * 10. Compensación atómica: fallo en metadatos limpia el binario sin dejar huérfanos
 */

import http from 'http';
import fs from 'fs';
import path from 'path';

const BASE_URL = 'http://localhost:8081';

function log(msg, ...args) {
    console.log(`[TEST-F06-A] ${msg}`, ...args);
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

// Helper para construir peticiones multipart/form-data nativas en Node.js
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
        const { fieldName, filename, contentType, data } = fileField;
        buffers.push(Buffer.from(
            `--${boundary}${crlf}` +
            `Content-Disposition: form-data; name="${fieldName}"; filename="${filename}"${crlf}` +
            `Content-Type: ${contentType}${crlf}${crlf}`
        ));
        buffers.push(data);
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
    log('=== INICIO DE PRUEBAS F06-A: ARCHIVOS DURADEROS Y PUBLICACIÓN ===');

    // 1. Iniciar sesión con diferentes roles
    log('1. Autenticando roles de prueba...');
    const doctorSession = await login('odontologo@coronyx.pe', '123456');
    log('Odontólogo autenticado:', doctorSession.nombres, '| Rol:', doctorSession.rol, '| Clínica:', doctorSession.clinicaId);
    assert(doctorSession.rol === 'ODONTOLOGO', 'Sesión Odontólogo verificada');

    const patientSession = await login('paciente@coronyx.pe', '123456');
    log('Paciente autenticado:', patientSession.nombres, '| PacienteId:', patientSession.pacienteId);
    assert(patientSession.rol === 'PACIENTE', 'Sesión Paciente verificada');

    const adminSession = await login('admin@coronyx.pe', '123456');
    assert(adminSession.rol === 'ADMIN_CLINICA', 'Sesión Admin verificada');

    const patientId = patientSession.pacienteId; // "2"
    assert(Boolean(patientId), `ID de paciente obtenido: ${patientId}`);

    // Crear un archivo binario sintético de prueba (PNG simulado con bytes específicos)
    const originalFileBytes = Buffer.from([
        0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG magic header
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR chunk
        0x00, 0x00, 0x00, 0x10, 0x00, 0x00, 0x00, 0x10,
        0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0xF3, 0xFF,
        0x5F, 0x43, 0x4F, 0x52, 0x4F, 0x4E, 0x59, 0x58, // Custom Coronyx marker
        0x5F, 0x54, 0x45, 0x53, 0x54, 0x5F, 0x42, 0x59,
        0x54, 0x45, 0x53, 0x5F, 0x44, 0x30, 0x38, 0x21
    ]);

    // 2. [D08] Odontólogo sube archivo binario privado (esVisiblePaciente = false)
    log('2. [D08] Odontólogo sube radiografía privada para Paciente ID ' + patientId);
    const multipartData = buildMultipartFormData(
        {
            pacienteId: patientId,
            tipoArchivo: 'RADIOGRAFIA',
            regionAnatomica: 'Maxilar inferior izquierdo (Molar 38)',
            esVisiblePaciente: 'false'
        },
        {
            fieldName: 'file',
            filename: 'radiografia_panoramica_molar_38.png',
            contentType: 'image/png',
            data: originalFileBytes
        }
    );

    const uploadRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/attachments/upload',
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${doctorSession.token}`,
            'Content-Type': multipartData.contentType,
            'Content-Length': multipartData.body.length
        }
    }, multipartData.body);

    assert(uploadRes.status === 201, `Upload status esperado 201, obtenido: ${uploadRes.status}`);
    const attachment = uploadRes.json;
    assert(attachment && typeof attachment.id === 'string', `Attachment ID devuelto como String decimal: ${attachment.id}`);
    assert(attachment.clinicaId === '1', `Clínica ID correcta: ${attachment.clinicaId}`);
    assert(attachment.pacienteId === String(patientId), `Paciente ID correcto: ${attachment.pacienteId}`);
    assert(attachment.tipoArchivo === 'RADIOGRAFIA', `Tipo de archivo correcto: ${attachment.tipoArchivo}`);
    assert(attachment.esVisiblePaciente === false, 'El archivo fue creado como PRIVADO (esVisiblePaciente = false)');
    assert(attachment.pesoBytes === originalFileBytes.length, `Peso en bytes coincide: ${attachment.pesoBytes}`);
    assert(attachment.claveAlmacenamiento.startsWith(`clinica_1/paciente_${patientId}/`), `Clave almacenamiento estructurada: ${attachment.claveAlmacenamiento}`);
    log('Archivo creado exitosamente con ID:', attachment.id, '| Clave:', attachment.claveAlmacenamiento);

    const attachmentId = attachment.id;

    // 3. [D08] Odontólogo descarga el binario a través del proxy autenticado
    log('3. [D08] Odontólogo descarga el archivo privado mediante proxy autenticado...');
    const doctorDownloadRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/attachments/${attachmentId}/download`,
        method: 'GET',
        headers: {
            'Authorization': `Bearer ${doctorSession.token}`
        }
    });

    assert(doctorDownloadRes.status === 200, `Descarga exitosa para Odontólogo (200 OK)`);
    assert(doctorDownloadRes.headers['content-type'] === 'image/png', `Content-Type es image/png: ${doctorDownloadRes.headers['content-type']}`);
    assert(doctorDownloadRes.body.equals(originalFileBytes), 'Los bytes descargados coinciden exactamente byte a byte con el archivo subido');

    // 4. [D08] Odontólogo lista los archivos del paciente (debe aparecer el archivo privado)
    log('4. [D08] Odontólogo lista los archivos del paciente...');
    const doctorListRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/attachments/patient/${patientId}`,
        method: 'GET',
        headers: {
            'Authorization': `Bearer ${doctorSession.token}`
        }
    });

    assert(doctorListRes.status === 200, 'Odontólogo puede listar archivos del paciente (200 OK)');
    const doctorList = doctorListRes.json;
    const foundInDoctorList = doctorList.find(a => a.id === attachmentId);
    assert(foundInDoctorList !== undefined, 'El archivo privado aparece en la lista del personal clínico');
    assert(foundInDoctorList.esVisiblePaciente === false, 'Se confirma que esVisiblePaciente es false');

    // 5. [P04] Paciente consulta su lista de archivos: el archivo privado NO DEBE APARECER
    log('5. [P04] Paciente consulta sus archivos (mientras el archivo es privado)...');
    const patientListRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/attachments/me`,
        method: 'GET',
        headers: {
            'Authorization': `Bearer ${patientSession.token}`
        }
    });

    assert(patientListRes.status === 200, 'Paciente puede consultar sus archivos propios (200 OK)');
    const patientList = patientListRes.json;
    const foundInPatientList = patientList.find(a => a.id === attachmentId);
    assert(foundInPatientList === undefined, 'CONTROL P04: El archivo privado NO aparece en el listado del paciente');

    // 6. [P04] Paciente intenta descargar directamente el archivo privado: DEBE RECIBIR 403 FORBIDDEN
    log('6. [P04] Paciente intenta descargar directamente el archivo privado por ID...');
    const patientDownloadForbidden = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/attachments/${attachmentId}/download`,
        method: 'GET',
        headers: {
            'Authorization': `Bearer ${patientSession.token}`
        }
    });

    assert(patientDownloadForbidden.status === 403, `CONTROL P04: Intento de descarga de archivo privado denegado con 403 (obtenido: ${patientDownloadForbidden.status})`);
    log('Respuesta 403 recibida correctamente:', patientDownloadForbidden.json?.message);

    // 7. [D08] Odontólogo comparte el archivo con el paciente (publicar / esVisiblePaciente = true)
    log('7. [D08] Odontólogo publica el archivo para visibilidad del paciente...');
    const publishData = JSON.stringify({ esVisiblePaciente: true });
    const publishRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/attachments/${attachmentId}/visibility`,
        method: 'PATCH',
        headers: {
            'Authorization': `Bearer ${doctorSession.token}`,
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(publishData)
        }
    }, publishData);

    assert(publishRes.status === 200, `Publicación exitosa (200 OK)`);
    assert(publishRes.json.esVisiblePaciente === true, 'esVisiblePaciente ahora es TRUE');

    // 8. [P04] Paciente consulta de nuevo su lista: AHORA SÍ APARECE
    log('8. [P04] Paciente consulta sus archivos después de la publicación...');
    const patientListAfterRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/attachments/me`,
        method: 'GET',
        headers: {
            'Authorization': `Bearer ${patientSession.token}`
        }
    });

    assert(patientListAfterRes.status === 200, 'Consulta exitosa');
    const foundInPatientListAfter = patientListAfterRes.json.find(a => a.id === attachmentId);
    assert(foundInPatientListAfter !== undefined, 'CONTROL P04: El archivo publicado ahora SÍ aparece para el paciente');
    assert(foundInPatientListAfter.esVisiblePaciente === true, 'esVisiblePaciente es TRUE para el paciente');

    // 9. [P04] Paciente descarga el archivo publicado a través del proxy autenticado
    log('9. [P04] Paciente descarga el archivo publicado a través del proxy...');
    const patientDownloadSuccess = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/attachments/${attachmentId}/download`,
        method: 'GET',
        headers: {
            'Authorization': `Bearer ${patientSession.token}`
        }
    });

    assert(patientDownloadSuccess.status === 200, 'CONTROL P04: Paciente descarga con éxito el archivo publicado (200 OK)');
    assert(patientDownloadSuccess.body.equals(originalFileBytes), 'Bytes descargados por el paciente coinciden al 100%');

    // 10. [D08] Odontólogo revoca el archivo (esVisiblePaciente = false)
    log('10. [D08] Odontólogo revoca el acceso del archivo al paciente...');
    const revokeRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/attachments/${attachmentId}/revocar`,
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${doctorSession.token}`
        }
    });

    assert(revokeRes.status === 200, 'Revocación exitosa (200 OK)');
    assert(revokeRes.json.esVisiblePaciente === false, 'esVisiblePaciente ahora es FALSE');

    // 11. [P04] Paciente intenta descargar INMEDIATAMENTE: nueva petición es revocada en tiempo real
    log('11. [P04] Paciente intenta descargar inmediatamente tras revocación...');
    const patientDownloadRevoked = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/attachments/${attachmentId}/download`,
        method: 'GET',
        headers: {
            'Authorization': `Bearer ${patientSession.token}`
        }
    });

    assert(patientDownloadRevoked.status === 403, 'CONTROL PROXY REVOCACIÓN: La descarga subsiguiente es bloqueada instantáneamente con 403 Forbidden');

    // 12. Negativo: Paciente intenta cargar un archivo (Rol no autorizado)
    log('12. [NEGATIVO] Paciente intenta realizar carga de archivo...');
    const patientUploadAttempt = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/attachments/upload',
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${patientSession.token}`,
            'Content-Type': multipartData.contentType,
            'Content-Length': multipartData.body.length
        }
    }, multipartData.body);

    assert(patientUploadAttempt.status === 403, `Paciente bloqueado para carga con 403 Forbidden (obtenido: ${patientUploadAttempt.status})`);

    // 13. Negativo: Paciente intenta modificar visibilidad (PATCH /visibility)
    log('13. [NEGATIVO] Paciente intenta modificar visibilidad...');
    const patientPatchAttempt = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/attachments/${attachmentId}/visibility`,
        method: 'PATCH',
        headers: {
            'Authorization': `Bearer ${patientSession.token}`,
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(publishData)
        }
    }, publishData);

    assert(patientPatchAttempt.status === 403, `Paciente bloqueado para cambiar visibilidad con 403 Forbidden (obtenido: ${patientPatchAttempt.status})`);

    // 14. Negativo: Paciente A intenta consultar los archivos de Paciente B (ID 1)
    log('14. [NEGATIVO] Paciente intenta consultar expediente de otro paciente (ID 1)...');
    const crossPatientList = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/attachments/patient/1`,
        method: 'GET',
        headers: {
            'Authorization': `Bearer ${patientSession.token}`
        }
    });

    assert(crossPatientList.status === 403, `Consulta cruzada de paciente bloqueada con 403 Forbidden (obtenido: ${crossPatientList.status})`);

    // 15. Negativo: Odontólogo de Clínica 1 intenta subir archivo para paciente de Clínica 2 (ID 17)
    log('15. [NEGATIVO] Odontólogo intenta subir archivo para paciente de otra clínica (ID 17)...');
    const crossClinicMultipart = buildMultipartFormData(
        {
            pacienteId: '17', // Paciente en Clínica 2
            tipoArchivo: 'RADIOGRAFIA'
        },
        {
            fieldName: 'file',
            filename: 'test_cross_clinic.png',
            contentType: 'image/png',
            data: originalFileBytes
        }
    );

    const crossClinicUpload = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/attachments/upload',
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${doctorSession.token}`,
            'Content-Type': crossClinicMultipart.contentType,
            'Content-Length': crossClinicMultipart.body.length
        }
    }, crossClinicMultipart.body);

    assert(crossClinicUpload.status === 404, `Aislamiento multi-tenant: intento sobre otra clínica rechazado con 404 Not Found (obtenido: ${crossClinicUpload.status})`);

    // 16. Negativo: Validación de archivo vacío (0 bytes)
    log('16. [NEGATIVO] Validación de archivo vacío (0 bytes)...');
    const emptyFileMultipart = buildMultipartFormData(
        {
            pacienteId: patientId,
            tipoArchivo: 'DOCUMENTO'
        },
        {
            fieldName: 'file',
            filename: 'archivo_vacio.txt',
            contentType: 'text/plain',
            data: Buffer.alloc(0) // 0 bytes
        }
    );

    const emptyFileUpload = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/attachments/upload',
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${doctorSession.token}`,
            'Content-Type': emptyFileMultipart.contentType,
            'Content-Length': emptyFileMultipart.body.length
        }
    }, emptyFileMultipart.body);

    assert(emptyFileUpload.status === 400, `Archivo de 0 bytes rechazado con 400 Bad Request (obtenido: ${emptyFileUpload.status})`);

    // 17. Negativo: Validación de tipo de archivo no permitido
    log('17. [NEGATIVO] Validación de tipo de archivo no permitido...');
    const invalidTypeMultipart = buildMultipartFormData(
        {
            pacienteId: patientId,
            tipoArchivo: 'MALICIOUS_EXE'
        },
        {
            fieldName: 'file',
            filename: 'malicious.exe',
            contentType: 'application/octet-stream',
            data: Buffer.from([0x01, 0x02, 0x03])
        }
    );

    const invalidTypeUpload = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/attachments/upload',
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${doctorSession.token}`,
            'Content-Type': invalidTypeMultipart.contentType,
            'Content-Length': invalidTypeMultipart.body.length
        }
    }, invalidTypeMultipart.body);

    assert(invalidTypeUpload.status === 400, `Tipo no permitido rechazado con 400 Bad Request (obtenido: ${invalidTypeUpload.status})`);

    // 18. Compensación Atómica: fallo de metadata en BD no deja archivo binario huérfano
    log('18. [COMPENSACIÓN] Verificando compensación ante fallo de metadatos...');
    const storageDir = path.join(process.env.USERPROFILE || process.env.HOME, '.coronyx', 'storage');
    const filesBefore = fs.existsSync(storageDir) ? countFilesInDir(storageDir) : 0;

    // Intentamos subir para un paciente inexistente (ID 999999)
    const failingMultipart = buildMultipartFormData(
        {
            pacienteId: '999999',
            tipoArchivo: 'RADIOGRAFIA'
        },
        {
            fieldName: 'file',
            filename: 'compensacion_test.png',
            contentType: 'image/png',
            data: originalFileBytes
        }
    );

    const failingUpload = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/attachments/upload',
        method: 'POST',
        headers: {
            'Authorization': `Bearer ${doctorSession.token}`,
            'Content-Type': failingMultipart.contentType,
            'Content-Length': failingMultipart.body.length
        }
    }, failingMultipart.body);

    assert(failingUpload.status === 404, `Petición para paciente inexistente falló con 404 (obtenido: ${failingUpload.status})`);
    const filesAfter = fs.existsSync(storageDir) ? countFilesInDir(storageDir) : 0;
    assert(filesBefore === filesAfter, `Compensación atómica confirmada: no hay archivos binarios huérfanos en disco (${filesBefore} vs ${filesAfter})`);

    log('================================================================');
    log('🎉 TODAS LAS PRUEBAS AUTOMATIZADAS DE F06-A COMPLETADAS CON ÉXITO');
    log('================================================================');
}

function countFilesInDir(dir) {
    let count = 0;
    if (!fs.existsSync(dir)) return 0;
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            count += countFilesInDir(full);
        } else if (entry.isFile()) {
            count++;
        }
    }
    return count;
}

runTests().catch(err => {
    console.error('❌ ERROR FATAL EN PRUEBAS F06-A:', err);
    process.exit(1);
});
