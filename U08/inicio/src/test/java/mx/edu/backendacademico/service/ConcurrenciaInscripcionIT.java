package mx.edu.backendacademico.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import mx.edu.backendacademico.exception.OperacionAcademicaException;
import mx.edu.backendacademico.repository.jpa.InscripcionRepository;
import mx.edu.backendacademico.repository.jpa.MateriaEntity;
import mx.edu.backendacademico.repository.jpa.MateriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

// Ejecutar contra PostgreSQL real (no H2): el comportamiento de bloqueo FOR UPDATE depende del motor.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrenciaInscripcionIT {

    @Autowired InscripcionService servicio;          // bean real, atravesando el proxy transaccional
    @Autowired MateriaRepository materias;
    @Autowired InscripcionRepository repoInscripciones;
    @Autowired JdbcTemplate jdbc;
    @Value("${local.server.port}") int puerto;

    private final List<Long> alumnos = new ArrayList<>();
    private Long materiaId;

    // Prueba principal: dos hilos, un solo lugar. Debe persistir una inscripción y rechazar la otra por SIN_CUPO.
    @Test
    void dosHilosCompitenPorUnSoloLugar() throws Exception {
        materiaId = materias.save(new MateriaEntity("TEST-CUPO1", "Cupo uno", 8, 1)).getId();
        Long a = crearAlumno("CUPO-A", "Alumno A", "a.cupo@universidad.mx");
        Long b = crearAlumno("CUPO-B", "Alumno B", "b.cupo@universidad.mx");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch listos = new CountDownLatch(2);
            CountDownLatch arranque = new CountDownLatch(1);
            Future<Object> fa = pool.submit(admision(a, listos, arranque));
            Future<Object> fb = pool.submit(admision(b, listos, arranque));

            assertThat(listos.await(5, TimeUnit.SECONDS)).isTrue();
            arranque.countDown();   // ambos hilos arrancan casi al mismo tiempo

            List<Object> resultados = List.of(fa.get(10, TimeUnit.SECONDS), fb.get(10, TimeUnit.SECONDS));
            assertThat(resultados.stream().filter(r -> r instanceof Long).count()).isEqualTo(1);
            assertThat(resultados.stream()
                    .filter(r -> r instanceof OperacionAcademicaException)
                    .map(r -> ((OperacionAcademicaException) r).getCodigo())
                    .toList())
                .containsExactly("SIN_CUPO");
            assertThat(repoInscripciones.countByMateriaId(materiaId)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // Evidencia: otra sesión mantiene el bloqueo de la fila de materia. La admisión debe quedar
    // esperando dentro de PostgreSQL (wait_event_type = 'Lock'), no en un monitor Java.
    @Test
    void laAdmisionEsperaUnBloqueoDeFilaEnPostgres() throws Exception {
        materiaId = materias.save(new MateriaEntity("TEST-LOCK", "Bloqueo", 8, 1)).getId();
        Long a = crearAlumno("LOCK-A", "Alumno A", "a.lock@universidad.mx");

        try (var holder = jdbc.getDataSource().getConnection()) {
            holder.setAutoCommit(false);
            try (var st = holder.prepareStatement("select id from materias where id = ? for update")) {
                st.setLong(1, materiaId);
                st.executeQuery();
            }

            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<Long> f = pool.submit(() -> servicio.inscribir(a, materiaId));

                Integer bloqueadas = 0;
                for (int i = 0; i < 50 && bloqueadas == 0; i++) {
                    Thread.sleep(100);
                    bloqueadas = jdbc.queryForObject(
                        "select count(*) from pg_stat_activity "
                        + "where datname = current_database() and wait_event_type = 'Lock'",
                        Integer.class);
                }
                assertThat(bloqueadas).isPositive();
                assertThat(f.isDone()).isFalse();

                holder.commit();                              // libera la fila; la admisión continúa
                assertThat(f.get(10, TimeUnit.SECONDS)).isNotNull();
            } finally {
                pool.shutdownNow();
            }
        }
        assertThat(repoInscripciones.countByMateriaId(materiaId)).isEqualTo(1);
    }

    private Callable<Object> admision(Long alumnoId, CountDownLatch listo, CountDownLatch arranque) {
        return () -> {
            listo.countDown();
            arranque.await();
            try {
                return servicio.inscribir(alumnoId, materiaId);
            } catch (OperacionAcademicaException e) {
                return e;   // se devuelve para contarlo; no escapa del hilo como excepción
            }
        };
    }

    private Long crearAlumno(String matricula, String nombre, String correo) {
        ResponseEntity<Void> r = RestClient.create("http://localhost:" + puerto)
                .post()
                .uri("/api/v1/alumnos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("matricula", matricula, "nombre", nombre, "correo", correo))
                .retrieve()
                .toBodilessEntity();
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = Long.parseLong(r.getHeaders().getLocation().getPath().replaceAll(".*/", ""));
        alumnos.add(id);
        return id;
    }

    @AfterEach
    void limpiar() {
        if (materiaId != null) {
            jdbc.update("delete from inscripciones where materia_id = ?", materiaId);
            jdbc.update("delete from materias where id = ?", materiaId);
            materiaId = null;
        }
        for (Long id : alumnos) {
            jdbc.update("delete from alumnos where id = ?", id);
        }
        alumnos.clear();
    }
}
