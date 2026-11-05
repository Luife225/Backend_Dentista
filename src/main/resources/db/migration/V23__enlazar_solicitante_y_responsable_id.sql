-- V23: Asegurar enlace formal de auditoría individual para solicitante_id y responsable_id en mesa_ayuda_ticket

-- 1. Actualizar solicitante_id en tickets existentes donde el solicitante coincida con un usuario del sistema
UPDATE mesa_ayuda_ticket t
SET solicitante_id = u.id
FROM usuario u
WHERE t.solicitante_id IS NULL
  AND (
    LOWER(u.correo) = LOWER(t.solicitante_nombre)
    OR LOWER(TRIM(CONCAT(u.nombres, ' ', u.apellidos))) = LOWER(TRIM(t.solicitante_nombre))
  );

-- 2. Asegurar que tickets creados por Luis Francisco Gamboa Velasquez queden enlazados a su usuario ID 6
UPDATE mesa_ayuda_ticket
SET solicitante_id = 6
WHERE solicitante_id IS NULL
  AND (solicitante_nombre ILIKE '%Gamboa%' OR solicitante_nombre ILIKE '%Luis Francisco%');

-- 3. Actualizar responsable_id en base al nombre del responsable coincidente con usuario
UPDATE mesa_ayuda_ticket t
SET responsable_id = u.id
FROM usuario u
WHERE t.responsable_id IS NULL
  AND (
    LOWER(u.correo) = LOWER(t.responsable_nombre)
    OR LOWER(TRIM(CONCAT(u.nombres, ' ', u.apellidos))) = LOWER(TRIM(t.responsable_nombre))
  );

-- 4. Asegurar que tickets atendidos por Paula Suárez queden enlazados a su usuario ID 2
UPDATE mesa_ayuda_ticket
SET responsable_id = 2
WHERE responsable_id IS NULL
  AND (responsable_nombre ILIKE '%Paula%');
