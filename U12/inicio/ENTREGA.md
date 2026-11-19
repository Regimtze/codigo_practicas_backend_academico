# Entrega U12 · Observación, caché, tareas y empaquetado

Sistema: `backend-academico` 0.0.12 · Spring Boot 4.1.0 · JAR para Java 21, imagen con runtime Java 25 · PostgreSQL 17 (Compose).

## 1. Requisitos y construcción desde carpeta limpia
- JDK 25 instalado (`java -version` muestra 25.x); el proyecto compila con `release 21` (Java 21 como destino).
- Docker Desktop iniciado.
```powershell
cd U12\inicio
docker compose down -v          # sólo si quieres partir de datos vacíos / credenciales limpias
.\mvnw.cmd clean test package   # pruebas + JAR (target\backend-academico-0.0.12-SNAPSHOT.jar)
docker compose build
docker compose up -d
docker compose ps               # postgres: healthy, app: running
docker compose logs app         # busca "Started BackendAcademicoApplication"
```
## 2. Peticiones y evidencia repetible
```powershell
.\evidencia.ps1             # casos HTTP, imprime OK/FALLA con código esperado y real
.\evidencia.ps1 -Postgres   # añade la falla controlada de PostgreSQL
```
(`requests.http` contiene las mismas peticiones para el cliente HTTP del IDE, pero la evidencia no depende de él.)

| Observación | Petición | Código |
|---|---|---|
| Éxito | `POST /api/v1/alumnos` (docente + CSRF) | 201 |
| Validación | `POST` con matrícula vacía y correo inválido | 400 `DATOS_INVALIDOS` |
| Ausencia | `GET /api/v1/alumnos/999999` | 404 `ALUMNO_NO_ENCONTRADO` |
| Conflicto | `POST` repitiendo matrícula | 409 `MATRICULA_DUPLICADA` |
| Seguridad 1 (autenticación) | `GET /api/v1/alumnos` sin credenciales | 401 |
| Seguridad 2 (autorización por rol) | alumno en `GET /api/v1/alumnos`; docente en `/mi-expediente` | 403 |
| Seguridad 3 (CSRF, rol correcto) | docente `POST` sin `X-CSRF-TOKEN` | 403 |

Causas distintas: 401 = no se sabe quién eres; 403 por rol = sabemos quién eres y no tienes permiso; 403 por CSRF = tienes el permiso, pero la petición no demuestra que provenga de tu sesión.

## 3. Falla controlada de PostgreSQL y readiness
```powershell
docker compose stop postgres
curl.exe -i http://localhost:8080/actuator/health/readiness   # 503 DOWN
curl.exe -i http://localhost:8080/actuator/health/liveness    # 200 UP
curl.exe -u admin:admin123 http://localhost:8080/actuator/health/readiness   # detalle: db DOWN (sólo ADMIN)
docker compose start postgres                                 # readiness vuelve a 200 sin reiniciar la app
```
Qué ocurre: el grupo `readiness` incluye `db`, así que sin PostgreSQL la instancia declara que **no debe recibir tráfico** (503). Liveness no incluye la base: la app sigue "viva" y **no se reinicia** (reiniciarla no arregla la base y, con varias réplicas, provocaría reinicios en cascada). Al volver la base, el pool reconecta y readiness regresa a UP. Nota: con la base caída, cada comprobación espera hasta el `connectionTimeout` de Hikari (30 s por defecto), por eso el script espera unos segundos y la respuesta puede tardar.

## 4. Decisión sobre la caché
**Se conserva** `@Cacheable` en `buscar(id)` y `listar()` con `@CacheEvict(allEntries=true)` en cada escritura, **con condiciones**:
- *Por qué es válida la clave `#id`:* el resultado depende solo del id, no de quién consulta. La autorización ocurre antes (filtro y reglas de `SecurityConfig`), por lo que una entrada en caché nunca se entrega a alguien sin permiso.
- *Por qué `mi-expediente` NO se cachea:* su resultado depende de la identidad; una clave incompleta mezclaría expedientes entre personas. Es una consulta por índice único: el costo de no cachear es bajo.
- *Consistencia:* las invalidaciones se difieren al commit (`TransactionAwareCacheManagerProxy`), así que un rollback no deja la caché invalidada sin motivo y no se publica un dato no confirmado. No es consistencia estricta: una lectura concurrente puede publicar un valor antiguo en ciertos entrelazados.
- *Límites:* sin TTL ni tamaño máximo (crece con los ids consultados) y cada JVM tiene su caché; con 2 réplicas, una escritura en A deja datos obsoletos en B. Por eso es válida solo con **una instancia** y tolerancia explícita a obsolescencia; para varias réplicas habría que quitarla o usar Caffeine con TTL corto/Redis.
- *No se usa para decisiones de cupo:* `InscripcionService` consulta la base con bloqueo.
- Las ausencias (404) no se cachean; `actualizar`/`eliminar` llaman a `buscar` internamente, sin pasar por el proxy, y por tanto leen la base.

Evidencia: `CacheAlumnoTest` (5 pruebas) y, en ejecución real:
```powershell
docker compose -f compose.yaml -f compose.debug.yaml up -d      # activa log SQL
curl.exe -u docente:docente123 http://localhost:8080/api/v1/alumnos/1   # x2
docker compose logs app | Select-String "from alumnos"          # 1 SELECT, no 2
# crear/modificar un alumno y volver a consultar -> reaparece 1 SELECT
```

## 5. Mejora de acceso por propietario
- Nuevo `GET /api/v1/mi-expediente` (rol ALUMNO). La matrícula se obtiene del principal (`usuario.toUpperCase()`); **ningún parámetro del cliente** selecciona el recurso (`?matricula=OTRA` se ignora).
- Se retiró ALUMNO de `GET /api/v1/alumnos/**`: antes cualquier alumno podía leer los datos personales (correo) de todos. Ahora solo DOCENTE/ADMIN.
- Pruebas: `MiExpedienteTest` (5): propietario 200, parámetro ajeno ignorado, sin credenciales 401, docente 403, alumno 403 en catálogo.
- Límite: la convención matrícula = usuario es de laboratorio; en producción se vincularía usuario↔alumno con una columna/tabla.

## 6. Cierre ordenado
`docker compose down` (conserva el volumen; **no** uses `-v` si quieres conservar datos). Compose da 30 s (`stop_grace_period`) y Spring 20 s por fase (`server.shutdown=graceful`); en logs aparece "Cierre del bean de demostración" (`@PreDestroy`). Un `kill -9` no ejecuta `@PreDestroy`.

## 7. Autoevaluación
- Un health UP no demuestra que las reglas académicas funcionen: sólo las condiciones de sus indicadores.
- Dos contenedores no comparten caché local.
- Un volumen no es respaldo: falta copia recuperable y prueba de restauración.
