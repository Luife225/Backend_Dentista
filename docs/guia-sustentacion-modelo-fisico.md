# GUÍA COMPLETA PARA LA SUSTENTACIÓN DEL MODELO FÍSICO DE BASE DE DATOS
## Proyecto CORONYX - Sistema de Gestión Odontológica Multi-Clínica con Inteligencia Artificial
**Autor:** Luis Gamboa  
**Asignatura / Cátedra:** Taller de Proyectos de Software / Ingeniería de Datos  
**Docente:** Mg. Ing. Junior Alexander Neyra Gonzales  

---

## 1. PITCH INICIAL (INTRODUCCIÓN DE 60 SEGUNDOS)

> *"Buenos días docente y compañeros. El día de hoy presento la sustentación técnica del modelo físico de base de datos relacional para la plataforma **CORONYX**.*
> 
> *CORONYX es una solución SaaS **Multi-Inquilino (Multi-Tenant)** de alta concurrencia diseñada para clínicas odontológicas que integra dos innovaciones de Inteligencia Artificial: un **asistente de voz clínico** para el llenado hands-free de historias clínicas y un modelo de **visión artificial (YOLOv8)** para la detección temprana de patologías y caries en radiografías.*
> 
> *El modelo físico consta de **12 tablas normalizadas en Tercera Forma Normal (3FN)** con claves universales **UUID v4**, control de acceso basado en roles (**RBAC granular**), y un esquema de **inmutabilidad y versionamiento legal** que garantiza la trazabilidad forense exigida por las normativas de salud."*

---

## 2. CLASIFICACIÓN DE ENTIDADES: PRINCIPALES VS. SECUNDARIAS

El esquema se divide metodológicamente según su ciclo de vida y jerarquía de dependencia:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   JERARQUÍA DEL MODELO RELACIONAL                      │
└────────────────────────────────────────────────────────────────────────┘

 [1. MAESTRAS / FUERTES]         clinica ─── rol ─── usuario
                                    │                  │
 [2. MEMBRESÍA CONTEXTUAL]          └──── usuario_clinica ────┘
                                               │
 [3. TRANSACCIONALES DOMINIO]       paciente ──┼── cita
                                       │       │    │
 [4. ACTO MÉDICO Y REGISTROS]          ├─── atencion_clinica (Cabecera)
                                       │       │
                                       │       └─── version_atencion_clinica (Inmutable)
                                       └─── version_odontograma (Inmutable)
                                               │
 [5. DETALLE Y EXTENSIONES]                    ├─── hallazgo_odontograma (Piezas FDI)
                                               ├─── archivo_adjunto (Radiografías / IA)
                                               └─── notificacion (Mensajería)
