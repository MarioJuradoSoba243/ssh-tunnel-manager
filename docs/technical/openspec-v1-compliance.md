# Matriz de evidencia OpenSpec para v1.0.0

Esta matriz es el resultado de la auditoría de las 13 delta specs del cambio
`create-ssh-tunnel-manager`. Se han contado 94 requirements y 112 scenarios.
Los nombres de requirements y scenarios se mantienen en español y corresponden
directamente a `openspec/changes/create-ssh-tunnel-manager/specs/**/spec.md`.

## Códigos de evidencia

| Código | Significado |
| --- | --- |
| `TEST` | Test automatizado reproducible que cubre el comportamiento. |
| `SRC` | Inspección de implementación/configuración, apoyada por build. |
| `DOCKER` | Test de integración contra la topología SSH Docker. |
| `PKG` | Verificación real de application image, portable o instalador. |
| `MANUAL` | Requiere interacción/entorno Windows que no se automatizó aquí. |
| `PARTIAL` | Hay implementación o evidencia parcial, pero falta una prueba exigida. |

## Application lifecycle — 5 requirements / 6 scenarios

| Requirement | Scenarios | Evidencia | Estado |
| --- | --- | --- | --- |
| Instancia única | Segunda ejecución | `SingleInstanceCoordinatorTest`, smoke portable | TEST/PKG |
| Cierre con túneles activos | Cancelar cierre; Confirmar cierre | `SshTunnelManagerApplication`, `TunnelRuntimeManagerTest` | TEST/SRC |
| Sin residencia tras el cierre | Cierre normal | smoke real de portable/instalador | PKG |
| Terminación de recursos SSH | Cierre normal con procesos SSH | cierre de `TunnelHandle`, runtime y smoke real | TEST/PKG |
| Detección de ejecución anterior anómala | Apertura después de un crash | `ExecutionMarkerTest`, `ResumeDetectorTest` | TEST |

La recuperación específica después de terminación forzada sigue registrada como
pendiente en 8.5: la implementación usa conexiones in-process y marcador de
ejecución, pero no existe un test de recuperación de recursos huérfanos de un
hard-kill real.

## Application settings — 5 requirements / 5 scenarios

| Requirement | Scenario | Evidencia | Estado |
| --- | --- | --- | --- |
| Valores globales de conexión | Modificar el keep-alive global | defaults/overrides de runtime y UI | SRC |
| Límite global de concurrencia | Cambiar el límite de concurrencia | tests de grupos y `TunnelRuntimeManagerTest` | TEST |
| Preferencias de notificación | Notificaciones desactivadas | `ApplicationSettingsService`, UI de Ajustes | SRC |
| Preferencia de tema | Seguir el tema del sistema | `ThemeManagerTest`, `WindowsSystemThemeProvider` | TEST/SRC |
| Nivel de logging | Seleccionar DEBUG | `LoggingLevelController`, `log4j2.xml`, UI | SRC |

## Credential security — 8 requirements / 8 scenarios

| Requirement | Scenario | Evidencia | Estado |
| --- | --- | --- | --- |
| Credenciales recordadas por host | Reutilizar una contraseña guardada | `PersistenceIntegrationIT`, repositories | TEST |
| Visualización y cambio de contraseña | Mostrar contraseña | editor de host y test de ViewModel | TEST/SRC |
| Protección de credenciales en modo instalado | Persistencia normal de un host | SQLite instalado; no se introduce cifrado no aprobado | TEST/SRC |
| Credenciales transferibles en modo portable | Copiar una instalación portable | rutas portable, export/import y smoke | TEST/PKG |
| Autorización manual de host keys | Primera conexión | spike MINA y coordinator | DOCKER/TEST |
| Cambio de fingerprint | Identidad modificada | `MinaSshSpikeIT`, `MinaSshDockerSpikeIT` | DOCKER/TEST |
| Gestión de confianza | Eliminar confianza | repository/UI de trusted keys | TEST/SRC |
| Fallo de autenticación no interactivo durante operaciones masivas | Contraseña incorrecta en un grupo | `GroupUseCaseTest`, runtime | TEST |

