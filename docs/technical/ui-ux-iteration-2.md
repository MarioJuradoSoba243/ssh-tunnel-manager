# Iteración UI/UX 2

La segunda iteración conserva el shell visual actual y añade soporte de operación
contextual:

- Túneles usa una composición master-detail con estado, forwarding, ruta SSH y
  actividad de sesión.
- `Ctrl+K` abre una paleta de comandos con navegación, creación y entidades del
  índice local de búsqueda. `Arriba`, `Abajo`, `Enter` y `Escape` son operables
  por teclado.
- Los filtros activos se representan como chips removibles y la selección de
  varias filas muestra una barra contextual.
- Los estados se muestran mediante badges con texto y punto, nunca solo por
  color. Las filas ofrecen acciones rápidas con tooltip y nombre accesible.
- Ajustes > Apariencia permite `COMFORTABLE` o `COMPACT`; la elección se guarda
  como `ui.density` usando el repositorio de ajustes existente.
- El shell dispone de un host de toasts para feedback no modal de operaciones
  rutinarias.

La auditoría de DPI, temas y layouts renderizados sigue requiriendo la checklist
manual de `docs/technical/ui-ux-audit-v1.md` y la validación final del entorno
Windows. Los tests de recursos CSS y de comportamiento puro no sustituyen esa
comprobación visual.

## Checklist manual de esta iteración

Marcar `PASS` o `FAIL` después de observar la aplicación renderizada:

| Superficie | Claro 100/125/150/200 % | Oscuro 100/125/150/200 % | Observaciones |
| --- | --- | --- | --- |
| Hub, métricas y actividad | ____ | ____ | ____________________ |
| Túneles, chips y barra contextual | ____ | ____ | ____________________ |
| Panel de detalle y acciones de fila | ____ | ____ | ____________________ |
| Paleta Ctrl+K y navegación por teclado | ____ | ____ | ____________________ |
| Editor de túnel | ____ | ____ | ____________________ |
| Grupos y editor de grupo | ____ | ____ | ____________________ |
| Hosts SSH y diálogos | ____ | ____ | ____________________ |
| Organización y Ajustes > Apariencia | ____ | ____ | ____________________ |
| Errores, tooltips y toasts | ____ | ____ | ____________________ |

Esta sesión validó compilación, tests, recursos CSS y arranque del portable;
no validó píxeles, contraste medido ni clipping en DPI porque no se pudo
observar una ventana JavaFX nativa mediante las herramientas disponibles.

## Regresión UI/UX corregida

- **Túneles desaparecidos:** la causa era crear una nueva `TableView` al volver
  a la sección y reutilizar la lista sin reasociar sus transformaciones. La
  vista se cachea y conserva `ObservableList`, `FilteredList`, `SortedList`,
  filtros, ordenación, selección válida y listeners; la actualización de datos
  se hace sobre esa misma lista.
- **Hover fantasma:** el `ListView` de navegación rellena el espacio sobrante
  con celdas vacías. Esas celdas se marcan no interactivas y tienen reglas CSS
  explícitas transparentes para `hover` y `focused`.
- **ComboBox truncado en Compact:** las reglas compactas reducían controles a
  30 px sin una altura mínima común para el renderer interno. Se fija una
  altura mínima/pref de 32 px para ComboBox, ChoiceBox, DatePicker, Spinner,
  campos, MenuButton y botones; las filas y popups usan 32 px sin reducir la
  fuente.

La evidencia automatizada cubre la persistencia del estado de lista, los
selectores CSS de celdas vacías y las reglas de densidad. La comprobación de
clipping real sigue siendo manual.

## Iteración de corrección UI/UX basada en evidencia

Esta iteración conserva la línea visual actual y corrige los defectos observados
en la inspección manual:

- **Detalles del túnel estrechos o sin cierre fiable:** el diálogo usaba el
  `contentText` de `Alert` para contenido técnico largo y añadía una acción sin
  un cierre explícito. Ahora usa un `TextArea` solo lectura con wrapping,
  desplazamiento interno, `Cerrar`, `Copiar`, tamaño responsivo limitado por la
  pantalla y cierre explícito para X/Escape/Alt+F4.
- **Tags incómodos:** la lista pequeña ya no permanece ocupando el editor. Las
  selecciones aparecen como chips removibles y `+ Añadir` abre un picker con
  búsqueda y selección múltiple; el control es compartido por túneles y grupos.
- **Favoritos y acciones de fila:** la estrella se centra mediante una celda
  real y las acciones usan glifos de 16 px con botones de 32×32 px, tooltips y
  nombres accesibles.

La cobertura automatizada comprueba el contrato CSS y la eliminación accesible
de chips. La inspección renderizada de diálogos, DPI, contraste y clipping sigue
siendo manual porque este entorno no expone una ventana JavaFX nativa para
capturarla y analizarla visualmente.
