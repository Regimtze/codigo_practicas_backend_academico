package mx.edu.backendacademico.dto;

// Define exclusivamente los campos que el cliente puede enviar al crear.


public record CrearAlumnoRequest(
        String matricula,
        String nombre,
        String correo
) {}