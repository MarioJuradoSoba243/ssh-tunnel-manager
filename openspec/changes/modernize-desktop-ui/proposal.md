## Why

La aplicación ya ofrece los flujos funcionales de la versión 1.0.0, pero su
presentación todavía resulta demasiado cercana a los controles JavaFX por
defecto. Este cambio mejora la jerarquía visual, la navegación y la
legibilidad para que el producto sea una herramienta de escritorio técnica y
profesional.

## What Changes

- Añadir un design system visual interno basado en JavaFX CSS, con tokens de
  color, tipografía, espaciado, superficies, estados y variantes de controles.
- Rediseñar el shell, la navegación lateral, la barra superior y la búsqueda
  global sin cambiar sus destinos ni atajos existentes.
- Modernizar Hub, Túneles, Grupos, Hosts SSH, Organización y Ajustes usando
  los ViewModels, servicios y repositorios existentes.
- Mejorar tablas, formularios, selectores, tags, acciones, diálogos, errores,
  estados vacíos y scrollbars para tema claro y oscuro.
- Mantener layouts responsivos para escritorio desde aproximadamente 1024×700
  y preparar la revisión manual de escalado Windows 100–200 %.
- Añadir documentación técnica del design system y conservar la checklist de
  validación visual manual.

## Scope

Este cambio es de presentación, interacción y experiencia de usuario. No
modifica la semántica de SSH, routing, lifecycle, persistencia, grupos,
fingerprints, import/export, backups ni diagnósticos. Las correcciones de
interacción solo podrán reparar regresiones UI directamente relacionadas.

## Impact

- Se modificarán FXML, CSS y componentes JavaFX de la capa `ui`.
- Podrán añadirse componentes visuales pequeños y reutilizables cuando eviten
  duplicación real.
- Se mantendrán Java 25, JavaFX, Maven, Apache MINA SSHD, SQLite y la
  arquitectura UI → Application → Domain con Infrastructure implementando
  puertos.
- No se añade WebView, HTML, un framework de componentes ni una librería
  visual estructural.

