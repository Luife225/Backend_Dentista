# FEEDBACK DEL DOCENTE - OBSERVACIONES Y CORRECCIONES AL MODELO FÍSICO
## Proyecto CORONYX - Sprint 03 | Sustentación del Modelo Físico
**Fecha de sustentación:** 25 de septiembre de 2026  
**Docente:** Mg. Ing. Junior Alexander Neyra Gonzales  
**Alumno:** Luis Gamboa

---

## ÍNDICE DE OBSERVACIONES

| # | Tabla | Observación del Docente | Tipo |
|:---:|:---|:---|:---:|
| 1 | `clinica` | Renombrar estados a ABIERTO/CERRADO/MANTENIMIENTO (jornada laboral) | 🔧 Cambio |
| 2 | `rol` | Justificar por qué `descripcion` es TEXT y no VARCHAR | ❓ Respuesta |
| 3 | `usuario_clinica` | Explicar qué registran `rol_asignado_en` y `rol_asignado_por` | ❓ Respuesta |
| 4 | `usuario_clinica` | Renombrar `actualizado_en` → `fecha_actualizacion`, `creado_en` → `fecha_asignacion` | 🔧 Cambio |
| 5 | `usuario_clinica` | Mover FK `rol_asignado_por` arriba junto a las otras FKs | 🔧 Cambio |
| 6 | `cita` | ¿El administrador puede crear citas? Rol de recepcionista y su FK | ❓ Respuesta + 🔧 Cambio |
| 7 | `cita` | `proveedor_teleconsulta` → mejor un link de telemedicina | 🔧 Cambio |
| 8 | `cita` | `sala_referencia` es para cita presencial, aclarar | ❓ Respuesta |
| 9 | `cita` | Renombrar `creado_en`/`actualizado_en` → `fecha_creacion`/`fecha_actualizacion` | 🔧 Cambio |
| 10 | `version_atencion_clinica` | ¿Para qué sirve? ¿Es un historial clínico por cita? | ❓ Respuesta |
| 11 | JOINs | ¿Cómo llegar al odontograma por INNER JOIN? | ❓ Respuesta |
| 12 | `version_atencion_clinica` | ¿Qué guarda `recetas_prescripciones`? | ❓ Respuesta |
| 13 | **AVANCE 2** | Simular datos de ejemplo en cada tabla (muy importante) | ⭐ Tarea |
| 14 | `version_odontograma` | Renombrar `creado_en`/`confirmado_en` a nombres descriptivos | 🔧 Cambio |
| 15 | `hallazgo_odontograma` | ¿Los hallazgos deberían ser JSON en vez de columnas separadas? | ❓ Respuesta |
| 16 | `archivo_adjunto` | ¿Es necesario `peso_bytes`? La validación se hace en el controlador | ❓ Respuesta |
| 17 | `archivo_adjunto` | ¿Qué guarda `region_anatomica`? | ❓ Respuesta |
| 18 | `archivo_adjunto` | ¿Qué es `tipo_mime`? | ❓ Respuesta |
| 19 | **TODAS** | Renombrar `creado_en`/`actualizado_en` → `fecha_creacion`/`fecha_actualizacion` | 🔧 Cambio global |

---

## OBSERVACIÓN 1: `clinica.estado` — Cambiar los valores del CHECK

### ❌ Lo que dijimos en la sustentación:
> *"Se trabaja cuando la clínica está activa e inactiva"* → valores: `ACTIVO`, `INACTIVO`, `SUSPENDIDO`

### ✅ Lo que recomienda el docente:
El campo `estado` debe reflejar la **jornada laboral operativa** de la clínica, no su estado administrativo SaaS. Los 3 estados correctos son:

| Estado | Significado |
|:---|:---|
| `ABIERTO` | La clínica está operando en su jornada laboral normal, atendiendo pacientes. |
| `CERRADO` | La clínica finalizó su jornada del día o está en día no laborable (domingo, feriado). |
| `MANTENIMIENTO` | La clínica está temporalmente cerrada por mantenimiento de equipos, fumigación, remodelación, etc. |

### 📝 Cambio en DDL:
```sql
-- ANTES:
estado VARCHAR(16) NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'INACTIVO', 'SUSPENDIDO'))

-- DESPUÉS:
estado VARCHAR(16) NOT NULL DEFAULT 'ABIERTO' CHECK (estado IN ('ABIERTO', 'CERRADO', 'MANTENIMIENTO'))
```

---

