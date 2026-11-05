-- V21: Eliminar registro duplicado INC-ADM-002 que fue reemplazado por INC-TI-001
-- Deja la tabla catalogo_incidencia con exactamente 16 registros normalizados

UPDATE mesa_ayuda_ticket
SET catalogo_id = 'INC-TI-001'
WHERE catalogo_id = 'INC-ADM-002';

DELETE FROM catalogo_incidencia
WHERE id = 'INC-ADM-002';
