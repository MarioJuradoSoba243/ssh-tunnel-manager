# Delta for Tunnel Lifecycle

## ADDED Requirements

### Requirement: Estados operativos de un túnel
El sistema SHALL representar el ciclo de vida de un túnel mediante estados observables que incluyan al menos `CLOSED`, `QUEUED`, `CONNECTING`, `OPEN`, `RECONNECTING`, `STOPPING` y `ERROR`.

#### Scenario: Inicio correcto
- GIVEN un túnel cerrado y válido
- WHEN el usuario lo inicia
- THEN el túnel pasa por `CONNECTING`
- AND termina en `OPEN` si la conexión se establece correctamente

### Requirement: Parada manual cancelable desde cualquier fase activa
El sistema SHALL permitir detener un túnel mientras esté en cola, conectando, abierto o reconectando.

Una parada manual SHALL cancelar futuros intentos automáticos y terminar en `CLOSED`.

#### Scenario: Detener durante reconexión
- GIVEN un túnel en `RECONNECTING`
- WHEN el usuario pulsa detener
- THEN se cancela la reconexión
- AND no se realizan nuevos intentos
- AND el túnel termina en `CLOSED`

#### Scenario: Detener un túnel en cola
- GIVEN un túnel en `QUEUED`
- WHEN el usuario pulsa detener
- THEN sale de la cola
- AND pasa a `CLOSED` sin iniciar una conexión

### Requirement: Keep-alive
Los túneles abiertos SHALL utilizar el keep-alive efectivo configurado para reducir cierres por inactividad.

#### Scenario: Túnel con override de keep-alive
- GIVEN un túnel con un keep-alive personalizado
- WHEN se abre
- THEN utiliza ese valor en lugar del global

### Requirement: Reconexión limitada
Cuando un túnel abierto pierda inesperadamente su conexión, el sistema SHALL intentar recuperarla hasta el número efectivo de reintentos configurado.

Tras agotar los reintentos, el túnel SHALL quedar en `ERROR`.

#### Scenario: Recuperación antes de agotar reintentos
- GIVEN un túnel abierto que pierde conexión
- WHEN una reconexión tiene éxito antes de alcanzar el límite
- THEN el túnel vuelve a `OPEN`
- AND el contador de reconexión deja de progresar

#### Scenario: Reintentos agotados
- GIVEN un túnel que no puede reconectar
- WHEN alcanza su número máximo de intentos
- THEN pasa a `ERROR`
- AND no vuelve a intentarlo automáticamente por una posterior recuperación de VPN o red

### Requirement: Reintento manual
El sistema SHALL permitir reintentar manualmente un túnel en error y SHALL reiniciar su contador de intentos.

#### Scenario: Reintentar después de agotar intentos
- GIVEN un túnel en `ERROR` por reintentos agotados
- WHEN el usuario pulsa `Reintentar`
- THEN el contador vuelve a su valor inicial
- AND comienza un nuevo ciclo de conexión

### Requirement: Puerto local ocupado
Si el endpoint local solicitado no puede abrirse porque ya está ocupado, el sistema SHALL dejar el túnel en `ERROR`, SHALL identificar el conflicto y SHALL NOT seleccionar automáticamente otro puerto.

#### Scenario: Puerto ocupado
- GIVEN que `127.0.0.1:15432` ya está utilizado
- WHEN se intenta iniciar un túnel con ese endpoint
- THEN el túnel pasa a `ERROR`
- AND el usuario recibe un mensaje que identifica el puerto ocupado
- AND el sistema no cambia el puerto

### Requirement: Reanudación después de suspensión
Tras reanudarse Windows de suspensión o hibernación, el sistema SHALL detectar los túneles que pretendían seguir abiertos y cuya conexión haya dejado de ser válida, y SHALL aplicarles su política normal de reconexión.

Un túnel detenido manualmente SHALL NOT reiniciarse por la reanudación del sistema.

#### Scenario: Reanudar con un túnel que estaba abierto
- GIVEN un túnel abierto antes de suspender Windows
- WHEN Windows se reanuda y la conexión ya no es válida
- THEN el túnel entra en `RECONNECTING`
- AND utiliza su política habitual de reintentos

#### Scenario: Reanudar con un túnel detenido manualmente
- GIVEN un túnel que el usuario había detenido
- WHEN Windows se reanuda
- THEN el túnel permanece `CLOSED`