```

### A. Entidades Principales (Fuertes o Maestras)
No dependen existencialmente de otras para tener sentido en el ecosistema:
1. **`clinica`**: Es la entidad **raíz del tenant**. Delimita las fronteras de aislamiento de datos. Si una clínica desaparece o se suspende, se bloquea su entorno operativo.
2. **`usuario`**: Representa a la **persona o cuenta de acceso global** a la plataforma (odontólogo, recepcionista, paciente, administrador). Existe independientemente de a qué clínica esté vinculada.
3. **`rol`**: Catálogo transversal de facultades del sistema (`ADMIN_CLINICA`, `ODONTOLOGO`, `RECEPCIONISTA`, `PACIENTE`). Mantiene la matriz de permisos serializada en `JSONB`.

### B. Entidad de Asociación y Contexto
4. **`usuario_clinica`**: Es la tabla **pivote multi-sede**. Un usuario no tiene un rol "global"; su rol es contextual. Puede ser odontólogo en la sede San Isidro y administrador en la sede Miraflores. Esta tabla resuelve la relación muchos a muchos con atributos de estado y auditoría.

### C. Entidades Principales de Dominio Operativo (Transaccionales)
5. **`paciente`**: Expediente central del cliente adscrito a una sede dental. Absorbe el perfil administrativo y los datos médicos críticos (alergias, antecedentes).
6. **`cita`**: Transacción de agendamiento omnicanal (presencial o teleconsulta virtual WebRTC). Conecta a la clínica, al paciente y al odontólogo tratante.
7. **`atencion_clinica`**: Representa el **encuentro médico (acto médico formal)** derivado de una cita o consulta de emergencia. Actúa como cabecera del historial.

### D. Entidades Secundarias / Hijas (Detalle e Inmutabilidad)
8. **`version_atencion_clinica`**: Registro legal inmutable del acto médico. Cada edición o procesamiento de voz genera una versión numerada consecutiva; jamás se realiza un `UPDATE` destructivo sobre diagnósticos pasados.
9. **`version_odontograma`**: Estado de la dentadura del paciente en un instante cronológico específico.
10. **`hallazgo_odontograma`**: Detalle atómico de cada pieza dental (nomenclatura internacional FDI 11 al 85), superficie y patología encontrada.
11. **`archivo_adjunto`**: Metadatos de imágenes radiográficas (DICOM, PNG) y resultados de inferencia IA (bounding boxes y confidencias).
12. **`notificacion`**: Bandeja de entrada transaccional y avisos automáticos dirigidos a cada usuario.

---

## 3. ANÁLISIS DETALLADO TABLA POR TABLA: CAMPOS, TIPOS Y JUSTIFICACIÓN

---

### TABLA 1: `clinica` (7 columnas)
> **Propósito:** Representa la sede u organización dental registrada en el modelo multi-inquilino. Es la base del aislamiento lógico de datos.

| Campo | Tipo | Restricción | ¿Por qué existe y qué almacena exactamente? |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Identificador único universal autogenerado con `gen_random_uuid()`. Se usa UUID en vez de enteros para evitar ataques de enumeración (impedir que alguien sepa cuántas clínicas hay o acceda iterando `/clinica/1`, `/clinica/2`). |
| `nombre` | `VARCHAR(160)` | `NOT NULL` | Nombre comercial de la sede (ej. *"Clínica Dental Coronyx - Sede San Isidro"*). Longitud de 160 caracteres para admitir denominaciones comerciales completas. |
| `razon_social` | `VARCHAR(200)` | `NULL` | Razón social legal inscrita en la administración tributaria (SUNAT) para efectos de comprobantes de pago y auditoría fiscal. |
| `zona_horaria` | `VARCHAR(64)` | `NOT NULL, DEFAULT 'America/Lima'` | Zona horaria IANA. **Vital en telemedicina:** cuando se programan citas en línea entre pacientes y odontólogos en distintas zonas o servidores en la nube, el sistema convierte fechas UTC a la hora local exacta de la sede. |
| `estado` | `VARCHAR(16)` | `NOT NULL, CHECK` | Estado operativo de la sede según su jornada laboral: `'ABIERTO'`, `'CERRADO'`, `'MANTENIMIENTO'`. |
| `fecha_creacion` | `TIMESTAMPTZ` | `NOT NULL` | Marca de tiempo UTC (`clock_timestamp()`) de cuándo se dio de alta la clínica. |
| `fecha_actualizacion` | `TIMESTAMPTZ` | `NOT NULL` | Marca de tiempo UTC de la última modificación en la configuración de la clínica. |

---

### TABLA 2: `usuario` (12 columnas)
> **Propósito:** Almacena la identidad de autenticación y credenciales universales de cualquier ser humano que ingrese al software.

| Campo | Tipo | Restricción | ¿Por qué existe y qué almacena exactamente? |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Clave primaria universal para vincular tokens JWT y sesiones seguras. |
| `correo` | `VARCHAR(254)` | `UNIQUE, NOT NULL` | Correo electrónico de acceso. 254 caracteres cumple con el estándar RFC 5321. Índice único para evitar cuentas duplicadas. |
| `clave_hash` | `VARCHAR(255)` | `NOT NULL` | Hash de la contraseña generado mediante algoritmos seguros (BCrypt o Argon2). **Nunca se guarda la clave en texto plano**. |
| `nombres` | `VARCHAR(100)` | `NOT NULL` | Nombres del usuario para personalización de interfaces y reportes. |
| `apellidos` | `VARCHAR(100)` | `NOT NULL` | Apellidos del usuario para emisión de recetas y firmas. |
| `estado` | `VARCHAR(20)` | `NOT NULL, CHECK` | Estado de la cuenta: `'PENDIENTE'` (requiere confirmar correo), `'ACTIVO'` (operativo), `'DESHABILITADO'` (bloqueado por seguridad). |
| `correo_verificado_en`| `TIMESTAMPTZ` | `NULL` | Fecha/hora en que el usuario confirmó su correo mediante el enlace de activación. |
| `token_recuperacion_hash` | `VARCHAR(255)` | `NULL` | Hash del token de restablecimiento de contraseña enviado cuando el usuario presiona "¿Olvidaste tu contraseña?". Se guarda el hash para que incluso si leen la base de datos, no puedan suplantar el token. |
| `token_recuperacion_expira_en` | `TIMESTAMPTZ` | `NULL` | Límite temporal de vigencia del enlace (ej. 15 minutos). |
| `token_recuperacion_usado_en` | `TIMESTAMPTZ` | `NULL` | Registro para invalidar el token una vez consumido y evitar ataques de repetición. |
| `fecha_creacion` | `TIMESTAMPTZ` | `NOT NULL` | Auditoría de creación de la cuenta. |
| `fecha_actualizacion` | `TIMESTAMPTZ` | `NOT NULL` | Auditoría de última modificación de perfil o clave. |

---

### TABLA 3: `rol` (7 columnas)
> **Propósito:** Catálogo oficial del modelo de seguridad RBAC (*Role-Based Access Control*).

| Campo | Tipo | Restricción | ¿Por qué existe y qué almacena exactamente? |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Identificador único del rol. |
| `codigo` | `VARCHAR(40)` | `UNIQUE, NOT NULL` | Llave textual de programación (ej. `'ADMIN_CLINICA'`, `'ODONTOLOGO'`, `'RECEPCIONISTA'`, `'PACIENTE'`). Utilizado en anotaciones Spring Security `@PreAuthorize("hasAuthority('ODONTOLOGO')")`. |
| `nombre` | `VARCHAR(80)` | `NOT NULL` | Nombre descriptivo para mostrar en UI (ej. *"Odontólogo Especialista"*). |
| `descripcion` | `TEXT` | `NULL` | Explicación funcional de las responsabilidades asignadas al rol (en PostgreSQL `TEXT` y `VARCHAR` tienen el mismo desempeño interno sin límite artificial). |
| `permisos_json` | `JSONB` | `NOT NULL, DEFAULT '[]'` | **Innovación arquitectural:** matriz de permisos atómicos (`["PATIENT_VIEW", "APPOINTMENT_WRITE", "ODONTOGRAM_WRITE", "AI_REVIEW"]`). Permite agregar permisos dinámicos sin alterar la estructura física de la base de datos. |
| `activo` | `BOOLEAN` | `NOT NULL, DEFAULT true` | Interruptor para habilitar o retirar roles del catálogo. |
| `fecha_creacion` | `TIMESTAMPTZ` | `NOT NULL` | Registro temporal de inserción. |

---

### TABLA 4: `usuario_clinica` (8 columnas)
> **Propósito:** Resuelve la membresía multi-sede. Asigna a un usuario un rol dentro de una clínica específica.

| Campo | Tipo | Restricción | ¿Por qué existe y qué almacena exactamente? |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Llave primaria de la membresía. |
| `clinica_id` | `UUID` | `FK (clinica.id)` | Sede en la cual el usuario ejercerá funciones. |
| `usuario_id` | `UUID` | `FK (usuario.id)` | Usuario al que se le concede la membresía. |
| `rol_id` | `UUID` | `FK (rol.id)` | Rol específico otorgado en esa sede. |
| `rol_asignado_por` | `UUID` | `FK (usuario.id)` | **Auditoría de seguridad:** registra qué administrador otorgó los permisos a este colaborador (colocado arriba junto a las FKs). |
| `estado` | `VARCHAR(16)` | `NOT NULL, CHECK` | `'ACTIVO'` o `'INACTIVO'`. Permite cesar a un empleado en una sede sin eliminar su cuenta ni su historial de atenciones previas. |
| `fecha_asignacion` | `TIMESTAMPTZ` | `NOT NULL` | Fecha y hora exacta de la asignación del rol. |
| `fecha_actualizacion` | `TIMESTAMPTZ` | `NOT NULL` | Auditoría de cambios en la asignación. |
| *Restricción UNIQUE* | `(clinica_id, usuario_id)` | `CONSTRAINT` | Impide que un usuario tenga duplicada la misma membresía en la misma sede. |

---

### TABLA 5: `paciente` (17 columnas)
> **Propósito:** Maestro del paciente en una sede. Almacena identificación, contacto y perfil médico basal.

| Campo | Tipo | Restricción | ¿Por qué existe y qué almacena exactamente? |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Llave primaria del paciente. |
| `clinica_id` | `UUID` | `FK (clinica.id)` | Garantiza que cada paciente pertenezca a la clínica que lo atiende (multi-tenant estricto). |
| `usuario_id` | `UUID` | `FK (usuario.id), NULL` | Opcional. Si el paciente se registra en el **portal web/móvil** para ver sus citas y recetas, se enlaza a su cuenta de `usuario`. Si no tiene app, permanece `NULL`. |
| `medico_actualizo_id` | `UUID` | `FK (usuario_clinica.id)`| Odontólogo o profesional que actualizó por última vez la ficha médica general (agrupado arriba junto a las FKs). |
| `tipo_documento` | `VARCHAR(20)` | `CHECK` | Tipo de documento legal: `'DNI'`, `'CE'`, `'PASAPORTE'`. Por defecto `'DNI'`. |
| `numero_documento` | `VARCHAR(30)` | `NULL` | Número de cédula o documento de identidad del paciente. |
| `nombres` | `VARCHAR(100)` | `NOT NULL` | Nombres del paciente. |
| `apellidos` | `VARCHAR(100)` | `NOT NULL` | Apellidos del paciente. |
| `fecha_nacimiento` | `DATE` | `NULL` | Fecha de nacimiento para cálculo dinámico de edad (pediatría odontológica vs. adultos). |
| `telefono` | `VARCHAR(30)` | `NULL` | Teléfono móvil para recordatorios de citas por WhatsApp / SMS. |
| `correo` | `VARCHAR(254)` | `NULL` | Correo de contacto para envío de confirmaciones y recetas en PDF. |
| `estado` | `VARCHAR(16)` | `NOT NULL, CHECK` | `'ACTIVO'`, `'INACTIVO'`, `'ARCHIVADO'` (pacientes dados de baja médica). |
| `alergias` | `TEXT` | `NULL` | Alergias farmacológicas críticas (ej. *Penicilina, Látex, Anestesia local*). De lectura obligatoria para el odontólogo antes de prescribir. |
| `antecedentes_medicos`| `TEXT` | `NULL` | Condiciones sistémicas (ej. *Diabetes, Hipertensión arterial, Coagulopatías, Embarazo*). |
| `medicamentos` | `TEXT` | `NULL` | Medicación habitual que toma el paciente para evitar interacciones medicamentosas. |
| `fecha_creacion` | `TIMESTAMPTZ` | `NOT NULL` | Fecha de ingreso como paciente. |
| `fecha_actualizacion` | `TIMESTAMPTZ` | `NOT NULL` | Fecha de última edición de datos basales. |
| *Restricción UNIQUE* | `(clinica_id, id)` | `CONSTRAINT` | Aislamiento multi-tenant por sede. |

---

### TABLA 6: `cita` (15 columnas)
> **Propósito:** Gestión de la agenda médica omnicanal (presencial en sillón dental y teleconsultas virtuales).

| Campo | Tipo | Restricción | ¿Por qué existe y qué almacena exactamente? |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Identificador de la cita. |
| `clinica_id` | `UUID` | `FK (clinica.id)` | Sede donde se desarrollará o registrará la cita. |
| `paciente_id` | `UUID` | `FK (paciente.id)` | Paciente agendado. |
| `odontologo_id` | `UUID` | `FK (usuario_clinica.id)`| Odontólogo profesional asignado para brindar la atención. |
| `creado_por_id` | `UUID` | `FK (usuario_clinica.id)`| Usuario que agendó la cita (recepcionista, administrador o el propio odontólogo). |
| `inicio_en` | `TIMESTAMPTZ` | `NOT NULL` | Fecha y hora exacta de inicio del turno. |
| `fin_en` | `TIMESTAMPTZ` | `NOT NULL, CHECK` | Fecha y hora de finalización (`fin_en > inicio_en`). |
| `modalidad` | `VARCHAR(12)` | `NOT NULL, CHECK` | `'PRESENCIAL'` (sillón dental) o `'VIRTUAL'` (teleconsulta). |
| `estado` | `VARCHAR(20)` | `NOT NULL, CHECK` | Flujo de vida: `'PROGRAMADA'`, `'CONFIRMADA'`, `'CANCELADA'`, `'EN_ATENCION'`, `'FINALIZADA'`. |
| `estado_asistencia` | `VARCHAR(16)` | `CHECK` | Auditoría de asistencia: `'ASISTIO'` o `'NO_SHOW'` (no se presentó). |
| `motivo` | `VARCHAR(500)` | `NULL` | Motivo de consulta manifestado por el paciente (ej. *"Dolor agudo en molar superior"*). |
| `enlace_teleconsulta` | `VARCHAR(500)` | `NULL` | Enlace URL de conexión de la videollamada para citas en modalidad virtual. |
| `consultorio` | `VARCHAR(100)` | `NULL` | Nombre o número de consultorio / sillón para citas en modalidad presencial. |
| `fecha_creacion` | `TIMESTAMPTZ` | `NOT NULL` | Fecha de creación del turno. |
| `fecha_actualizacion` | `TIMESTAMPTZ` | `NOT NULL` | Fecha de última modificación o reprogramación. |

---

### TABLA 7: `atencion_clinica` (8 columnas)
> **Propósito:** Cabecera del encuentro médico formal. Conecta la cita con el historial de versiones clínicas.

| Campo | Tipo | Restricción | ¿Por qué existe y qué almacena exactamente? |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Clave del acto médico. |
| `clinica_id` | `UUID` | `FK (clinica.id)` | Sede de atención. |
| `paciente_id` | `UUID` | `FK (paciente.id)` | Paciente atendido. |
| `cita_id` | `UUID` | `FK (cita.id), UNIQUE, NULL`| Enlace 1 a 0..1 con la cita que originó la atención. Es `NULL` si la atención fue de emergencia directa sin cita previa. |
| `odontologo_id` | `UUID` | `FK (usuario_clinica.id)`| Odontólogo responsable directo de la atención. |
| `estado` | `VARCHAR(16)` | `NOT NULL, CHECK` | `'BORRADOR'` (atención abierta en proceso) o `'CONFIRMADO'` (atención cerrada). |
| `fecha_creacion` | `TIMESTAMPTZ` | `NOT NULL` | Fecha y hora en que se abrió la sesión médica. |
| `fecha_confirmacion` | `TIMESTAMPTZ` | `NULL` | Fecha y hora del cierre y firma médica. |

---

### TABLA 8: `version_atencion_clinica` (15 columnas)
> **Propósito:** Registro inmutable de la historia clínica. Almacena anamnesis, evolución, recetas y el borrador de voz procesado por IA.

| Campo | Tipo | Restricción | ¿Por qué existe y qué almacena exactamente? |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Identificador de la versión. |
| `clinica_id` | `UUID` | `FK (clinica.id)` | Sede de custodia legal. |
| `atencion_clinica_id`| `UUID` | `FK (atencion_clinica.id)`| Cabecera de la atención a la cual pertenece. |
| `version_anterior_id`| `UUID` | `FK (version_atencion_clinica.id)`| **Puntero auto-referencial (cadena de versiones):** apunta a la versión anterior rectificada. Garantiza la auditoría forense legal. |
| `firmado_por` | `UUID` | `FK (usuario_clinica.id)`| Odontólogo colegiado que rubrica legalmente la versión de la historia clínica (agrupado arriba junto a las FKs). |
| `numero_version` | `INTEGER` | `NOT NULL, CHECK (>0)` | Número secuencial (1, 2, 3...) de la versión. |
| `diagnostico` | `TEXT` | `NULL` | Diagnóstico odontológico emitido según CIE-10 / CIE-11. |
| `procedimientos` | `TEXT` | `NULL` | Detalle técnico de las maniobras realizadas (ej. *"Profilaxis, obturación con resina compuesta en pieza 3.6"*). |
| `recetas_prescripciones`| `TEXT` | `NULL` | Fármacos recetados (dosis, posología y duración). |
| `indicaciones` | `TEXT` | `NULL` | Instrucciones post-operatorias para el paciente (ej. *"Dieta blanda por 48 horas, aplicar hielo"*). |
| `evolucion` | `TEXT` | `NULL` | Nota médica sobre la respuesta del paciente a tratamientos previos. |
| `texto_pendiente_revision`| `TEXT` | `NULL` | **Innovación IA (Asistente de Voz):** transcripción procesada y estructurada por el LLM en tiempo real. Permanece aquí hasta que el odontólogo la lea, la ajuste y la confirme. |
| `esta_confirmado` | `BOOLEAN` | `NOT NULL, DEFAULT false` | `false` si es un borrador generado por voz; `true` cuando el odontólogo aprueba formalmente. |
| `fecha_registro` | `TIMESTAMPTZ` | `NOT NULL` | Fecha de captura de la versión. |
| `fecha_confirmacion` | `TIMESTAMPTZ` | `NULL` | Fecha de estampado de la firma digital. |

---

### TABLA 9: `version_odontograma` (9 columnas)
> **Propósito:** Representación inmutable del estado anatómico bucal a lo largo del tiempo.

| Campo | Tipo | Restricción | ¿Por qué existe y qué almacena exactamente? |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Identificador de la versión del odontograma. |
| `clinica_id` | `UUID` | `FK (clinica.id)` | Sede de atención. |
| `paciente_id` | `UUID` | `FK (paciente.id)` | Paciente al que corresponde el mapeo dental. |
| `version_anterior_id`| `UUID` | `FK (version_odontograma.id)`| Puntero auto-referencial que enlaza con el estado previo del odontograma. |
| `firmado_por` | `UUID` | `FK (usuario_clinica.id)`| Odontólogo que valida y firma el odontograma (agrupado arriba junto a las FKs). |
| `numero_version` | `INTEGER` | `NOT NULL, CHECK (>0)` | Versión correlativa del odontograma (1: inicial/ingreso, 2: evolutivo, 3: post-quirúrgico). |
| `estado` | `VARCHAR(16)` | `NOT NULL, CHECK` | `'BORRADOR'` o `'CONFIRMADO'`. |
| `fecha_creacion` | `TIMESTAMPTZ` | `NOT NULL` | Fecha de creación del levantamiento dental. |
| `fecha_confirmacion` | `TIMESTAMPTZ` | `NULL` | Fecha de validación definitiva. |

---

### TABLA 10: `hallazgo_odontograma` (7 columnas)
> **Propósito:** Detalle atómico de las marcas anatómicas sobre piezas dentales específicas según el estándar internacional FDI.

| Campo | Tipo | Restricción | ¿Por qué existe y qué almacena exactamente? |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Identificador del hallazgo. |
| `clinica_id` | `UUID` | `FK (clinica.id)` | Tenant de pertenencia. |
| `version_odontograma_id`| `UUID` | `FK (version_odontograma.id)`| Versión a la cual pertenece este detalle. |
| `codigo_pieza_dental`| `VARCHAR(4)` | `NOT NULL` | Código de 2 dígitos según la nomenclatura internacional de la Federación Dental Internacional (FDI): cuadrantes 1 a 4 para permanentes (11 al 48) y 5 a 8 para temporales/deciduos (51 al 85). |
| `codigo_superficie_cara`| `VARCHAR(12)`| `NOT NULL, CHECK` | Cara dental afectada: `'MESIAL'`, `'DISTAL'`, `'OCLUSAL'`, `'VESTIBULAR'`, `'LINGUAL'`, o `'GENERAL'` (toda la pieza). |
| `codigo_condicion` | `VARCHAR(40)` | `NOT NULL` | Condición patológica o tratamiento presente (ej. `'CARIES'`, `'OBTURACION_RESINA'`, `'CORONA'`, `'ENDODONCIA'`, `'PIEZA_AUSENTE'`). |
| `nota_observacion` | `TEXT` | `NULL` | Apuntes complementarios (ej. *"Caries recidivante bajo margen gingival"*). |

---

### TABLA 11: `archivo_adjunto` (18 columnas)
> **Propósito:** Almacén de metadatos de radiografías, fotografías y resultados de Visión Artificial (YOLOv8) con validación humana (*Human-in-the-Loop*).

| Campo | Tipo | Restricción | ¿Por qué existe y qué almacena exactamente? |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Identificador del archivo multimedia. |
| `clinica_id` | `UUID` | `FK (clinica.id)` | Sede propietaria del documento. |
| `paciente_id` | `UUID` | `FK (paciente.id)` | Paciente al que pertenecen las imágenes. |
| `subido_por_usuario_id`| `UUID` | `FK (usuario.id)` | Usuario responsable de la carga (asistente dental u odontólogo). |
| `cita_id` | `UUID` | `FK (cita.id), NULL` | Cita médica durante la cual se tomó o adjuntó el estudio radiográfico (agrupado arriba junto a las FKs). |
| `ia_revisado_por` | `UUID` | `FK (usuario_clinica.id)`| Odontólogo colegiado que auditó el resultado del algoritmo (agrupado arriba junto a las FKs). |
| `tipo_archivo` | `VARCHAR(24)` | `NOT NULL, CHECK` | Clasificación médica: `'RADIOGRAFIA'`, `'FOTOGRAFIA'`, `'CONSENTIMIENTO'`, `'LABORATORIO'`. |
| `clave_almacenamiento`| `VARCHAR(500)`| `UNIQUE, NOT NULL` | **Ruta S3 o Cloud Storage:** en la base de datos **nunca se guardan imágenes pesadas en BLOB**, sino la clave criptográfica o path en el bucket de almacenamiento (ej. `clinica-a/pacientes/123/rx-panoramica.png`). |
| `nombre_original` | `VARCHAR(255)`| `NOT NULL` | Nombre con el que el usuario subió el archivo. |
| `tipo_mime` | `VARCHAR(100)`| `NOT NULL` | Formato MIME (ej. `image/png`, `application/pdf`, `application/dicom`). |
| `peso_bytes` | `BIGINT` | `NOT NULL, CHECK (>0)`| Peso en bytes para control de cuota de almacenamiento por clínica. |
| `region_anatomica` | `VARCHAR(80)` | `NULL` | Zona anatómica de la toma (ej. *"Maxilar Superior Izquierdo, Molares"*). |
| `es_visible_paciente`| `BOOLEAN` | `NOT NULL, DEFAULT false`| Flag de privacidad médica: define si el paciente puede visualizar la radiografía desde su portal. |
| `ia_nombre_modelo` | `VARCHAR(100)`| `NULL` | Modelo de IA ejecutado (ej. *"YOLOv8-DentalCaries"*). |
| `ia_version_modelo`| `VARCHAR(80)` | `NULL` | Versión del modelo para trazabilidad científica (ej. *"v2.1.0-weights-utp"*). |
| `ia_hallazgos_json` | `JSONB` | `NULL` | **Persistencia de inferencia de IA:** coordenadas de bounding boxes `[x, y, w, h]`, etiqueta de patología detectada y score de confianza (ej. `0.94`). Al ser `JSONB`, permite consultas complejas e índices GIN. |
| `ia_estado_revision`| `VARCHAR(16)` | `DEFAULT 'PENDIENTE', CHECK`| **Flujo Human-in-the-Loop:** `'PENDIENTE'`, `'ACEPTADO'`, `'RECHAZADO'`, `'CORREGIDO'`. La IA sugiere; el odontólogo valida. |
| `fecha_creacion` | `TIMESTAMPTZ` | `NOT NULL` | Fecha de carga del archivo. |

---

### TABLA 12: `notificacion` (9 columnas)
> **Propósito:** Bandeja de mensajería transaccional y alertas internas.

| Campo | Tipo | Restricción | ¿Por qué existe y qué almacena exactamente? |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | `PRIMARY KEY` | Identificador de la notificación. |
| `clinica_id` | `UUID` | `FK (clinica.id)` | Sede de procedencia del aviso. |
| `usuario_destinatario_id`| `UUID` | `FK (usuario.id)` | Usuario al que va dirigido el mensaje. |
| `tipo_evento` | `VARCHAR(40)` | `NOT NULL` | Código de evento: `'CITA_RECORDATORIO'`, `'TELECONSULTA_LINK'`, `'RESULTADO_IA'`, `'RECETA_DISPONIBLE'`. |
| `titulo` | `VARCHAR(160)`| `NOT NULL` | Título breve para push notification o campana web. |
| `cuerpo_mensaje` | `VARCHAR(500)`| `NOT NULL` | Contenido del aviso con instrucciones claras. |
| `estado` | `VARCHAR(16)` | `NOT NULL, CHECK` | `'PENDIENTE'`, `'ENVIADO'`, `'LEIDO'`, `'FALLIDO'`. |
| `fecha_creacion` | `TIMESTAMPTZ` | `NOT NULL` | Momento de emisión del evento. |
| `fecha_lectura` | `TIMESTAMPTZ` | `NULL` | Momento exacto en que el usuario abrió la notificación. |

---

## 4. MAPA DE RELACIONES, LLAVES Y CARDINALIDADES

### Resumen de Llaves Foráneas y Reglas de Integridad

| Tabla Origen (Hija) | Columna FK | Tabla Destino (Padre) | Columna PK | Cardinalidad | Justificación de Negocio |
| :--- | :--- | :--- | :--- | :---: | :--- |
| `usuario_clinica` | `clinica_id` | `clinica` | `id` | `N : 1` | Una clínica tiene múltiples colaboradores vinculados. |
| `usuario_clinica` | `usuario_id` | `usuario` | `id` | `N : 1` | Un usuario puede colaborar en una o más clínicas. |
| `usuario_clinica` | `rol_id` | `rol` | `id` | `N : 1` | Un rol es asignado a muchas membresías. |
| `paciente` | `clinica_id` | `clinica` | `id` | `N : 1` | El expediente del paciente pertenece a una clínica específica. |
| `paciente` | `usuario_id` | `usuario` | `id` | `N : 0..1` | Portal de autoservicio: un paciente puede o no tener cuenta de usuario activa. |
| `cita` | `clinica_id` | `clinica` | `id` | `N : 1` | La cita pertenece a la sede dental. |
| `cita` | `paciente_id` | `paciente` | `id` | `N : 1` | Un paciente puede programar múltiples citas a lo largo del tiempo. |
| `cita` | `odontologo_id` | `usuario_clinica` | `id` | `N : 1` | La cita es atendida por un odontólogo con membresía activa en la sede. |
| `atencion_clinica` | `cita_id` | `cita` | `id` | `1 : 0..1` | **Relación 1 a 0..1 con UNIQUE:** una cita solo puede originar como máximo un acto médico; y una atención de emergencia puede no provenir de una cita previa. |
| `version_atencion_clinica` | `atencion_clinica_id` | `atencion_clinica` | `id` | `N : 1` | Una cabecera de atención alberga 1 o más versiones inmutables sucesivas. |
| `version_atencion_clinica` | `version_anterior_id` | `version_atencion_clinica`| `id` | `N : 0..1` | **Auto-referencia:** cadena de versiones tipo blockchain para auditoría médica. |
| `version_odontograma` | `paciente_id` | `paciente` | `id` | `N : 1` | Un paciente tiene evoluciones cronológicas de su dentadura. |
| `version_odontograma` | `version_anterior_id` | `version_odontograma` | `id` | `N : 0..1` | **Auto-referencia:** permite comparar la evolución del tratamiento respecto a la versión previa. |
| `hallazgo_odontograma` | `version_odontograma_id`| `version_odontograma` | `id` | `N : 1` | Una versión de odontograma contiene decenas de hallazgos por pieza y cara. |
| `archivo_adjunto` | `paciente_id` | `paciente` | `id` | `N : 1` | El archivo pertenece a la historia radiográfica del paciente. |
| `archivo_adjunto` | `cita_id` | `cita` | `id` | `N : 0..1` | Opcionalmente se vincula con la cita en la que se tomó la placa. |
| `notificacion` | `usuario_destinatario_id`| `usuario` | `id` | `N : 1` | Una bandeja de alertas dirigida a un usuario específico. |

---

## 5. PREGUNTAS TRAMPA DEL DOCENTE Y CÓMO RESPONDER CON FIRMEZA TÉCNICA

### Pregunta 1: *"¿Por qué la tabla `usuario` no tiene un campo `clinica_id` directamente?"*
> **Tu Respuesta:**
> *"Profesor, si colocáramos `clinica_id` dentro de `usuario`, romperíamos la capacidad **Multi-Clínica**. Un odontólogo especialista o un cirujano maxilofacial suele prestar servicios en dos o tres clínicas distintas. Si le amarramos una clínica fija a su cuenta, tendríamos que obligarlo a crearse tres correos distintos. Con nuestra tabla asociativa `usuario_clinica`, el usuario tiene una sola identidad y contraseña, pero en la Clínica A es 'ODONTOLOGO' y en la Clínica B puede tener otro rol o estar inactivo, garantizando aislamiento y escalabilidad."*

### Pregunta 2: *"¿Por qué separaron `atencion_clinica` de `version_atencion_clinica` en vez de hacer una sola tabla?"*
> **Tu Respuesta:**
> *"Por **normativa médico-legal de historias clínicas**. La ley prohíbe que el registro de una atención sufra un `UPDATE` destructivo donde se borre lo que un doctor escribió previamente. 
> Al separar la cabecera (`atencion_clinica`) de sus versiones (`version_atencion_clinica`), cada vez que el odontólogo modifica un diagnóstico o usa el Asistente de Voz IA, se genera un registro nuevo inmutable con su `numero_version` correlativo, su fecha y su firma digital, enlazado a la `version_anterior_id`. Esto permite peritajes forenses y garantiza que nunca se pierda información clínica."*

### Pregunta 3: *"¿Por qué usar UUID v4 en vez de IDs numéricos autoincrementales (1, 2, 3...)?"*
> **Tu Respuesta:**
> *"Por tres razones críticas de ingeniería:
> 1. **Seguridad contra enumeración (IDOR):** en un sistema multi-tenant web, si usamos IDs numéricos, un usuario malintencionado podría cambiar la URL a `/paciente/101` y predecir los registros de otros pacientes. Con UUID v4, la probabilidad de colisión o adivinanza es matemáticamente nula.
> 2. **Desacoplamiento y concurrencia:** el frontend o servicios de sincronización offline pueden generar el UUID del borrador antes de enviarlo a PostgreSQL sin generar bloqueos por secuencias (`nextval`).
> 3. **Preparación para Sharding:** facilita particionar o migrar datos entre clústeres en la nube sin conflicto de llaves primarias duplicadas."*

### Pregunta 4: *"¿Dónde se guarda la imagen de la radiografía y cómo se entera la base de datos de lo que detectó la Inteligencia Artificial?"*
> **Tu Respuesta:**
> *"La radiografía física **nunca se almacena en PostgreSQL** porque degradaría el rendimiento de los buffers de memoria de la base de datos; se envía a un bucket de objetos S3 seguro y en la columna `clave_almacenamiento` se guarda su URI protegida.
> El microservicio de Visión Artificial (YOLOv8) analiza la placa y deposita el resultado inferido en la columna `ia_hallazgos_json` en formato `JSONB`, guardando coordenadas de cajas y porcentajes de certeza. 
> Además, implementamos la columna `ia_estado_revision` con el flujo **Human-in-the-Loop**: la IA jamás diagnostica sola; el odontólogo debe revisar la placa y cambiar el estado a `'ACEPTADO'` o `'RECHAZADO'` mediante su firma en `ia_revisado_por`."*

### Pregunta 5: *"¿Cómo garantizan que un odontólogo no tenga dos citas a la misma hora en la misma clínica?"*
> **Tu Respuesta:**
> *"A nivel de base de datos hemos establecido el índice compuesto `idx_cita_agenda ON cita(clinica_id, odontologo_id, inicio_en)`. Además, en las recomendaciones del Sprint se tiene contemplado incorporar la extensión `btree_gist` para añadir un `CONSTRAINT EXCLUDE USING gist` que impida físicamente la inserción de rangos temporales solapados `[inicio_en, fin_en]` para un mismo profesional."*

---

## 6. GUION PASO A PASO PARA TU EXPOSICIÓN ORAL

| Momento | Lo que debes mostrar en pantalla | Lo que debes decir |
| :--- | :--- | :--- |
| **Minuto 0:00 - 0:30** | El visor interactivo `diagrama-fisico-interactivo.html` con las 12 tablas en pantalla. | *"Buenos días profesor. Presento el modelo físico oficial de CORONYX. Cuenta con 12 tablas en 3FN distribuidas en 5 dominios funcionales: Seguridad, Pacientes y Citas, Historia Clínica, Archivos con IA y Notificaciones."* |
| **Minuto 0:30 - 1:15** | Enfocar en las tablas `clinica`, `usuario`, `rol` y `usuario_clinica`. | *"El primer pilar es el multi-tenant y la seguridad RBAC. `clinica` es el inquilino raíz. `usuario` desacopla la autenticación centralizada. Y `usuario_clinica` resuelve la pertenencia asignando roles dinámicos con matriz de permisos JSONB, permitiendo que un odontólogo labore en múltiples sedes sin fricción."* |
| **Minuto 1:15 - 2:00** | Enfocar en `paciente`, `cita` y `atencion_clinica`. | *"El flujo clínico arranca con `paciente` y su agenda omnicanal en `cita`. `cita` soporta atención física y teleconsulta WebRTC con LiveKit. Cuando el paciente ingresa a consulta, se abre una `atencion_clinica` en relación 1 a 0..1 con la cita."* |
| **Minuto 2:00 - 3:00** | Enfocar en `version_atencion_clinica`, `version_odontograma` y `hallazgo_odontograma`. | *"La historia clínica y el odontograma están blindados legalmente: implementamos inmutabilidad por versiones. Si el médico habla mediante el asistente de voz, el texto viaja a `texto_pendiente_revision`. Una vez validado, se genera la versión numerada con firma digital. Los hallazgos del odontograma siguen estrictamente la norma internacional FDI pieza por pieza."* |
| **Minuto 3:00 - 3:45** | Enfocar en `archivo_adjunto` (columnas IA). | *"Para la IA de visión computacional, `archivo_adjunto` gestiona las radiografías en la nube y aloja los hallazgos de YOLOv8 en JSONB, exigiendo auditoría humana obligatoria mediante `ia_estado_revision`."* |
| **Minuto 3:45 - 4:00** | Mostrar el script DDL o el informe en Markdown. | *"El modelo cuenta con su DDL en PostgreSQL 17, con llaves UUID v4, índices B-Tree y GIN para JSONB, compilado y listo para migración con Flyway. Quedo atento a sus consultas."* |

---

## 7. RECORRIDO COMPLETO DE RELACIONES: TABLA POR TABLA, FLECHA POR FLECHA

A continuación se explica **absolutamente cada relación** del diagrama tal como se ve en el visor interactivo HTML, empezando desde `clinica` como punto de partida y recorriendo cada flecha hasta las tablas hijas más profundas.

---

### 🏥 Partimos de `clinica` (el centro del universo multi-tenant)

`clinica` es la tabla **raíz** de todo el sistema. Casi todas las demás tablas llevan una columna `clinica_id` que apunta aquí. Esto es lo que se llama **aislamiento multi-inquilino**: todos los datos del sistema están segmentados por clínica para que la Clínica A jamás vea los datos de la Clínica B.

Desde `clinica` salen **9 flechas** (todas de **1 a N**, es decir, una clínica tiene muchos registros hijos):

```
clinica ──(1:N)──► usuario_clinica    "Una clínica tiene muchos colaboradores"
clinica ──(1:N)──► paciente            "Una clínica tiene muchos pacientes"
clinica ──(1:N)──► cita                "Una clínica tiene muchas citas agendadas"
clinica ──(1:N)──► atencion_clinica    "Una clínica tiene muchas atenciones médicas"
clinica ──(1:N)──► version_atencion_clinica  "Una clínica custodia muchas versiones de historias clínicas"
clinica ──(1:N)──► version_odontograma      "Una clínica custodia muchos odontogramas"
clinica ──(1:N)──► hallazgo_odontograma     "Una clínica custodia muchos hallazgos dentales"
clinica ──(1:N)──► archivo_adjunto          "Una clínica almacena muchas radiografías y archivos"
clinica ──(1:N)──► notificacion             "Una clínica genera muchas notificaciones"
```

> **¿Por qué `clinica_id` aparece en TODAS las tablas y no solo en las directas?**  
> Porque en un modelo multi-tenant estricto, **cada consulta SQL debe filtrar por `clinica_id`** para garantizar que un odontólogo de la Sede A nunca pueda ver los pacientes de la Sede B. Si `hallazgo_odontograma` no tuviera `clinica_id`, tendríamos que hacer JOINs costosos para averiguar a qué clínica pertenece. Al ponerlo directamente, las consultas son rápidas y seguras.

---

### 👤 `usuario` (las cuentas de acceso al sistema)

`usuario` es la identidad global de una persona. **No pertenece a ninguna clínica en particular** (no tiene `clinica_id`). ¿Por qué? Porque un odontólogo puede trabajar en 2 o 3 clínicas distintas.

Desde `usuario` salen **4 flechas principales**:

```
usuario ──(1:N)──► usuario_clinica     "Un usuario puede tener membresías en muchas clínicas"
usuario ──(0..1:N)──► paciente          "Un usuario opcionalmente puede estar vinculado como paciente (portal web)"
usuario ──(1:N)──► archivo_adjunto     "Un usuario sube muchos archivos radiográficos"
usuario ──(1:N)──► notificacion        "Un usuario recibe muchas notificaciones"
```

Además, `usuario` tiene **2 flechas extra** (FK de auditoría) hacia `usuario_clinica`:
```
usuario ──(1:N)──► usuario_clinica.rol_asignado_por  "Registra QUÉ admin asignó el rol a cada colaborador"
```

> **Ejemplo práctico:** La Dra. María García (`usuario.id = abc-123`) tiene una cuenta con su correo `maria@gmail.com`. En la tabla `usuario_clinica` tiene 2 registros:
> - Registro 1: `clinica_id = Sede San Isidro`, `rol_id = ODONTOLOGO`
> - Registro 2: `clinica_id = Sede Miraflores`, `rol_id = ADMIN_CLINICA`
> Así, con un solo login, el sistema sabe en qué sede está y qué permisos tiene.

---

### 🛡️ `rol` (los perfiles de acceso RBAC)

`rol` es un catálogo pequeño y estático (normalmente solo 4 registros: ADMIN_CLINICA, ODONTOLOGO, RECEPCIONISTA, PACIENTE).

Desde `rol` sale **1 sola flecha**:

```
rol ──(1:N)──► usuario_clinica     "Un rol se asigna a muchas membresías usuario-clínica"
```

> **¿Cómo se lee?** Un rol (por ejemplo `ODONTOLOGO`) puede estar asignado a muchos registros de `usuario_clinica`. Es decir, muchos usuarios en muchas clínicas pueden tener el rol de odontólogo.

---

### 🔗 `usuario_clinica` (la tabla pivote que lo conecta todo)

Esta es la tabla **más referenciada del sistema** después de `clinica`. Es el punto donde convergen la clínica, el usuario y el rol. Tiene **3 llaves foráneas de entrada** (recibe de `clinica`, `usuario` y `rol`) y es **referenciada como FK por 6 tablas hijas**:

```
                    clinica ──(1:N)──┐
                    usuario ──(1:N)──┤──► usuario_clinica
                    rol     ──(1:N)──┘
                                         │
                                         ├──(1:N)──► cita               "Un odontólogo atiende muchas citas"
                                         ├──(1:N)──► atencion_clinica   "Un odontólogo realiza muchas atenciones"
                                         ├──(1:N)──► version_atencion_clinica  "Un odontólogo firma muchas versiones de HC"
                                         ├──(1:N)──► version_odontograma      "Un odontólogo firma muchos odontogramas"
                                         ├──(1:N)──► paciente                 "Un odontólogo actualiza fichas médicas de muchos pacientes"
                                         └──(1:N)──► archivo_adjunto          "Un odontólogo revisa hallazgos IA de muchos archivos"
