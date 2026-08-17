# Delta for Application Settings

## ADDED Requirements

### Requirement: Valores globales de conexión
El sistema SHALL permitir configurar valores globales de timeout de conexión, intervalo de keep-alive y número máximo de reintentos.

#### Scenario: Modificar el keep-alive global
- GIVEN varios túneles sin override de keep-alive
- WHEN el usuario modifica el valor global
- THEN los túneles que no tengan override usarán el nuevo valor en futuros inicios

### Requirement: Límite global de concurrencia
El sistema SHALL permitir configurar un límite global para el número de túneles que pueden estar iniciándose simultáneamente.

#### Scenario: Cambiar el límite de concurrencia
- GIVEN un límite global existente
- WHEN el usuario establece otro valor válido
- THEN las siguientes operaciones masivas respetan el nuevo límite

### Requirement: Preferencias de notificación
El sistema SHALL permitir activar o desactivar globalmente las notificaciones operativas de Windows generadas por la aplicación.

#### Scenario: Notificaciones desactivadas
- GIVEN que el usuario ha desactivado las notificaciones
- WHEN ocurre un evento que normalmente generaría una notificación
- THEN no se muestra una notificación del sistema
- AND el evento sigue siendo visible dentro de la aplicación

### Requirement: Preferencia de tema
El sistema SHALL permitir seleccionar tema claro, oscuro o seguimiento del tema de Windows.

#### Scenario: Seguir el tema del sistema
- GIVEN la preferencia `Seguir Windows`
- WHEN cambia el tema de Windows
- THEN la aplicación adopta el tema correspondiente

### Requirement: Nivel de logging
El sistema SHALL permitir seleccionar un nivel de logging basado en severidades estándar.

#### Scenario: Seleccionar DEBUG
- GIVEN que el usuario necesita mayor detalle de diagnóstico
- WHEN selecciona `DEBUG`
- THEN los nuevos logs incluyen eventos admitidos por ese nivel y superiores
