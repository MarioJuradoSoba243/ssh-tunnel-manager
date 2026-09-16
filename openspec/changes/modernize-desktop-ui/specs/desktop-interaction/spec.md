## ADDED Requirements

### Requirement: Interacción de edición conservada

El rediseño SHALL conservar las validaciones, acciones, atajos y persistencia
existentes. Los componentes visuales reutilizables SHALL delegar a los
ViewModels y casos de uso actuales.

#### Scenario: Guardar una configuración

- GIVEN un formulario funcionalmente válido
- WHEN el usuario pulsa Guardar
- THEN se ejecuta el mismo flujo de aplicación que antes del rediseño
- AND la UI refleja el resultado sin acceder directamente a JDBC o SSH

### Requirement: Selección accesible por teclado y ratón

Las tablas, listas y checkboxes de selección SHALL poder operarse con teclado y
ratón cuando ya formen parte del flujo funcional.

#### Scenario: Seleccionar miembros de un grupo

- GIVEN el editor de grupo
- WHEN el usuario marca miembros con ratón o teclado
- THEN la selección visual cambia inmediatamente
- AND el guardado/reapertura conserva los miembros seleccionados

### Requirement: Estados vacíos y errores accionables

Las vistas y diálogos SHALL ofrecer un texto claro, acciones disponibles y
detalles técnicos desplazables cuando corresponda, sin cambiar la semántica
del error.

#### Scenario: No hay túneles

- GIVEN una instalación sin túneles configurados
- WHEN se abre la vista Túneles
- THEN se muestra un estado vacío orientativo
- AND se ofrece la acción funcional para crear el primer túnel

