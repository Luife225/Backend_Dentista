# CORONYX — Plan de Desarrollo y Levantamiento de Observaciones (Feedback Docente)

**Proyecto:** CORONYX: Sistema Integral de Gestión Odontológica con Asistente de IA  
**Documentos de Referencia:** Avance del Proyecto Final 2 (APF2 v1.1), Informe del Sprint 03 (v3.2) e Informe del Sprint 04 (v4.3)  
**Fecha de Planificación:** 14 de octubre de 2026  
**Ámbito:** Backend (`Backend_Dentista`), Frontend (`Frontend_Dentista`) y Base de Datos PostgreSQL  
**Estado:** Planificado y Listo para Implementación  

---

## 1. Introducción y Objetivos

Este documento formaliza la planificación técnica, arquitectura y contratos de implementación requeridos para atender el feedback recibido en la revisión docente del **Avance del Proyecto Final 2**:

> *"Buen trabajo, algunas consideraciones:*
> 1. *Terminar las funcionalidades del administrador.*
> 2. *Incorporar un servicio para las videollamadas Zoom o Meet.*
> 3. *Revisar los campos del detalle del historial, hay títulos que se deben cambiar.*
> 4. *Quitar el modo dark del login, para el usuario no sea estético.*
> 5. *Queda pendiente la funcionalidad principal que es atender al usuario."*

La planificación preserva las reglas de arquitectura establecidas en los Sprints 3 y 4:
- Identificadores numéricos `BIGINT` transportados como strings decimales en JSON.
- Aislamiento multi-sede estricto por `clinica_id`.
- Modelo inmutable para versiones clínicas confirmadas (sin `DELETE` físico ni sobrescritura de auditoría).
- Desacoplamiento de credenciales y variables de entorno para despliegue en la nube (Railway).

---

## 2. Matriz de Requerimientos vs. Casos de Aceptación

| Código | Requerimiento de Feedback | Componente Principal | Casos APF2 Relacionados | Prioridad |
| :---: | :--- | :--- | :---: | :---: |
| **REQ-01** | **Flujo Principal: Atender al Paciente** | Odontólogo / Historia Clínica | `D03`, `D04`, `D05`, `D06`, `H05`, `H10` | **Crítica (P1)** |
| **REQ-02** | **Rediseño Light Mode del Login** | Frontend / Auth | `V01`, `G01` | **Alta (P2)** |
| **REQ-03** | **Estandarización de Títulos del Historial** | Frontend / Expediente | `V05`, `D05`, `H06` | **Alta (P2)** |
| **REQ-04** | **Completar Funcionalidades de Administrador** | Admin Clínica / Consultorio | `A01`–`A04`, `A09`, `A11`, `V08`, `V09`, `V10` | **Alta (P2)** |
| **REQ-05** | **Servicio de Videollamadas (Zoom / Meet)** | Agenda / Teleconsulta | `A08`, `R06`, `P01`, `V04`, `V11` | **Media (P3)** |

---

## 3. Especificación Técnica por Requerimiento

---

### REQ-01: Funcionalidad Principal: "Atender al Usuario" (Atención Clínica Integral)

#### 3.1.1 Diagnóstico de Situación Actual
- La tabla `atencion_clinica` y `version_atencion_clinica` existen en la base de datos con relaciones `FK` a `clinica`, `paciente`, `cita` y `usuario_clinica`.
- El backend dispone de controladores base bajo `/api/v1/clinical-encounters`.
- **Faltante clave:** En el frontend del odontólogo (`Agenda.tsx` / `HistoriaClinica.tsx` / `Consultorio.tsx`), el odontólogo no cuenta con un botón interactivo y directo en la cita para "Comenzar Consulta", abrir el formulario clínico estructurado, guardar borradores progresivos y **firmar la atención de forma definitiva**, provocando la transición automática de la cita a `FINALIZADA` y marcando asistencia `ASISTIO`.

#### 3.1.2 Flujo Operativo Objetivo
```
[Cita en Agenda: CONFIRMADA]
       │
       ▼ (Odontólogo presiona "Iniciar Atención")
[Cita pasa a: EN_ATENCION] ────► [Crea/Abre borrador en atencion_clinica (BORRADOR)]
                                                │
                                                ▼ (Registra anamnesis, diagnóstico, plan, recetas, notas)
                                  [Botón "Guardar Borrador"] (Persiste sin cerrar consulta)
                                                │
                                                ▼ (Odontólogo presiona "Finalizar y Firmar Atención")
                                  [Validación de campos obligatorios]
                                                │
                                                ▼
                   ┌─────────────────────────────────────────────────────────────┐
                   │ 1. Genera version_atencion_clinica (esta_confirmado = true) │
                   │ 2. Sella firma con usuario_clinica_id y fecha/hora exacta   │
                   │ 3. Pasa atencion_clinica a CONFIRMADO                       │
                   │ 4. Transiciona cita a FINALIZADA                            │
                   │ 5. Registra estado_asistencia = ASISTIO                     │
                   │ 6. Publica versión al historial inmutable del expediente    │
                   └─────────────────────────────────────────────────────────────┘
```

