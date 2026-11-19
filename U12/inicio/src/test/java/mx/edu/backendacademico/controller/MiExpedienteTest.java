package mx.edu.backendacademico.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import mx.edu.backendacademico.config.SecurityConfig;
import mx.edu.backendacademico.domain.*;
import mx.edu.backendacademico.service.AlumnoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({MiExpedienteController.class, AlumnoController.class})
@Import(SecurityConfig.class)
class MiExpedienteTest {
    @Autowired MockMvc mvc;
    @MockitoBean AlumnoService service;

    @Test void propietarioVeSuExpediente() throws Exception {
        when(service.buscarPorMatricula("ALUMNO")).thenReturn(
            new Alumno(1L, "ALUMNO", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO));
        mvc.perform(get("/api/v1/mi-expediente").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.matricula").value("ALUMNO"));
    }

    @Test void elClienteNoPuedeElegirOtroExpediente() throws Exception {
        when(service.buscarPorMatricula("ALUMNO2")).thenReturn(
            new Alumno(2L, "ALUMNO2", "Bea", "bea@u.mx", EstatusAlumno.ACTIVO));
        mvc.perform(get("/api/v1/mi-expediente").param("matricula", "ALUMNO")
                .with(user("alumno2").roles("ALUMNO")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.matricula").value("ALUMNO2"));
        verify(service, never()).buscarPorMatricula("ALUMNO");
    }

    @Test void sinCredencialesRecibe401() throws Exception {
        mvc.perform(get("/api/v1/mi-expediente")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void docenteRecibe403PorRol() throws Exception {
        mvc.perform(get("/api/v1/mi-expediente").with(httpBasic("docente", "docente123")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void alumnoNoPuedeLeerElExpedienteDeOtro() throws Exception {
        mvc.perform(get("/api/v1/alumnos/2").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/alumnos").with(httpBasic("alumno", "alumno123")))
            .andExpect(status().isForbidden());
        verify(service, never()).buscar(anyLong());
        verify(service, never()).listar();
    }
}
