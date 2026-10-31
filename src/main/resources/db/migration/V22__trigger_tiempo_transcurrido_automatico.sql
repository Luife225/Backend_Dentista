-- V22: Trigger automático para cálculo exacto y transparente de tiempo_transcurrido en mesa_ayuda_ticket
CREATE OR REPLACE FUNCTION fn_calcular_tiempo_transcurrido_ticket()
RETURNS TRIGGER AS $$
DECLARE
    minutos INT;
    horas INT;
    mins_restantes INT;
BEGIN
    IF NEW.estado IN ('ATENDIDO', 'CANCELADO') THEN
        IF NEW.fecha_resolucion IS NULL THEN
            NEW.fecha_resolucion := CURRENT_TIMESTAMP;
        END IF;
        minutos := GREATEST(1, ROUND(EXTRACT(EPOCH FROM (NEW.fecha_resolucion - NEW.fecha_registro)) / 60)::INT);
    ELSE
        minutos := GREATEST(1, ROUND(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - NEW.fecha_registro)) / 60)::INT);
    END IF;

    IF minutos < 60 THEN
        NEW.tiempo_transcurrido := CONCAT(minutos, ' min');
    ELSE
        horas := minutos / 60;
        mins_restantes := minutos % 60;
        IF mins_restantes = 0 THEN
            NEW.tiempo_transcurrido := CONCAT(horas, ' h');
        ELSE
            NEW.tiempo_transcurrido := CONCAT(horas, ' h ', LPAD(mins_restantes::TEXT, 2, '0'), ' min');
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_calcular_tiempo_transcurrido ON mesa_ayuda_ticket;
CREATE TRIGGER trg_calcular_tiempo_transcurrido
BEFORE INSERT OR UPDATE ON mesa_ayuda_ticket
FOR EACH ROW
EXECUTE FUNCTION fn_calcular_tiempo_transcurrido_ticket();

-- Recalcular todos los tickets existentes para auditoría perfecta
UPDATE mesa_ayuda_ticket SET estado = estado;