#### 3.1.3 Contrato de Endpoints Backend

1. **Iniciar Atención de Cita:**
   - **Ruta:** `POST /api/v1/citas/{citaId}/iniciar-atencion`
   - **Autorización:** `ODONTOLOGO` (asignado a la cita).
   - **Comportamiento:** Pasa `cita.estado` a `'EN_ATENCION'`. Crea `atencion_clinica` si no existía previamente para dicha cita.
   - **Respuesta (`200 OK`):**
     ```json
     {
       "citaId": "1",
       "estadoCita": "EN_ATENCION",
       "atencionClinicaId": "1",
       "estadoAtencion": "BORRADOR",
       "pacienteId": "1",
       "odontologoId": "2"
     }
     ```

2. **Guardar Borrador Clínico:**
   - **Ruta:** `PUT /api/v1/clinical-encounters/{id}/draft` (o `POST /api/v1/clinical-encounters`)
   - **Payload Request:**
     ```json
     {
       "diagnostico": "Caries de dentina profunda en pieza 46 (CIE-10: K02.1)",
       "procedimientos": "Apertura cavitaria, eliminación de tejido cariado, base cavitaria con ionómero de vidrio",
       "recetasPrescripciones": "Amoxicilina 500mg c/8h por 5 días; Ibuprofeno 400mg c/8h condicional al dolor",
       "indicaciones": "Evitar masticar alimentos duros por 24 horas; higiene cuidadosa con cepillo suave",
       "evolucion": "Paciente refiere dolor moderado provocado al frío, sin sintomatología espontánea nocturna",
       "textoPendienteRevision": null
     }
     ```
   - **Respuesta (`200 OK`):** Retorna el borrador guardado sin cerrar la consulta.

3. **Confirmar y Sellar Atención:**
   - **Ruta:** `POST /api/v1/clinical-encounters/{id}/confirm`
   - **Payload Request:**
     ```json
     {
       "diagnostico": "Caries de dentina profunda en pieza 46 (CIE-10: K02.1)",
       "procedimientos": "Restauración definitiva con resina compuesta fotocurable",
       "recetasPrescripciones": "Ibuprofeno 400mg condicional",
       "indicaciones": "Control a los 6 meses",
       "evolucion": "Procedimiento concluido sin complicaciones"
     }
     ```
   - **Respuesta (`200 OK`):** DTO con la versión inmutable sellada (`numeroVersion`, `firmadoPorId`, `fechaConfirmacion`).

---

### REQ-02: Rediseño Light Mode del Formulario de Inicio de Sesión (Login)

