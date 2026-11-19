package mx.edu.backendacademico.controller;

import mx.edu.backendacademico.dto.AlumnoMapper;
import mx.edu.backendacademico.dto.AlumnoResponse;
import mx.edu.backendacademico.service.AlumnoService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MiExpedienteController {
    private final AlumnoService servicio;

    public MiExpedienteController(AlumnoService servicio) {
        this.servicio = servicio;
    }

    // Regla por objeto: la identidad sale del principal autenticado, nunca del cliente.
    // En el laboratorio, la matrícula del alumno es el nombre de usuario en mayúsculas.
    @GetMapping("/api/v1/mi-expediente")
    public AlumnoResponse miExpediente(Authentication autenticacion) {
        var matricula = autenticacion.getName().toUpperCase();
        return AlumnoMapper.toResponse(servicio.buscarPorMatricula(matricula));
    }
}
