-- SOLO para la base temporal creada por start-closure-preview.ps1.
DO $$
DECLARE c BIGINT; u BIGINT; r RECORD;
BEGIN
  INSERT INTO clinica(nombre) VALUES ('Clínica de validación local') RETURNING id INTO c;
  FOR r IN SELECT codigo,id FROM rol WHERE codigo IN ('ADMIN_CLINICA','RECEPCIONISTA','ODONTOLOGO','PACIENTE') LOOP
    INSERT INTO usuario(correo,clave_hash,nombres,apellidos,estado)
    VALUES (lower(r.codigo)||'@prueba.invalid',crypt('CierreLocal2026!',gen_salt('bf',12)),'Validación',r.codigo,'ACTIVO') RETURNING id INTO u;
    INSERT INTO usuario_clinica(clinica_id,usuario_id,rol_id,estado) VALUES(c,u,r.id,'ACTIVO');
    IF r.codigo='PACIENTE' THEN
      INSERT INTO paciente(clinica_id,usuario_id,nombres,apellidos,tipo_documento,numero_documento,estado)
      VALUES(c,u,'Paciente','Validación','DNI','99999001','ACTIVO');
    END IF;
  END LOOP;

  -- Insumo de prueba con presentación CAJA y existencias cerradas
  INSERT INTO inventario_insumo(clinica_id, codigo, nombre, categoria, presentacion, unidad, clasificacion, contenido_descriptivo, stock_cerrado, stock_en_uso, stock, stock_minimo, costo_unitario, activo)
  VALUES (c, 'MAT-001', 'Guantes de látex desechables', 'Bioseguridad', 'CAJA', 'cajas', 'CONSUMIBLE', 'Caja x 100 unidades', 5, 0, 5, 2, 28.50, TRUE);

  -- Cita virtual para teleconsulta integrada
  INSERT INTO cita(clinica_id, paciente_id, odontologo_id, creado_por_id, inicio_en, fin_en, modalidad, estado, motivo, consultorio)
  VALUES (
    c,
    (SELECT id FROM paciente WHERE clinica_id = c LIMIT 1),
    (SELECT id FROM usuario_clinica WHERE clinica_id = c AND rol_id = (SELECT id FROM rol WHERE codigo = 'ODONTOLOGO') LIMIT 1),
    (SELECT id FROM usuario_clinica WHERE clinica_id = c AND rol_id = (SELECT id FROM rol WHERE codigo = 'ADMIN_CLINICA') LIMIT 1),
    NOW() - INTERVAL '10 minutes',
    NOW() + INTERVAL '50 minutes',
    'VIRTUAL',
    'CONFIRMADA',
    'Teleconsulta inicial de control',
    'Sillón Virtual 1'
  );
END $$;
