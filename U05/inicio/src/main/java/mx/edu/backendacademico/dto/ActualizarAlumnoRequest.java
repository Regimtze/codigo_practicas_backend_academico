package mx.edu.backendacademico.dto;

// Define la parte editable; matrícula, identidad y estatus no se alteran por PUT.
public record ActualizarAlumnoRequest(String nombre, String correo) {}