## OBSERVACIÓN 2: `rol.descripcion` — ¿Por qué TEXT y no VARCHAR?

### ✅ Respuesta para el docente:
> *"Profesor, elegimos `TEXT` en vez de `VARCHAR(N)` porque la descripción de un rol es un campo narrativo de longitud libre donde el administrador puede redactar explicaciones detalladas sobre las responsabilidades y alcances del rol.*
>
> *En PostgreSQL, técnicamente `TEXT` y `VARCHAR` sin límite tienen el mismo rendimiento interno (ambos usan la misma estructura de almacenamiento `varlena`). La diferencia es semántica: `VARCHAR(100)` comunica que hay un límite de negocio claro (como un nombre de 100 caracteres), mientras que `TEXT` comunica que no existe un límite funcional predefinido. Como las descripciones de roles pueden variar desde una línea hasta un párrafo completo, `TEXT` es la elección más apropiada."*

> **TIP:** Si el docente insiste en que prefiere VARCHAR, se puede cambiar a `VARCHAR(500)` sin problema. Pero técnicamente en PostgreSQL no hay diferencia de rendimiento.

---

## OBSERVACIÓN 3: `usuario_clinica.rol_asignado_en` y `rol_asignado_por`

### ✅ Respuesta para el docente:

**`rol_asignado_en` (TIMESTAMPTZ):**
> *"Este campo registra la **fecha y hora exacta** en que se le otorgó el rol al colaborador dentro de esa clínica. Por ejemplo: el 15 de marzo de 2026 a las 09:30 AM, el administrador asignó el rol de ODONTOLOGO a la Dra. María García en la Sede San Isidro. Esa marca de tiempo queda grabada aquí para fines de auditoría y trazabilidad."*

**`rol_asignado_por` (UUID FK → usuario.id):**
> *"Este campo registra **QUIÉN fue la persona** (administrador) que ejecutó la asignación del rol. Es una llave foránea que apunta a `usuario.id`. De esta forma, si hay alguna disputa o investigación de seguridad, se puede rastrear exactamente qué administrador le dio permisos a quién y cuándo."*

### Ejemplo práctico:
| id | clinica_id | usuario_id | rol_id | rol_asignado_en | rol_asignado_por |
|:---|:---|:---|:---|:---|:---|
| uuid-001 | Sede Lima | Dra. María | ODONTOLOGO | 2026-03-15 09:30:00-05 | Admin Carlos (uuid del admin) |

---

## OBSERVACIÓN 4 y 19: Renombrar `creado_en`/`actualizado_en` en TODAS las tablas

### ✅ Recomendación del docente:
El docente prefiere nombres más descriptivos y en español formal:

| Nombre actual | Nombre recomendado por el docente |
|:---|:---|
| `creado_en` | `fecha_creacion` |
| `actualizado_en` | `fecha_actualizacion` |
| `confirmado_en` | `fecha_confirmacion` |
| `registrado_en` | `fecha_registro` |
| `leido_en` | `fecha_lectura` |

### Tablas afectadas y cambios:

| Tabla | Columna actual | Columna renombrada |
|:---|:---|:---|
| `clinica` | `creado_en`, `actualizado_en` | `fecha_creacion`, `fecha_actualizacion` |
| `usuario` | `creado_en`, `actualizado_en` | `fecha_creacion`, `fecha_actualizacion` |
| `rol` | `creado_en` | `fecha_creacion` |
| `usuario_clinica` | `creado_en`, `actualizado_en`, `rol_asignado_en` | `fecha_asignacion` (principal), `fecha_actualizacion` |
| `paciente` | `creado_en`, `actualizado_en` | `fecha_creacion`, `fecha_actualizacion` |
| `cita` | `creado_en`, `actualizado_en` | `fecha_creacion`, `fecha_actualizacion` |
| `atencion_clinica` | `creado_en`, `confirmado_en` | `fecha_creacion`, `fecha_confirmacion` |
| `version_atencion_clinica` | `registrado_en`, `confirmado_en` | `fecha_registro`, `fecha_confirmacion` |
| `version_odontograma` | `creado_en`, `confirmado_en` | `fecha_creacion`, `fecha_confirmacion` |
| `archivo_adjunto` | `creado_en` | `fecha_creacion` |
| `notificacion` | `creado_en`, `leido_en` | `fecha_creacion`, `fecha_lectura` |

---

## OBSERVACIÓN 5: Mover FK `rol_asignado_por` arriba con las otras FKs