## Data portability — 12 requirements / 13 scenarios

| Requirement | Scenarios | Evidencia | Estado |
| --- | --- | --- | --- |
| Persistencia automática de configuración | Editar un túnel | repositories y `PortabilityIntegrationIT` | TEST |
| Estado inicial cerrado | Reiniciar después de una sesión normal | runtime separado de snapshot; bootstrap | TEST/SRC |
| Exportación completa legible | Exportar configuración completa | `PortabilityIntegrationIT`, JSON real | TEST |
| Importación con resolución de conflictos | Mismo ID con contenido distinto; ID desconocido | `PortabilityIntegrationIT` | TEST |
| IDs persistentes y nombres únicos | Crear copia durante importación | conflicto `CREATE_COPY`, UUID/nombre | TEST |
| Backups automáticos completos | Rotación del backup número once | snapshot/retención integration tests | TEST |
| Restauración segura | Restaurar un backup | restore con backup previo | TEST |
| Importación y restauración sin túneles activos | Restaurar con túneles abiertos | `ActiveRuntime`, UI de confirmación | TEST/SRC |
| Recuperación ante corrupción | Base de datos dañada | `PersistenceIntegrationIT`, `PortabilityIntegrationIT` | TEST |
| Operaciones persistentes atómicas | Fallo al guardar | rollback integration tests | TEST |
| Modo portable autocontenido | Trasladar directorio portable | resolver de rutas, portable smoke | TEST/PKG |
| Eliminaciones definitivas | Confirmar eliminación | repositories/casos de uso | TEST/SRC |

## Desktop experience — 15 requirements / 17 scenarios

| Requirement | Scenarios | Evidencia | Estado |
| --- | --- | --- | --- |
| Plataforma Windows 11 x64 | Ejecutar como usuario estándar | instalador per-user y app image | PARTIAL: falta cuenta estándar real |
| Distribuciones instalable y portable | Usar la versión portable | jpackage y smoke real | PKG |
| Interfaz responsiva | Iniciar un grupo grande | workers virtuales y tests de grupos | TEST/SRC |
| Escala objetivo | Buscar entre más de cien túneles | búsqueda/filtros en memoria con 500 | TEST |
| Arranque local sin conexiones SSH | Abrir la aplicación | `ShellSmokeTest`, runtime no persistido | TEST |
| Tema configurable | Selección manual de tema oscuro | `ThemeManagerTest` y Ajustes | TEST/SRC |
| Accesibilidad visual de estados | Estado de error | textos de estado en tabla y guía | SRC |
| Escalado y tamaño inicial de ventana | Escalado al 150 % | tamaño 1024×700 automatizado | PARTIAL: 100/125/150/200 manual pendiente |
| Uso mediante teclado | Abrir búsqueda global por teclado | `ShortcutRegistryTest`, cache sin SQLite por evento | TEST/SRC |
| Idioma de v1 y preparación para localización | Textos de interfaz de v1 | revisión de strings visibles y guía es | PARTIAL: interfaz v1 revisada en español; no hay todavía un catálogo ResourceBundle para inglés |
| Notificaciones operativas relevantes | Túnel agota reintentos | servicio de notificación y runtime | SRC |
| Confirmaciones proporcionales | Detener un túnel individual; Detener todos los túneles | UI y tests de runtime | TEST/SRC |
| Aislamiento de fallos | Un túnel falla dentro de un grupo | `GroupUseCaseTest` | TEST |
| Sin actualización integrada | Ejecutar v1 | revisión de dependencias/código | SRC |
| Documentación de uso | Nueva funcionalidad visible; Cambio interno sin impacto de uso | guía es y README | SRC |

La comprobación visual real en los cuatro niveles de escalado Windows es el
pendiente 8.20. La interacción completa de crear host, crear túnel y búsqueda
global sigue pendiente 8.30; `ShellSmokeTest` solo cubre arranque/scene/styles.

## Diagnostics — 5 requirements / 5 scenarios

