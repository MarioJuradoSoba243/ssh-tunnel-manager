# Auditoría UI/UX de v1.0.0

Este documento registra el bloque de corrección visual realizado antes de la
validación manual final. No sustituye la comprobación visual en Windows y no
cierra las tareas 8.20, 8.30 ni la validación final de v1.0.0.

## Evidencia disponible

- No se encontraron capturas adjuntas en el entorno de trabajo al iniciar la
  auditoría.
- Se arrancó el application image Windows generado desde el repositorio en
  tema oscuro del sistema y se capturó una ventana real de aproximadamente
  1040x739 píxeles, correspondiente a una escena de 1024x700.
- Captura previa: `%TEMP%\ssh-tunnel-manager-ui-home.png`.
- Captura posterior: `%TEMP%\ssh-tunnel-manager-ui-home-after-fix.png`.
- La inspección real se limitó al arranque y a la vista Inicio. Las demás
  vistas y los escalados 125%, 150% y 200% siguen requiriendo comprobación
  manual.

## Problemas encontrados y tratamiento

| Pantalla | Tema / escala | Problema | Causa | Cambio aplicado | Evidencia |
| --- | --- | --- | --- | --- | --- |
| Inicio | Oscuro / escala del entorno actual | Los botones sin datos tenían fondo claro y texto claro, con contraste insuficiente. | El tema oscuro no definía estados de botones ni colores de texto de controles; se heredaban defaults de JavaFX. | Paleta completa por tema y reglas comunes para botones normales, hover, focus y disabled. | Captura previa y posterior; test `UiThemeCssTest`. |
| Todas | Claro y oscuro / todos los escalados | Inputs, ComboBox, tablas, selección, disabled y diálogos dependían de defaults inconsistentes. | `dark.css` solo definía cuatro variables y `base.css` no cubría los estados de controles. | Variables de contraste por tema y estilos compartidos para inputs, ComboBox, listas, tablas, tabs, diálogos, errores y warnings. | Test `UiThemeCssTest`; comprobación visual manual pendiente. |
| Túneles | Claro y oscuro / ventana estrecha o DPI alto | Filtros y acciones se construían en filas `HBox` largas y podían salirse del viewport. | Sin wrapping ni política de crecimiento. | `FlowPane` con wrapping para filtros, acciones y acciones masivas. | Inspección de layout y test de recursos; comprobación manual pendiente. |
| Túneles | Oscuro / escala del entorno actual | El placeholder del filtro terminaba truncado y el filtro de etiquetas vacío no explicaba su función. | Texto demasiado largo para el ancho preferido y ausencia de placeholder en la lista. | Placeholder más corto y placeholder visible `Etiquetas`; la barra se reorganiza por filas. | Captura real `ssh-tunnel-manager-ui-tunnels-final2.png`. |
| Túneles | Oscuro / escala del entorno actual | Los ComboBox de filtros heredaban un relleno azul demasiado dominante para un control sin selección. | El botón/celda de ComboBox no tenía fondo explícito en el tema compartido. | Fondo de celda explícito con el color de control de cada tema. | Comparación de capturas real previa/posterior. |
| Túneles | Oscuro / escala del entorno actual | La primera vista dejaba la barra de acciones parcialmente fuera del viewport. | La tabla usaba una altura preferida grande y no estaba acotada dentro del scroll del shell. | Altura preferida/máxima moderada para tablas; el contenido de la tabla conserva scroll propio y las acciones hacen wrapping. | Captura real `ssh-tunnel-manager-ui-tunnels-final2.png`. |
| Hosts, grupos y editor de túnel | Claro y oscuro / ventana estrecha o DPI alto | Las columnas de formularios no permitían que el control creciera con el contenedor. | `formGrid` no tenía `ColumnConstraints` ni `maxWidth` de controles. | Columna de etiquetas acotada y columna de controles expansible. | Inspección de layout; comprobación manual pendiente. |
| Ajustes, importación y editores | Claro y oscuro / altura limitada | El contenido largo no tenía un scroll principal uniforme. | El shell central contenía directamente el `VBox` dinámico. | `ScrollPane` central con `fitToWidth`, sin scroll horizontal y viewport transparente. | Inspección FXML; comprobación manual pendiente. |
| Tablas de hosts, túneles y grupos | Claro y oscuro / varios anchos | No había política de resize ni mínimos basados en el contenido. | Columnas añadidas sin resize policy ni mínimos. | Resize policy constrained y mínimos moderados por columna, manteniendo scroll cuando sea necesario. | Inspección de layout; comprobación manual pendiente. |
| Inicio | Oscuro / escala del entorno actual | Las tres listas vacías podían crecer demasiado y empujar el resto de la vista. | Las tres listas tenían `Vgrow.ALWAYS` simultáneamente. | Alturas preferidas y máximas moderadas; cada lista conserva su scroll interno. | Captura posterior; comprobación manual en otros tamaños pendiente. |

## Cambios revisados

- `base.css`, `light.css` y `dark.css`.
- `shell.fxml` para el scroll central y sizing de navegación.
- `ShellController` para layouts responsivos, tablas y formularios.
- `UiThemeCssTest` para evitar que desaparezcan los tokens de contraste y los
  estilos comunes de estados problemáticos.

## Checklist manual previa a marcar 8.20 como PASS

En una instalación limpia o portable recién generado, repetir cada vista en
tema claro y oscuro, con la ventana inicial aproximada de 1024x700 y después
maximizada. Repetir el recorrido a 100%, 125%, 150% y 200% de escalado de
Windows. Registrar PASS/FAIL y observaciones para cada combinación.

### Vistas y estados

- [ ] Inicio / Hub: resumen, favoritos, recientes, túneles activos y botones
  de estado; comprobar que los estados no dependan solo del color.
- [ ] Túneles: tabla, columnas, filtros, acciones normales y masivas, estados
  CLOSED/CONNECTING/OPEN/RECONNECTING/ERROR.
- [ ] Editor de túnel: pestañas, ComboBox de hosts, opciones avanzadas,
  validación, errores y scroll.
- [ ] Grupos: tabla y barra de acciones.
- [ ] Editor de grupo: búsqueda, tabla de selección, tags y conflicto.
- [ ] Hosts SSH: tabla, editor modal, password, Mostrar contraseña y errores.
- [ ] Organización: proyectos, entornos y etiquetas.
- [ ] Ajustes: tema, logging, atajos, import/export, backups y diagnóstico.
- [ ] Búsqueda global: diálogo de entrada, resultados y selección.
- [ ] Import/export: preview sin conflictos y resolución de conflictos con
  nombres y resúmenes largos.
- [ ] Diálogos y errores: warning, error, detalles técnicos, copiar error y
  botones default/cancelar.

### Escalados

- [ ] 100% — claro — 1024x700 y maximizada.
- [ ] 100% — oscuro — 1024x700 y maximizada.
- [ ] 125% — claro — 1024x700 y maximizada.
- [ ] 125% — oscuro — 1024x700 y maximizada.
- [ ] 150% — claro — 1024x700 y maximizada.
- [ ] 150% — oscuro — 1024x700 y maximizada.
- [ ] 200% — claro — 1024x700 y maximizada.
- [ ] 200% — oscuro — 1024x700 y maximizada.

Para cada combinación revisar truncamiento, clipping, solapamientos, foco,
hover, disabled, contraste, scroll horizontal accidental, columnas, botones,
placeholders y labels. Esta checklist debe permanecer abierta hasta que la
validación manual aporte observaciones y capturas finales.