### ✅ Lo que pide el docente:
Que las llaves foráneas estén agrupadas visualmente juntas al inicio de los atributos, antes de los campos operativos. El orden quedaría:

```
usuario_clinica:
  * id           : UUID <<PK>>
  # clinica_id   : UUID <<FK>>
  # usuario_id   : UUID <<FK>>
  # rol_id       : UUID <<FK>>
  # rol_asignado_por : UUID <<FK>>    ← SUBIÓ AQUÍ (junto a las FK)
  estado              : VARCHAR(16)
  fecha_asignacion    : TIMESTAMPTZ   ← renombrado de rol_asignado_en
  fecha_actualizacion : TIMESTAMPTZ   ← renombrado de actualizado_en
```

---

## OBSERVACIÓN 6: `cita` — Rol de recepcionista y quién puede crear citas

### ❓ Pregunta del docente:
> *"¿El administrador puede crear citas? ¿Va a tener secretaria o recepcionista? Porque en una cita presencial debería haber una recepcionista ya que el odontólogo no puede hacer todo eso."*

### ✅ Respuesta y solución:
> *"Profesor, sí. En nuestro sistema el rol de RECEPCIONISTA está definido en la tabla `rol` y se asigna a través de `usuario_clinica`. La recepcionista es quien principalmente crea y gestiona las citas presenciales del día a día.*
>
> *Para registrar quién creó cada cita (ya sea la recepcionista, el administrador, o incluso el propio paciente desde el portal web), necesitamos agregar un campo FK `creado_por_id` en la tabla `cita` que apunte a `usuario_clinica.id`, identificando al colaborador que registró la cita en el sistema."*

### 📝 Cambio propuesto en `cita`:
Agregar columna:
```sql
creado_por_id UUID NOT NULL REFERENCES usuario_clinica(id) ON DELETE RESTRICT
```

De esta forma:
- Si la recepcionista crea la cita → `creado_por_id` = id de la recepcionista en `usuario_clinica`
- Si el admin la crea → `creado_por_id` = id del admin en `usuario_clinica`
- Si el paciente la crea desde el portal → `creado_por_id` podría ser NULL o apuntar a una cuenta sistema

---

## OBSERVACIÓN 7: `cita.proveedor_teleconsulta` → Cambiar a link de telemedicina

### ❓ Pregunta del docente:
> *"¿Las citas van a ser de diversos proveedores como Zoom, Meet? Eso sería muy complejo. Mejor coloquen un link directo de la telemedicina."*

### ✅ Respuesta y solución:
> *"Tiene razón profesor. No vamos a soportar múltiples proveedores de videollamada. Trabajaremos con un solo proveedor de telemedicina integrado. Cambiaremos `proveedor_teleconsulta` por `enlace_teleconsulta` que almacenará directamente la URL/link de la videollamada generada por el sistema."*

### 📝 Cambio propuesto:
```sql
-- ANTES:
proveedor_teleconsulta VARCHAR(40) DEFAULT 'LIVEKIT'
sala_referencia VARCHAR(255)

-- DESPUÉS:
enlace_teleconsulta VARCHAR(500)   -- URL/link directo a la videollamada cuando la cita es VIRTUAL
consultorio VARCHAR(100)           -- Nombre o número del consultorio cuando la cita es PRESENCIAL
```

---

## OBSERVACIÓN 8: `cita.sala_referencia` — Aclaración

### ✅ Respuesta para el docente:
> *"Profesor, `sala_referencia` se refiere al **consultorio o sala física** donde se atenderá al paciente cuando la cita es presencial. Por ejemplo: 'Consultorio 3', 'Sala de Cirugía B'. No tiene relación con la teleconsulta."*

Con el cambio de la observación 7, este campo se renombraría a `consultorio` para que sea más claro.

---

## OBSERVACIÓN 10: `version_atencion_clinica` — ¿Para qué sirve?

### ✅ Respuesta para el docente:
> *"Profesor, `version_atencion_clinica` funciona como un **historial clínico inmutable por cada atención médica** (no exactamente por cada cita, sino por cada encuentro clínico).*
>
> *Cuando el odontólogo abre una atención (tabla `atencion_clinica`) para un paciente, comienza a llenar el diagnóstico, los procedimientos, las recetas, etc. Esa información se guarda como la **Versión 1**.*
>
> *Si después el doctor necesita corregir algo, agregar notas de evolución, o si el asistente de voz de IA genera una transcripción que necesita revisión, NO se modifica la versión 1. Se crea una **Versión 2** nueva que apunta a la versión 1 mediante `version_anterior_id`.*
>
> *Esto garantiza que nunca se pierde información clínica. Es como un historial de versiones tipo Git pero para documentos médicos legales. Si alguna vez hay un peritaje médico o demanda, se puede reconstruir exactamente qué escribió el doctor y cuándo."*

