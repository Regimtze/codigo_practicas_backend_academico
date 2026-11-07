SELECT installed_rank, version, description, checksum, success
  FROM flyway_schema_history ORDER BY installed_rank;

SELECT oid AS oid_alumnos, relfilenode FROM pg_class WHERE relname = 'alumnos';

SELECT COUNT(*) AS filas_alumnos FROM alumnos;

SELECT conname, pg_get_constraintdef(oid)
  FROM pg_constraint WHERE conrelid = 'alumnos'::regclass AND conname = 'ck_nombre_no_vacio';

SELECT id, matricula, nombre FROM alumnos WHERE LENGTH(TRIM(nombre)) = 0;