```

> **¿Por qué `cita.odontologo_id` apunta a `usuario_clinica` y NO a `usuario`?**  
> Porque nos importa que el odontólogo tenga **membresía activa en ESA clínica**. Si apuntara directo a `usuario`, no podríamos validar que ese doctor realmente trabaja en esa sede. Al apuntar a `usuario_clinica`, la FK garantiza a nivel de base de datos que el odontólogo tiene un rol vigente allí.

---

### 🧑 `paciente` (el expediente del paciente por clínica)

`paciente` recibe flechas de 3 tablas padre:
```
clinica         ──(1:N)──► paciente    "Una clínica registra muchos pacientes"
usuario         ──(0..1:N)──► paciente "Un paciente OPCIONALMENTE tiene cuenta de usuario (portal web)"
usuario_clinica ──(1:N)──► paciente    "Un odontólogo actualiza la ficha médica de muchos pacientes (medico_actualizo_id)"
```

Y desde `paciente` salen **4 flechas** hacia tablas hijas:
```
paciente ──(1:N)──► cita                "Un paciente puede tener muchas citas a lo largo del tiempo"
paciente ──(1:N)──► atencion_clinica    "Un paciente puede recibir muchas atenciones clínicas"
paciente ──(1:N)──► version_odontograma "Un paciente tiene muchas versiones de su odontograma (evolutivo)"
paciente ──(1:N)──► archivo_adjunto     "Un paciente tiene muchas radiografías y documentos adjuntos"
```

> **Ejemplo:** El paciente Juan Pérez (DNI 12345678) fue registrado en la Clínica Coronyx Sede Lima. A lo largo de 2 años ha tenido: 8 citas, 5 atenciones clínicas, 3 versiones de odontograma (ingreso, post-tratamiento, control) y 12 radiografías almacenadas.

---

### 📅 `cita` (la agenda omnicanal)

`cita` recibe flechas de 3 tablas padre:
```
clinica         ──(1:N)──► cita    "Una clínica tiene muchas citas programadas"
paciente        ──(1:N)──► cita    "Un paciente agenda muchas citas"
usuario_clinica ──(1:N)──► cita    "Un odontólogo atiende muchas citas (odontologo_id)"
```

Y desde `cita` salen **2 flechas**:
```
cita ──(1:0..1)──► atencion_clinica    "Una cita origina COMO MÁXIMO una atención clínica"
cita ──(1:N)────► archivo_adjunto      "Durante una cita se pueden subir muchas radiografías"
```

> **La relación más importante aquí es `cita → atencion_clinica` con cardinalidad `1:0..1`.**  
> ¿Qué significa? Que:
> - Una cita puede tener **0 atenciones** (si el paciente no asistió o la cita fue cancelada).
> - Una cita puede tener **como máximo 1 atención** (la restricción `UNIQUE` en `atencion_clinica.cita_id` lo impide).
> - Pero una `atencion_clinica` puede existir **sin cita** (`cita_id` es `NULL`), lo que cubre las **consultas de emergencia** que llegan sin cita previa.

---

### 🩺 `atencion_clinica` (la cabecera del acto médico)

`atencion_clinica` recibe flechas de 4 tablas padre:
```
clinica         ──(1:N)──► atencion_clinica    "Una clínica tiene muchas atenciones"
paciente        ──(1:N)──► atencion_clinica    "Un paciente recibe muchas atenciones"
cita            ──(1:0..1)──► atencion_clinica "Una cita origina como máximo una atención"
usuario_clinica ──(1:N)──► atencion_clinica    "Un odontólogo realiza muchas atenciones"
```

Y desde `atencion_clinica` sale **1 flecha principal**:
```
atencion_clinica ──(1:N)──► version_atencion_clinica  "Una atención contiene muchas versiones inmutables de la historia clínica"
```

> **¿Por qué la atención tiene muchas versiones?**  
> Porque cada vez que el odontólogo escribe algo nuevo o el asistente de voz procesa la grabación, se crea una **nueva versión numerada** (versión 1, versión 2, versión 3...) sin borrar las anteriores. Esto es la **inmutabilidad legal** de la historia clínica.

---

### 📜 `version_atencion_clinica` (el corazón legal inmutable)

`version_atencion_clinica` recibe flechas de:
```
clinica           ──(1:N)──► version_atencion_clinica   "Custodia legal por sede"
atencion_clinica  ──(1:N)──► version_atencion_clinica   "Una atención tiene múltiples versiones"
usuario_clinica   ──(1:N)──► version_atencion_clinica   "El odontólogo firma cada versión (firmado_por)"
```

Y tiene una **auto-referencia** (flecha que apunta a sí misma):
```
version_atencion_clinica ──(1:N)──► version_atencion_clinica  "version_anterior_id apunta a la versión previa"
```

> **¿Qué es una auto-referencia?**  
> La columna `version_anterior_id` es una FK que apunta a la misma tabla `version_atencion_clinica`. Funciona como una **cadena enlazada**: la versión 3 apunta a la versión 2, y la versión 2 apunta a la versión 1. Esto permite reconstruir el historial completo de cambios (como un "blockchain médico").

---

### 🦷 `version_odontograma` (el mapeo dental evolutivo)

`version_odontograma` recibe flechas de:
```
clinica         ──(1:N)──► version_odontograma   "Custodia por sede"
paciente        ──(1:N)──► version_odontograma   "Un paciente tiene múltiples versiones de su dentadura"
usuario_clinica ──(1:N)──► version_odontograma   "El odontólogo firma el odontograma (firmado_por)"
```

Y tiene **2 flechas salientes**:
```
version_odontograma ──(1:N)──► hallazgo_odontograma     "Una versión contiene muchos hallazgos por pieza dental"
version_odontograma ──(1:N)──► version_odontograma       "Auto-referencia: version_anterior_id (cadena evolutiva)"
```

---

### 📍 `hallazgo_odontograma` (el detalle atómico por pieza dental)

`hallazgo_odontograma` es una **tabla terminal** (no tiene hijos). Solo recibe flechas:
```
clinica             ──(1:N)──► hallazgo_odontograma       "Aislamiento multi-tenant"
version_odontograma ──(1:N)──► hallazgo_odontograma       "Una versión del odontograma tiene muchos hallazgos"
```

> **Ejemplo:** En la versión 2 del odontograma del paciente Juan Pérez, se registraron 6 hallazgos:
> - Pieza `36`, cara `OCLUSAL`, condición `CARIES`
> - Pieza `36`, cara `MESIAL`, condición `CARIES`
> - Pieza `14`, cara `GENERAL`, condición `CORONA`
> - Pieza `21`, cara `VESTIBULAR`, condición `OBTURACION_RESINA`
> - Pieza `46`, cara `GENERAL`, condición `PIEZA_AUSENTE`
> - Pieza `11`, cara `LINGUAL`, condición `CALCULO_DENTAL`

---

### 📁 `archivo_adjunto` (radiografías + IA)

`archivo_adjunto` es una **tabla terminal** con muchas FKs de entrada:
```
clinica         ──(1:N)──► archivo_adjunto   "Una clínica almacena muchos archivos"
paciente        ──(1:N)──► archivo_adjunto   "Un paciente tiene muchas radiografías"
usuario         ──(1:N)──► archivo_adjunto   "Un usuario sube muchos archivos (subido_por_usuario_id)"
cita            ──(1:N)──► archivo_adjunto   "Durante una cita se toman muchas placas (cita_id, opcional)"
usuario_clinica ──(1:N)──► archivo_adjunto   "Un odontólogo revisa los hallazgos IA (ia_revisado_por)"
```

> **Nota:** `archivo_adjunto` tiene **2 FKs hacia tablas diferentes para persona**: `subido_por_usuario_id → usuario.id` (la cuenta global que subió el archivo) y `ia_revisado_por → usuario_clinica.id` (el profesional con membresía activa que validó la IA). Son columnas distintas con propósitos distintos.

---

### 🔔 `notificacion` (la bandeja de alertas)

`notificacion` es una **tabla terminal** con 2 FKs de entrada:
```
clinica ──(1:N)──► notificacion   "Una clínica genera muchas notificaciones"
usuario ──(1:N)──► notificacion   "Un usuario recibe muchas notificaciones (usuario_destinatario_id)"
```

---

### RESUMEN VISUAL: FLUJO COMPLETO DE RELACIONES

```
                              ┌──────────┐
                              │   rol    │
                              │ (7 cols) │
                              └────┬─────┘
                                   │ 1:N
    ┌──────────┐             ┌─────▼──────────┐              ┌──────────┐
    │ clinica  │──(1:N)─────►│usuario_clinica │◄──(1:N)──────│ usuario  │
    │ (7 cols) │             │   (9 cols)     │              │ (12 cols)│
    └──┬───────┘             └──┬──┬──┬──┬────┘              └──┬──┬───┘
       │                        │  │  │  │                      │  │
       │ 1:N                    │  │  │  │ 1:N                  │  │ 1:N
       │                        │  │  │  └──► paciente.         │  └──► notificacion
       │                        │  │  │       medico_actualizo  │       (9 cols)
       │                        │  │  │                         │
       │ 1:N                    │  │  │ 1:N                     │ 0..1:N
       ▼                        │  │  └──► archivo_adjunto.     └──► paciente.
    ┌──────────┐                │  │       ia_revisado_por           usuario_id
    │ paciente │◄───────────────┘  │
    │ (17 cols)│                    │ 1:N
    └──┬──┬──┬─┘                   │
       │  │  │                     ▼
       │  │  │ 1:N        ┌────────────┐
       │  │  └──────────► │    cita    │
       │  │               │  (15 cols) │
       │  │               └──┬────┬────┘
       │  │                  │    │
       │  │ 1:N              │    │ 1:0..1
       │  └──► archivo_adj   │    ▼
       │       (18 cols)     │  ┌─────────────────┐
       │                     │  │atencion_clinica │
       │ 1:N                 │  │   (8 cols)      │
       ▼                     │  └────┬────────────┘
    ┌────────────────┐       │       │
    │version_odont.  │       │       │ 1:N
    │   (9 cols)     │       │       ▼
    └────┬───────────┘       │  ┌──────────────────────────┐
         │                   │  │version_atencion_clinica  │
         │ 1:N               │  │      (15 cols)           │
         ▼                   │  │  ↺ version_anterior_id   │
    ┌──────────────────┐     │  └──────────────────────────┘
    │hallazgo_odont.   │     │
    │   (7 cols)       │     │  1:N
    └──────────────────┘     └──► archivo_adjunto.cita_id
