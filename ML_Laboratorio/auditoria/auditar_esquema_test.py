"""Inspect only the vocabulary/schema of official test annotation JSON files.

No image payload retrieval, image viewing, conversion, inference or training.
The inspection determines whether the released reference schema can support
the planned two-class evaluation; no model results are used.
"""

from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from hashlib import sha256
import json
from pathlib import PurePosixPath
import struct
import zlib

from revisar_etiquetas_dentex import LAB, CACHE, REPO, get_directory, annotation_from_zip, request


def local_data_start(header, offset):
    values = struct.unpack_from('<4s5H3L2H', header)
    if values[0] != b'PK\x03\x04':
        raise RuntimeError('Invalid test annotation local record')
    return offset + 30 + values[-2] + values[-1]


def inspect():
    revision = json.loads((CACHE / 'revision.json').read_text(encoding='utf-8'))['sha']
    folder = CACHE / revision / 'test_annotation_audit'
    folder.mkdir(exist_ok=True)
    url = f'{REPO}/resolve/{revision}/DENTEX/test_data.zip'
    index_path = folder / 'directory.json'
    if index_path.exists():
        directory = json.loads(index_path.read_text(encoding='utf-8'))
    else:
        directory = get_directory(url)
        index_path.write_text(json.dumps(directory, indent=2), encoding='utf-8')
    entries = [e for e in directory['entries'] if e['name'].lower().endswith('.json')]
    if not entries or any(e['uncompressed'] > 100_000 for e in entries) or sum(e['uncompressed'] for e in entries) > 10_000_000:
        raise RuntimeError('Test JSONs exceed bounded metadata inspection limit')
    raw_files = {}
    pending = []
    for entry in entries:
        cached = folder / PurePosixPath(entry['name']).name
        if cached.exists():
            raw_files[entry['name']] = cached.read_bytes()
        else:
            pending.append(entry)
    retrieval_mode = 'cached'
    if pending:
        first = min(pending, key=lambda e: e['offset'])
        last = max(pending, key=lambda e: e['offset'])
        header, _ = request(url, f"{last['offset']}-{last['offset'] + 29}")
        last_start = local_data_start(header, last['offset'])
        lower, upper = first['offset'], last_start + last['compressed'] - 1
        contains_nonjson_payload = any(lower <= e['offset'] <= upper and
                                       not e['name'].lower().endswith('.json') and
                                       not e['name'].endswith('/') for e in directory['entries'])
        if upper - lower + 1 <= 4_000_000 and not contains_nonjson_payload:
            block, _ = request(url, f'{lower}-{upper}')
            retrieval_mode = 'bounded_contiguous_json_records_only'
            for entry in pending:
                local_offset = entry['offset'] - lower
                start = local_data_start(block[local_offset:local_offset + 30], local_offset)
                packed = block[start:start + entry['compressed']]
                if entry['method'] != 8:
                    raise RuntimeError('Unexpected JSON compression')
                raw_files[entry['name']] = zlib.decompress(packed, -15)
        else:
            retrieval_mode = 'individual_json_records_only'
            def fetch(entry):
                return entry['name'], annotation_from_zip(url, entry)
            with ThreadPoolExecutor(max_workers=4) as pool:
                for name, raw in pool.map(fetch, pending):
                    raw_files[name] = raw
    labels, codes, names, shape_types, keys = Counter(), Counter(), Counter(), Counter(), Counter()
    receipts, inconsistent, malformed = [], [], []
    image_entries = [e for e in directory['entries'] if e['name'].lower().endswith(('.png', '.jpg', '.jpeg'))]
    image_basenames = Counter(PurePosixPath(e['name']).name for e in image_entries)
    for entry in sorted(entries, key=lambda e: e['name']):
        raw = raw_files[entry['name']]
        if len(raw) != entry['uncompressed'] or zlib.crc32(raw) != entry['crc32']:
            raise RuntimeError('Test JSON size/CRC verification failed')
        data = json.loads(raw)
        if data.get('imageData'):
            raise RuntimeError('Embedded image data found; do not process as metadata-only')
        (folder / PurePosixPath(entry['name']).name).write_bytes(raw)
        keys.update(data.keys())
        image_path = data.get('imagePath', '').replace('\\', '/')
        basename = PurePosixPath(image_path).name
        if not basename or image_basenames[basename] != 1:
            inconsistent.append(entry['name'])
        shapes = data.get('shapes', [])
        for shape in shapes:
            value = shape.get('label')
            if not isinstance(value, str):
                malformed.append(entry['name'])
                continue
            labels[value] += 1
            shape_types[shape.get('shape_type', 'unknown')] += 1
            parts = value.split('-')
            if len(parts) < 3:
                malformed.append(dict(file=entry['name'], label=value))
            else:
                codes[parts[0]] += 1
                names['-'.join(parts[1:-1])] += 1
        receipts.append(dict(file=entry['name'], sha256=sha256(raw).hexdigest(), bytes=len(raw),
                             shapes=len(shapes), image_path=data.get('imagePath'),
                             image_width=data.get('imageWidth'), image_height=data.get('imageHeight')))
    report = dict(date='2026-10-25', repository=REPO, revision=revision,
                  scope='Vocabulary and format audit only, prior to training; no test image payloads or model predictions',
                  retrieval_mode=retrieval_mode, archive_bytes=directory['archive_bytes'],
                  image_files_listed=len(image_entries), json_files=len(entries),
                  total_shapes=sum(shape_types.values()), label_code_counts=dict(codes),
                  label_name_counts=dict(names), shape_types=dict(shape_types),
                  json_keys=dict(keys), malformed_labels=malformed,
                  ambiguous_image_path_matches=inconsistent,
                  literal_deep_caries_label_present=any('deep' in name.lower() or 'profund' in name.lower() for name in names),
                  mapping_to_training_taxonomy_verified=False,
                  test_images_viewed=False, inference_or_training_performed=False,
                  files=receipts)
    path = LAB / 'auditoria/ESQUEMA_TEST_2026-10-25.json'
    path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding='utf-8')
    compact = {key: value for key, value in report.items() if key not in ('files', 'json_keys')}
    print(json.dumps(compact, indent=2, ensure_ascii=True))


if __name__ == '__main__':
    inspect()