### Ejemplo del flujo:
```
Paciente Juan Pérez llega a su cita del 25/09/2026
    │
    ▼
Se abre: atencion_clinica (id=AC-001, estado=BORRADOR)
    │
    ├──► Versión 1 (numero_version=1, diagnostico="Caries en pieza 36", version_anterior_id=NULL)
    │        El doctor escribe su primera evaluación
    │
    ├──► Versión 2 (numero_version=2, diagnostico="Caries profunda en pieza 36 con compromiso pulpar",
    │    version_anterior_id=Versión 1)
    │        El doctor actualiza tras ver la radiografía
    │
    └──► Versión 3 (numero_version=3, esta_confirmado=true, firmado_por=Dr. López)
             El doctor firma digitalmente la versión definitiva
```

---

## OBSERVACIÓN 11: ¿Cómo llegar al odontograma por INNER JOIN?

### ✅ Respuesta para el docente:
> *"Para llegar desde cualquier tabla hasta el odontograma, se puede navegar a través de `clinica_id` y `paciente_id` que son las columnas compartidas."*

### Ruta de JOINs:

```sql
-- Obtener los hallazgos del odontograma de un paciente en una clínica específica:
SELECT 
    p.nombres, p.apellidos,
    vo.numero_version,
    ho.codigo_pieza_dental,
    ho.codigo_superficie_cara,
    ho.codigo_condicion,
    ho.nota_observacion
FROM paciente p
INNER JOIN version_odontograma vo 
    ON vo.paciente_id = p.id 
    AND vo.clinica_id = p.clinica_id
INNER JOIN hallazgo_odontograma ho 
    ON ho.version_odontograma_id = vo.id 
    AND ho.clinica_id = vo.clinica_id
WHERE p.clinica_id = 'uuid-de-la-clinica'
  AND p.id = 'uuid-del-paciente'
ORDER BY vo.numero_version DESC;
```

**La clave es que `clinica_id` actúa como el hilo conductor** que permite filtrar y unir todas las tablas de forma eficiente sin cruzar datos de otra sede.

---

## OBSERVACIÓN 12: ¿Qué guarda `recetas_prescripciones`?

### ✅ Respuesta para el docente:
> *"El campo `recetas_prescripciones` almacena el texto de los medicamentos recetados por el odontólogo al paciente durante esa versión de la atención clínica."*

### Ejemplo de contenido:
```
"1. Amoxicilina 500mg - 1 cápsula cada 8 horas por 7 días (vía oral)
 2. Ibuprofeno 400mg - 1 tableta cada 8 horas por 3 días (para el dolor)
 3. Clorhexidina 0.12% - Enjuague bucal 3 veces al día por 5 días"
```

Es de tipo `TEXT` porque la receta puede ser corta (1 medicamento) o larga (múltiples fármacos con dosificaciones detalladas).

---

## OBSERVACIÓN 13: ⭐ TAREA PARA AVANCE 2 — Simular datos en cada tabla

### Lo que pide el docente:
> *"Para la sustentación del avance 2, por cada tabla del modelo físico, mostrar una imagen con datos de ejemplo simulados que demuestren qué información se registraría en cada campo."*

A continuación se presentan datos de ejemplo para las 12 tablas:

---

#### Datos de ejemplo: `clinica`
| id | nombre | razon_social | zona_horaria | estado | fecha_creacion | fecha_actualizacion |
|:---|:---|:---|:---|:---|:---|:---|
| `a1b2c3d4-...` | Clínica Dental Coronyx - Sede Lima Centro | Coronyx Salud Dental S.A.C. | America/Lima | ABIERTO | 2026-01-15 08:00:00-05 | 2026-09-20 14:30:00-05 |
| `e5f6g7h8-...` | Clínica Dental Coronyx - Sede Miraflores | Coronyx Salud Dental S.A.C. | America/Lima | CERRADO | 2026-03-01 09:00:00-05 | 2026-09-25 18:00:00-05 |

---

