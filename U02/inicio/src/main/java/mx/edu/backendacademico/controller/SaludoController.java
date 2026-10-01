package mx.edu.backendacademico.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1")
public class SaludoController {
//    @GetMapping("/saludo")
//  public Map<String, String> saludar() {
// Jackson serializa este valor; no se construye JSON por concatenación.
// return Map.of("mensaje", "Hola backend");

    @GetMapping("/saludo")
    public Map<String, String> saludo(@RequestParam(defaultValue = "Mundo") String nombre) {
        String mensaje = "Hola, " + nombre;
        return Map.of("mensaje", mensaje);
    }

}