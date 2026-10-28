"""Retrieve only the three image records explicitly requested for visual review."""

from hashlib import sha256
import json
from pathlib import Path
import struct
import zlib

from PIL import Image
from revisar_etiquetas_dentex import LAB, CACHE, REPO, request


def main():
    revision = json.loads((CACHE / 'revision.json').read_text(encoding='utf-8'))['sha']
    base = CACHE / revision
    directory = json.loads((base / 'training_data.zip.directory.json').read_text(encoding='utf-8'))
    annotations = json.loads((base / 'train_quadrant_enumeration_disease.json').read_text(encoding='utf-8'))
    output = base / 'samples'
    output.mkdir(exist_ok=True)
    url = f'{REPO}/resolve/{revision}/DENTEX/training_data.zip'
    receipts = []
    for filename in ('train_377.png', 'train_481.png', 'train_440.png'):
        entry = next(e for e in directory['entries']
                     if e['name'] == 'training_data/quadrant-enumeration-disease/xrays/' + filename)
        image_record = next(i for i in annotations['images'] if i['file_name'] == filename)
        target = output / filename
        if target.exists():
            raw = target.read_bytes()
        else:
            if entry['compressed'] > 8_000_000 or entry['uncompressed'] > 15_000_000:
                raise RuntimeError('Sample exceeds the bounded image limit')
            offset = entry['offset']
            header, _ = request(url, f'{offset}-{offset + 29}')
            values = struct.unpack('<4s5H3L2H', header)
            if values[0] != b'PK\x03\x04':
                raise RuntimeError('Invalid image ZIP local header')
            start = offset + 30 + values[-2] + values[-1]
            packed, _ = request(url, f"{start}-{start + entry['compressed'] - 1}", limit=8_000_000)
            if entry['method'] != 8:
                raise RuntimeError('Unexpected image compression')
            raw = zlib.decompress(packed, -15)
        if len(raw) != entry['uncompressed'] or zlib.crc32(raw) != entry['crc32']:
            raise RuntimeError('Sample size/CRC check failed')
        if not target.exists():
            target.write_bytes(raw)
        with Image.open(target) as image:
            image.load()
            if image.size != (image_record['width'], image_record['height']):
                raise RuntimeError('Sample dimensions disagree with annotation JSON')
            actual_size = image.size
        receipts.append(dict(file_name=filename, sha256=sha256(raw).hexdigest(), bytes=len(raw),
                             dimensions=actual_size, image_id=image_record['id'], path=str(target)))
    manifest = dict(repository=REPO, revision=revision, samples=receipts,
                    scope='Three training radiographs only; no model inference or annotation changes')
    (output / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
