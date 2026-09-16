## 1. Design system y shell

- [x] 1.1 Inventariar superficies, estados y duplicación visual existente.
- [x] 1.2 Centralizar tokens y estilos base para claro/oscuro.
- [x] 1.3 Modernizar shell, sidebar, top bar y búsqueda global.
- [x] 1.4 Añadir tests de recursos CSS y smoke de arranque/navegación.

## 2. Controles y temas

- [x] 2.1 Definir variantes primary, secondary, ghost, danger e icon.
- [x] 2.2 Revisar TextField, PasswordField, ComboBox, CheckBox, Spinner y
  TextArea en normal, hover, focus, disabled y error.
- [x] 2.3 Revisar TableView, ListView, TabPane, DialogPane, Tooltips y
  scrollbars en ambos temas.
- [x] 2.4 Eliminar representaciones técnicas de objetos en selectores.

## 3. Pantallas de operación

- [x] 3.1 Rediseñar Hub con métricas reales, favoritos, activos y actividad.
- [x] 3.2 Rediseñar Túneles con tabla densa, filtros y acciones jerarquizadas.
- [x] 3.3 Modernizar editor de túnel, tags y estados de validación.
- [x] 3.4 Rediseñar Grupos y editor, conservando selección por teclado/ratón.

## 4. Gestión y configuración

- [x] 4.1 Rediseñar Hosts SSH y su diálogo modal sin layout shift.
- [x] 4.2 Rediseñar Organización con composición responsiva.
- [x] 4.3 Agrupar visualmente Ajustes sin cambiar sus opciones.
- [x] 4.4 Unificar diálogos, errores, estados vacíos y progreso.

## 5. Auditoría y entrega

- [ ] 5.1 Auditar claro/oscuro, 1024×700, maximizada y escalados 100–200 %.
- [x] 5.2 Actualizar documentación técnica y checklist manual visual.
- [x] 5.3 Ejecutar Spotless, smoke tests JavaFX y `clean verify`.
- [x] 5.4 Registrar pendientes manuales sin marcar 8.20, 8.30 ni READY.

## 6. Iteración operativa contextual

- [x] 6.1 Añadir componentes UI reutilizables para badges de estado, chips de
  filtro, toasts y detalle contextual.
- [x] 6.2 Incorporar panel master-detail en Túneles con ruta SSH y actividad.
- [x] 6.3 Incorporar paleta Ctrl+K con comandos, entidades, navegación y
  operación por teclado.
- [x] 6.4 Incorporar filtros activos, barra contextual de selección múltiple y
  acciones rápidas por fila.
- [x] 6.5 Persistir densidad visual y estado de selección/sección de forma
  compatible con los ajustes existentes.
- [x] 6.6 Mejorar estados vacíos, onboarding inicial, tooltips y feedback no
  modal sin cambiar la lógica de aplicación.
- [ ] 6.7 Validar manualmente la iteración en claro/oscuro y escalados Windows;
  esta tarea sigue vinculada a 8.20 y no se marca automáticamente.

## 7. Regresión UI/UX

- [x] 7.1 Corregir el ciclo de vida de Túneles para reutilizar la vista, sus
  listas transformadas, filtros, ordenación y listeners al navegar.
- [x] 7.2 Hacer explícitamente no interactivas las celdas vacías de la sidebar
  y limitar los estados hover/focus a navegación real.
- [x] 7.3 Revisar la altura mínima de controles de texto y selectores en Compact
  sin reducir la tipografía ni introducir anchos fijos arbitrarios.
- [x] 7.4 Añadir pruebas de regresión para estado de lista, CSS de sidebar y
  legibilidad estructural de Compact.
- [ ] 7.5 Repetir la auditoría visual renderizada en Windows; permanece manual.

## 8. Corrección UI/UX basada en evidencia

- [x] 8.1 Unificar los diálogos técnicos con cierre explícito, contenido
  desplazable y tamaño responsivo para detalles y errores.
- [x] 8.2 Sustituir la lista permanente de etiquetas por chips removibles y un
  selector compacto compartido por túneles y grupos.
- [x] 8.3 Centrar favoritos y normalizar escala, área de interacción, tooltip y
  accesibilidad de las acciones rápidas de tabla.
- [x] 8.4 Añadir cobertura de CSS y comportamiento para los contratos de la
  regresión UI/UX y registrar la guía del sistema visual.
- [ ] 8.5 Repetir la auditoría visual renderizada en Windows en claro/oscuro y
  100/125/150/200 %; sigue siendo una validación manual.
