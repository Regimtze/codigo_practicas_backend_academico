<#
 Evidencia repetible del reto U12 (sin IDE). Requisitos: app en http://localhost:8080 (docker compose up -d), PowerShell, curl.exe.
 Uso:  .\evidencia.ps1            -> casos HTTP (200/201/400/404/409/401/403/403)
       .\evidencia.ps1 -Postgres  -> además detiene PostgreSQL, observa readiness y lo restablece
#>
param([switch]$Postgres)
$base = "http://localhost:8080"
$jar  = Join-Path $env:TEMP "u12-cookies.txt"
Remove-Item $jar -ErrorAction SilentlyContinue

function Codigo($titulo, $esperado, [string[]]$args2) {
    $real = & curl.exe -s -o NUL -w "%{http_code}" @args2
    $marca = if ($real -eq "$esperado") { "OK  " } else { "FALLA" }
    "{0} {1,-52} esperado={2} real={3}" -f $marca, $titulo, $esperado, $real
}
function Cuerpo([string[]]$args2) { & curl.exe -s @args2 }

# Token CSRF + cookie de sesión del docente (misma sesión para escribir).
$csrf = (Cuerpo @("-c",$jar,"-u","docente:docente123","$base/api/v1/csrf") | ConvertFrom-Json).token
$sufijo = (Get-Date).ToString("MMddHHmmss")
$mat = "E$sufijo"
$json = @{matricula=$mat; nombre="Ada Lovelace"; correo="ada@u.mx"} | ConvertTo-Json -Compress
$tmp = Join-Path $env:TEMP "u12-body.json"

"=== Éxito, validación, ausencia, conflicto ==="
Codigo "200 saludo público" 200 @("$base/api/v1/saludo")
[IO.File]::WriteAllText($tmp, $json)
Codigo "201 crea alumno $mat" 201 @("-b",$jar,"-u","docente:docente123","-H","X-CSRF-TOKEN: $csrf","-H","Content-Type: application/json","--data-binary","@$tmp","$base/api/v1/alumnos")
Codigo "409 misma matrícula otra vez" 409 @("-b",$jar,"-u","docente:docente123","-H","X-CSRF-TOKEN: $csrf","-H","Content-Type: application/json","--data-binary","@$tmp","$base/api/v1/alumnos")
[IO.File]::WriteAllText($tmp, '{"matricula":"","nombre":"A","correo":"incorrecto"}')
Codigo "400 datos inválidos" 400 @("-b",$jar,"-u","docente:docente123","-H","X-CSRF-TOKEN: $csrf","-H","Content-Type: application/json","--data-binary","@$tmp","$base/api/v1/alumnos")
Codigo "404 alumno inexistente" 404 @("-u","docente:docente123","$base/api/v1/alumnos/999999")

"=== Rechazos de seguridad de causas distintas ==="
Codigo "401 sin credenciales" 401 @("$base/api/v1/alumnos")
Codigo "403 por ROL (alumno lee catálogo)" 403 @("-u","alumno:alumno123","$base/api/v1/alumnos")
[IO.File]::WriteAllText($tmp, $json.Replace($mat, "X$sufijo"))
Codigo "403 por CSRF (docente, rol correcto, sin token)" 403 @("-u","docente:docente123","-H","Content-Type: application/json","--data-binary","@$tmp","$base/api/v1/alumnos")
Codigo "403 por ROL (docente en /mi-expediente)" 403 @("-u","docente:docente123","$base/api/v1/mi-expediente")

"=== Acceso por propietario (alumno -> matrícula ALUMNO) ==="
[IO.File]::WriteAllText($tmp, '{"matricula":"ALUMNO","nombre":"Alumno Demo","correo":"alumno@u.mx"}')
$null = & curl.exe -s -o NUL -b $jar -u "docente:docente123" -H "X-CSRF-TOKEN: $csrf" -H "Content-Type: application/json" --data-binary "@$tmp" "$base/api/v1/alumnos"  # 201 la 1a vez, 409 después
Codigo "200 alumno ve su expediente" 200 @("-u","alumno:alumno123","$base/api/v1/mi-expediente")
Cuerpo @("-u","alumno:alumno123","$base/api/v1/mi-expediente?matricula=OTRA")
Codigo "ignora ?matricula= ajena (sigue siendo 200 propio)" 200 @("-u","alumno:alumno123","$base/api/v1/mi-expediente?matricula=OTRA")

"=== Observación ==="
Codigo "200 health público" 200 @("$base/actuator/health")
Codigo "200 liveness" 200 @("$base/actuator/health/liveness")
Codigo "200 readiness (con PostgreSQL)" 200 @("$base/actuator/health/readiness")
Codigo "401 métricas sin credenciales" 401 @("$base/actuator/metrics")
Codigo "403 métricas con rol DOCENTE" 403 @("-u","docente:docente123","$base/actuator/metrics")
Codigo "200 métricas con ADMIN" 200 @("-u","admin:admin123","$base/actuator/metrics")

if ($Postgres) {
    "=== Falla controlada de PostgreSQL ==="
    docker compose stop postgres | Out-Null
    Start-Sleep -Seconds 12
    Codigo "503 readiness con PostgreSQL detenido" 503 @("$base/actuator/health/readiness")
    Codigo "200 liveness sigue vivo" 200 @("$base/actuator/health/liveness")
    Codigo "500 API que necesita la base" 500 @("-u","docente:docente123","$base/api/v1/alumnos/999998")
    "Detalle de readiness para ADMIN:"
    Cuerpo @("-u","admin:admin123","$base/actuator/health/readiness")
    docker compose start postgres | Out-Null
    Start-Sleep -Seconds 20
    Codigo "200 readiness tras restablecer PostgreSQL" 200 @("$base/actuator/health/readiness")
}
