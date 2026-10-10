package mx.edu.backendacademico.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AlumnoTest {
    @Test void bajaConservaIdentidadSinModificarElOriginal() {
        var alumno = new Alumno(1L, " a001 ", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var baja = alumno.darDeBaja();
        // Comprueba tanto el resultado como la ausencia de efectos laterales.
        assertEquals("A001", baja.matricula());
        assertEquals(alumno.id(), baja.id());
        assertEquals(EstatusAlumno.BAJA, baja.estatus());
        assertEquals(EstatusAlumno.ACTIVO, alumno.estatus());
    }

    @Test void rechazaMatriculaVacia() {
        assertThrows(IllegalArgumentException.class, () ->
                new Alumno(null, " ", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO));
    }

    @Test void materiaRechazaCreditosNoPositivos() {
        assertThrows(IllegalArgumentException.class, () -> new Materia(null, "M1", "Análisis", 0));
    }

    // Reto
    @Test void reactivarConservaIdentidadSinModificarElOriginal() {
        var activo = new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var baja = activo.darDeBaja();
        var reactivado = baja.reactivar();

        // La transición BAJA → ACTIVO produce un valor nuevo...
        assertEquals(EstatusAlumno.ACTIVO, reactivado.estatus());
        assertEquals(baja.id(), reactivado.id());
        assertEquals(baja.matricula(), reactivado.matricula());
        // ...y el valor de baja no cambia.
        assertEquals(EstatusAlumno.BAJA, baja.estatus());
        // Ida y vuelta: mismos componentes, así que es igual al primero.
        assertEquals(activo, reactivado);
    }

    @Test void mismoIdDistintoCorreoNoSonIgualesComoValores() {
        var antes = new Alumno(1L, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO);
        var despues = new Alumno(1L, "A001", "Ada", "ada.lovelace@u.mx", EstatusAlumno.ACTIVO);

        // Como valores (record) son distintos: difiere un componente.
        assertNotEquals(antes, despues);
        // Pero representan al mismo alumno: comparten identidad académica.
        assertEquals(antes.id(), despues.id());
        assertEquals(antes.matricula(), despues.matricula());
    }
}