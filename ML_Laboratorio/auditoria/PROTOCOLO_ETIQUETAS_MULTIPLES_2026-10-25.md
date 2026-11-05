# DENTEX — consulta del protocolo sobre etiquetas múltiples

Fecha de consulta: 25/10/2026. Alcance: revisión documental; sin nuevas radiografías, conversiones, correcciones ni entrenamiento.

## Conclusión para CORONYX

**Una combinación de diagnósticos no se considera error únicamente por aparecer bajo un mismo diente o por tener cuadros solapados.** La literatura de los organizadores contempla métodos para varias condiciones por diente. Sin embargo, no se encontró en las fuentes accesibles una regla explícita que valide, prohíba o priorice la combinación concreta **Caries + Deep Caries sobre una misma región**.

Por tanto, los 11 grupos señalados por nuestra auditoría permanecen como **casos para aclaración**, no como 11 errores clínicos confirmados. El registro duplicado exactamente salvo por ID sigue siendo un hallazgo técnico independiente.

## Fuentes primarias revisadas

1. [Artículo DENTEX, versión de 2023](https://arxiv.org/html/2305.19112v1), sección 2.1.1. Describe jerarquía, numeración FDI y anotación inicial con revisión profesional. No se encontró allí una regla específica para la combinación objetivo.
2. [Artículo DENTEX, versión de noviembre de 2025](https://arxiv.org/html/2305.19112v2), sección II-A y tabla III. Describe revisión profesional y métodos del reto que integran varias condiciones por diente mediante clasificadores independientes. Ese diseño de métodos no certifica individualmente nuestros casos.
3. [Artículo de HierarchicalDet](https://arxiv.org/html/2303.06500v3), sección 2.2. Su terminología de varias etiquetas incluye tres niveles: cuadrante, número de diente y diagnóstico. No debe interpretarse automáticamente como una autorización para cualquier par de diagnósticos.
4. [README de los organizadores](https://github.com/ibrahimethemhamamci/DENTEX) y [ficha del dataset](https://huggingface.co/datasets/ibrahimhamamci/DENTEX). Definen las cuatro categorías, pero no se encontró una instrucción específica sobre el par objetivo en el mismo cuadro.

La página del reto en Grand Challenge devolvió acceso denegado (403) a la herramienta de lectura. No se afirma haber revisado contenido adicional de esa página. No se contactó a los autores ni se publicó ninguna consulta.

## Diferencia entre tres situaciones

### 1. Registro exactamente repetido

En `train_377.png`, las anotaciones 2335 y 2337 coinciden en todos los campos salvo ID. Propuesta técnica: conservar una sola en una futura copia derivada, preservando la fuente y un registro de la decisión. No requiere elegir entre diagnósticos porque ambos registros tienen el mismo.

### 2. Varias categorías en una región

En `train_481.png`, el mismo cuadro tiene dos categorías diferentes. La documentación general sobre varias condiciones impide descartar una automáticamente. Tampoco demuestra que ambas sean correctas en ese caso concreto.

Queda pendiente aclarar si el esquema permite esa combinación, si representa condiciones distintas dentro de un mismo diente o si requiere una corrección de origen. No se presupone ninguna de esas explicaciones como cierta.

### 3. Misma identidad registrada y regiones separadas

En `train_440.png`, las marcas están en zonas distintas, aunque comparten cuadrante y número de diente en el JSON. Se requiere revisar su identidad/numeración. No se fusionan solo por compartir esos valores; no se cambian categorías clínicas por deducción.

## Reglas propuestas de preparación, todavía sin ejecutar

La especificación posterior y el registro por caso están en [reglas de preparación, versión 1](REGLAS_PREPARACION_ETIQUETAS_2026-10-25.md) y [registro de casos](REGISTRO_CASOS_2026-10-25.json). Su documentación no resuelve por sí sola las dudas clínicas ni aplica transformaciones.

- Mantener las etiquetas originales y la procedencia de cada registro.
- Considerar deduplicable un registro idéntico en todo salvo ID; documentar el registro omitido únicamente en la copia de trabajo.
- No aplicar la regla de duplicados a dos registros con diagnósticos diferentes.
- No convertir automáticamente caries en caries profunda, ni hacer que una categoría sustituya a la otra por severidad supuesta.
- Revisar las 11 identidades señaladas y distinguir coincidencia geométrica de coincidencia de número. No eliminar todos los grupos con varias condiciones ni sumar los distintos avisos como errores independientes.
- Antes de convertir al formato de un modelo, comprobar que su representación, entrenamiento y evaluación no pierdan etiquetas válidas. YOLOv8 nano continúa siendo candidato; esta revisión no aprueba una conversión directa ni selecciona una arquitectura final.
- Si no se logra aclarar un caso a tiempo, acordar una política explícita para la copia derivada antes del entrenamiento. No excluir una caja y conservar silenciosamente la misma región como fondo; cualquier exclusión, representación alternativa o limitación se documentará y evaluará por sus efectos. No se modificó ni excluyó ningún caso con esta consulta.

La pregunta pendiente para revisión de dominio es: **¿Caries y Deep Caries se pueden anotar simultáneamente en el mismo diente/cuadro bajo el protocolo concreto de DENTEX, y cómo deben conservarse al preparar el experimento?**

## Imágenes sin anotaciones

Esta consulta no confirmó una regla formal suficiente para declarar negativas fiables las 27 imágenes de entrenamiento y cuatro de validación sin anotaciones que contamos. Una discusión de usuarios del repositorio menciona 27 imágenes sin enfermedad, pero no se tomó ese comentario como aprobación del protocolo ni se verificó la pertenencia de quien responde al equipo anotador.

Siguen registradas como imágenes sin anotación, pendientes de interpretar según el protocolo. No se generó una etiqueta clínica de sano.

## Observaciones separadas que afectan a pendientes futuros

- **Licencia:** los artículos mencionan CC BY, mientras la distribución consultada en Hugging Face y el README declaran CC BY-NC-SA 4.0. La discrepancia se registra; no se relajaron las condiciones aplicadas a nuestra copia ni se concluyó permiso comercial. La licencia de un artículo no se usa por sí sola para cambiar la del archivo descargado.
- **Prueba final:** apareció una [consulta pública reciente sobre el mapeo de las etiquetas de test](https://github.com/ibrahimethemhamamci/DENTEX/issues/9). Es un reporte de un usuario, no una confirmación de los organizadores ni una auditoría nuestra. Refuerza el pendiente ya existente de comprobar la correspondencia real de etiquetas antes de comprometer una evaluación de ambas categorías. No se abrió el paquete de prueba ni se adoptó el mapeo sugerido.

## Estado al terminar esta consulta

**Actualización posterior sobre prueba final:** el usuario pidió definir la evaluación y se inspeccionó directamente el esquema de los 250 JSON del paquete publicado. La diferencia de formato/vocabulario quedó confirmada, pero la equivalencia clínica continúa sin verificar. Ver [plan de evaluación](../PLAN_EVALUACION_2026-10-25.md). No se obtuvieron ni visualizaron imágenes del test.

La revisión de documentación accesible está realizada. La coexistencia de varias condiciones es compatible con los métodos descritos, pero la combinación concreta y la política de los casos señalados siguen sin resolverse. Se puede continuar con la revisión visual variada y la planificación de archivos mientras se documentan estas dudas. La preparación final y el entrenamiento requieren una política registrada que preserve la información y explique las limitaciones.
