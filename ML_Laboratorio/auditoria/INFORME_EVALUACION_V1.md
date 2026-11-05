# CORONYX — Informe de Selección (Fase E) y Evaluación Interna Final (Fase F)

**Fecha UTC:** `2026-10-29T00:28:41.006739+00:00`  
**Modelo congelado:** `coronyx-dentex-yolov8n` (`1.0.0-mvp`)  
**SHA-256 de los pesos (`best.pt`):** `30acddf3f3a20182a6555bba8f64c4999ea3d8297d688d5296b20fc01d4ad652`  
**Umbral operativo congelado en validación:** `0.05` (IoU de coincidencia `0.50`)

---

## 1. Resumen en Validación (`44` imágenes — Fase E)

- **Media no ponderada AP50:** `0.308963`
- **Media no ponderada AP50-95:** `0.210846`
- **Macro F1 (en umbral `0.05`):** `0.378348` (Precisión macro `0.370259`, Recall macro `0.578589`)

---

## 2. Resultados en Prueba Interna (`127` imágenes reservadas — Fase F)

- **Media no ponderada AP50:** `0.310704`
- **Media no ponderada AP50-95:** `0.202196`
- **Macro F1 (en umbral congelado `0.05`):** `0.378177` (Precisión macro `0.36157`, Recall macro `0.524196`)

| Clase | Imágenes con ref. | Anotaciones ref. | TP | FP | FN | Precisión | Recall | F1 | AP50 | AP50-95 |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **`0: caries`** | 122 | 463 | 350 | 928 | 113 | 0.273865 | 0.75594 | 0.402068 | 0.371781 | 0.259249 |
| **`1: caries_profunda`** | 61 | 106 | 31 | 38 | 75 | 0.449275 | 0.292453 | 0.354286 | 0.249627 | 0.145143 |

---

## 3. Límites metodológicos que acompañan estos resultados

1. **Prueba interna restringida:** las 127 imágenes proceden de la reserva interna estratificada (semilla `20261025`), no del test oficial de DENTEX (cuyo esquema LabelMe utiliza otra taxonomía).
2. **Sesgo de selección:** tanto entrenamiento como validación y prueba interna contienen únicamente radiografías con al menos una etiqueta objetivo. Estas cifras **no establecen rendimiento sobre radiografías sanas o población general no seleccionada**.
3. **Independencia por paciente no verificada:** la fuente pública DENTEX no incluye identificadores de paciente; aunque se verificó ausencia de duplicados exactos y perceptuales, no puede garantizarse que distintas radiografías pertenezcan siempre a pacientes distintos.
4. **Uso previsto:** apoyo visual sujeto a revisión obligatoria por el odontólogo (`PENDIENTE` → `ACEPTADO` / `RECHAZADO` / `CORREGIDO`).
