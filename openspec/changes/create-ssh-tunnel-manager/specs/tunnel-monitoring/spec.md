# Delta for Tunnel Monitoring

## ADDED Requirements

### Requirement: Telemetría operativa básica
El sistema SHALL mostrar para cada túnel su estado actual y, cuando corresponda, el tiempo transcurrido desde que el usuario solicitó mantenerlo activo.

Ese tiempo SHALL conservarse durante reconexiones automáticas y SHALL reiniciarse cuando el usuario detenga el túnel y vuelva a iniciarlo posteriormente.

#### Scenario: Mostrar tiempo activo
- GIVEN un túnel que el usuario inició hace dos horas
- AND que durante ese periodo sufrió una reconexión automática
- WHEN el usuario visualiza el Hub o la lista de túneles
- THEN ve el estado actual del túnel
- AND ve aproximadamente dos horas de tiempo activo

#### Scenario: Reiniciar tiempo después de una parada manual
- GIVEN un túnel que llevaba una hora activo
- WHEN el usuario lo detiene y posteriormente vuelve a iniciarlo
- THEN el tiempo activo de la nueva ejecución comienza de nuevo

### Requirement: Progreso de reconexión
Durante una reconexión limitada, el sistema SHALL mostrar el intento actual y el máximo configurado.

#### Scenario: Tercer intento de cinco
- GIVEN un túnel realizando el tercer intento de un máximo de cinco
- WHEN el usuario consulta su estado
- THEN ve `RECONNECTING`
- AND ve un progreso equivalente a `3/5`

### Requirement: Mensajes comprensibles y detalle técnico
El sistema SHALL presentar mensajes comprensibles para errores SSH y de forwarding habituales y SHALL permitir consultar detalle técnico adicional.

#### Scenario: Error de autenticación
- GIVEN que un host rechaza las credenciales
- WHEN el intento falla
- THEN la UI indica que la autenticación ha fallado
- AND permite abrir los detalles técnicos del error

### Requirement: Historial de eventos de la sesión
El sistema SHALL mantener para cada túnel un historial de eventos operativos de la sesión actual.

El historial MAY incluir conexión, desconexión, reintentos, recuperación, parada y errores, y SHALL NOT persistirse como estado de túnel para la siguiente ejecución.

#### Scenario: Pérdida y recuperación
- GIVEN un túnel que estaba abierto
- WHEN pierde conexión y posteriormente reconecta
- THEN su historial de sesión muestra la pérdida, los intentos y la recuperación en orden temporal

### Requirement: Resumen derivado de grupos
El sistema SHALL calcular el estado resumido de un grupo a partir de los estados de sus túneles y SHALL NOT tratarlo como estado propietario independiente.

#### Scenario: Grupo parcialmente abierto
- GIVEN un grupo con tres túneles abiertos, uno cerrado y uno en error
- WHEN se muestra el grupo
- THEN la UI refleja un estado parcial
- AND presenta conteos que permiten entender la composición del estado

### Requirement: Progreso de operaciones masivas
El sistema SHALL mostrar progreso mediante conteos reales de estados durante una operación masiva y SHALL NOT requerir una barra de porcentaje artificial.

#### Scenario: Inicio de grupo en progreso
- GIVEN un grupo cuyo inicio sigue en curso
- WHEN el usuario consulta el progreso
- THEN ve conteos de abiertos, conectando, en cola y error
- AND puede seguir utilizando el resto de la aplicación
