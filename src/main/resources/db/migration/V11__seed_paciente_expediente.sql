-- V11: Semillero de expediente clínico vinculado para usuario paciente de prueba
DO $$
DECLARE
    v_clinica_id BIGINT;
    v_user_paciente BIGINT;
BEGIN
    SELECT id INTO v_clinica_id FROM clinica ORDER BY id ASC LIMIT 1;
    SELECT id INTO v_user_paciente FROM usuario WHERE correo = 'paciente@coronyx.pe';

    IF v_clinica_id IS NOT NULL AND v_user_paciente IS NOT NULL THEN
        IF NOT EXISTS (SELECT 1 FROM paciente WHERE usuario_id = v_user_paciente) THEN
            INSERT INTO paciente (
                clinica_id, usuario_id, tipo_documento, numero_documento,
                nombres, apellidos, fecha_nacimiento, telefono, correo,
                estado, ciudad, tipo_sangre, seguro
            ) VALUES (
                v_clinica_id, v_user_paciente, 'DNI', '77889900',
                'Luis', 'Paciente', '1995-05-15', '987654321', 'paciente@coronyx.pe',
                'ACTIVO', 'Lima', 'O+', 'PARTICULAR'
            );
        END IF;
    END IF;
END $$;
