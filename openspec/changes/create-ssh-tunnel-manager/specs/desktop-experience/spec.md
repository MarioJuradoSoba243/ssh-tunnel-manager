# Delta for Desktop Experience

## ADDED Requirements

### Requirement: Plataforma Windows 11 x64
La aplicación SHALL soportar oficialmente Windows 11 x64.

El uso habitual SHALL NOT requerir privilegios de administrador.

#### Scenario: Ejecutar como usuario estándar
- GIVEN un usuario estándar de Windows 11
- WHEN instala o ejecuta una distribución que no requiere elevación para esa acción
- THEN puede utilizar las funciones normales de gestión de túneles sin ejecutar la aplicación como administrador

### Requirement: Distribuciones instalable y portable
La aplicación SHALL distribuirse en v1 mediante un instalador y mediante una versión portable.

#### Scenario: Usar la versión portable
- GIVEN el paquete portable
- WHEN el usuario lo ejecuta desde un directorio con permisos de escritura adecuados
- THEN puede utilizar la funcionalidad principal sin instalar la aplicación

### Requirement: Interfaz responsiva
La interfaz SHALL permanecer utilizable durante operaciones SSH, persistencia, backups, logging, búsqueda, filtrado y operaciones masivas.

Las operaciones de red SHALL NOT bloquear el hilo de interacción de la UI.

#### Scenario: Iniciar un grupo grande
- GIVEN un grupo con numerosos túneles
- WHEN se inicia
- THEN el usuario puede navegar y realizar otras acciones mientras las conexiones progresan

### Requirement: Escala objetivo
La aplicación SHALL manejar de forma usable al menos 100 configuraciones de túnel y una carga esperada aproximada de hasta 20 túneles simultáneamente activos.

#### Scenario: Buscar entre más de cien túneles
- GIVEN al menos 100 túneles configurados
- WHEN el usuario utiliza búsqueda o filtros
- THEN la interacción no presenta bloqueos perceptibles de la interfaz

### Requirement: Arranque local sin conexiones SSH
La aplicación SHALL cargar el Hub y la configuración local sin iniciar conexiones SSH hasta una acción explícita del usuario.

#### Scenario: Abrir la aplicación
- GIVEN que existen numerosos túneles configurados
- WHEN el usuario abre la aplicación
- THEN aparece el Hub utilizando datos locales
- AND no se inicia ningún túnel automáticamente

### Requirement: Tema configurable
La aplicación SHALL soportar tema claro, tema oscuro y seguimiento del tema de Windows, con posibilidad de selección manual.

#### Scenario: Selección manual de tema oscuro
- GIVEN cualquier tema actual
- WHEN el usuario selecciona oscuro
- THEN la UI utiliza el tema oscuro aunque Windows utilice otro tema

### Requirement: Accesibilidad visual de estados
La aplicación SHALL comunicar estados mediante texto y/o iconografía además del color.

#### Scenario: Estado de error
- GIVEN un túnel en error
- WHEN se representa en la UI
- THEN el usuario puede identificar el estado sin depender únicamente del color

### Requirement: Escalado y tamaño inicial de ventana
La ventana principal SHALL usar aproximadamente `1024×700` como tamaño inicial y SHALL soportar los niveles habituales de escalado de Windows, incluidos `100%`, `125%`, `150%` y `200%`, sin pérdida funcional.

#### Scenario: Escalado al 150 %
- GIVEN Windows configurado al 150 %
- WHEN se abre la aplicación
- THEN los controles esenciales siguen accesibles
- AND el texto relevante no queda inutilizable por recortes de layout

### Requirement: Uso mediante teclado
Las funciones principales SHALL poder operarse mediante teclado y la aplicación SHALL proporcionar atajos para acciones frecuentes.

Los atajos exactos SHALL definirse en diseño de interacción.

#### Scenario: Abrir búsqueda global por teclado
- GIVEN la aplicación enfocada
- WHEN el usuario utiliza el atajo definido para búsqueda global
- THEN el foco pasa a la búsqueda sin requerir el ratón

### Requirement: Idioma de v1 y preparación para localización
La UI visible SHALL estar en español en v1.

La estructura de la aplicación SHALL permitir incorporar inglés en una versión futura sin requerir reescribir cada pantalla.

#### Scenario: Textos de interfaz de v1
- GIVEN una instalación de v1
- WHEN el usuario navega por la aplicación
- THEN los textos de UI están en español

### Requirement: Notificaciones operativas relevantes
La aplicación MAY utilizar notificaciones de Windows para eventos importantes como un grupo finalizado con errores, un túnel que agota sus reintentos o una operación masiva con fallos.

La aplicación SHALL NOT requerir una notificación por cada conexión o reconexión normal.

#### Scenario: Túnel agota reintentos
- GIVEN notificaciones activadas
- WHEN un túnel agota sus reintentos y queda en error
- THEN la aplicación puede mostrar una notificación relevante
- AND el error sigue visible en la UI

### Requirement: Confirmaciones proporcionales
El sistema SHALL pedir confirmación para operaciones destructivas o de gran impacto y SHALL evitar confirmaciones innecesarias en operaciones frecuentes y reversibles.

#### Scenario: Detener un túnel individual
- GIVEN un túnel abierto
- WHEN el usuario pulsa detener
- THEN se inicia la parada sin pedir una confirmación adicional

#### Scenario: Detener todos los túneles
- GIVEN varios túneles activos
- WHEN el usuario pulsa `Detener todos`
- THEN el sistema solicita confirmación antes de detenerlos

### Requirement: Aislamiento de fallos
Un fallo de un túnel, operación de backup o proceso SSH SHALL NOT cerrar toda la aplicación cuando el error pueda aislarse.

#### Scenario: Un túnel falla dentro de un grupo
- GIVEN varios túneles iniciándose
- WHEN uno produce una excepción o error operativo aislable
- THEN la aplicación continúa funcionando
- AND los demás túneles continúan según corresponda

### Requirement: Sin actualización integrada
La versión v1 SHALL NOT realizar comprobación ni instalación automática de actualizaciones.

#### Scenario: Ejecutar v1
- GIVEN la aplicación instalada
- WHEN se inicia o utiliza
- THEN no contacta con un servicio de actualización propio para actualizarse automáticamente

### Requirement: Documentación de uso
El sistema SHALL distribuir documentación de uso en español que explique los flujos y funciones visibles para el usuario.

La documentación SHALL actualizarse cuando un cambio modifique comportamiento, navegación, configuración, errores, importación/exportación, backup, seguridad operativa o gestión de túneles de forma relevante para el usuario.

La estructura de documentación SHALL permitir añadir una versión en inglés en el futuro.

#### Scenario: Nueva funcionalidad visible
- GIVEN un cambio que añade o modifica una función visible para el usuario
- WHEN el cambio se considera terminado
- THEN la documentación de uso correspondiente está creada o actualizada
- AND describe el comportamiento vigente de la aplicación

#### Scenario: Cambio interno sin impacto de uso
- GIVEN un cambio puramente interno que no modifica el comportamiento observable
- WHEN se completa
- THEN no es obligatorio modificar la guía de uso