| Requirement | Scenario | Evidencia | Estado |
| --- | --- | --- | --- |
| Niveles estándar de logging | Evento informativo | Log4j2 y settings TRACE/DEBUG/INFO/WARN/ERROR | SRC |
| Ausencia de contraseñas en logs | Fallo de autenticación con DEBUG | auditoría de logs y `ZipDiagnosticServiceTest` | TEST |
| Rotación de logs | Alcanzar el límite de rotación | `log4j2.xml` rolling policy | SRC |
| Acceso a diagnóstico desde la UI | Copiar un error de túnel | UI `Ver detalles`/`Copiar error` | SRC |
| Paquete de diagnóstico | Exportar diagnóstico | ZIP real y test de redacción | TEST/SRC |

## SSH host management — 3 requirements / 5 scenarios

| Requirement | Scenarios | Evidencia | Estado |
| --- | --- | --- | --- |
| Libreta de hosts SSH reutilizables | Crear un host con el puerto por defecto; Crear un host con un puerto personalizado | editor, ViewModel y SQLite | TEST/SRC |
| Nombres de host no ambiguos | Intentar guardar un nombre ya utilizado | unicidad SQLite/caso de uso | TEST |
| Dependencias al eliminar hosts | Eliminar un host utilizado; Eliminar un host sin dependencias | FK `RESTRICT`, UI de dependencias | TEST/SRC |

## SSH routing — 5 requirements / 8 scenarios

| Requirement | Scenarios | Evidencia | Estado |
| --- | --- | --- | --- |
| Ruta SSH de hasta dos jumps | Conexión sin jump server; Conexión con dos jump servers | MINA in-process/Docker/productivo | DOCKER |
| Credenciales independientes por salto | Usuarios distintos por host | spike 2 jumps | DOCKER |
| Destino independiente del host SSH final | Forwarding hacia un host interno | Docker `tcp-target` | DOCKER |
| Coherencia de la ruta | Jump 2 sin Jump 1; Host repetido en la ruta | dominio/ViewModel | TEST |
| Prueba completa de conexión | Prueba satisfactoria con dos jumps; Fallo en un jump | spike y engine productivo | DOCKER/TEST |

## Tunnel configuration — 6 requirements / 9 scenarios

| Requirement | Scenarios | Evidencia | Estado |
| --- | --- | --- | --- |
| Configuración de Local Port Forwarding | Crear un túnel básico; Configurar un bind distinto de localhost | editor, persistence, engine | TEST/DOCKER |
| Validación local antes del guardado | Puerto fuera de rango; Guardar mientras el servidor está desconectado | `TunnelEditorViewModelTest` | TEST |
| Ajustes avanzados por túnel | Override de keep-alive; Opción avanzada incompatible | ViewModel y persistence | TEST |
| Edición de un túnel activo | Guardar cambios de un túnel abierto | UI Save & restart y runtime | SRC/TEST |
| Duplicado de túneles | Duplicar un túnel | caso de uso y UI | TEST/SRC |
| Eliminación de túneles | Eliminar un túnel utilizado por varios grupos | FK cascade/N:M y UI | TEST |

## Tunnel groups — 7 requirements / 7 scenarios

| Requirement | Scenario | Evidencia | Estado |
| --- | --- | --- | --- |
| Grupos reutilizables y relación muchos-a-muchos | Túnel compartido entre grupos | repositories y `GroupUseCaseTest` | TEST |
| Inicio masivo concurrente | Grupo mayor que el límite de concurrencia | `GroupUseCaseTest` | TEST |
| Túneles ya abiertos al iniciar un grupo | Túnel compartido ya abierto | `GroupUseCaseTest` | TEST |
| Fallos independientes dentro de un grupo | Un miembro del grupo falla | `GroupUseCaseTest` | TEST |
| Parada masiva sin ownership | Detener un túnel compartido mediante un grupo | `GroupUseCaseTest` | TEST |
| Advertencia de conflictos locales | Dos túneles usan el mismo puerto local | group editor/ViewModel | TEST/SRC |
| Duplicado de grupos | Duplicar un grupo | `GroupUseCaseTest` | TEST |

## Tunnel lifecycle — 7 requirements / 10 scenarios