#### Datos de ejemplo: `usuario`
| id | correo | clave_hash | nombres | apellidos | estado | correo_verificado_en | token_recuperacion_hash | fecha_creacion |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
| `u001-...` | carlos.admin@coronyx.pe | $2a$12$xK9v... | Carlos Eduardo | Ramírez Torres | ACTIVO | 2026-01-15 08:15:00-05 | NULL | 2026-01-15 08:00:00-05 |
| `u002-...` | dra.maria@gmail.com | $2a$12$mP7q... | María del Carmen | García López | ACTIVO | 2026-02-01 10:00:00-05 | NULL | 2026-01-20 09:00:00-05 |
| `u003-...` | recepcion.ana@coronyx.pe | $2a$12$jL3r... | Ana Lucía | Mendoza Ríos | ACTIVO | 2026-02-10 08:30:00-05 | NULL | 2026-02-10 08:00:00-05 |
| `u004-...` | juan.perez@hotmail.com | $2a$12$kN5t... | Juan Carlos | Pérez Sánchez | PENDIENTE | NULL | $2a$12$abc... | 2026-09-20 10:00:00-05 |

---

#### Datos de ejemplo: `rol`
| id | codigo | nombre | descripcion | permisos_json | activo | fecha_creacion |
|:---|:---|:---|:---|:---|:---|:---|
| `r001-...` | ADMIN_CLINICA | Administrador de Clínica | Gestión total: usuarios, reportes, configuración de sede | `["CLINICA_MANAGE","USER_MANAGE","REPORT_VIEW"]` | true | 2026-01-15 08:00:00-05 |
| `r002-...` | ODONTOLOGO | Odontólogo Especialista | Atención médica, historia clínica, odontograma e IA | `["PATIENT_VIEW","CLINICAL_WRITE","ODONTOGRAM_WRITE","AI_REVIEW"]` | true | 2026-01-15 08:00:00-05 |
| `r003-...` | RECEPCIONISTA | Recepcionista | Gestión de agenda, admisión de pacientes, creación de citas | `["PATIENT_MANAGE","APPOINTMENT_MANAGE"]` | true | 2026-01-15 08:00:00-05 |
| `r004-...` | PACIENTE | Paciente | Portal personal: ver citas, recetas e historial | `["MY_APPOINTMENTS_VIEW","MY_RECORDS_VIEW"]` | true | 2026-01-15 08:00:00-05 |

---

#### Datos de ejemplo: `usuario_clinica`
| id | clinica_id | usuario_id | rol_id | rol_asignado_por | estado | fecha_asignacion | fecha_actualizacion |
|:---|:---|:---|:---|:---|:---|:---|:---|
| `uc001-...` | Sede Lima (a1b2...) | Carlos (u001...) | ADMIN_CLINICA (r001...) | NULL (fue el primer admin) | ACTIVO | 2026-01-15 08:00:00-05 | 2026-01-15 08:00:00-05 |
| `uc002-...` | Sede Lima (a1b2...) | Dra. María (u002...) | ODONTOLOGO (r002...) | Carlos (u001...) | ACTIVO | 2026-02-01 10:15:00-05 | 2026-02-01 10:15:00-05 |
| `uc003-...` | Sede Lima (a1b2...) | Ana (u003...) | RECEPCIONISTA (r003...) | Carlos (u001...) | ACTIVO | 2026-02-10 08:30:00-05 | 2026-02-10 08:30:00-05 |
| `uc004-...` | Sede Miraflores (e5f6...) | Dra. María (u002...) | ODONTOLOGO (r002...) | Carlos (u001...) | ACTIVO | 2026-03-01 09:00:00-05 | 2026-03-01 09:00:00-05 |

> **Nótese:** La Dra. María aparece 2 veces: como ODONTOLOGO en Sede Lima y como ODONTOLOGO en Sede Miraflores. Eso demuestra el modelo multi-sede.

---

#### Datos de ejemplo: `paciente`
| id | clinica_id | usuario_id | tipo_documento | numero_documento | nombres | apellidos | fecha_nacimiento | telefono | correo | estado | alergias | antecedentes_medicos | medicamentos | medico_actualizo_id |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|
| `p001-...` | Sede Lima | Juan (u004...) | DNI | 72345678 | Juan Carlos | Pérez Sánchez | 1990-05-12 | +51 987654321 | juan.perez@hotmail.com | ACTIVO | Alergia a Penicilina | Hipertensión arterial controlada | Losartán 50mg diario | Dra. María (uc002...) |
| `p002-...` | Sede Lima | NULL | DNI | 45678901 | Rosa María | Flores Huamán | 1975-11-28 | +51 912345678 | NULL | ACTIVO | Ninguna conocida | Diabetes tipo 2 | Metformina 850mg | Dra. María (uc002...) |

