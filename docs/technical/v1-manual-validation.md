# Checklist de validación manual v1.0.0

Esta checklist cubre las comprobaciones que no quedan demostradas de forma
reproducible por Maven. Rellena `Resultado` con `PASS` o `FAIL` y conserva la
observación concreta, incluyendo versión de Windows, escalado y artefacto usado.

## Preparación

| Campo | Valor |
| --- | --- |
| Windows | PASS / FAIL — versión y build: |
| Artefacto | PASS / FAIL — installer o portable: |
| Versión | PASS / FAIL — 1.0.0: |
| Usuario | PASS / FAIL — estándar / administrador: |
| JDK/JRE externo instalado | PASS / FAIL — no debe ser necesario: |
| Observaciones | |

## 8.20 — escalado y layout

En cada nivel abre la aplicación y revisa Hub, Hosts, Tunnels, Groups,
Organization, Settings, diálogos y el flujo de importación con conflictos.
Comprueba que no hay texto recortado, botones inaccesibles, columnas inutilizables
ni controles fuera de la ventana.

| Escalado | Resultado | Observaciones |
| --- | --- | --- |
| 100 % | PASS / FAIL | |
| 125 % | PASS / FAIL | |
| 150 % | PASS / FAIL | |
| 200 % | PASS / FAIL | |

Áreas que deben revisarse en cada nivel:

- Hub.
- Hosts.
- Tunnels.
- Groups.
- Organization.
- Settings.
- Diálogos de confirmación, edición, error, fingerprint y backup.
- Importación con conflictos `Conservar`, `Reemplazar` y `Crear copia`.

## 8.30 — smoke UI completo

| Acción | Resultado | Observaciones |
| --- | --- | --- |
| Crear host SSH | PASS / FAIL | |
| Editar host SSH | PASS / FAIL | |
| Eliminar host sin dependencias | PASS / FAIL | |
| Intentar eliminar host referenciado y ver dependencias | PASS / FAIL | |
| Crear túnel | PASS / FAIL | |
| Guardar túnel | PASS / FAIL | |
| Búsqueda global por `Ctrl+K` | PASS / FAIL | |
| Iniciar túnel | PASS / FAIL | |
| Detener túnel | PASS / FAIL | |

## 9.9 — cuenta estándar real

Realiza esta sección con una cuenta que no pertenezca a Administradores. No
consideres suficiente UAC con token filtrado.

| Comprobación | Resultado | Observaciones |
| --- | --- | --- |
| Ejecutar portable como usuario estándar | PASS / FAIL | |
| Instalar per-user sin elevación inesperada | PASS / FAIL | |
| Uso habitual sin solicitar elevación | PASS / FAIL | |
| Crear y editar configuración | PASS / FAIL | |
| Crear y revisar logs | PASS / FAIL | |
| Crear/iniciar/detener túneles | PASS / FAIL | |
| Persistencia tras cerrar y volver a abrir | PASS / FAIL | |

## 9.22 — release workflow

1. Abrir GitHub Actions y seleccionar `CI`.
2. Usar `Run workflow` sobre el commit que se quiere validar.
3. Confirmar que `compile`, `quality` y `package` terminan correctamente.
4. Descargar el artefacto `ssh-tunnel-manager-windows`.
5. Confirmar estos nombres exactos:

```text
ssh-tunnel-manager-1.0.0-windows-x64-installer.exe
ssh-tunnel-manager-1.0.0-windows-x64-portable.zip
```

| Comprobación | Resultado | Observaciones |
| --- | --- | --- |
| Workflow remoto completado | PASS / FAIL | URL del run: |
| Installer generado | PASS / FAIL | SHA-256: |
| Portable generado | PASS / FAIL | SHA-256: |
| Versiones/nombres correctos | PASS / FAIL | |

## Resultado final manual

| Área | Resultado | Observaciones |
| --- | --- | --- |
| 8.20 | PASS / FAIL | |
| 8.30 | PASS / FAIL | |
| 9.9 | PASS / FAIL | |
| 9.22 | PASS / FAIL | |
