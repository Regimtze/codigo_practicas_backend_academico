DO $$
DECLARE
    incompatibles bigint;
BEGIN
    SELECT COUNT(*) INTO incompatibles
      FROM alumnos
     WHERE LENGTH(TRIM(nombre)) = 0;
    IF incompatibles > 0 THEN
        RAISE EXCEPTION 'V2 abortada: % fila(s) con nombre vacío. Corrígelas y vuelve a migrar.', incompatibles;
    END IF;
END $$;

ALTER TABLE alumnos
    ADD CONSTRAINT ck_nombre_no_vacio CHECK (LENGTH(TRIM(nombre)) > 0);
