-- Si hay duplicados históricos, la migración falla y permite revisarlos sin borrar datos clínicos.
CREATE UNIQUE INDEX uq_odontograma_paciente_version ON version_odontograma(paciente_id, numero_version);
CREATE UNIQUE INDEX uq_odontograma_borrador ON version_odontograma(paciente_id) WHERE estado = 'BORRADOR';
