# Delta for SSH Host Management

## ADDED Requirements

### Requirement: Libreta de hosts SSH reutilizables
El sistema SHALL permitir crear, consultar, editar y reutilizar hosts SSH persistentes.

Cada host SHALL incluir como mínimo un identificador persistente, nombre, hostname o IP, puerto SSH, usuario y referencia a sus credenciales. El puerto SSH SHALL usar `22` por defecto y SHALL poder modificarse.

#### Scenario: Crear un host con el puerto por defecto
- GIVEN que el usuario está creando un host SSH
- WHEN informa nombre, hostname, usuario y contraseña sin modificar el puerto
- THEN el host se guarda con puerto SSH `22`
- AND queda disponible para utilizarse como jump server o host SSH final

#### Scenario: Crear un host con un puerto personalizado
- GIVEN que el usuario está creando un host SSH
- WHEN configura un puerto SSH válido distinto de `22`
- THEN el host se guarda con el puerto indicado

### Requirement: Nombres de host no ambiguos
El sistema SHALL exigir nombres únicos entre los hosts SSH almacenados.

#### Scenario: Intentar guardar un nombre ya utilizado
- GIVEN que existe un host llamado `Corp Bastion`
- WHEN el usuario intenta guardar otro host con el mismo nombre
- THEN el sistema rechaza el guardado
- AND informa de que el nombre ya está utilizado

### Requirement: Dependencias al eliminar hosts
El sistema SHALL impedir eliminar un host SSH mientras esté referenciado por uno o más túneles.

#### Scenario: Eliminar un host utilizado
- GIVEN que `Corp Bastion` es utilizado por varios túneles
- WHEN el usuario intenta eliminarlo
- THEN el sistema bloquea la eliminación
- AND muestra los túneles que dependen del host

#### Scenario: Eliminar un host sin dependencias
- GIVEN que un host no está referenciado por ningún túnel
- WHEN el usuario confirma su eliminación
- THEN el host se elimina definitivamente
