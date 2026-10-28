"""Select and inspect five additional DENTEX training images for technical QA.

Selection is fixed from annotations before retrieving any image. No annotation
conversion, clinical relabeling, model installation, inference, or training.
"""

from collections import Counter, defaultdict
from hashlib import sha256
import json
import struct
import sys
import zlib

from PIL import Image, ImageDraw, ImageOps
from revisar_etiquetas_dentex import LAB, CACHE, REPO, request
from mostrar_tres_casos import CYAN, ORANGE, INK, MUTED, WHITE, text


SEED = 'CORONYX-visual-20261025'


def context():
    revision = json.loads((CACHE / 'revision.json').read_text(encoding='utf-8'))['sha']
    base = CACHE / revision
    raw = (base / 'train_quadrant_enumeration_disease.json').read_bytes()
    report = json.loads((LAB / 'auditoria/resultado_etiquetas_2026-10-25.json').read_text(encoding='utf-8'))
    if sha256(raw).hexdigest() != report['sources']['train']['sha256']:
        raise RuntimeError('Source annotation hash changed')
    data = json.loads(raw)
    by_image = defaultdict(list)
    for annotation in data['annotations']:
        by_image[annotation['image_id']].append(annotation)
    folder = base / 'visual_review_5'
    folder.mkdir(exist_ok=True)
    return revision, base, data, by_image, folder


def select():
    revision, base, data, by_image, folder = context()
    cases = json.loads((LAB / 'auditoria/REGISTRO_CASOS_2026-10-25.json').read_text(encoding='utf-8'))
    excluded = {case['image'] for case in cases['cases']}
    target_classes = lambda image: {a['category_id_3'] for a in by_image[image['id']] if a['category_id_3'] in (1, 3)}
    target_count = lambda image: sum(a['category_id_3'] in (1, 3) for a in by_image[image['id']])
    rank = lambda image: sha256((SEED + '|' + revision + '|' + image['file_name']).encode()).hexdigest()
    candidates = [image for image in data['images'] if image['file_name'] not in excluded and target_classes(image)]
    selected = []
    criteria = [
        ('Pocas marcas de caries; sin caries profunda anotada', lambda image: target_classes(image) == {1} and target_count(image) <= 2),
        ('Pocas marcas de caries profunda; sin caries anotada', lambda image: target_classes(image) == {3} and target_count(image) <= 2),
        ('Ambas categorías en la misma radiografía', lambda image: target_classes(image) == {1, 3}),
        ('Ocho o más marcas de las categorías objetivo', lambda image: target_count(image) >= 8),
    ]
    used = set()
    for description, predicate in criteria:
        pool = [image for image in candidates if image['id'] not in used and predicate(image)]
        if not pool:
            raise RuntimeError('Empty selection stratum: ' + description)
        image = min(pool, key=rank)
        used.add(image['id'])
        selected.append(dict(image=image, reason=description))
    pool = [image for image in candidates if image['id'] not in used]
    image = min(pool, key=lambda image: (image['width'] * image['height'], rank(image)))
    selected.append(dict(image=image, reason='Menor área de imagen entre las candidatas restantes'))
    for record in selected:
        rows = by_image[record['image']['id']]
        record['annotations_by_category'] = dict(Counter(a['category_id_3'] for a in rows))
        record['annotation_ids'] = [a['id'] for a in rows]
    manifest = dict(revision=revision, selection_seed=SEED,
                    selection_rule='Four strata, minimum SHA256 rank per stratum; fifth: minimum pixel area, same rank as tie-breaker',
                    excluded_previously_flagged_images=sorted(excluded),
                    scope='Directed technical QA sample, not a representative clinical performance estimate',
                    selected_before_image_retrieval=True, selected=selected)
    target = folder / 'selection.json'
    if target.exists() and json.loads(target.read_text(encoding='utf-8')) != manifest:
        raise RuntimeError('Existing selection differs; do not silently change it')
    target.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


