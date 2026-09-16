## ADDED Requirements

### Requirement: Design system visual consistente

La UI SHALL utilizar un design system JavaFX CSS común para superficies,
texto, bordes, acento, estados, radios y espaciado en los temas claro y
oscuro.

#### Scenario: Cambiar entre temas

- GIVEN una pantalla funcional de la aplicación
- WHEN el usuario cambia entre claro, oscuro o seguir Windows
- THEN la jerarquía de superficies y textos se conserva
- AND los controles esenciales mantienen contraste y estados visibles

### Requirement: Shell de escritorio responsivo

El shell SHALL conservar la navegación funcional actual y SHALL usar layouts
adaptables sin posicionamiento absoluto.

#### Scenario: Ventana estrecha

- GIVEN una ventana cercana a 1024×700 o un escalado alto
- WHEN el usuario navega por una vista con contenido largo
- THEN los controles principales siguen accesibles mediante wrapping o scroll
- AND no se recortan textos esenciales ni se solapan controles

### Requirement: Controles y tablas legibles

Los controles, tablas, listas y diálogos SHALL representar normal, hover,
focus, selected, disabled, error y warning de forma legible sin depender solo
del color.

#### Scenario: Seleccionar una fila

- GIVEN una tabla con varias filas
- WHEN el usuario selecciona una fila con ratón o teclado
- THEN la selección es visible mediante fondo y/o indicador adicional
- AND el texto de la fila sigue siendo legible

### Requirement: Presentación humana de selectores

Los selectores SHALL mostrar nombres y contexto comprensibles para el usuario
y SHALL ocultar representaciones técnicas de objetos Java.

#### Scenario: Seleccionar un proyecto

- GIVEN un ComboBox de proyecto, entorno, grupo, host o jump
- WHEN el control está cerrado o muestra su lista
- THEN muestra el nombre humano y el contexto útil
- AND nunca muestra una referencia técnica de objeto

### Requirement: Acciones jerarquizadas

Las vistas de operación SHALL mostrar las acciones frecuentes de forma clara y
agrupar las secundarias sin ocultar las acciones esenciales.

#### Scenario: Operar un túnel

- GIVEN la vista Túneles
- WHEN no hay conflicto con la selección actual
- THEN Nuevo, Editar, Iniciar y Detener tienen una jerarquía visible
- AND el resto puede estar agrupado en un menú identificable

