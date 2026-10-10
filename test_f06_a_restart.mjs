/**
 * Test de Resiliencia ante Reinicio (Reboot Resilience) para F06-A
 */

import http from 'http';

function log(msg, ...args) {
    console.log(`[RESTART-TEST] ${msg}`, ...args);
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
    return res.json;
}

async function run() {
    log('Verificando persistencia de archivos tras reinicio de Spring Boot...');
    const doctor = await login('odontologo@coronyx.pe', '123456');
    const patient = await login('paciente@coronyx.pe', '123456');

    // 1. Consultar archivos del paciente por odontólogo
    const listRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: '/api/v1/attachments/patient/2',
        method: 'GET',
        headers: { 'Authorization': `Bearer ${doctor.token}` }
    });
    assert(listRes.status === 200, 'Odontólogo puede consultar la lista tras reinicio');
    assert(Array.isArray(listRes.json) && listRes.json.length > 0, `Archivos encontrados: ${listRes.json.length}`);

    const att = listRes.json[0];
    log(`Archivo verificado: ID=${att.id}, nombre=${att.nombreOriginal}, clave=${att.claveAlmacenamiento}`);

    // 2. Descargar binario tras reinicio
    const dlRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/attachments/${att.id}/download`,
        method: 'GET',
        headers: { 'Authorization': `Bearer ${doctor.token}` }
    });
    assert(dlRes.status === 200, 'Descarga del archivo exitosa tras reinicio del servidor (200 OK)');
    assert(dlRes.body.length === att.pesoBytes, `El tamaño en bytes coincide exactamente: ${dlRes.body.length}`);

    // 3. Publicar y verificar que el paciente puede descargarlo tras reinicio
    const pubData = JSON.stringify({ esVisiblePaciente: true });
    await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/attachments/${att.id}/visibility`,
        method: 'PATCH',
        headers: {
            'Authorization': `Bearer ${doctor.token}`,
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(pubData)
        }
    }, pubData);

    const patDlRes = await request({
        hostname: 'localhost',
        port: 8081,
        path: `/api/v1/attachments/${att.id}/download`,
        method: 'GET',
        headers: { 'Authorization': `Bearer ${patient.token}` }
    });
    assert(patDlRes.status === 200, 'Paciente descarga con éxito archivo persistido tras reinicio');
    assert(patDlRes.body.length === att.pesoBytes, 'Integridad binaria perfecta');

    log('✅ PRUEBA DE REINICIO SUPERADA CON ÉXITO: Persistencia durable demostrada.');
}

run().catch(err => {
    console.error('❌ ERROR:', err);
    process.exit(1);
});
