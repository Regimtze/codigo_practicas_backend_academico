package mx.edu.backendacademico.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import mx.edu.backendacademico.config.SecurityConfig;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.service.AlumnoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Filtros reales: aquí sí participan autenticación y autorización.
@WebMvcTest(MiExpedienteController.class)
@Import(SecurityConfig.class)
class MiExpedienteTest {
    @Autowired MockMvc mvc;
    @MockitoBean AlumnoService service;

    @Test void propietarioVeSuExpediente() throws Exception {
        when(service.buscarPorMatricula("ALUMNO"))
                .thenReturn(new Alumno(1L, "ALUMNO", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO));

        mvc.perform(get("/api/v1/mi-expediente").with(httpBasic("alumno", "alumno123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricula").value("ALUMNO"));
    }

    @Test void usuarioAjenoNoPuedeElegirOtroExpediente() throws Exception {
        when(service.buscarPorMatricula("ALUMNO2"))
                .thenReturn(new Alumno(2L, "ALUMNO2", "Emmy", "emmy@u.mx", EstatusAlumno.ACTIVO));

        // alumno2 intenta pedir el expediente de ALUMNO mediante un parámetro.
        mvc.perform(get("/api/v1/mi-expediente?matricula=ALUMNO").with(user("alumno2").roles("ALUMNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricula").value("ALUMNO2"));

        // El servicio nunca consultó el expediente del propietario.
        verify(service, never()).buscarPorMatricula("ALUMNO");
    }

    @Test void sinCredencialesRecibe401() throws Exception {
        mvc.perform(get("/api/v1/mi-expediente")).andExpect(status().isUnauthorized());
    }

    @Test void administradorRecibe403PorRol() throws Exception {
        // Política del laboratorio: esta operación es sólo para el rol ALUMNO.
        mvc.perform(get("/api/v1/mi-expediente").with(httpBasic("admin", "admin123")))
                .andExpect(status().isForbidden());
        verify(service, never()).buscarPorMatricula(anyString());
    }
}