> **Nótese:** La paciente Rosa Flores tiene `usuario_id = NULL` porque solo va a consulta presencial y no tiene cuenta en el portal web.

---

#### Datos de ejemplo: `cita`
| id | clinica_id | paciente_id | odontologo_id | creado_por_id | inicio_en | fin_en | modalidad | estado | motivo | enlace_teleconsulta | consultorio |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|
| `c001-...` | Sede Lima | Juan (p001...) | Dra. María (uc002...) | Ana recepcionista (uc003...) | 2026-09-25 10:00-05 | 2026-09-25 10:45-05 | PRESENCIAL | CONFIRMADA | Dolor en molar inferior izquierdo | NULL | Consultorio 2 |
| `c002-...` | Sede Lima | Rosa (p002...) | Dra. María (uc002...) | Ana recepcionista (uc003...) | 2026-09-25 11:00-05 | 2026-09-25 11:30-05 | VIRTUAL | PROGRAMADA | Control post-extracción | https://meet.coronyx.pe/sala-abc123 | NULL |

> **Nótese:** La cita presencial tiene `consultorio = "Consultorio 2"` y `enlace_teleconsulta = NULL`. La cita virtual es al revés.
> **Nótese:** `creado_por_id` apunta a Ana la recepcionista, demostrando que ella fue quien registró ambas citas.

---

#### Datos de ejemplo: `atencion_clinica`
| id | clinica_id | paciente_id | cita_id | odontologo_id | estado | fecha_creacion | fecha_confirmacion |
|:---|:---|:---|:---|:---|:---|:---|:---|
| `ac001-...` | Sede Lima | Juan (p001...) | Cita 1 (c001...) | Dra. María (uc002...) | CONFIRMADO | 2026-09-25 10:05-05 | 2026-09-25 10:40-05 |

---

#### Datos de ejemplo: `version_atencion_clinica`
| id | clinica_id | atencion_clinica_id | numero_version | version_anterior_id | diagnostico | procedimientos | recetas_prescripciones | indicaciones | esta_confirmado | firmado_por | fecha_registro |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|
| `vac001-...` | Sede Lima | AC-001 | 1 | NULL | Caries profunda en pieza 3.6, cara oclusal | Anestesia local, remoción de caries, obturación con resina Z350 | Ibuprofeno 400mg c/8h x 3 días | Dieta blanda 24h, no masticar del lado izquierdo | false | NULL | 2026-09-25 10:15-05 |
| `vac002-...` | Sede Lima | AC-001 | 2 | vac001-... | Caries profunda en pieza 3.6 con compromiso pulpar leve | Anestesia local, recubrimiento pulpar con MTA, obturación con resina Z350 | Ibuprofeno 400mg c/8h x 3 días; Amoxicilina 500mg c/8h x 7 días | Dieta blanda 48h, control en 7 días | true | Dra. María (uc002...) | 2026-09-25 10:35-05 |

> **Nótese:** La versión 2 apunta a la versión 1 mediante `version_anterior_id`. El diagnóstico se actualizó tras ver la radiografía. La versión 2 está firmada (`esta_confirmado=true`).

---

#### Datos de ejemplo: `version_odontograma`
| id | clinica_id | paciente_id | numero_version | version_anterior_id | estado | firmado_por | fecha_creacion | fecha_confirmacion |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
| `vo001-...` | Sede Lima | Juan (p001...) | 1 | NULL | CONFIRMADO | Dra. María (uc002...) | 2026-09-25 10:10-05 | 2026-09-25 10:38-05 |

---

#### Datos de ejemplo: `hallazgo_odontograma`
| id | clinica_id | version_odontograma_id | codigo_pieza_dental | codigo_superficie_cara | codigo_condicion | nota_observacion |
|:---|:---|:---|:---|:---|:---|:---|
| `ho001-...` | Sede Lima | vo001-... | 36 | OCLUSAL | CARIES | Caries profunda con compromiso pulpar leve |
| `ho002-...` | Sede Lima | vo001-... | 36 | MESIAL | CARIES | Extensión proximal de la lesión cariosa |
| `ho003-...` | Sede Lima | vo001-... | 14 | GENERAL | CORONA | Corona metal-cerámica en buen estado |
| `ho004-...` | Sede Lima | vo001-... | 46 | GENERAL | PIEZA_AUSENTE | Extracción previa hace 2 años |
| `ho005-...` | Sede Lima | vo001-... | 21 | VESTIBULAR | OBTURACION_RESINA | Restauración de resina antigua con buena integridad marginal |
| `ho006-...` | Sede Lima | vo001-... | 11 | LINGUAL | CALCULO | Presencia de cálculo supragingival leve |

