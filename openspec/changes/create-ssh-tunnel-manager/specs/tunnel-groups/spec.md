# Delta for Tunnel Groups

## ADDED Requirements

### Requirement: Grupos reutilizables y relación muchos-a-muchos
El sistema SHALL permitir crear grupos persistentes de túneles.

Un túnel MAY no pertenecer a ningún grupo y MAY pertenecer a varios grupos. Un grupo SHALL NOT adquirir propiedad sobre los túneles que contiene.

#### Scenario: Túnel compartido entre grupos
- GIVEN un túnel perteneciente a `Backend` y `Debug`
- WHEN el usuario consulta ambos grupos
- THEN el mismo túnel aparece referenciado por ambos
- AND existe una única configuración de túnel

### Requirement: Inicio masivo concurrente
El sistema SHALL iniciar los túneles de un grupo de forma concurrente, respetando el límite global de inicios simultáneos.

Los túneles que excedan el límite SHALL permanecer en estado `QUEUED` hasta disponer de capacidad.

#### Scenario: Grupo mayor que el límite de concurrencia
- GIVEN un grupo de 10 túneles cerrados
- AND un límite de concurrencia de 3
- WHEN el usuario inicia el grupo
- THEN como máximo 3 túneles se encuentran iniciando simultáneamente
- AND los demás quedan en cola hasta disponer de capacidad

### Requirement: Túneles ya abiertos al iniciar un grupo
El sistema SHALL NOT volver a abrir un túnel que ya se encuentre activo cuando el usuario inicia un grupo que lo contiene.

#### Scenario: Túnel compartido ya abierto
- GIVEN que `PostgreSQL` está abierto por una acción anterior
- AND pertenece al grupo `Debug`
- WHEN el usuario inicia `Debug`
- THEN `PostgreSQL` continúa usando su instancia existente
- AND se inician únicamente los túneles del grupo que lo necesiten

### Requirement: Fallos independientes dentro de un grupo
El fallo de un túnel durante el inicio de un grupo SHALL NOT detener los demás túneles del grupo.

#### Scenario: Un miembro del grupo falla
- GIVEN un grupo con cinco túneles
- WHEN uno falla y los otros cuatro pueden abrirse
- THEN los cuatro túneles correctos permanecen abiertos
- AND el túnel fallido muestra su error individual

### Requirement: Parada masiva sin ownership
Cuando el usuario detenga un grupo, el sistema SHALL detener todos los túneles de ese grupo que estén activos o iniciándose, incluso si pertenecen también a otros grupos.

#### Scenario: Detener un túnel compartido mediante un grupo
- GIVEN un túnel abierto que pertenece a dos grupos
- WHEN el usuario detiene uno de esos grupos
- THEN el túnel se detiene
- AND el otro grupo refleja su nuevo estado parcial

### Requirement: Advertencia de conflictos locales
Antes de guardar un grupo, el sistema SHALL detectar túneles miembros con el mismo endpoint local efectivo y SHALL advertir al usuario del conflicto.

El usuario MAY confirmar el guardado a pesar de la advertencia.

#### Scenario: Dos túneles usan el mismo puerto local
- GIVEN dos túneles del grupo configurados con `127.0.0.1:5432`
- WHEN el usuario guarda el grupo
- THEN el sistema advierte de que no podrán ejecutarse simultáneamente con ese endpoint
- AND permite corregir el grupo o confirmar el guardado

### Requirement: Duplicado de grupos
El sistema SHALL permitir duplicar un grupo conservando sus referencias a túneles y su clasificación, pero asignando un nuevo identificador.

#### Scenario: Duplicar un grupo
- GIVEN un grupo existente
- WHEN el usuario lo duplica
- THEN se crea un grupo independiente con un ID distinto
- AND contiene inicialmente los mismos túneles
