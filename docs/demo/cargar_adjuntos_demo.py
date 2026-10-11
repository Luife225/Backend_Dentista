"""Carga explícita de adjuntos ficticios por API; nunca borra datos ni crea cuentas."""
import argparse
import datetime as dt
import getpass
import hashlib
import json
import mimetypes
import os
from pathlib import Path
import urllib.request
import urllib.error
import urllib.parse
import uuid


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base-url', required=True)
    p.add_argument('--aplicar', action='store_true')
    p.add_argument('--permitir-remoto', action='store_true')
    p.add_argument('--credenciales-tsv', type=Path)
    p.add_argument('--resultado', type=Path)
    args = p.parse_args()
    parsed = urllib.parse.urlsplit(args.base_url)
    local = parsed.hostname in ('localhost', '127.0.0.1', '::1')
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError('URL inválida: no debe contener credenciales ni parámetros')
    if not local and (parsed.scheme != 'https' or not args.permitir_remoto):
        raise ValueError('Destino remoto requiere HTTPS y --permitir-remoto')
    if local and parsed.scheme not in ('http', 'https'):
        raise ValueError('Esquema inválido')
    if args.credenciales_tsv and not local:
        raise ValueError('El TSV privado local no se admite para destinos remotos')
    base = args.base_url.rstrip('/')
    if not base.endswith('/api/v1'):
        base += '/api/v1'
    root = Path(__file__).resolve().parent
    manifest = json.loads((root / 'MANIFIESTO_ADJUNTOS_DEMO.json').read_text(encoding='utf-8-sig'))['archivos']
    secrets = {}
    if args.credenciales_tsv:
        for line in args.credenciales_tsv.read_text(encoding='utf-8-sig').splitlines():
            cols = line.split('\t')
            if len(cols) >= 4:
                secrets[cols[1]] = cols[3]

    def request(path, token=None, data=None, content_type=None, binary=False):
        headers = {}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        if content_type:
            headers['Content-Type'] = content_type
        req = urllib.request.Request(base + path, data=data, headers=headers)
        # No seguir redirecciones: nunca reenviar credenciales a otro host.
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *unused, **kwargs):
                return None
        try:
            with urllib.request.build_opener(NoRedirect).open(req, timeout=45) as response:
                raw = response.read()
                return raw if binary else json.loads(raw)
        except urllib.error.HTTPError as exc:
            raise RuntimeError('HTTP %s en %s' % (exc.code, path)) from None

    sessions = {}
    planned = []
    # Prevalidar todos los binarios antes de cualquier escritura.
    for item in manifest:
        file = root / 'adjuntos' / item['archivo']
        blob = file.read_bytes()
        if hashlib.sha256(blob).hexdigest() != item['sha256'] or len(blob) != item['bytes']:
            raise ValueError('Integridad incorrecta: ' + file.name)
    for email in dict.fromkeys(item['profesional_correo'] for item in manifest):
        number = '1' if email.startswith('odontologo1@') else '2'
        login_email = os.environ.get('CORONYX_DEMO_DOCTOR' + number + '_EMAIL', email)
        password = secrets.get(email) or os.environ.get('CORONYX_DEMO_DOCTOR' + number + '_PASSWORD')
        if not password:
            password = getpass.getpass('Contraseña del odontólogo ' + number + ': ')
        session = request('/auth/login', data=json.dumps({'correo': login_email, 'clave': password}).encode(), content_type='application/json')
        if session['rol'] != 'ODONTOLOGO':
            raise ValueError('La cuenta debe tener rol ODONTOLOGO')
        sessions[email] = session
    patients_cache, citas_cache, attachments_cache = {}, {}, {}
    # Resolver identidades y citas; abortar por ambigüedad o falta de datos.
    for item in manifest:
        session = sessions[item['profesional_correo']]
        token = session['token']
        if token not in patients_cache:
            patients_cache[token] = request('/patients', token)
        matches = [x for x in patients_cache[token] if str(x.get('numeroDocumento')) == item['paciente_documento'] and x.get('correo') == item['paciente_correo']]
        if len(matches) != 1:
            raise ValueError('Paciente ausente o ambiguo: ' + item['paciente_documento'])
        patient = matches[0]
        if str(patient['clinicaId']) != str(session['clinicaId']):
            raise ValueError('Clínica incorrecta')
        pid = str(patient['id'])
        key = (token, pid)
        if key not in citas_cache:
            citas_cache[key] = request('/citas/paciente/' + pid, token)
            attachments_cache[key] = request('/attachments/patient/' + pid, token)
        def local_date(appointment):
            return dt.datetime.fromisoformat(appointment['inicioEn'].replace('Z', '+00:00')).astimezone(dt.timezone(dt.timedelta(hours=-5))).date().isoformat()
        citas = [x for x in citas_cache[key] if local_date(x) == item['fecha_cita']]
        if len(citas) != 1:
            raise ValueError('Cita ausente o ambigua: ' + item['fecha_cita'])
        cid = str(citas[0]['id'])
        if str(citas[0].get('odontologoId')) != str(session['usuarioClinicaId']):
            raise ValueError('La cita pertenece a otro odontólogo')
        existing = [x for x in attachments_cache[key] if x['nombreOriginal'] == item['archivo']]
        if len(existing) > 1:
            raise ValueError('Adjunto duplicado: ' + item['archivo'])
        def verify(meta, item=item, pid=pid, cid=cid, token=token):
            if str(meta['pacienteId']) != pid or str(meta['citaId']) != cid or meta['tipoArchivo'] != item['tipo_archivo'] or meta['esVisiblePaciente'] != item['visible_paciente']:
                raise ValueError('Metadatos incompatibles: ' + item['archivo'])
            download = request('/attachments/' + str(meta['id']) + '/download', token, binary=True)
            if hashlib.sha256(download).hexdigest() != item['sha256']:
                raise ValueError('Descarga distinta al original: ' + item['archivo'])
        if existing:
            verify(existing[0])
        planned.append((item, token, pid, cid, existing, verify))
    results = []
    def save():
        if args.resultado:
            args.resultado.parent.mkdir(parents=True, exist_ok=True)
            args.resultado.write_text(json.dumps({'fecha': dt.datetime.now(dt.timezone.utc).isoformat(), 'aplicado': args.aplicar, 'archivos': results}, ensure_ascii=False, indent=2), encoding='utf-8')
    for item, token, pid, cid, existing, verify in planned:
        status, attachment_id = 'prevalidado', None
        if existing:
            status, attachment_id = 'existente_verificado', str(existing[0]['id'])
        elif args.aplicar:
            boundary = uuid.uuid4().hex
            fields = {'pacienteId': pid, 'citaId': cid, 'tipoArchivo': item['tipo_archivo'], 'esVisiblePaciente': str(item['visible_paciente']).lower()}
            if item.get('region_anatomica'):
                fields['regionAnatomica'] = item['region_anatomica']
            chunks = []
            for name, value in fields.items():
                chunks.append(('--' + boundary + '\r\nContent-Disposition: form-data; name="' + name + '"\r\n\r\n' + value + '\r\n').encode())
            mime = mimetypes.guess_type(item['archivo'])[0] or 'application/octet-stream'
            chunks.append(('--' + boundary + '\r\nContent-Disposition: form-data; name="file"; filename="' + item['archivo'] + '"\r\nContent-Type: ' + mime + '\r\n\r\n').encode())
            chunks.extend([(root / 'adjuntos' / item['archivo']).read_bytes(), ('\r\n--' + boundary + '--\r\n').encode()])
            meta = request('/attachments/upload', token, b''.join(chunks), 'multipart/form-data; boundary=' + boundary)
            verify(meta)
            status, attachment_id = 'cargado_verificado', str(meta['id'])
        results.append({'archivo': item['archivo'], 'estado': status, 'id': attachment_id, 'sha256': item['sha256']})
        save()
        print(status + ': ' + item['archivo'])
    print('Total: ' + str(len(results)) + '. Sin borrar datos ni modificar historias clínicas.')


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        # No imprimir cuerpos HTTP, tokens ni credenciales.
        print('Carga detenida: ' + (str(exc) if isinstance(exc, (ValueError, RuntimeError)) else type(exc).__name__))
        raise SystemExit(1)