> **Nótese:** Cada pieza dental tiene su propio registro. La pieza 36 tiene 2 hallazgos porque tiene caries en 2 caras distintas (oclusal y mesial).

---

#### Datos de ejemplo: `archivo_adjunto`
| id | clinica_id | paciente_id | subido_por_usuario_id | tipo_archivo | clave_almacenamiento | nombre_original | tipo_mime | peso_bytes | region_anatomica | es_visible_paciente | cita_id | ia_nombre_modelo | ia_hallazgos_json | ia_estado_revision | ia_revisado_por |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|
| `aa001-...` | Sede Lima | Juan (p001...) | Dra. María (u002...) | RADIOGRAFIA | clinicas/a1b2/pacientes/p001/rx-periapical-36.png | Rx_Periapical_Pieza36.png | image/png | 2458624 | Maxilar inferior izq - Zona molar | false | Cita 1 (c001...) | YOLOv8-DentalCaries-v2.1 | `{"detections":[{"label":"caries","bbox":[120,85,45,38],"confidence":0.94}]}` | ACEPTADO | Dra. María (uc002...) |

---

#### Datos de ejemplo: `notificacion`
| id | clinica_id | usuario_destinatario_id | tipo_evento | titulo | cuerpo_mensaje | estado | fecha_creacion | fecha_lectura |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|
| `n001-...` | Sede Lima | Juan (u004...) | CITA_RECORDATORIO | Recordatorio: Cita mañana 10:00 AM | Estimado Juan, le recordamos su cita con la Dra. García mañana 25/09 a las 10:00 AM en Consultorio 2. | LEIDO | 2026-09-24 18:00-05 | 2026-09-24 19:30-05 |
| `n002-...` | Sede Lima | Juan (u004...) | RESULTADO_IA | Resultado de análisis de radiografía | Se completó el análisis de IA de su radiografía periapical. Su odontólogo revisará los hallazgos. | ENVIADO | 2026-09-25 10:20-05 | NULL |

---

## OBSERVACIÓN 15: `hallazgo_odontograma` — ¿Mejor en JSON?

### ❓ Pregunta del docente:
> *"Los campos `codigo_pieza_dental`, `codigo_superficie_cara`, `codigo_condicion` ¿se van a considerar como historiales? ¿No sería mejor guardarlos en JSON? Porque por cada muela o diente se tendrá un registro de hallazgos."*

### ✅ Respuesta para el docente:
> *"Profesor, entiendo su punto. Efectivamente, por cada pieza dental se genera un registro individual en la tabla `hallazgo_odontograma`. Es decir, si un paciente tiene 28 dientes revisados, podrían existir 28 o más registros en esta tabla para una sola versión de odontograma.*
>
> *La razón por la que NO usamos JSON aquí es:*
> 1. **Consultas SQL directas:** Con columnas individuales podemos hacer queries como `SELECT * FROM hallazgo_odontograma WHERE codigo_condicion = 'CARIES'` para encontrar todas las caries de todos los pacientes. Con JSON, esas consultas serían más complejas y lentas.
> 2. **Índices eficientes:** PostgreSQL puede indexar `codigo_pieza_dental` directamente con B-Tree. Con JSON necesitaríamos índices GIN que son más pesados.
> 3. **Integridad referencial:** Con columnas y CHECK constraints, el motor de base de datos valida que `codigo_superficie_cara` solo sea 'MESIAL', 'DISTAL', etc. Con JSON, esa validación tendría que hacerse en la aplicación.
> 4. **Reporting y estadísticas:** Para el módulo de reportes (ej. "¿Cuántas caries se detectaron este mes?"), las columnas relacionales permiten `GROUP BY` y `COUNT` directos.*
>
> *Sin embargo, si en el futuro necesitamos guardar metadatos variables o no estructurados adicionales por hallazgo, la columna `nota_observacion` (TEXT) ya cubre ese caso.*"

---

## OBSERVACIÓN 16: `archivo_adjunto.peso_bytes` — ¿Es necesario?

### ❓ Pregunta del docente:
> *"¿Es necesario `peso_bytes`? La validación se hace a nivel de controlador (máximo 2MB en PDF)."*

