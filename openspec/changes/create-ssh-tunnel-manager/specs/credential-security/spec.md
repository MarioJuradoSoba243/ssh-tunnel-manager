# Delta for Credential Security

## ADDED Requirements

### Requirement: Credenciales recordadas por host
El sistema SHALL permitir almacenar usuario y contraseña por host SSH y reutilizarlos sin solicitar la contraseña en cada inicio.

#### Scenario: Reutilizar una contraseña guardada
- GIVEN un host con credenciales almacenadas
- WHEN se inicia un túnel que utiliza ese host
- THEN el sistema usa las credenciales almacenadas
- AND no solicita de nuevo la contraseña

### Requirement: Visualización y cambio de contraseña
El sistema SHALL permitir mostrar temporalmente la contraseña almacenada desde el editor de host y SHALL permitir cambiarla.

#### Scenario: Mostrar contraseña
- GIVEN que el usuario edita un host
- WHEN activa `Mostrar contraseña`
- THEN la contraseña se hace visible en esa pantalla
- AND vuelve a ocultarse al abandonar o cerrar el editor

### Requirement: Protección de credenciales en modo instalado
En una instalación normal, el sistema SHALL evitar persistir las contraseñas como texto plano dentro de la configuración principal de la aplicación.

El mecanismo concreto de protección SHALL definirse en el diseño.

#### Scenario: Persistencia normal de un host
- GIVEN que la aplicación funciona en modo instalado
- WHEN se guarda un host con contraseña
- THEN la configuración principal no contiene la contraseña en texto plano

### Requirement: Credenciales transferibles en modo portable
En modo portable, el sistema SHALL permitir que la configuración y las credenciales necesarias viajen junto con la aplicación y puedan utilizarse después de copiarla a otra máquina Windows 11.

El sistema SHALL advertir que quien tenga acceso a esos datos puede recuperar las contraseñas.

#### Scenario: Copiar una instalación portable
- GIVEN una versión portable con hosts y credenciales
- WHEN se copia el directorio completo a otra máquina compatible
- THEN la aplicación puede cargar las credenciales necesarias desde los datos portables

### Requirement: Autorización manual de host keys
La primera vez que se encuentre una identidad SSH desconocida para una combinación de hostname y puerto, el sistema SHALL mostrar su fingerprint y SHALL requerir autorización explícita antes de continuar.

#### Scenario: Primera conexión
- GIVEN que no existe fingerprint confiable para `host:port`
- WHEN se recibe una host key
- THEN el sistema muestra el host, puerto, algoritmo y fingerprint
- AND identifica la posición del host en la ruta SSH
- AND solo continúa si el usuario la autoriza

### Requirement: Cambio de fingerprint
Si una combinación de hostname y puerto presenta una fingerprint distinta de la confiable, el sistema SHALL bloquear la conexión hasta intervención explícita.

#### Scenario: Identidad modificada
- GIVEN una fingerprint previamente confiable
- WHEN el host presenta otra fingerprint
- THEN la conexión se bloquea
- AND se muestran la fingerprint almacenada y la nueva
- AND la nueva no se acepta automáticamente

### Requirement: Gestión de confianza
El sistema SHALL permitir eliminar una fingerprint confiable desde la libreta de hosts para forzar nueva autorización en la siguiente conexión.

#### Scenario: Eliminar confianza
- GIVEN un host con fingerprint confiable
- WHEN el usuario elimina su confianza
- THEN la fingerprint deja de considerarse autorizada
- AND la siguiente conexión vuelve a requerir aprobación

### Requirement: Fallo de autenticación no interactivo durante operaciones masivas
El sistema SHALL representar un fallo de autenticación como error del túnel y SHALL NOT abrir diálogos de contraseña automáticos durante el inicio de un grupo.

#### Scenario: Contraseña incorrecta en un grupo
- GIVEN un grupo que inicia varios túneles
- WHEN uno de los hosts rechaza su contraseña
- THEN el túnel correspondiente queda en `ERROR`
- AND los otros túneles continúan
- AND la UI ofrece editar el host y reintentar posteriormente
