# Continuar desde la laptop

El ML está dentro de **la rama `mvp-ml` del backend**, en la carpeta `ML_Laboratorio`. Es la única rama nueva para este avance. Tu rama habitual continúa siendo la de la aplicación. El frontend tiene `mvp-ml` reservada para su futura integración; todavía no incluye pantallas de ML nuevas.

## 1. Descargar la rama

Si ya tienes el backend clonado, abre una terminal en su carpeta raíz:

```powershell
git fetch origin --prune
git switch --track origin/mvp-ml
```

El segundo comando es para crear la rama local la primera vez. Si ya existe:

```powershell
git switch mvp-ml
git pull --ff-only origin mvp-ml
```

Si aún no tienes el backend:

```powershell
git clone --branch mvp-ml https://github.com/Luife225/Backend_Dentista.git
cd Backend_Dentista
```

Para alternar entre tu rama habitual y el avance con ML, desde la raíz del backend:

```powershell
git switch feature/sebastian
git switch mvp-ml
```

Usa el nombre de tu rama habitual si en tu laptop es diferente de `feature/sebastian`. Tus ramas existentes conservan su historial. Las dos modificaciones documentales que había sin commit en la PC original se conservaron allí y no se incorporaron a la rama de ML.

Antes de cambiar de rama, guarda tus propios cambios con el mecanismo que ya uses. Sal de `ML_Laboratorio` antes de volver a tu rama habitual, porque esa carpeta solo existe en la rama de ML.

En el frontend puedes obtener `mvp-ml` con los mismos comandos. Su integración con el detector continúa pendiente.

## 2. Comprobar el código sin descargar radiografías

Desde la raíz del backend, con Python 3.12 disponible en Windows:

```powershell
cd ML_Laboratorio
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-preparacion.txt
.\.venv\Scripts\python.exe -m unittest discover -s tests -v
.\.venv\Scripts\python.exe -m herramientas.estado_mvp
```

Se fija [Pillow 12.3.0](https://pypi.org/project/pillow/12.3.0/), la versión utilizada en estas comprobaciones. Este entorno es solo de preparación; no instala PyTorch ni YOLO, ni descarga pesos. Puedes revisar las reglas, particiones, informes y las pruebas desde la laptop sin GPU y sin las imágenes grandes.

El estado distinguirá los resultados históricos de auditoría de las imágenes realmente presentes en ese equipo. En un clon nuevo es normal que todavía no haya radiografías ni copia local preparada.

## 3. Obtener y preparar los datos en ese equipo

GitHub contiene el código, la política, las listas de particiones y aproximadamente 2,06 MB de anotaciones e índices públicos. **Las 755 radiografías, unos 3,09 GB, no se suben a GitHub.** La copia preparada ocupa espacio adicional. Los comandos comprueban el espacio y mantienen un margen de 10 GB libres; no eliminan archivos para conseguirlo.

Para obtener únicamente las imágenes previstas desde la distribución oficial fijada:

```powershell
.\.venv\Scripts\python.exe -m herramientas.adquirir_dentex --descargar --trabajadores 2
.\.venv\Scripts\python.exe -m herramientas.preparar_dataset --aplicar
.\.venv\Scripts\python.exe -m herramientas.verificar_preparacion
```

La descarga puede reanudarse repitiendo el primer comando; verifica cada archivo existente. No baja todo el ZIP de entrenamiento ni las radiografías del test público. La preparación usa las mismas particiones versionadas: 508 para entrenar, 44 para validar y 127 para prueba interna.

Como alternativa, puedes transportar por un medio propio la carpeta de **originales** `ML_Laboratorio/data/dentex_raw/7b27ccc8e342dcb774f69adc6ca5e6c09fefce93/` al mismo lugar relativo del clon. Después ejecuta los tres comandos para revalidar lo existente y reconstruir la copia local. No transportes el entorno `.venv`; se crea por separado en cada equipo. La descarga no es necesaria si todos los originales ya están presentes y coinciden.

## 4. Qué continúa después

La preparación está hecha para un experimento académico restringido a imágenes con etiquetas objetivo. Se excluyeron imágenes completas con dudas sin resolver; no se certificaron ni corrigieron diagnósticos. No se ha demostrado independencia por paciente ni rendimiento en una población no seleccionada.

El siguiente paso es seleccionar el modelo y comprobar licencias y entorno GPU. Entrenamiento, evaluación e inferencia real siguen pendientes. Los detalles de la preparación están en [la continuidad del 28/10](CONTINUIDAD_MVP_2026-10-28.md).