### ✅ Respuesta para el docente:
> *"Profesor, tiene razón en que la validación de tamaño máximo se hace en el controlador de Spring Boot antes de aceptar la carga del archivo. Sin embargo, `peso_bytes` en la base de datos cumple funciones adicionales:*
> 1. **Auditoría y control de cuota:** Permite calcular cuánto espacio de almacenamiento S3 consume cada clínica (`SUM(peso_bytes) WHERE clinica_id = X`) para facturación SaaS.
> 2. **Reportes administrativos:** El administrador puede ver cuántas MB de radiografías tiene cada paciente.
> 3. **Historial:** Si un archivo se subió hace 2 años, saber cuánto pesó sin tener que consultar S3.
>
> *Dicho esto, si el docente considera que no es prioritario, podemos dejarlo como opcional (NULL) o retirarlo. Es una decisión de diseño.*"

---

## OBSERVACIÓN 17: `archivo_adjunto.region_anatomica` — ¿Qué guarda?

### ✅ Respuesta para el docente:
> *"`region_anatomica` guarda la **zona o área del cuerpo donde se tomó la imagen radiográfica**. Es un campo descriptivo de texto que permite al odontólogo especificar exactamente qué parte de la boca corresponde la placa."*

### Ejemplos de valores:
| Valor de `region_anatomica` | Descripción |
|:---|:---|
| `"Maxilar superior - Zona anterior (incisivos)"` | Radiografía periapical de los dientes frontales superiores |
| `"Maxilar inferior izquierdo - Zona molar (piezas 36-38)"` | Radiografía periapical de las muelas inferiores izquierdas |
| `"Panorámica completa"` | Radiografía panorámica de toda la arcada dental |
| `"ATM bilateral"` | Articulación temporomandibular, para diagnóstico de bruxismo |

---

## OBSERVACIÓN 18: `archivo_adjunto.tipo_mime` — ¿Qué es?

### ✅ Respuesta para el docente:
> *"Profesor, `tipo_mime` (también llamado **MIME type** o **Media Type**) es un estándar de Internet que indica el **formato del archivo digital**. Es como la 'extensión' del archivo pero a nivel de protocolo web.*
>
> *Lo necesitamos porque cuando el sistema descarga o muestra una radiografía en el navegador del odontólogo, debe saber si es una imagen PNG, un PDF o un archivo DICOM para renderizarlo correctamente."*

### Ejemplos de valores:
| Archivo | `tipo_mime` | Para qué sirve |
|:---|:---|:---|
| Radiografía PNG | `image/png` | El navegador sabe que debe mostrar una imagen |
| Radiografía JPEG | `image/jpeg` | El navegador sabe que debe mostrar una imagen JPEG |
| Consentimiento en PDF | `application/pdf` | El navegador sabe que debe abrir el visor de PDF |
| Archivo DICOM (TAC) | `application/dicom` | El sistema invoca un visor DICOM especializado |

---

## RESUMEN DE TODOS LOS CAMBIOS A APLICAR EN EL ESQUEMA

### Cambios globales (todas las tablas):
- [ ] Renombrar `creado_en` → `fecha_creacion`
- [ ] Renombrar `actualizado_en` → `fecha_actualizacion`
- [ ] Renombrar `confirmado_en` → `fecha_confirmacion`
- [ ] Renombrar `registrado_en` → `fecha_registro`
- [ ] Renombrar `leido_en` → `fecha_lectura`

### Cambios por tabla:
| Tabla | Cambio |
|:---|:---|
| `clinica` | `estado` CHECK: `('ABIERTO','CERRADO','MANTENIMIENTO')`, DEFAULT `'ABIERTO'` |
| `usuario_clinica` | Mover `rol_asignado_por` junto a las FK; renombrar `rol_asignado_en` → `fecha_asignacion` |
| `cita` | Agregar `creado_por_id UUID FK → usuario_clinica.id`; renombrar `proveedor_teleconsulta` → `enlace_teleconsulta VARCHAR(500)`; renombrar `sala_referencia` → `consultorio VARCHAR(100)`; eliminar `teleconsulta_estado` (simplificar) |
| Todas | Aplicar renombramientos de fechas |

> **IMPORTANTE:** Estos cambios deben aplicarse de forma sincronizada en los 4 archivos: `informe-sprint-03.md`, `diagrama-fisico-interactivo.html`, `diagrama-fisico-coronyx.puml` y `supabase_schema_coronyx.sql`.
