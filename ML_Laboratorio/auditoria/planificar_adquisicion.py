"""Create an acquisition manifest from cached official metadata; no network calls.

This is a plan/inventory only. It never obtains images, creates the proposed
destination tree, modifies annotations, assigns splits or trains a model.
"""

from hashlib import sha256
import json
from pathlib import Path, PurePosixPath
import shutil
import zlib

from revisar_etiquetas_dentex import LAB, CACHE, REPO


def main():
    revision = json.loads((CACHE / 'revision.json').read_text(encoding='utf-8'))['sha']
    base = CACHE / revision
    audit = json.loads((LAB / 'auditoria/resultado_etiquetas_2026-10-25.json').read_text(encoding='utf-8'))
    destination = LAB / 'data' / 'dentex_raw' / revision
    local_folders = [base / 'samples', base / 'visual_review_5',
                     Path('C:/Users/USER/AppData/Local/Temp/coronyx_dentex_visual_check')]
    image_records, annotation_records, summaries = [], [], []
    for split, archive, annotation in [
        ('train', 'training_data.zip', 'train_quadrant_enumeration_disease.json'),
        ('validation', 'validation_data.zip', 'validation_triple.json')
    ]:
        directory_path = base / (archive + '.directory.json')
        directory_raw = directory_path.read_bytes()
        directory = json.loads(directory_raw)
        annotation_path = base / annotation
        annotation_raw = annotation_path.read_bytes()
        if sha256(annotation_raw).hexdigest() != audit['sources'][split]['sha256']:
            raise RuntimeError('Source annotation hash mismatch')
        data = json.loads(annotation_raw)
        prefix = 'training_data/quadrant-enumeration-disease/' if split == 'train' else ''
        entries = {}
        for entry in directory['entries']:
            if entry['name'].startswith(prefix) and entry['name'].lower().endswith(('.png', '.jpg', '.jpeg')):
                entries.setdefault(PurePosixPath(entry['name']).name, []).append(entry)
        selected = []
        for image in sorted(data['images'], key=lambda row: row['file_name']):
            filename = image['file_name']
            if PurePosixPath(filename).name != filename or filename in ('.', '..'):
                raise RuntimeError('Unexpected image basename')
            matches = entries.get(filename, [])
            if len(matches) != 1:
                raise RuntimeError('Ambiguous/missing archive record')
            entry = matches[0]
            candidates = []
            for folder in local_folders:
                local = folder / filename
                if local.is_file() and local.stat().st_size == entry['uncompressed']:
                    raw = local.read_bytes()
                    if zlib.crc32(raw) == entry['crc32']:
                        candidates.append(dict(path=str(local), sha256=sha256(raw).hexdigest(),
                                               temporary='AppData/Local/Temp' in str(local).replace('\\', '/')))
            record = dict(source_split=split, image_id=image['id'], file_name=filename,
                          expected_width=image['width'], expected_height=image['height'],
                          archive_url=f'{REPO}/resolve/{revision}/DENTEX/{archive}',
                          archive_entry=entry['name'], archive_entry_offset=entry['offset'],
                          compression_method=entry['method'], compressed_bytes=entry['compressed'],
                          original_bytes=entry['uncompressed'], original_crc32=entry['crc32'],
                          proposed_destination=str(destination / split / 'images' / filename),
                          verified_reuse_candidates=candidates,
                          planned_action='revalidate_and_copy_existing' if candidates else 'retrieve_selected_record',
                          action_executed=False)
            selected.append(record)
        image_records.extend(selected)
        annotation_records.append(dict(source_split=split, source_local_path=str(annotation_path),
                                        sha256=sha256(annotation_raw).hexdigest(), bytes=len(annotation_raw),
                                        source_archive_entry='training_data/quadrant-enumeration-disease/train_quadrant_enumeration_disease.json' if split == 'train' else None,
                                        source_url=f'{REPO}/resolve/{revision}/DENTEX/{archive if split == "train" else annotation}',
                                        proposed_destination=str(destination / split / 'annotations.original.json'),
                                        action_executed=False))
        missing = [row for row in selected if not row['verified_reuse_candidates']]
        summaries.append(dict(source_split=split, image_count=len(selected),
                              image_original_bytes=sum(row['original_bytes'] for row in selected),
                              image_compressed_bytes=sum(row['compressed_bytes'] for row in selected),
                              annotation_bytes=len(annotation_raw), archive_bytes=directory['archive_bytes'],
                              archive_directory_sha256=sha256(directory_raw).hexdigest(),
                              verified_reusable_images=len(selected) - len(missing), missing_image_count=len(missing),
                              missing_image_original_bytes=sum(row['original_bytes'] for row in missing),
                              missing_image_compressed_bytes=sum(row['compressed_bytes'] for row in missing)))
    image_bytes = sum(row['original_bytes'] for row in image_records)
    annotation_bytes = sum(row['bytes'] for row in annotation_records)
    totals = dict(images=len(image_records), image_original_bytes=image_bytes,
                  annotation_bytes=annotation_bytes, complete_raw_bytes=image_bytes + annotation_bytes,
                  compressed_image_records_bytes=sum(row['compressed_bytes'] for row in image_records),
                  verified_reusable_images=sum(bool(row['verified_reuse_candidates']) for row in image_records),
                  missing_image_count=sum(not row['verified_reuse_candidates'] for row in image_records),
                  missing_image_original_bytes=sum(row['original_bytes'] for row in image_records if not row['verified_reuse_candidates']),
                  missing_image_compressed_bytes=sum(row['compressed_bytes'] for row in image_records if not row['verified_reuse_candidates']))
    free = shutil.disk_usage(LAB.anchor).free
    budget = dict(units='decimal bytes; planning allowances are estimates, not measurements',
                  raw_round_up=3_100_000_000, optional_prepared_copy=3_100_000_000,
                  future_environment_and_dependency_cache_allowance=10_000_000_000,
                  experiment_outputs_allowance=5_000_000_000, staging_allowance=2_000_000_000,
                  minimum_free_to_leave=10_000_000_000)
    work_budget = sum(value for key, value in budget.items() if key != 'units' and key != 'minimum_free_to_leave')
    disk = dict(measured_date='2026-10-25', free_bytes=free,
                current_laboratory_logical_files_bytes=sum(f.stat().st_size for f in LAB.rglob('*') if f.is_file()),
                work_budget_bytes=work_budget,
                work_plus_free_margin_required_bytes=work_budget + budget['minimum_free_to_leave'],
                sufficient_for_planned_allowance_at_measurement=free >= work_budget + budget['minimum_free_to_leave'],
                reserved_or_allocated=False, allowances=budget)
    manifest = dict(version=1, date='2026-10-25', status='planned_not_executed',
                    repository=REPO, revision=revision, proposed_root=str(destination),
                    primary_method='bounded_HTTP_Range_per_selected_ZIP_entry_with_integrity_checks',
                    full_archive_fallback_automatic=False,
                    scope='705 original diagnostic training images plus 50 original validation images; includes all labels and empty-label images',
                    excluded_from_image_acquisition=['test_data.zip', 'quadrant-only', 'quadrant-enumeration-only', 'unlabelled'],
                    summaries=summaries, totals=totals, disk=disk,
                    source_annotations=annotation_records, images=image_records,
                    transformations_applied=False, final_partitions_generated=False, training_executed=False)
    target = LAB / 'MANIFIESTO_ADQUISICION_2026-10-25.json'
    target.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(dict(totals=totals, disk=disk, output=str(target)), ensure_ascii=True, indent=2))


if __name__ == '__main__':
    main()
