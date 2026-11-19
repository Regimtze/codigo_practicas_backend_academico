package mx.edu.backendacademico.controller;

import java.util.Locale;
import mx.edu.backendacademico.dto.AlumnoMapper;
import mx.edu.backendacademico.dto.AlumnoResponse;
import mx.edu.backendacademico.service.AlumnoService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mi-expediente")
public class MiExpedienteController {
    private final AlumnoService service;

    public MiExpedienteController(AlumnoService service) { this.service = service; }

    // La identidad sale del principal autenticado, nunca de parámetros del cliente.
    // Convención del laboratorio: matrícula = nombre de usuario en mayúsculas.
    @GetMapping
    public AlumnoResponse miExpediente(Authentication autenticacion) {
        var matricula = autenticacion.getName().toUpperCase(Locale.ROOT);
        return AlumnoMapper.toResponse(service.buscarPorMatricula(matricula));
    }
}
