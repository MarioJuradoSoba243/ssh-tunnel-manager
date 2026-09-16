# Design system visual

Este documento describe el lenguaje visual de la interfaz JavaFX de SSH Tunnel
Manager. Es una guía de presentación; no cambia los requisitos funcionales ni
las fronteras de arquitectura.

## Principios

- Priorizar jerarquía, contraste y superficies antes que bordes fuertes.
- Mantener una densidad cómoda para una herramienta técnica de escritorio.
- Usar texto e indicadores además de color para comunicar estados.
- Permitir que el contenido crezca mediante wrapping y scroll, sin posiciones
  absolutas ni anchos arbitrariamente grandes.

## Paleta y temas

Los tokens comunes viven en `base.css`. `light.css` y `dark.css` proporcionan
los valores concretos para fondo, superficies, texto, bordes, selección, foco,
éxito, warning y error.

El tema claro utiliza un fondo gris muy claro y superficies blancas. El tema
oscuro utiliza azul grisáceo oscuro, nunca negro absoluto. La selección y el
foco mantienen contraste suficiente en ambos temas.

## Tipografía y espaciado

La fuente prioritaria es `Segoe UI`, con fallback del sistema. La jerarquía es:

- título de página: 24 px y negrita;
- título de sección: 15–16 px y negrita;
- cuerpo: 14 px;
- secundario/caption: 11–12 px.

El espaciado usa la escala 4, 8, 12, 16, 24 y 32 px. Los radios habituales
son 6–8 px para superficies y 5–6 px para controles.

## Componentes

- Botones: variantes `primary`, `secondary`, `ghost` y `danger`, con estados
  hover, pressed, focus y disabled.
- Navegación: sidebar con icono tipográfico sencillo, etiqueta, selección
  rellena y foco visible.
- Tablas: filas densas, cabecera diferenciada, selección legible y scrollbar
  utilizable.
- Métricas: tarjetas compactas en el Hub que muestran datos reales y permiten
  navegar a estados cuando la acción es aplicable.
- Formularios: grids expansibles con labels acotadas y controles que crecen.
- Tags: chips de selección compactos con eliminación individual y un picker de
  búsqueda multi-selección bajo la acción `+ Añadir`. El mismo control se usa
  en túneles y grupos.
- Diálogos: superficie común, cabecera diferenciada, acciones consistentes y
  contenido largo desplazable cuando procede. Los diálogos técnicos parten de
  un ancho legible aproximado de 600–760 px y un alto de 420–560 px, limitados
  por el área visible de la pantalla; no deben consumir una medida fija que
  impida usarlos con DPI alto.

## Iconos y objetivos de interacción

- Los iconos pequeños usan una escala visual de 14–16 px; las acciones de fila
  usan 16 px para que el glifo no se pierda junto al texto de la tabla.
- Todo botón de icono mantiene un objetivo mínimo de 32×32 px, incluyendo
  padding y estados de foco. Las acciones de una fila se separan 4 px y deben
  tener tooltip y nombre accesible.
- El favorito se centra en su celda y comunica la acción mediante tooltip; no
  depende de espacios invisibles ni únicamente del color.
- Los chips mantienen un botón de eliminación de al menos 22×22 px dentro del
  objetivo del chip, con foco y hover visibles.

## Layout

El shell usa un `BorderPane`: navegación lateral y una columna principal con
top bar y contenido en `ScrollPane`. Las pantallas emplean `VBox`, `HBox`,
`FlowPane`, `GridPane`, `StackPane` y `TitledPane`; las columnas de formularios
usan `ColumnConstraints` y los paneles permiten wrapping.

La comprobación visual real de 100 %, 125 %, 150 % y 200 % de Windows sigue
siendo manual. Los tests de CSS y los smoke tests JavaFX protegen contratos y
arranque, pero no sustituyen esa inspección.
