# Delta for Workspace Organization

## ADDED Requirements

### Requirement: Proyectos y entornos estructurados
El sistema SHALL permitir administrar catálogos de proyectos y entornos y asignarlos opcionalmente a túneles y grupos.

Un túnel o grupo MAY quedar sin proyecto y MAY quedar sin entorno.

#### Scenario: Túnel sin proyecto
- GIVEN que el usuario crea un túnel
- WHEN no selecciona proyecto
- THEN el túnel se guarda como `Sin proyecto`

#### Scenario: Entorno personalizado
- GIVEN que los entornos existentes no cubren una necesidad
- WHEN el usuario crea un nuevo entorno
- THEN puede asignarlo posteriormente a túneles y grupos

### Requirement: Tags libres
El sistema SHALL permitir asignar cero o más tags libres a túneles y grupos.

#### Scenario: Clasificación multidimensional
- GIVEN un túnel de producción
- WHEN el usuario asigna varios tags
- THEN todos los tags quedan asociados simultáneamente al túnel
- AND pueden utilizarse para localizarlo o filtrarlo

### Requirement: Eliminación de clasificaciones utilizadas
Cuando el usuario elimine un proyecto o entorno utilizado, el sistema SHALL advertir cuántas configuraciones se verán afectadas y SHALL requerir confirmación.

Tras confirmar, las configuraciones SHALL conservarse y quedar sin esa clasificación.

#### Scenario: Eliminar un entorno utilizado
- GIVEN que `PROD` está asignado a varios túneles
- WHEN el usuario lo elimina y confirma
- THEN los túneles no se eliminan
- AND quedan sin ese entorno

### Requirement: Favoritos
El sistema SHALL permitir marcar y desmarcar como favoritos tanto túneles individuales como grupos.

Los favoritos SHALL alimentar la sección de acciones rápidas del Hub.

#### Scenario: Marcar grupo favorito
- GIVEN un grupo existente
- WHEN el usuario lo marca como favorito
- THEN aparece en acciones rápidas del Hub

### Requirement: Elementos recientes
El sistema SHALL mantener los 8 túneles o grupos iniciados más recientemente mediante una acción explícita del usuario.

Las reconexiones automáticas SHALL NOT modificar la lista de recientes y un elemento repetido SHALL aparecer una sola vez en la posición más reciente.

#### Scenario: Reutilizar un elemento reciente
- GIVEN que un grupo ya aparece en recientes
- WHEN el usuario vuelve a iniciarlo manualmente
- THEN el grupo se mueve a la primera posición
- AND no aparece duplicado

### Requirement: Hub operativo
El sistema SHALL proporcionar un Hub principal que reúna acciones rápidas, favoritos, recientes, túneles activos y un resumen global de estados.

#### Scenario: Consultar el Hub con túneles activos
- GIVEN que existen túneles abiertos, reconectando y en error
- WHEN el usuario abre el Hub
- THEN ve un resumen de esas cantidades
- AND puede acceder a las listas filtradas correspondientes

### Requirement: Búsqueda global
El sistema SHALL proporcionar búsqueda global sobre túneles, grupos y hosts SSH y SHALL permitir iniciar o editar un resultado aplicable sin navegar previamente a otra pantalla.

#### Scenario: Buscar un túnel por texto
- GIVEN que existe `PostgreSQL Payments PROD`
- WHEN el usuario busca términos que lo identifican
- THEN el túnel aparece entre los resultados
- AND puede iniciarse o editarse desde el resultado

### Requirement: Filtrado y ordenación
Las vistas de gestión SHALL permitir buscar, filtrar y ordenar configuraciones para manejar al menos 100 túneles.

Los filtros SHALL cubrir como mínimo proyecto, entorno, tags, grupo y estado cuando sean aplicables.

#### Scenario: Filtrar por proyecto y entorno
- GIVEN múltiples túneles de varios proyectos y entornos
- WHEN el usuario filtra por `Payments` y `PROD`
- THEN la lista muestra únicamente los túneles que cumplen ambos criterios

### Requirement: Selección múltiple
La vista de túneles SHALL permitir seleccionar varios elementos y ejecutar operaciones masivas de inicio, parada, alta o baja en grupos, favorito y asignación de proyecto o entorno.

#### Scenario: Añadir varios túneles a un grupo
- GIVEN varios túneles seleccionados
- WHEN el usuario ejecuta `Añadir a grupo`
- THEN todos los seleccionados quedan asociados al grupo elegido

### Requirement: Primera ejecución orientada
Cuando no existan configuraciones, el Hub SHALL mostrar un estado vacío orientado a crear primero un host SSH y SHALL explicar brevemente el flujo host → túnel → grupo.

#### Scenario: Primera apertura
- GIVEN una instalación sin hosts ni túneles
- WHEN el usuario abre la aplicación
- THEN ve una acción principal para crear un host SSH
- AND ve una breve explicación de los siguientes pasos
