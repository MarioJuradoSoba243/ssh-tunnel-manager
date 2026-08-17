# Delta for Diagnostics

## ADDED Requirements

### Requirement: Niveles estándar de logging
El sistema SHALL generar logs técnicos usando niveles de severidad estándar que incluyan como mínimo `TRACE`, `DEBUG`, `INFO`, `WARN` y `ERROR`.

#### Scenario: Evento informativo
- GIVEN que ocurre un evento operativo normal
- WHEN el nivel configurado permite eventos `INFO`
- THEN el evento puede registrarse con severidad `INFO`

### Requirement: Ausencia de contraseñas en logs
El sistema SHALL NOT escribir contraseñas SSH en logs, independientemente del nivel de logging seleccionado.

#### Scenario: Fallo de autenticación con DEBUG
- GIVEN que el nivel activo es `DEBUG`
- WHEN una autenticación falla
- THEN el log puede incluir host, usuario y causa técnica
- AND no incluye la contraseña

### Requirement: Rotación de logs
El sistema SHALL aplicar una política de rotación que impida que los archivos de log crezcan indefinidamente.

Los límites concretos SHALL definirse en `design.md`.

#### Scenario: Alcanzar el límite de rotación
- GIVEN que los logs alcanzan el límite configurado por diseño
- WHEN se generan nuevos eventos
- THEN el sistema rota o elimina logs antiguos según la política
- AND evita crecimiento ilimitado

### Requirement: Acceso a diagnóstico desde la UI
El sistema SHALL permitir abrir la carpeta de logs, consultar detalles técnicos de errores y copiar información diagnóstica relevante.

#### Scenario: Copiar un error de túnel
- GIVEN un túnel en error
- WHEN el usuario pulsa `Copiar error`
- THEN obtiene texto con timestamp, túnel, estado, ruta SSH y detalle técnico disponible
- AND el texto no contiene contraseñas

### Requirement: Paquete de diagnóstico
El sistema SHALL permitir generar un paquete de diagnóstico que incluya versión de la aplicación, versión de Windows, logs, configuración sanitizada, estado runtime y errores recientes.

El paquete MAY incluir hostnames, direcciones IP, puertos y usuarios, pero SHALL NOT incluir contraseñas.

#### Scenario: Exportar diagnóstico
- GIVEN que el usuario solicita un paquete de diagnóstico
- WHEN la exportación finaliza correctamente
- THEN el paquete contiene la información operativa acordada
- AND las contraseñas quedan excluidas
