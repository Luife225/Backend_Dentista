"""Render exact DENTEX annotation comparisons as static data figures.

No image synthesis, clinical relabeling, contrast changes, or model inference.
The original radiographs stay intact under the ignored data/ directory.
"""

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps
from revisar_etiquetas_dentex import CACHE


WHITE = '#ffffff'
INK = '#182229'
MUTED = '#46535c'
CYAN = '#00d5f0'
ORANGE = '#ffb525'


def font(size, bold=False):
    return ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf' if bold else 'C:/Windows/Fonts/arial.ttf', size)


def text(draw, position, value, size=30, fill=INK, bold=False):
    draw.text(position, value, font=font(size, bold), fill=fill)


def fit_panel(canvas, source, crop, panel, annotation, color):
    left, top, right, bottom = panel
    cropped = source.crop(crop)
    shown = ImageOps.contain(cropped, (right - left, bottom - top), Image.Resampling.LANCZOS)
    ox, oy = left + (right - left - shown.width) // 2, top + (bottom - top - shown.height) // 2
    canvas.paste(shown, (ox, oy))
    sx, sy = shown.width / cropped.width, shown.height / cropped.height
    x, y, width, height = annotation['bbox']
    coordinates = (ox + (x - crop[0]) * sx, oy + (y - crop[1]) * sy,
                   ox + (x + width - crop[0]) * sx, oy + (y + height - crop[1]) * sy)
    ImageDraw.Draw(canvas).rectangle(coordinates, outline=color, width=6)


def crop_for(source, box):
    x, y, width, height = box
    return (max(0, math.floor(x - 100)), max(0, math.floor(y - 100)),
            min(source.width, math.ceil(x + width + 100)),
            min(source.height, math.ceil(y + height + 100)))


def footer(draw, height, name):
    text(draw, (38, height - 100), f'{name} · Anotaciones originales; sin predicciones', 27)
    text(draw, (38, height - 56), 'DENTEX · Hamamci et al. · CC BY-NC-SA 4.0 · Recortes y cuadros añadidos', 24, MUTED)


def side_by_side(samples, output, filename, title, left_id, right_id, by_id, labels, colors, target):
    source = Image.open(samples / filename).convert('RGB')
    left, right = by_id[left_id], by_id[right_id]
    assert left['image_id'] == right['image_id'] and left['bbox'] == right['bbox']
    crop = crop_for(source, left['bbox'])
    canvas = Image.new('RGB', (1500, 1050), WHITE)
    draw = ImageDraw.Draw(canvas)
    text(draw, (38, 30), title, 44, bold=True)
    text(draw, (38, 94), 'El mismo recorte se muestra dos veces para comparar los registros.', 30, MUTED)
    text(draw, (60, 164), labels[0], 33, bold=True)
    text(draw, (790, 164), labels[1], 33, bold=True)
    fit_panel(canvas, source, crop, (40, 225, 735, 916), left, colors[0])
    fit_panel(canvas, source, crop, (765, 225, 1460, 916), right, colors[1])
    footer(draw, canvas.height, filename)
    canvas.save(output / target)
    return dict(file=target, source=filename, annotation_ids=[left_id, right_id], source_crop=list(crop))


def separated(samples, output, by_id):
    filename = 'train_440.png'
    source = Image.open(samples / filename).convert('RGB')
    canvas = Image.new('RGB', (1500, 1020), WHITE)
    draw = ImageDraw.Draw(canvas)
    text(draw, (38, 26), '3. Mismo código dental, cuadros separados', 44, bold=True)
    text(draw, (38, 94), 'A: Caries (209)                       B: Caries profunda (211)', 32)
    panel = (35, 160, 1465, 845)
    shown = ImageOps.contain(source, (panel[2] - panel[0], panel[3] - panel[1]), Image.Resampling.LANCZOS)
    ox, oy = panel[0] + (panel[2] - panel[0] - shown.width) // 2, panel[1] + (panel[3] - panel[1] - shown.height) // 2
    canvas.paste(shown, (ox, oy))
    sx, sy = shown.width / source.width, shown.height / source.height
    draw = ImageDraw.Draw(canvas)
    for annotation_id, label, color in [(209, 'A', CYAN), (211, 'B', ORANGE)]:
        row = by_id[annotation_id]
        x, y, width, height = row['bbox']
        left, top = ox + x * sx, oy + y * sy
        draw.rectangle((left, top, ox + (x + width) * sx, oy + (y + height) * sy), outline=color, width=5)
        draw.rectangle((left, top - 44, left + 42, top), fill=INK)
        text(draw, (left + 9, top - 41), label, 33, color, bold=True)
    text(draw, (38, 863), 'En el archivo ambos tienen cuadrante 4 y número de diente 7.', 30)
    footer(draw, canvas.height, filename)
    target = 'caso-3-codigo-repetido.png'
    canvas.save(output / target)
    return dict(file=target, source=filename, annotation_ids=[209, 211], source_crop=None)


def main():
    revision = json.loads((CACHE / 'revision.json').read_text(encoding='utf-8'))['sha']
    base = CACHE / revision
    samples = base / 'samples'
    output = base / 'examples'
    output.mkdir(exist_ok=True)
    data = json.loads((base / 'train_quadrant_enumeration_disease.json').read_text(encoding='utf-8'))
    by_id = {a['id']: a for a in data['annotations']}
    a, b = by_id[2335], by_id[2337]
    assert {k: v for k, v in a.items() if k != 'id'} == {k: v for k, v in b.items() if k != 'id'}
    assert by_id[2780]['category_id_3'] == 1 and by_id[2782]['category_id_3'] == 3
    assert by_id[209]['category_id_1'] == by_id[211]['category_id_1'] == 3
    assert by_id[209]['category_id_2'] == by_id[211]['category_id_2'] == 6
    receipts = [
        side_by_side(samples, output, 'train_377.png', '1. Una anotación guardada dos veces', 2335, 2337, by_id,
                     ['Registro 2335 · Caries', 'Registro 2337 · Caries'], [CYAN, CYAN], 'caso-1-duplicado.png'),
        side_by_side(samples, output, 'train_481.png', '2. Dos categorías para el mismo cuadro', 2780, 2782, by_id,
                     ['Registro 2780 · Caries', 'Registro 2782 · Caries profunda'], [CYAN, ORANGE], 'caso-2-dos-categorias.png'),
        separated(samples, output, by_id),
    ]
    (output / 'manifest.json').write_text(json.dumps(dict(revision=revision, figures=receipts,
                                                        modifications='Cropping, display rescaling, exact bounding boxes and labels only',
                                                        license='CC BY-NC-SA 4.0'), ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(dict(output=str(output), figures=receipts), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
