-- Los archivos anteriores permanecen clínicos: no se publica información técnica al personal administrativo.
ALTER TABLE archivo_adjunto ADD COLUMN ambito VARCHAR(20) NOT NULL DEFAULT 'CLINICO'
    CHECK(ambito IN ('CLINICO','ADMINISTRATIVO'));
CREATE UNIQUE INDEX uq_inventario_nombre ON inventario_insumo(clinica_id,lower(nombre));
