package mx.edu.backendacademico.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import mx.edu.backendacademico.config.CacheConfig;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.AlumnoNoEncontradoException;
import mx.edu.backendacademico.repository.AlumnoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

// Contexto mínimo: sólo la infraestructura de caché y el servicio, sin base de datos.
@SpringJUnitConfig({CacheConfig.class, AlumnoService.class})
class CacheAlumnoTest {
    @Autowired AlumnoService servicio;
    @Autowired CacheManager cacheManager;
    @MockitoBean AlumnoRepository repository;

    // El contexto (y su caché) se comparte entre pruebas: se vacía para aislarlas.
    @BeforeEach void vaciarCaches() {
        cacheManager.getCacheNames().forEach(n -> cacheManager.getCache(n).clear());
    }

    private static Alumno ada() { return new Alumno(1L, "A1", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO); }

    @Test void dosLecturasDelMismoIdUsanUnSoloAccesoAlRepositorio() {
        when(repository.buscarPorId(1L)).thenReturn(Optional.of(ada()));
        servicio.buscar(1L);
        servicio.buscar(1L);
        verify(repository, times(1)).buscarPorId(1L);
    }

    @Test void unaEscrituraInvalidaLaCache() {
        when(repository.buscarPorId(1L)).thenReturn(Optional.of(ada()));
        when(repository.existePorMatricula(any())).thenReturn(false);
        when(repository.guardar(any())).thenReturn(ada());
        servicio.buscar(1L);
        servicio.crear("B2", "Bea", "bea@u.mx");
        servicio.buscar(1L);
        verify(repository, times(2)).buscarPorId(1L);
    }

    @Test void laAusenciaNoSeAlmacenaEnCache() {
        when(repository.buscarPorId(99L)).thenReturn(Optional.empty());
        assertThrows(AlumnoNoEncontradoException.class, () -> servicio.buscar(99L));
        assertThrows(AlumnoNoEncontradoException.class, () -> servicio.buscar(99L));
        verify(repository, times(2)).buscarPorId(99L);
    }

    @Test void elListadoSeReutilizaHastaQueCambiaElCatalogo() {
        when(repository.buscarTodos()).thenReturn(List.of(ada()));
        servicio.listar();
        servicio.listar();
        verify(repository, times(1)).buscarTodos();
    }

    @Test void elExpedientePorMatriculaNoSeCachea() {
        when(repository.buscarPorMatricula("A1")).thenReturn(Optional.of(ada()));
        servicio.buscarPorMatricula("A1");
        servicio.buscarPorMatricula("A1");
        verify(repository, times(2)).buscarPorMatricula("A1");
    }
}