#### 3.2.1 Diagnóstico de Situación Actual
- [Login.tsx](file:///c:/Users/sebas/Desktop/CoronyxSASS/FrontendCoronyx/Frontend_Dentista/src/pages/Login.tsx) utiliza paletas oscuras (`bg-slate-900`, `text-slate-100`, bordes oscuros) que le dan una estética de herramienta de desarrollo o consola, alejada del estándar clínico / odontológico.
- La observación solicita expresamente: *"Quitar el modo dark del login, para el usuario no sea estético."*

#### 3.2.2 Diseño Light Clínico Profesional
- **Fondo:** Tonos claros y luminosos: degradado sutil de blanco puro a azul médico suave (`bg-gradient-to-br from-slate-50 via-blue-50/40 to-indigo-50/30`).
- **Tarjeta de Autenticación:** Tarjeta blanca (`bg-white`), borde sutil (`border border-slate-200/80`), sombra clínica difusa (`shadow-xl shadow-slate-200/60`), esquinas redondeadas modernas (`rounded-2xl`).
- **Tipografía:** Nítida, oscura y legible (`text-slate-800` para títulos, `text-slate-500` para subtítulos e instrucciones).
- **Campos de Texto:** Fondos blancos limpios, bordes `border-slate-300`, foco con anillo azul corporativo (`focus:ring-2 focus:ring-blue-600 focus:border-blue-600`).
- **Botón Primario:** Azul zafiro médico (`bg-blue-600 hover:bg-blue-700 text-white font-medium shadow-md shadow-blue-500/20`).
- **Branding:** Logotipo de Coronyx Dental integrado nítidamente sobre fondo claro con su isotipo y tagline institucional: *"Sistema Integral de Gestión Odontológica"*.

---

### REQ-03: Estandarización de Campos y Títulos del Historial Clínico

#### 3.3.1 Diagnóstico de Situación Actual
- Varios títulos en las tarjetas y pestañas de [HistoriaClinica.tsx](file:///c:/Users/sebas/Desktop/CoronyxSASS/FrontendCoronyx/Frontend_Dentista/src/pages/HistoriaClinica.tsx) y [PacientePerfil.tsx](file:///c:/Users/sebas/Desktop/CoronyxSASS/FrontendCoronyx/Frontend_Dentista/src/pages/PacientePerfil.tsx) utilizan nombres técnicos internos (ej. *"Texto pendiente revisión"*, *"Procedimientos"*, *"Recetas prescripciones"*, *"Evolución"*).

#### 3.3.2 Mapeo Terminológico Oficial

| Campo en Base de Datos / DTO | Título Anterior | Nuevo Título Clínico Oficial | Descripción / Ayuda en Pantalla |
| :--- | :--- | :--- | :--- |
| `evolucion` | Evolución | **Anamnesis y Evolución del Paciente** | Motivo de consulta y síntomas referidos en la sesión |
| `diagnostico` | Diagnóstico | **Diagnóstico Estomatológico (CIE-10)** | Impresión diagnóstica y patologías dentarias/periodontales |
| `procedimientos` | Procedimientos | **Procedimientos Realizados y Tratamiento** | Actos quirúrgicos, operatoria, endodoncia u ortodoncia ejecutados |
| `recetasPrescripciones` | Recetas prescripciones | **Prescripción Farmacológica (Receta)** | Medicación indicada, posología, concentración y duración |
| `indicaciones` | Indicaciones | **Recomendaciones y Cuidados Post-Operatorios** | Cuidados de higiene, dieta e indicaciones para el paciente |
| `textoPendienteRevision` | Texto pendiente revisión | **Observaciones Internas / Nota Interconsulta** | Anotaciones clínicas privadas entre profesionales |

---

### REQ-04: Cierre de Funcionalidades del Administrador (`ADMIN_CLINICA`)

#### 3.4.1 Módulos a Completar

1. **Gestión Integral de Usuarios y Membresías de la Sede (`Consultorio.tsx` - Casos `A01`–`A04`):**
   - **Alta de usuario:** Formulario con nombres, apellidos, correo institucional, rol asignado (`ODONTOLOGO` o `RECEPCIONISTA`) y contraseña inicial.
   - **Activación / Desactivación:** Interruptor de estado (`ACTIVO` / `INACTIVO`) en la lista de usuarios. Al desactivar, el backend incrementa `session_version` revocando sesiones JWT abiertas inmediatamente.
   - **Restricción de seguridad:** El administrador de una sede solo puede ver y administrar usuarios de su propia clínica (`clinica_id`). No puede asignarse rol `SUPER_ADMIN`.

2. **Horarios de Atención y Disponibilidad de la Sede (`A09`, Vista `V09`):**
   - Configuración de días laborables y franjas horarias de apertura de la sede (ej. Lunes a Sábado de 08:00 a 20:00).
   - Consulta y bloqueo de ausencias o licencias médicas de los odontólogos (`/api/v1/disponibilidad/ausencias`) para evitar que recepción o pacientes agenden citas en horarios no disponibles.

3. **Dashboard Administrativo con Datos Reales de Base de Datos (`A11`, Vista `V10`):**
   - Conexión del frontend a `/api/v1/dashboard/summary`.
   - **Métricas calculadas:**
     * Total de pacientes registrados en la clínica.
     * Citas del día desglosadas por estado (`PROGRAMADA`, `CONFIRMADA`, `EN_ATENCION`, `FINALIZADA`, `CANCELADA`).
     * Tasa de cumplimiento de asistencia (`ASISTIO` vs. `NO_SHOW`).
     * Odontólogos activos en la sede.
   - **Eliminación estricta de datos ficticios:** Retirar gráficas de ingresos monetarios simulados o cobros de caja fuera del alcance.

---

### REQ-05: Integración de Videollamadas (Zoom / Google Meet para Teleconsulta)

#### 3.5.1 Soporte en Base de Datos y Modelo
La tabla `cita` cuenta de forma nativa con los campos:
- `modalidad VARCHAR(12)`: Admite `'PRESENCIAL'` o `'VIRTUAL'`.
- `enlace_teleconsulta VARCHAR(500)`: Almacena la URL de la sala de videollamada.

#### 3.5.2 Flujo Funcional
1. **Al Crear o Reprogramar una Cita:**
   - El selector de modalidad permite elegir entre:
     * 🏥 **Presencial:** Requiere asignar consultorio físico (ej. "Consultorio 1").
     * 💻 **Virtual (Teleconsulta Odontológica):**
       - Muestra un campo de texto para pegar el enlace generado de **Google Meet** (`https://meet.google.com/...`) o **Zoom** (`https://zoom.us/j/...`).
       - Botón auxiliar: *"Generar sala segura Jitsi/Meet"* que genera automáticamente un enlace determinista bajo el dominio seguro de la clínica (ej. `https://meet.jit.si/coronyx-cita-{id}-{hash}`).

2. **Visualización en la Agenda del Odontólogo:**
   - La tarjeta de cita virtual muestra un distintivo azul con icono de cámara: **`Teleconsulta`**.
   - Botón directo de acción: **`Unirse a la Videollamada`** que abre el enlace en una nueva pestaña del navegador.

3. **Visualización en el Portal del Paciente (`PatientApp.tsx`):**
   - En la sección **"Mis Citas"**, si la cita es virtual y su estado es `CONFIRMADA` o `PROGRAMADA`, se muestra un banner destacado con el enlace y botón:
     ```text
     📹 Cita Virtual Programada
     Dr. Andrés Vega Demo — Odontología General
     Fecha: 15 de Octubre, 10:00 AM
     [ Unirse a la Sesión por Google Meet / Zoom ]
     ```

---

## 4. Fases de Ejecución del Próximo Incremento

```mermaid
gantt
    title Plan de Ejecución — Cierre de Observaciones y Feedback Docente
    dateFormat  YYYY-MM-DD
    section Fase 1: Core Clínico
    Backend: Endpoints de Inicio y Firma de Atención     :active, f1_be, 2026-10-15, 2d
    Frontend: Interfaz "Atender Paciente" y Borrador     :f1_fe, after f1_be, 2d
    Estandarización de Títulos de Historial Clínico      :f1_tit, 2026-10-15, 1d
    section Fase 2: Identidad & Admin
    Rediseño Light Mode del Login                       :f2_log, 2026-10-16, 1d
    Completar CRUD de Usuarios y Roles en Consultorio    :f2_adm, 2026-10-17, 2d
    Métricas Reales del Dashboard Administrativo         :f2_dash, 2026-10-18, 1d
    section Fase 3: Teleconsulta
    Campo y Validación de Enlace Zoom/Meet en API        :f3_be, 2026-10-19, 1d
    Botones "Unirse a Videollamada" en Agenda y Portal   :f3_fe, after f3_be, 1d
    section Fase 4: Pruebas & Cierre
    Validación Integral de los 34 Casos de Aceptación    :f4_test, 2026-10-21, 2d
    Actualización de Documentación Final                 :f4_doc, after f4_test, 1d
```

---

## 5. Criterios de Aceptación para la Entrega Final

1. **Atención al Paciente:**
   - [ ] El odontólogo puede transicionar una cita confirmada a `EN_ATENCION`.
   - [ ] El formulario clínico permite autoguardado en borrador y confirmación explícita con firma del profesional.
   - [ ] La atención confirmada se registra en el historial con candado inmutable y cierra la cita como `FINALIZADA` (`ASISTIO`).
2. **Login Light Mode:**
   - [ ] El login carga en modo claro clínico, con diseño armonioso, sombras suaves y branding corporativo visible sin elementos dark oscuros.
3. **Historial Odontológico:**
   - [ ] Todas las vistas de expediente reflejan títulos estomatológicos estándar (Anamnesis, Diagnóstico CIE-10, Procedimientos, Recetas, Indicaciones).
4. **Administrador de Clínica:**
   - [ ] Creación y desactivación de odontólogos/recepcionistas operativa desde la interfaz de administración.
   - [ ] Dashboard muestra contadores derivados 100% de la base de datos real.
5. **Teleconsulta:**
   - [ ] Agendamiento de citas virtuales almacena el enlace de Zoom/Meet y permite tanto al médico como al paciente unirse a la sala con un solo clic.

---
*Documento elaborado para el equipo CORONYX · Universidad Tecnológica del Perú.*
