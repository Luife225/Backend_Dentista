-- =========================================================================================
-- PROYECTO CORONYX - SISTEMA DE GESTION ODONTOLOGICA
-- FLYWAY MIGRATION V18: LIMPIEZA DE TICKETS Y BITACORA DE PRUEBA (DATOS 100% REALES)
-- Motor: PostgreSQL 16 / 17
-- =========================================================================================

-- Limpiar tickets y bitácora preliminares de prueba para iniciar pruebas con datos reales
TRUNCATE TABLE mesa_ayuda_bitacora CASCADE;
TRUNCATE TABLE mesa_ayuda_ticket CASCADE;
