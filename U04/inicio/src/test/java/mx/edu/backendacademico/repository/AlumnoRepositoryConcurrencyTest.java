package mx.edu.backendacademico.repository;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.MatriculaDuplicadaException;
import org.junit.jupiter.api.Test;


class AlumnoRepositoryConcurrenciaTest {

    private static final String MATRICULA = "2048024";
    private static final String NOMBRE_1 = "Regina Martínez";
    private static final String NOMBRE_2 = "Luis Gómez";

    private enum Resultado { GUARDADO, CONFLICTO }

    @Test
    void dosGuardadosSimultaneosDeLaMismaMatriculaDejanUnRegistroYUnConflicto() throws Exception {
        var repository = new AlumnoRepositoryEnMemoria();
        var listos = new CountDownLatch(2);   // cada hilo avisa que ya está en la línea de salida
        var salida = new CountDownLatch(1);   // el hilo de la prueba da el disparo de salida
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<Resultado> primero = pool.submit(
                    intento(repository, listos, salida, NOMBRE_1, "regina@uanl.mx"));
            Future<Resultado> segundo = pool.submit(
                    intento(repository, listos, salida, NOMBRE_2, "luis@uanl.mx"));

            assertTrue(listos.await(5, SECONDS), "Los dos hilos no llegaron a la salida");

            salida.countDown();

            List<Resultado> resultados = List.of(primero.get(5, SECONDS), segundo.get(5, SECONDS));
            List<Alumno> almacenados = repository.buscarTodos();

            int guardados = Collections.frequency(resultados, Resultado.GUARDADO);
            int conflictos = Collections.frequency(resultados, Resultado.CONFLICTO);

            System.out.println("=== Evidencia de concurrencia ===");
            System.out.println("Matrícula disputada: " + MATRICULA);
            System.out.println("Resultados de los dos hilos: " + resultados);
            System.out.println("Guardados: " + guardados + " | Conflictos: " + conflictos);
            System.out.println("Registros en el almacén: " + almacenados.size());
            almacenados.forEach(a -> System.out.println("  -> " + a));

            assertEquals(1, guardados, resultados::toString);
            assertEquals(1, conflictos, resultados::toString);

            assertEquals(1, almacenados.size(), almacenados::toString);
            Alumno unico = almacenados.get(0);
            assertEquals(MATRICULA, unico.matricula());

            assertTrue(Set.of(NOMBRE_1, NOMBRE_2).contains(unico.nombre()));
        } finally {
            pool.shutdownNow();
        }
    }

    private static Callable<Resultado> intento(AlumnoRepository repository,
                                               CountDownLatch listos,
                                               CountDownLatch salida,
                                               String nombre,
                                               String correo) {
        return () -> {
            listos.countDown();
            salida.await();
            try {
                repository.guardar(new Alumno(null, MATRICULA, nombre, correo, EstatusAlumno.ACTIVO));
                return Resultado.GUARDADO;
            } catch (MatriculaDuplicadaException e) {
                return Resultado.CONFLICTO;
            }
        };
    }
}