def retrieve():
    revision, base, data, by_image, folder = context()
    selection = json.loads((folder / 'selection.json').read_text(encoding='utf-8'))
    directory = json.loads((base / 'training_data.zip.directory.json').read_text(encoding='utf-8'))
    url = f'{REPO}/resolve/{revision}/DENTEX/training_data.zip'
    receipts = []
    for chosen in selection['selected']:
        expected = chosen['image']
        filename = expected['file_name']
        entry = next(e for e in directory['entries'] if e['name'] == 'training_data/quadrant-enumeration-disease/xrays/' + filename)
        target = folder / filename
        if target.exists():
            raw = target.read_bytes()
        else:
            if entry['compressed'] > 8_000_000 or entry['uncompressed'] > 15_000_000:
                raise RuntimeError('Sample exceeds bounded read limit')
            offset = entry['offset']
            header, _ = request(url, f'{offset}-{offset + 29}')
            values = struct.unpack('<4s5H3L2H', header)
            if values[0] != b'PK\x03\x04' or entry['method'] != 8:
                raise RuntimeError('Unexpected image archive record')
            start = offset + 30 + values[-2] + values[-1]
            packed, _ = request(url, f"{start}-{start + entry['compressed'] - 1}", limit=8_000_000)
            raw = zlib.decompress(packed, -15)
        if len(raw) != entry['uncompressed'] or zlib.crc32(raw) != entry['crc32']:
            raise RuntimeError('Sample size/CRC mismatch')
        if not target.exists():
            target.write_bytes(raw)
        with Image.open(target) as source:
            source.load()
            if source.size != (expected['width'], expected['height']):
                raise RuntimeError('Decoded dimensions differ from annotation JSON')
            original_mode = source.mode
        rows = by_image[expected['id']]
        outside = [a['id'] for a in rows if a['bbox'][0] < 0 or a['bbox'][1] < 0 or
                   a['bbox'][0] + a['bbox'][2] > expected['width'] or
                   a['bbox'][1] + a['bbox'][3] > expected['height']]
        if outside:
            raise RuntimeError('Sample contains boxes outside decoded image')
        receipts.append(dict(file_name=filename, sha256=sha256(raw).hexdigest(), bytes=len(raw),
                             dimensions=[expected['width'], expected['height']], mode=original_mode,
                             annotation_ids=chosen['annotation_ids'], boxes_within_decoded_dimensions=True,
                             clinical_review_performed=False, visual_review_status='pending'))
    manifest = dict(revision=revision, samples=receipts,
                    source_annotations_unchanged=True, scope='Five selected training images only')
    (folder / 'retrieval.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


def render():
    revision, base, data, by_image, folder = context()
    selection = json.loads((folder / 'selection.json').read_text(encoding='utf-8'))
    retrieval = json.loads((folder / 'retrieval.json').read_text(encoding='utf-8'))
    by_name = {item['file_name']: item for item in retrieval['samples']}
    figures = []
    for number, chosen in enumerate(selection['selected'], 1):
        filename = chosen['image']['file_name']
        raw = (folder / filename).read_bytes()
        if sha256(raw).hexdigest() != by_name[filename]['sha256']:
            raise RuntimeError('Original sample hash changed')
        source = Image.open(folder / filename).convert('RGB')
        canvas = Image.new('RGB', (1800, 1160), WHITE)
        draw = ImageDraw.Draw(canvas)
        text(draw, (36, 24), f'Muestra {number} · {filename}', 43, bold=True)
        text(draw, (36, 88), chosen['reason'], 31, MUTED)
        text(draw, (36, 139), 'C = Caries (celeste)    P = Caries profunda (naranja)    O = Otra categoría (blanco)', 29)
        panel = (35, 205, 1765, 1015)
        shown = ImageOps.contain(source, (panel[2] - panel[0], panel[3] - panel[1]), Image.Resampling.LANCZOS)
        ox, oy = panel[0] + (panel[2] - panel[0] - shown.width) // 2, panel[1] + (panel[3] - panel[1] - shown.height) // 2
        canvas.paste(shown, (ox, oy))
        draw = ImageDraw.Draw(canvas)
        sx, sy = shown.width / source.width, shown.height / source.height
        rows = by_image[chosen['image']['id']]
        for row in sorted(rows, key=lambda row: row['id']):
            x, y, width, height = row['bbox']
            left, top = ox + x * sx, oy + y * sy
            category = row['category_id_3']
            color, label = (CYAN, 'C') if category == 1 else (ORANGE, 'P') if category == 3 else (WHITE, 'O')
            draw.rectangle((left, top, ox + (x + width) * sx, oy + (y + height) * sy), outline=color, width=4)
            marker_y = top - 38 if top - 38 >= oy else top
            draw.rectangle((left, marker_y, left + 36, marker_y + 38), fill=INK)
            text(draw, (left + 7, marker_y), label, 29, color, bold=True)
        counts = Counter(row['category_id_3'] for row in rows)
        summary = f"Caries: {counts[1]} · Caries profunda: {counts[3]} · Otras: {counts[0] + counts[2]}"
        text(draw, (36, 1040), summary + f' · Original: {source.width} × {source.height} px', 28)
        text(draw, (36, 1085), 'DENTEX · Hamamci et al. · CC BY-NC-SA 4.0 · Escalado y marcas añadidos', 24, MUTED)
        text(draw, (36, 1120), 'Anotaciones de referencia; no son predicciones ni una revisión clínica.', 24, MUTED)
        figure = folder / f'muestra-{number}-marcas.png'
        canvas.save(figure)
        figures.append(dict(number=number, image=filename, path=str(figure),
                            counts=dict(counts), annotation_ids=[row['id'] for row in rows]))
    (folder / 'figures.json').write_text(json.dumps(dict(revision=revision, figures=figures), ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(figures, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    operations = {'select': select, 'retrieve': retrieve, 'render': render}
    if len(sys.argv) != 2 or sys.argv[1] not in operations:
        raise SystemExit('Choose select, retrieve, or render')
    operations[sys.argv[1]]()
