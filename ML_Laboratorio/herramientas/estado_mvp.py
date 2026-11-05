"""Muestra avance comprobado del ML; no accede a la red ni inicia entrenamiento."""

import json
import subprocess

from .adquirir_dentex import LAB, RAW_ROOT, RECEIPT, REVISION


def main():
    branch = subprocess.check_output(
        ["git", "-C", str(LAB), "branch", "--show-current"], text=True
    ).strip()
    receipt = json.loads(RECEIPT.read_text(encoding="utf-8")) if RECEIPT.is_file() else {}
    integrity_path = LAB / "auditoria" / "INTEGRIDAD_IMAGENES_2026-10-27.json"
    integrity = json.loads(integrity_path.read_text(encoding="utf-8")) if integrity_path.is_file() else {}
    cases = json.loads((LAB / "auditoria" / "REGISTRO_CASOS_2026-10-25.json").read_text(encoding="utf-8"))
    partitions_path = LAB / "preparacion" / "PARTICIONES_DENTEX_V1.json"
    partitions = json.loads(partitions_path.read_text(encoding="utf-8")) if partitions_path.is_file() else {}
    prepared_root = LAB / "datasets" / "dentex_mvp_v1"
    prepared_receipt_path = prepared_root / "recibo.preparacion.json"
    prepared_receipt = json.loads(prepared_receipt_path.read_text(encoding="utf-8")) if prepared_receipt_path.is_file() else {}
    local_originals = sum(1 for _ in RAW_ROOT.glob("*/images/*.png"))
    local_prepared = sum(1 for _ in prepared_root.glob("images/*/*.png"))
    prepared = (prepared_receipt.get("status") == "complete" and
                local_prepared == prepared_receipt.get("prepared_images"))
    result = {
        "laboratorio": str(LAB), "rama": branch, "revision_dentex": REVISION,
        "adquisicion": {"estado": receipt.get("status", "not_started"),
                        "imagenes_verificadas_en_ultimo_recibo": len(receipt.get("images", [])),
                        "originales_presentes_en_este_equipo": local_originals,
                        "imagenes_previstas": 755, "errores": len(receipt.get("errors", []))},
        "auditoria_de_referencia_del_27_octubre": integrity.get("summary", {"estado": "pending"}),
        "grupos_con_duda_clinica_conservados_en_fuente": cases["summary"]["pending_review_groups"],
        "particiones_fijadas_en_codigo": partitions.get("summary", {}),
        "preparacion_local": {"completa": prepared, "imagenes_presentes": local_prepared,
                               "verificar_integridad_con": "python -m herramientas.verificar_preparacion"},
        "datos_preparados_para_experimento_restringido_en_este_equipo": prepared,
        "entrenamiento_listo": False,
        "pasos_pendientes": ["En un equipo nuevo: obtener y preparar las imágenes siguiendo LAPTOP.md",
                             "Elegir modelo y comprobar licencias y entorno GPU",
                             "Entrenar, evaluar y comprobar inferencia real",
                             "Integrar el resultado con revisión del odontólogo"],
        "limite": "Este comando informa preparación de datos; no genera predicciones",
    }
    print(json.dumps(result, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