| Requirement | Scenarios | Evidencia | Estado |
| --- | --- | --- | --- |
| Estados operativos de un túnel | Inicio correcto | `TunnelRuntimeManagerTest` | TEST |
| Parada manual cancelable desde cualquier fase activa | Detener durante reconexión; Detener un túnel en cola | runtime tests | TEST |
| Keep-alive | Túnel con override de keep-alive | engine/spike | DOCKER/TEST |
| Reconexión limitada | Recuperación antes de agotar reintentos; Reintentos agotados | runtime/backoff tests | TEST |
| Reintento manual | Reintentar después de agotar intentos | runtime tests | TEST |
| Puerto local ocupado | Puerto ocupado | engine integration | DOCKER |
| Reanudación después de suspensión | Reanudar con un túnel abierto; Reanudar con un túnel detenido manualmente | `ResumeDetectorTest`, runtime | TEST |

## Tunnel monitoring — 6 requirements / 7 scenarios

| Requirement | Scenarios | Evidencia | Estado |
| --- | --- | --- | --- |
| Telemetría operativa básica | Mostrar tiempo activo; Reiniciar tiempo después de una parada manual | runtime/UI | TEST/SRC |
| Progreso de reconexión | Tercer intento de cinco | runtime/UI text | TEST/SRC |
| Mensajes comprensibles y detalle técnico | Error de autenticación | `ErrorPresentation`, UI details | SRC |
| Historial de eventos de la sesión | Pérdida y recuperación | runtime events/UI | TEST/SRC |
| Resumen derivado de grupos | Grupo parcialmente abierto | `GroupUseCaseTest`, `GroupStatusService` | TEST/SRC |
| Progreso de operaciones masivas | Inicio de grupo en progreso | group operation/UI | TEST/SRC |

## Workspace organization — 10 requirements / 11 scenarios

| Requirement | Scenarios | Evidencia | Estado |
| --- | --- | --- | --- |
| Proyectos y entornos estructurados | Túnel sin proyecto; Entorno personalizado | organization tests/repositories | TEST |
| Tags libres | Clasificación multidimensional | organization/persistence tests | TEST |
| Eliminación de clasificaciones utilizadas | Eliminar un entorno utilizado | `OrganizationUseCaseTest`, FK SET NULL | TEST |
| Favoritos | Marcar grupo favorito | Hub/persistence tests | TEST |
| Elementos recientes | Reutilizar un elemento reciente | `RecentItemServiceTest` | TEST |
| Hub operativo | Consultar el Hub con túneles activos | `HubViewModelTest` | TEST |
| Búsqueda global | Buscar un túnel por texto | `WorkspaceSearchViewModelTest` | TEST |
| Filtrado y ordenación | Filtrar por proyecto y entorno | `TunnelFilter`, UI table | TEST/SRC |
| Selección múltiple | Añadir varios túneles a un grupo | UI bulk actions/repository IT | TEST/SRC |
| Primera ejecución orientada | Primera apertura | Hub empty state y UI | SRC |

## Resultado de la auditoría

No se encontró una contradicción entre implementación y `proposal.md`,
`design.md` o las specs. Apache MINA SSHD 2.18.0 continúa aceptado: la
topología Docker completa pasó 5/5 escenarios y los escenarios in-process
pasaron 14/14. La prueba productiva añadió 20 rutas independientes con ventana
de cuatro handshakes concurrentes y cierre verificable de los 20 endpoints.

Los pendientes que impiden afirmar una validación manual completa son:

1. 8.5: prueba específica de recuperación de recursos huérfanos tras hard-kill.
2. 8.20: inspección visual Windows real a 100%, 125%, 150% y 200%.
3. 8.30: smoke UI completo de crear host, crear túnel y búsqueda global.
4. 9.9: ejecución final como cuenta estándar real; la cuenta de esta sesión es
   miembro de Administradores con token UAC filtrado.
5. 9.17: la interfaz visible está en español, pero los literales siguen en
   código y no existe todavía un catálogo ResourceBundle para inglés.
6. 9.22: no se ejecutó el job remoto de GitHub Actions; se ejecutó el
   equivalente local con JDK 25 y WiX instalado.

Por ello esta matriz no convierte esos puntos en checks verdes de `tasks.md`.
