## Context

La base vigente es una aplicación JavaFX/FXML con un shell de una sola ventana,
ViewModels y servicios de aplicación ya implementados. El rediseño debe
mejorar la apariencia sin convertir el controller en un lugar para reglas de
negocio ni duplicar el modelo persistente.

## Decisions

### Design system JavaFX

`base.css` contendrá la estructura común y los nombres de tokens visuales
mediante propiedades CSS de JavaFX. `light.css` y `dark.css` definirán los
valores de superficie, texto, borde, acento, estados y controles propios de
cada tema. Se usará `Segoe UI` como primera opción segura en Windows, con el
fallback del sistema.

La escala visual será compacta y consistente, basada en 4/8/12/16/24/32 px.
Los componentes reutilizables se limitarán a piezas que reduzcan duplicación
real: encabezados de página, estados, barras de filtros/acciones, tarjetas
compactas y selectores de tags.

### Layout

El shell seguirá siendo un `BorderPane` con navegación lateral y contenido
central en `ScrollPane`. Las vistas usarán `VBox`, `HBox`, `FlowPane`,
`GridPane`, `StackPane` y `TitledPane` con `hgrow`, `vgrow`,
`ColumnConstraints`, wrapping y límites min/pref/max. No se usará
posicionamiento absoluto ni se resolverá el DPI con anchos fijos
arbitrariamente grandes.

### Presentation boundaries

FXML seguirá describiendo la estructura estable del shell. Controllers finos
seguirán delegando navegación y eventos a ViewModels/casos de uso. Los nuevos
componentes visuales no accederán directamente a JDBC, SSH ni al runtime.
Domain y Application permanecerán libres de JavaFX y de decisiones de estilo.

### Interaction preservation

Los IDs de navegación, textos funcionales, atajos, acciones, validaciones y
semántica de los diálogos existentes se conservan. Los selectores deben usar
una presentación humana mediante cell factories, converters o modelos de
presentación; nunca `Object.toString()` técnico. Las acciones principales
seguirán visibles y las acciones secundarias podrán agruparse en `Más`.

### Visual validation

La inspección real de la ventana se documentará por pantalla, tema y escala.
La ausencia de captura o de un entorno Windows con una escala concreta no se
considerará una validación automática de ese caso. Los tests comprobarán
bindings, navegación, presentación y contratos CSS estables, no píxeles.

## Non-Goals

- Cambiar requisitos o specs del cambio `create-ssh-tunnel-manager`.
- Añadir WebView, HTML/CSS web, Electron o frameworks de UI.
- Añadir funcionalidades de grupos, SSH, persistencia o portabilidad.
- Declarar superada la validación manual de DPI o el smoke funcional por
  inspección estática.

