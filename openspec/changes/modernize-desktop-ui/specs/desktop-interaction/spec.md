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

### Requirement: Operación contextual del workspace

La interfaz SHALL ofrecer detalle contextual del elemento seleccionado, filtros
visibles y acciones rápidas sin duplicar reglas de aplicación. La paleta de
comandos SHALL reutilizar los mismos comandos de navegación y creación que la
UI principal.

#### Scenario: Inspeccionar un túnel

- GIVEN la vista Túneles con un túnel seleccionado
- WHEN el usuario cambia la selección
- THEN un panel de detalle muestra ruta SSH, forwarding, estado y actividad
- AND el panel no abre una segunda configuración persistente del túnel

#### Scenario: Usar la paleta de comandos

- GIVEN el shell visible
- WHEN el usuario pulsa Ctrl+K y escribe una consulta
- THEN puede navegar por comandos y entidades indexadas
- AND las teclas Arriba/Abajo/Enter/Escape operan la paleta sin bloquear la UI

#### Scenario: Aplicar filtros y selección múltiple

- GIVEN la vista Túneles
- WHEN el usuario aplica un filtro o selecciona varias filas
- THEN los filtros activos se muestran como chips removibles
- AND aparece una barra contextual con las operaciones de selección disponibles

### Requirement: Preferencias visuales persistentes

La UI SHALL permitir densidad Comfortable o Compact y SHALL persistir esa
preferencia mediante el servicio de ajustes existente. Las vistas SHALL seguir
siendo utilizables sin depender únicamente del color para comunicar estados.

#### Scenario: Cambiar densidad

- GIVEN Ajustes abierto
- WHEN el usuario selecciona Compact
- THEN se aplica la clase visual correspondiente y se guarda la preferencia
- AND el siguiente arranque conserva la densidad elegida

#### Scenario: La lista de túneles sobrevive a la navegación

- GIVEN existen túneles configurados y la vista Túneles ya está cargada
- WHEN el usuario navega a otra sección y vuelve a Túneles
- THEN se reutiliza la vista y su estado observable
- AND los túneles, filtros, ordenación y selección válida no se pierden
- AND no se registran listeners duplicados ni aparecen filas duplicadas

#### Scenario: El espacio vacío de la sidebar no es interactivo

- GIVEN la sidebar está visible con espacio libre bajo sus opciones
- WHEN el puntero pasa por una zona sin elemento de navegación
- THEN no se muestra estado hover, focus ni selección
- AND solo las celdas de navegación reales aceptan interacción

#### Scenario: La densidad compacta conserva la legibilidad

- GIVEN está seleccionada la densidad Compact
- WHEN un ComboBox, campo, botón, badge o fila de tabla renderiza texto
- THEN conserva una altura mínima suficiente para la tipografía
- AND el contenido no queda cortado verticalmente

### Requirement: Diálogos técnicos legibles y cerrables

Los diálogos de detalles, errores y diagnóstico SHALL ofrecer un cierre
explícito y responder a cerrar, Escape y Alt+F4. El contenido técnico largo
SHALL poder leerse mediante wrapping y desplazamiento sin alterar la semántica
del error.

#### Scenario: Cerrar detalles del túnel

- GIVEN el diálogo de detalles de un túnel
- WHEN el usuario pulsa Cerrar, Escape, la X o Alt+F4
- THEN el diálogo se cierra sin consumir la solicitud de cierre
- AND el estado del túnel no cambia

#### Scenario: Leer un error técnico largo

- GIVEN un error con historial o stack trace largo
- WHEN el usuario abre Ver detalles
- THEN el contenido se muestra en un control legible y desplazable
- AND existe una acción explícita para copiar el detalle

### Requirement: Selección compacta de etiquetas

Los editores de túnel y grupo SHALL mostrar las etiquetas seleccionadas como
chips compactos con eliminación individual. La lista de etiquetas SHALL abrirse
desde una acción Añadir y SHALL permitir búsqueda, selección múltiple y uso por
teclado.

#### Scenario: Añadir y quitar etiquetas

- GIVEN un editor de túnel o grupo con etiquetas disponibles
- WHEN el usuario abre Añadir y selecciona etiquetas
- THEN las etiquetas aparecen como chips en el formulario
- AND cada chip ofrece una acción accesible para quitarlo

### Requirement: Acciones de tabla perceptibles

Las acciones de fila y los indicadores de favorito SHALL mantener una escala
visual y un área de interacción utilizables. Los glifos SHALL tener texto
accesible y tooltip, y el favorito SHALL quedar centrado en su celda.

#### Scenario: Operar acciones rápidas de una fila

- GIVEN una fila de túnel visible
- WHEN el usuario apunta, enfoca o navega por sus acciones rápidas
- THEN cada acción mantiene un objetivo mínimo utilizable y un tooltip
- AND el estado de favorito se distingue por alineación y texto accesible,
  además del glifo
