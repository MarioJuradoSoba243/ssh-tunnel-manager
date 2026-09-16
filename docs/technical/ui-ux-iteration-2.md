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
