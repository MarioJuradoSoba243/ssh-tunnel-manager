# Delta for Tunnel Configuration

## ADDED Requirements

### Requirement: Configuración de Local Port Forwarding
El sistema SHALL permitir crear túneles SSH exclusivamente de tipo Local Port Forwarding en v1.

Cada túnel SHALL definir un identificador persistente, nombre, bind local, puerto local, host destino, puerto destino, host SSH final y, opcionalmente, una ruta de jumps y ajustes avanzados.

El bind local SHALL usar `127.0.0.1` por defecto.

#### Scenario: Crear un túnel básico
- GIVEN que existe un host SSH final
- WHEN el usuario crea un túnel indicando puerto local, host destino y puerto destino
- THEN el túnel queda guardado como Local Port Forwarding
- AND usa `127.0.0.1` como bind local si el usuario no especifica otro valor

#### Scenario: Configurar un bind distinto de localhost
- GIVEN que el usuario edita el bind local
- WHEN selecciona una dirección distinta de `127.0.0.1`
- THEN el sistema permite guardarla
- AND advierte cuando la configuración expone el puerto más allá de localhost

### Requirement: Validación local antes del guardado
El sistema SHALL validar localmente los datos estructurados antes de permitir guardar un túnel.

La validación SHALL cubrir como mínimo campos obligatorios, puertos entre `1` y `65535`, referencias existentes, nombres únicos y coherencia de la ruta SSH.

El guardado SHALL NOT requerir que ningún servidor remoto esté disponible.

#### Scenario: Puerto fuera de rango
- GIVEN que el usuario introduce un puerto `70000`
- WHEN el formulario se valida
- THEN el sistema muestra un error de validación
- AND no permite guardar hasta corregirlo

#### Scenario: Guardar mientras el servidor está desconectado
- GIVEN una configuración localmente válida
- AND que el host SSH no es alcanzable
- WHEN el usuario guarda el túnel
- THEN la configuración se guarda
- AND no se exige una conexión remota durante el guardado

### Requirement: Ajustes avanzados por túnel
El sistema SHALL permitir que un túnel use los valores globales de timeout, keep-alive y reintentos o defina overrides propios.

El sistema SHALL permitir opciones SSH avanzadas adicionales, pero SHALL impedir que contradigan campos estructurados gestionados por la aplicación, incluidos endpoint local, destino, credenciales y ruta de jumps.

#### Scenario: Override de keep-alive
- GIVEN que existe un valor global de keep-alive
- WHEN el usuario configura un keep-alive personalizado en un túnel
- THEN ese túnel usa su valor personalizado
- AND los demás túneles continúan usando el valor global

#### Scenario: Opción avanzada incompatible
- GIVEN que el usuario añade una opción SSH avanzada
- WHEN dicha opción intenta redefinir un dato controlado por el modelo estructurado
- THEN el sistema rechaza o invalida esa opción
- AND explica el conflicto

### Requirement: Edición de un túnel activo
El sistema SHALL permitir editar un túnel activo, pero SHALL requerir reiniciarlo para aplicar cambios que afecten a su ejecución.

El sistema SHALL NOT mantener una configuración persistida distinta de la configuración realmente ejecutada sin informar al usuario.

#### Scenario: Guardar cambios de un túnel abierto
- GIVEN un túnel en estado `OPEN`
- WHEN el usuario modifica su configuración y pulsa guardar
- THEN el sistema informa de que debe reiniciarse
- AND ofrece cancelar o guardar y reiniciar

### Requirement: Duplicado de túneles
El sistema SHALL permitir duplicar un túnel conservando su configuración, clasificación y ajustes, pero asignando un nuevo identificador.

#### Scenario: Duplicar un túnel
- GIVEN un túnel existente
- WHEN el usuario ejecuta la acción de duplicar
- THEN se crea una nueva configuración con un ID distinto
- AND el usuario puede modificar los campos necesarios antes de utilizarla

### Requirement: Eliminación de túneles
El sistema SHALL eliminar automáticamente las referencias de un túnel en todos sus grupos cuando el usuario confirme su eliminación.

Si el túnel está activo, el sistema SHALL detenerlo antes de eliminarlo.

#### Scenario: Eliminar un túnel utilizado por varios grupos
- GIVEN un túnel perteneciente a varios grupos
- WHEN el usuario solicita eliminarlo
- THEN el sistema informa de los grupos afectados
- AND tras la confirmación elimina el túnel
- AND elimina sus referencias de todos los grupos
