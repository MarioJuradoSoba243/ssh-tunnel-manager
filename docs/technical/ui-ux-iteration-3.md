# Iteración UI/UX 3: producto operativo

Esta iteración refina el workspace existente sin modificar la semántica SSH,
runtime, persistencia ni los casos de uso.

## Cambios implementados

- `StatusBadge` utiliza un icono semántico distinto por estado además del texto
  y color.
- La tabla de Túneles ofrece menú contextual por fila, acciones deshabilitadas
  según el estado y empty states diferenciados para colección vacía y filtros
  sin resultados.
- La barra de selección múltiple es horizontal y concentra las acciones menos
  frecuentes en `Más`.
- El panel lateral muestra conexión, ruta, clasificación, etiquetas, último
  error y acciones; puede ocultarse sin recrear la tabla. Su visibilidad se
  guarda como preferencia visual (`ui.tunnelDetailVisible`).
- `ToastHost` limita a cuatro avisos, admite cierre manual y expiración
  automática, y mantiene los avisos fuera del flujo modal.
- La paleta conserva el índice en memoria y añade comandos de navegación,
  creación, exportación y diagnóstico, con navegación Arriba/Abajo/Enter/Escape.
- Ajustes utiliza el reemplazo centralizado del shell, evitando composiciones
  apiladas durante la navegación.

## Pendiente de validación visual

La inspección renderizada continúa siendo manual: claro/oscuro, Comfortable/
Compact, ventana 1024×700 y maximizada, escalados 100/125/150/200 %, menús
contextuales, toasts, panel lateral, paleta y diálogos. Los tests verifican
contratos de estructura y comportamiento, pero no sustituyen la observación
de clipping, contraste o densidad en una ventana real.