```

---

### CÓMO EXPLICARLO NARRATIVAMENTE AL DOCENTE (GUION ORAL)

> *"Profesor, si seguimos el flujo del diagrama empezando desde la tabla `clinica`:*
>
> *Una **clínica** registra muchos **colaboradores** a través de `usuario_clinica`, que es la tabla que conecta a un `usuario` con una `clinica` y le asigna un `rol`. Esta es una relación de muchos a muchos resuelta con una tabla asociativa.*
>
> *Esa misma clínica registra muchos **pacientes**. Cada paciente puede opcionalmente tener una cuenta de `usuario` para el portal web, pero eso no es obligatorio: si la señora viene solo a consulta presencial, no necesita app.*
>
> *Un paciente agenda muchas **citas** a lo largo del tiempo. Cada cita es atendida por un odontólogo, y aquí es clave que `odontologo_id` apunta a `usuario_clinica` y NO a `usuario`, porque necesitamos garantizar que ese doctor tiene membresía activa en esa sede específica.*
>
> *Cuando el paciente llega a su cita, se abre una **atención clínica**. La relación entre `cita` y `atencion_clinica` es de 1 a 0..1: una cita puede no generar atención si el paciente no asistió, pero como máximo genera una sola atención. Y al revés, una atención puede existir sin cita previa en caso de emergencia.*
>
> *Dentro de cada atención clínica se generan múltiples **versiones de la historia clínica** (`version_atencion_clinica`). Cada versión es inmutable: nunca se hace un UPDATE destructivo. Si el doctor corrige algo o el asistente de voz transcribe la consulta, se crea una nueva versión que apunta a la anterior mediante `version_anterior_id` (auto-referencia), formando una cadena de trazabilidad como un blockchain médico.*
>
> *Paralelamente, el paciente tiene su **odontograma** (`version_odontograma`) que también es inmutable y versionado. Cada versión contiene muchos **hallazgos** (`hallazgo_odontograma`) donde se registra pieza por pieza dental (nomenclatura FDI), qué cara está afectada y qué condición tiene.*
>
> *Finalmente, los **archivos adjuntos** (`archivo_adjunto`) almacenan los metadatos de radiografías. La imagen real va a la nube S3, pero aquí se guarda la referencia, y además los resultados de la IA de YOLOv8 en `ia_hallazgos_json`. El odontólogo valida la detección con `ia_estado_revision`, completando el ciclo Human-in-the-Loop.*
>
> *Y las **notificaciones** cierran el ciclo informando al usuario de recordatorios de citas, resultados de IA y avisos del sistema."*

---
*Documento preparado como guía de sustentación académica y defensa técnica del Sprint 03.*
