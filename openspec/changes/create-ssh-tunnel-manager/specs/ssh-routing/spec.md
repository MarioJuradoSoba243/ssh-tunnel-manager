# Delta for SSH Routing

## ADDED Requirements

### Requirement: Ruta SSH de hasta dos jumps
El sistema SHALL permitir que un túnel utilice cero, uno o dos jump servers intermedios seguidos de un host SSH final.

El host SSH final SHALL ser obligatorio y los jumps SHALL ser opcionales.

#### Scenario: Conexión sin jump server
- GIVEN un túnel con host SSH final y sin jumps
- WHEN se inicia el túnel
- THEN el sistema establece la sesión SSH directamente con el host final

#### Scenario: Conexión con dos jump servers
- GIVEN un túnel configurado con `Jump 1`, `Jump 2` y host SSH final
- WHEN se inicia el túnel
- THEN el sistema establece la ruta en ese orden
- AND utiliza el host final para alcanzar el destino del forwarding

### Requirement: Credenciales independientes por salto
El sistema SHALL utilizar la configuración y credenciales propias de cada host SSH de la ruta.

#### Scenario: Usuarios distintos por host
- GIVEN una ruta donde cada jump y el host final tienen usuarios y contraseñas diferentes
- WHEN se establece la ruta
- THEN cada conexión utiliza las credenciales asociadas al host correspondiente

### Requirement: Destino independiente del host SSH final
El sistema SHALL permitir que `destinationHost:destinationPort` sea un servicio distinto del host SSH final y sea alcanzado desde dicho host final.

#### Scenario: Forwarding hacia un host interno
- GIVEN una ruta que termina en `backend-server`
- AND un destino `db.internal:5432`
- WHEN el túnel se abre correctamente
- THEN el forwarding local termina en `db.internal:5432` desde la perspectiva del host SSH final

### Requirement: Coherencia de la ruta
El sistema SHALL impedir rutas inválidas.

Como mínimo, `Jump 2` SHALL NOT existir sin `Jump 1` y un mismo host SHALL NOT aparecer repetido dentro de la misma ruta.

#### Scenario: Jump 2 sin Jump 1
- GIVEN que el usuario selecciona un segundo jump
- AND no existe un primer jump
- WHEN intenta guardar
- THEN el sistema muestra un error
- AND no permite guardar la ruta

#### Scenario: Host repetido en la ruta
- GIVEN que un host ya aparece en la ruta
- WHEN el usuario intenta seleccionarlo de nuevo en otra posición
- THEN el sistema rechaza la ruta

### Requirement: Prueba completa de conexión
El sistema SHALL proporcionar una acción `Probar conexión` que compruebe la ruta SSH completa y la alcanzabilidad de `destinationHost:destinationPort` sin dejar un túnel persistente abierto.

#### Scenario: Prueba satisfactoria con dos jumps
- GIVEN una configuración con dos jumps, host final y destino
- WHEN el usuario ejecuta `Probar conexión`
- THEN el sistema valida secuencialmente la ruta SSH
- AND comprueba que el destino es alcanzable desde el extremo SSH final
- AND finaliza los recursos temporales al terminar la prueba

#### Scenario: Fallo en un jump
- GIVEN una ruta de varios hosts
- WHEN falla la conexión o autenticación de uno de los jumps
- THEN la prueba falla
- AND identifica qué tramo de la ruta produjo el error
