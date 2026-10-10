# CORONYX — Política de Corte de JWT UUID y Plan de Rollback con Escrituras Posteriores (F01-A)

**Fecha de Vigencia:** 10 de Octubre de 2026  
**Responsable:** Desarrollador A (Backend)  
**Alcance:** Base de Datos PostgreSQL, Spring Boot Backend, Contrato con Desarrollador B (Frontend)

---

## 1. Política de Corte Inmediato de Tokens JWT con UUID

### 1.1 Contexto y Justificación
Durante las versiones iniciales del sistema (V1–V3), la identidad de los usuarios, clínicas y miembros se representaba mediante UUID v4 en formato canónico de 36 caracteres (ej. `c1111111-aaaa-bbbb-cccc-000000000001`). Con la migración integral **F01-A** a `BIGINT identity` (en PostgreSQL) y cadenas decimales positivas en el contrato de API/JSON (ej. `"1"`, `"2"`), cualquier sesión activa que porte un token emitido con claims UUID queda **incompatible con el modelo de datos de persistencia**.

### 1.2 Regla de Corte Institucional (Hard Cut-off)
1. **Invalidación Activa:** El componente `JwtService` (`isTokenValid()`) ejecuta una validación estricta sobre los claims del payload:
   - `userId` (o `usuarioId`): debe cumplir la expresión regular `^[1-9]\d*$`.
   - `usuarioClinicaId`: debe cumplir la expresión regular `^[1-9]\d*$`.
   - `clinicaId`: debe cumplir la expresión regular `^[1-9]\d*$`.
2. **Rechazo Inmediato:** Si un cliente envía una cabecera `Authorization: Bearer <token>` cuyo payload contiene UUIDs, el token es calificado como inválido de forma inmediata (`isTokenValid == false`), devolviendo código HTTP `401 Unauthorized`.
3. **Comportamiento del Cliente:** El frontend (Desarrollador B) no debe intentar parsear ni almacenar tokens rechazados. Ante un `401`, debe limpiar el almacenamiento local (`localStorage` / `sessionStorage`) y redirigir al formulario institucional de inicio de sesión (`/login`) para obtener un nuevo token conforme al contrato F01.
4. **Prohibición de Conversión en Vuelo:** No se realizan traducciones dinámicas UUID→BIGINT en filtros de seguridad. El corte es limpio y total para evitar inconsistencias de identidad.

---

## 2. Procedimiento de Rollback con Escrituras Posteriores

### 2.1 Problemática de un Rollback Post-Migración
Si la migración V4 se aplica y el sistema entra en operación temporalmente, pueden generarse nuevas altas (nuevos pacientes registrados, citas agendadas, atenciones clínicas). Un simple `RESTORE` de la copia de seguridad previa a V4 **destruiría irreversiblemente las transacciones realizadas después de la migración**.

Por tanto, el procedimiento de rollback debe:
1. Revertir el tipo de datos de `BIGINT` a `UUID` preservando la integridad referencial.
2. Restaurar los UUIDs originales para las filas preexistentes (usando el log de correspondencia).
3. Generar UUIDs válidos (`gen_random_uuid()`) para las nuevas filas creadas durante el período de operación en V4.
4. Preservar las 32 relaciones de claves foráneas entre entidades nuevas y antiguas.

---

### 2.2 Tabla de Correspondencia Persistente para Reversión
Durante la ventana de transición, la base de datos conserva o exporta la correspondencia histórica generada:

```sql
-- Tabla de auditoría/mapeo para rollback (almacenada temporalmente)
CREATE TABLE IF NOT EXISTS _rollback_uuid_map (
    tabla_nombre VARCHAR(64) NOT NULL,
    bigint_id BIGINT NOT NULL,
    uuid_id UUID NOT NULL,
    es_nueva_fila BOOLEAN DEFAULT FALSE,
    PRIMARY KEY (tabla_nombre, bigint_id)
);
```

---

### 2.3 Script de Reversión Bidireccional (V4 -> V3 con Escrituras Posteriores)

```sql
-- =========================================================================
-- PROCEDIMIENTO DE ROLLBACK SEGURO V4 -> V3 (CON ESCRITURAS POSTERIORES)
-- =========================================================================

BEGIN;

-- Paso 1: Para cada tabla, asegurar que cada fila tenga un UUID asignado
-- Si la fila venía de la migración previa, recupera su UUID original.
-- Si la fila fue creada después de V4, genera un nuevo UUID v4.

-- Ejemplo para tabla paciente:
ALTER TABLE paciente ADD COLUMN rollback_uuid UUID;

-- 1.1 Filas históricas: reasignar UUID original desde mapa de migración
UPDATE paciente p
SET rollback_uuid = m.old_id
FROM _map_paciente m
WHERE p.id = m.new_id;

-- 1.2 Nuevas filas post-migración: asignar nuevo UUID v4
UPDATE paciente
SET rollback_uuid = gen_random_uuid()
WHERE rollback_uuid IS NULL;

-- Paso 2: Revertir columnas de Clave Foránea en tablas hijas (ej: cita)
ALTER TABLE cita ADD COLUMN rollback_paciente_id UUID;

UPDATE cita c
SET rollback_paciente_id = p.rollback_uuid
FROM paciente p
WHERE c.paciente_id = p.id;

-- Paso 3: Desactivar temporalmente restricciones FK
ALTER TABLE cita DROP CONSTRAINT IF EXISTS cita_paciente_id_fkey;
ALTER TABLE paciente DROP CONSTRAINT IF EXISTS paciente_pkey;

-- Paso 4: Reemplazar columnas BIGINT por UUID
ALTER TABLE paciente DROP COLUMN id;
ALTER TABLE paciente RENAME COLUMN rollback_uuid TO id;
ALTER TABLE paciente ADD CONSTRAINT paciente_pkey PRIMARY KEY (id);

ALTER TABLE cita DROP COLUMN paciente_id;
ALTER TABLE cita RENAME COLUMN rollback_paciente_id TO paciente_id;
ALTER TABLE cita ADD CONSTRAINT cita_paciente_id_fkey 
    FOREIGN KEY (paciente_id) REFERENCES paciente(id) ON DELETE RESTRICT;

-- Paso 5: Ajustar historial de Flyway
DELETE FROM flyway_schema_history WHERE version = '4';

COMMIT;
```

---

## 3. Matriz de Validación de Ensayos Ejecutados

| Entorno de Prueba | Estado | Migraciones | Verificación de Datos | Concurrencia / Overflow |
| :--- | :--- | :--- | :--- | :--- |
| `coronyx_flyway_test` | **EXITOSO** | V1 → V2 → V3 → V4 | Semillas V2 migradas a BIGINT | Validado por Spring Boot y Flyway |
| `coronyx_ensayo` | **EXITOSO** | V1 → V2 → V3 → V4 | Dump completo de producción migrado | 5 hilos paralelos OK, ID > 2^53 OK |
| `coronyx` (Producción) | **INTACTO** | V1 → V3 (Respaldado) | Copia física en `backup_coronyx_pre_v4.dump` | Pendiente de autorización y despliegue |

---

## 4. Conclusión y Veredicto
El ensayo de migración V4 ha sido validado satisfactoriamente en entorno limpio y en réplica con datos reales, garantizando cero colisiones, cero redondeo en IDs gigantes, y rechazo hermético de credenciales obsoletas con UUID.
