# Tasks

## Manual prerequisites

- Desarrollo y packaging final soportados en Windows 11 x64.
- Docker será necesario para ejecutar la topología completa de integración SSH, pero no para ejecutar la aplicación.
- Apache MINA SSHD sigue siendo un candidato condicionado al spike de la Fase 2.
- Si el spike SSH falla materialmente, detener el trabajo afectado y revisar `design.md` antes de seleccionar otro motor.

## 1. Bootstrap técnico y build base

- [ ] 1.1 Crear el proyecto Maven único con Java 25 y la estructura de packages `domain`, `application`, `infrastructure` y `ui`.
- [ ] 1.2 Añadir Maven Wrapper y verificar `mvnw.cmd --version` en Windows.
- [ ] 1.3 Configurar JavaFX y crear una aplicación mínima que abra la ventana principal.
- [ ] 1.4 Crear `ApplicationBootstrap` con constructor injection manual y sin contenedor DI.
- [ ] 1.5 Añadir JUnit 5, AssertJ, Mockito y ArchUnit como dependencias de test.
- [ ] 1.6 Añadir SLF4J + Log4j2 con nivel `INFO` por defecto y configuración inicial de rolling files.
- [ ] 1.7 Añadir Spotless + google-java-format y configurar su comprobación en el build.
- [ ] 1.8 Añadir JaCoCo para generar informes de cobertura sin imponer un porcentaje global.
- [ ] 1.9 Configurar Maven Surefire para unit tests y Failsafe para integration tests `*IT`.
- [ ] 1.10 Crear reglas ArchUnit iniciales para proteger las fronteras definidas en `design.md`.
- [ ] 1.11 Crear el shell JavaFX mínimo con navegación vacía a Inicio, Túneles, Grupos, Hosts SSH, Organización y Ajustes.
- [ ] 1.12 Añadir `base.css`, `light.css` y `dark.css` con una primera infraestructura de tema.
- [ ] 1.13 Crear una prueba smoke de arranque JavaFX.
- [ ] 1.14 Crear GitHub Actions para compile, format check, unit tests, SQLite-independent tests y ArchUnit.
- [ ] 1.15 Crear un primer job Windows de `jpackage` que produzca una application image mínima.
- [ ] 1.16 Generar un instalador Windows mínimo mediante `jpackage` y verificar que no requiere un JRE/JDK externo.
- [ ] 1.17 Generar un ZIP portable mínimo a partir de la application image.
- [ ] 1.18 Documentar en `README.md` los comandos de build/test/package realmente disponibles tras el bootstrap.
- [ ] 1.19 Ejecutar Spotless y `.\mvnw.cmd verify`.
- [ ] 1.20 Marcar la Fase 1 completada solo si CI y los smoke builds de packaging son reproducibles.

## 2. Spike obligatorio del motor SSH

- [ ] 2.1 Añadir Apache MINA SSHD con una versión concreta y aislada en infraestructura/spike.
- [ ] 2.2 Crear la topología Docker Compose de integración con `jump-1`, `jump-2`, `ssh-final` y un servicio TCP destino.
- [ ] 2.3 Configurar usuarios y passwords independientes para `jump-1`, `jump-2` y `ssh-final`.
- [ ] 2.4 Probar conexión SSH directa mediante usuario/password.
- [ ] 2.5 Probar una ruta con un jump y credenciales distintas entre jump y host final.
- [ ] 2.6 Probar una ruta con dos jumps y credenciales distintas en los tres hosts SSH.
- [ ] 2.7 Probar Local Port Forwarding directo.
- [ ] 2.8 Probar Local Port Forwarding a través de un jump.
- [ ] 2.9 Probar Local Port Forwarding a través de dos jumps.
- [ ] 2.10 Probar un `destinationHost` distinto del host SSH final.
- [ ] 2.11 Probar obtención y validación de la host public key/fingerprint para cada host de la ruta.
- [ ] 2.12 Probar rechazo de una host key desconocida cuando no existe aprobación.
- [ ] 2.13 Probar continuación de la conexión después de una aprobación explícita controlada por el test.
- [ ] 2.14 Probar detección de cambio de host key y bloqueo de la conexión.
- [ ] 2.15 Probar keep-alive/heartbeat con el valor de diseño.
- [ ] 2.16 Probar detección de pérdida de conexión.
- [ ] 2.17 Probar timeout de conexión por tramo.
- [ ] 2.18 Probar cierre/cancelación limpia durante conexión y con forwarding activo.
- [ ] 2.19 Probar destino TCP inaccesible para la operación `Probar conexión`.
- [ ] 2.20 Documentar resultados, limitaciones y APIs relevantes del spike.
- [ ] 2.21 GATE: si todos los requisitos críticos son viables, aceptar MINA y fijar su versión para producción.
- [ ] 2.22 GATE: si MINA falla materialmente, detener la implementación SSH y reportar qué decisión de `design.md` debe reabrirse.
- [ ] 2.23 Convertir los escenarios reutilizables del spike en integration tests permanentes.
- [ ] 2.24 Ejecutar los SSH integration tests completos y `.\mvnw.cmd verify`.

## 3. Persistencia SQLite y servicios base

- [ ] 3.1 Añadir `org.xerial:sqlite-jdbc` con versión concreta.
- [ ] 3.2 Implementar resolución de directorios de datos para modo instalado y portable.
- [ ] 3.3 Crear `DatabaseFactory`/DataSource con conexiones JDBC cortas por operación.
- [ ] 3.4 Activar `PRAGMA foreign_keys = ON` en cada conexión.
- [ ] 3.5 Configurar `PRAGMA journal_mode = WAL` y validar su aplicación.
- [ ] 3.6 Diseñar y crear `V001__initial_schema.sql`.
- [ ] 3.7 Crear tablas `ssh_host`, `credential`, `tunnel`, `tunnel_ssh_option`, `tunnel_group` y `tunnel_group_member`.
- [ ] 3.8 Crear tablas `project`, `environment`, `tag`, `tunnel_tag` y `group_tag`.
- [ ] 3.9 Crear tablas `trusted_host_key`, `recent_item`, `application_setting`, `keyboard_shortcut` y `schema_version`.
- [ ] 3.10 Implementar `DatabaseMigrator` propio con migraciones SQL ordenadas y versionadas.
- [ ] 3.11 Añadir integration tests del migrador desde una base vacía.
- [ ] 3.12 Añadir integration test de rollback/estado válido ante una migración fallida.
- [ ] 3.13 Implementar `SshHostRepository` y `CredentialRepository`.
- [ ] 3.14 Implementar `TunnelRepository`, incluida la ruta de 0/1/2 jumps y opciones SSH avanzadas.
- [ ] 3.15 Implementar `TunnelGroupRepository` y relaciones N:M.
- [ ] 3.16 Implementar repositories de Project, Environment y Tag.
- [ ] 3.17 Implementar repositorio de trusted host keys.
- [ ] 3.18 Implementar persistencia de settings, favoritos, recientes y atajos de teclado.
- [ ] 3.19 Añadir integration tests con SQLite real para cada repository.
- [ ] 3.20 Añadir tests de foreign keys y políticas de borrado acordadas.
- [ ] 3.21 Añadir tests transaccionales para eliminación de túnel y limpieza de relaciones.
- [ ] 3.22 Implementar snapshot SQLite consistente para backups sin copiar ingenuamente el archivo WAL activo.
- [ ] 3.23 Implementar retención de los 10 backups más recientes.
- [ ] 3.24 Implementar el flag `dirty` y el backup periódico de máximo una vez cada 30 minutos con cambios.
- [ ] 3.25 Implementar backup previo a migración, importación, restauración y eliminación masiva.
- [ ] 3.26 Añadir tests de snapshots, retención y restauración de SQLite.
- [ ] 3.27 Implementar detección de base corrupta/fallo de apertura sin sobrescritura silenciosa.
- [ ] 3.28 Añadir tests de recuperación ante una base no cargable.
- [ ] 3.29 Ejecutar `.\mvnw.cmd verify`.

## 4. Milestone 0.1.0 — vertical slice de túneles individuales

- [ ] 4.1 Crear los modelos de dominio `SshHost`, `Credential`, `Tunnel`, `SshRoute` y tipos de configuración relacionados.
- [ ] 4.2 Implementar validaciones de host y túnel: nombres únicos, puertos, bind, destino y coherencia de jumps.
- [ ] 4.3 Implementar `SaveSshHostUseCase`, `DeleteSshHostUseCase` y consultas de hosts.
- [ ] 4.4 Implementar `SaveTunnelUseCase`, `DeleteTunnelUseCase`, duplicado y consultas de túneles.
- [ ] 4.5 Definir el contrato productivo de `SshTunnelEngine` a partir del spike aceptado.
- [ ] 4.6 Convertir el spike aceptado en `MinaSshTunnelEngine` productivo sin filtrar clases MINA fuera de infraestructura.
- [ ] 4.7 Implementar `TunnelConnectionRequest` como snapshot inmutable de configuración efectiva.
- [ ] 4.8 Implementar `TunnelHandle` y cierre determinista de todos los recursos de una ruta.
- [ ] 4.9 Implementar colaboración de host-key verification con public key real y fingerprint SHA-256.
- [ ] 4.10 Implementar `HostKeyApprovalRequest` y persistencia inmediata de una aprobación.
- [ ] 4.11 Implementar `HostKeyApprovalCoordinator` serializado.
- [ ] 4.12 Implementar detección y bloqueo de host key cambiada.
- [ ] 4.13 Implementar `TestTunnelConnectionUseCase` reutilizando la misma construcción de ruta SSH.
- [ ] 4.14 Implementar `TunnelRuntime`, `desiredState`, `actualState`, `startedAt` y `executionGeneration`.
- [ ] 4.15 Implementar `TunnelRuntimeManager` como coordinador único de start/stop.
- [ ] 4.16 Implementar start idempotente.
- [ ] 4.17 Implementar stop idempotente.
- [ ] 4.18 Implementar serialización de operaciones por `tunnelId`.
- [ ] 4.19 Implementar límite de conexiones/reconexiones mediante una cola FIFO y un coordinador equivalente a `Semaphore`.
- [ ] 4.20 Implementar estados `CLOSED`, `QUEUED`, `CONNECTING`, `OPEN`, `RECONNECTING`, `STOPPING` y `ERROR`.
- [ ] 4.21 Implementar keep-alive global por defecto de 30s y override por túnel.
- [ ] 4.22 Implementar timeout global por defecto de 15s por tramo y override por túnel.
- [ ] 4.23 Implementar 5 reintentos por defecto y override por túnel.
- [ ] 4.24 Implementar backoff 2s, 5s, 10s, 20s y 30s.
- [ ] 4.25 Implementar reconstrucción completa de la ruta en cada reconexión.
- [ ] 4.26 Implementar cancelación cooperativa y descarte de callbacks de generaciones obsoletas.
- [ ] 4.27 Implementar `ApplicationEventPublisher` y eventos básicos de lifecycle.
- [ ] 4.28 Aislar fallos de listeners sin afectar al túnel ni a otros listeners.
- [ ] 4.29 Implementar logging runtime con `tunnelId`, `hostId` y contexto relevante sin passwords.
- [ ] 4.30 Implementar editor modal de Host SSH en JavaFX.
- [ ] 4.31 Implementar mostrar/cambiar password en el editor de Host.
- [ ] 4.32 Implementar lista de Hosts SSH.
- [ ] 4.33 Implementar lista de Túneles con `TableView`.
- [ ] 4.34 Implementar editor amplio de túnel dentro del shell.
- [ ] 4.35 Implementar selección independiente de Jump 1, Jump 2 y SSH final.
- [ ] 4.36 Implementar campos y validaciones de Local Port Forwarding.
- [ ] 4.37 Implementar ajustes avanzados de timeout, keep-alive, retry count y opciones SSH libres.
- [ ] 4.38 Impedir opciones SSH libres que contradigan campos estructurados.
- [ ] 4.39 Implementar `Probar conexión` y su feedback.
- [ ] 4.40 Implementar diálogo serializado de aprobación de fingerprint.
- [ ] 4.41 Implementar flujo explícito para fingerprint cambiada.
- [ ] 4.42 Implementar acciones individuales Iniciar, Detener y Reintentar.
- [ ] 4.43 Implementar visualización de estado, intento de reconexión y tiempo activo desde `startedAt`.
- [ ] 4.44 Implementar historial de eventos de la sesión actual por túnel.
- [ ] 4.45 Implementar edición de túnel activo con `Guardar y reiniciar`.
- [ ] 4.46 Implementar error explícito de puerto local ocupado sin selección automática de otro puerto.
- [ ] 4.47 Implementar mensajes de error amigables y `Ver detalles`/`Copiar error`.
- [ ] 4.48 Añadir tests unitarios exhaustivos del state machine, idempotencia, backoff y generaciones.
- [ ] 4.49 Añadir integration tests productivos de MINA para conexión directa, 1 jump y 2 jumps.
- [ ] 4.50 Añadir tests del flujo de fingerprints desconocidas y cambiadas.
- [ ] 4.51 Añadir tests del editor/ViewModels de Host y Túnel donde sean estables.
- [ ] 4.52 Documentar en la guía de usuario: hosts, passwords, túneles, jumps, fingerprints, probar conexión, start/stop, reconexión y errores.
- [ ] 4.53 Generar artefactos instalable y portable de `0.1.0`.
- [ ] 4.54 Ejecutar SSH integration tests, `.\mvnw.cmd verify` y smoke test del package `0.1.0`.

## 5. Milestone 0.2.0 — grupos y operación masiva

- [ ] 5.1 Completar el modelo de dominio de `TunnelGroup`.
- [ ] 5.2 Implementar casos de uso crear, editar, duplicar y eliminar grupos.
- [ ] 5.3 Implementar relación muchos-a-muchos sin ownership.
- [ ] 5.4 Implementar validación/advertencia previa por conflictos de endpoint local dentro de un grupo.
- [ ] 5.5 Implementar `StartGroupUseCase` usando `TunnelRuntimeManager`.
- [ ] 5.6 No reiniciar túneles que ya estén activos al iniciar un grupo.
- [ ] 5.7 Implementar `StopGroupUseCase` deteniendo miembros aunque pertenezcan a otros grupos.
- [ ] 5.8 Implementar `operationId` para operaciones masivas.
- [ ] 5.9 Implementar `GroupStartCompleted` y resumen del intento inicial.
- [ ] 5.10 Implementar progreso por conteos OPEN/CONNECTING/QUEUED/ERROR.
- [ ] 5.11 Implementar estado resumido de grupo derivado de sus miembros.
- [ ] 5.12 Implementar editor de grupo como pantalla completa dentro del shell.
- [ ] 5.13 Implementar búsqueda y selección de túneles dentro del editor de grupo.
- [ ] 5.14 Implementar iniciar/detener grupo desde la UI sin modal bloqueante.
- [ ] 5.15 Implementar cancelación de túneles queued/connecting mediante `Detener grupo`.
- [ ] 5.16 Añadir tests de concurrencia con límite configurable.
- [ ] 5.17 Añadir tests de túnel compartido entre varios grupos.
- [ ] 5.18 Añadir tests de fallo independiente de un miembro del grupo.
- [ ] 5.19 Añadir tests de parada de grupo sin reference counting.
- [ ] 5.20 Documentar grupos, conflictos locales, progreso y semántica de túneles compartidos.
- [ ] 5.21 Generar y verificar instalador/portable `0.2.0`.
- [ ] 5.22 Ejecutar `.\mvnw.cmd verify`.

## 6. Milestone 0.3.0 — Hub, organización y gestión de gran volumen

- [ ] 6.1 Implementar modelos y casos de uso de Project, Environment y Tag.
- [ ] 6.2 Implementar pantalla única `Organización` para proyectos, entornos y tags.
- [ ] 6.3 Implementar asignación opcional de proyecto y entorno en túneles.
- [ ] 6.4 Implementar proyecto y entorno en grupos sin imponer reglas de pertenencia.
- [ ] 6.5 Implementar tags N:M en túneles y grupos.
- [ ] 6.6 Implementar eliminación confirmada de proyecto/entorno dejando referencias a NULL.
- [ ] 6.7 Implementar favoritos en túneles y grupos.
- [ ] 6.8 Implementar recientes automáticos con límite fijo de 8 elementos.
- [ ] 6.9 Actualizar recientes solo por acciones explícitas de start y no por reconexiones.
- [ ] 6.10 Implementar Hub con acciones rápidas/favoritos.
- [ ] 6.11 Implementar sección de túneles activos en el Hub.
- [ ] 6.12 Implementar sección de recientes en el Hub.
- [ ] 6.13 Implementar resumen global de abiertos/reconectando/error con navegación a listas filtradas.
- [ ] 6.14 Implementar búsqueda global sobre túneles, grupos y hosts.
- [ ] 6.15 Implementar acciones directas desde resultados de búsqueda.
- [ ] 6.16 Implementar `Ctrl+K` como atajo inicial de búsqueda global.
- [ ] 6.17 Implementar carga de tablas mediante `ObservableList` + `FilteredList` + `SortedList`.
- [ ] 6.18 Implementar filtros por proyecto, entorno, tags, grupo y estado.
- [ ] 6.19 Implementar ordenación de columnas relevante para túneles, grupos y hosts.
- [ ] 6.20 Implementar selección múltiple de túneles.
- [ ] 6.21 Implementar bulk start/stop desde selección.
- [ ] 6.22 Implementar bulk add/remove group.
- [ ] 6.23 Implementar bulk favorite/unfavorite.
- [ ] 6.24 Implementar bulk assignment de proyecto/entorno.
- [ ] 6.25 Implementar acción global `Detener todos` con confirmación.
- [ ] 6.26 Implementar estado vacío de primera ejecución orientado a crear un Host SSH y explicar host → túnel → grupo.
- [ ] 6.27 Añadir tests de búsquedas/filtros sobre al menos 100 túneles.
- [ ] 6.28 Añadir tests de recientes, favoritos y clasificación.
- [ ] 6.29 Añadir tests de ViewModels del Hub.
- [ ] 6.30 Documentar Hub, búsqueda, filtros, favoritos, recientes, organización y bulk actions.
- [ ] 6.31 Generar y verificar instalador/portable `0.3.0`.
- [ ] 6.32 Ejecutar `.\mvnw.cmd verify`.

## 7. Milestone 0.4.0 — exportación, importación, backups y portabilidad

- [ ] 7.1 Definir DTOs del formato JSON de exportación con `formatVersion = 1`.
- [ ] 7.2 Implementar exportación completa de hosts, credentials, túneles, grupos, organización, settings, fingerprints, favoritos y recientes.
- [ ] 7.3 Incluir passwords en la exportación completa y mostrar la advertencia acordada.
- [ ] 7.4 Implementar importación de `formatVersion = 1`.
- [ ] 7.5 Clasificar entidades importadas por ID como nuevas, sin cambios o conflicto.
- [ ] 7.6 Implementar resolución `Conservar actual`.
- [ ] 7.7 Implementar resolución `Reemplazar`.
- [ ] 7.8 Implementar resolución `Crear copia` con nuevo UUID y nombre no conflictivo.
- [ ] 7.9 Implementar aplicación de una estrategia a múltiples conflictos.
- [ ] 7.10 Implementar preview/resumen de importación antes del commit.
- [ ] 7.11 Ejecutar importación completa en una única operación transaccional apropiada.
- [ ] 7.12 Impedir import/restauración con túneles activos sin ofrecer `Detener todos y continuar`.
- [ ] 7.13 Implementar UI de backups automáticos disponibles.
- [ ] 7.14 Implementar restauración creando primero un snapshot de seguridad del estado actual.
- [ ] 7.15 Implementar flujo de recuperación al detectar base corrupta.
- [ ] 7.16 Validar que la distribución portable almacena DB, backups y logs bajo `<application>/data/`.
- [ ] 7.17 Validar que la distribución instalada almacena datos por usuario bajo `%LOCALAPPDATA%`.
- [ ] 7.18 Validar copia del directorio portable a otra máquina/entorno Windows compatible.
- [ ] 7.19 Añadir tests round-trip export → import.
- [ ] 7.20 Añadir tests de conflictos y `Crear copia`.
- [ ] 7.21 Añadir tests de backup/restore con credenciales.
- [ ] 7.22 Añadir tests de compatibilidad del `formatVersion`.
- [ ] 7.23 Documentar exportación, passwords en export, importación, conflictos, backups, restauración, corrupción y modo portable.
- [ ] 7.24 Generar y verificar instalador/portable `0.4.0`.
- [ ] 7.25 Ejecutar `.\mvnw.cmd verify`.

## 8. Milestone 0.5.0 — experiencia Windows, diagnóstico y polish

- [ ] 8.1 Implementar aplicación de instancia única y bring-to-front de la instancia existente.
- [ ] 8.2 Implementar confirmación de cierre cuando existan túneles activos.
- [ ] 8.3 Implementar parada ordenada de reintentos, túneles y recursos SSH antes de salir.
- [ ] 8.4 Verificar que la aplicación no permanece residente en tray al cerrar.
- [ ] 8.5 Implementar mecanismo para minimizar procesos/recursos SSH huérfanos ante terminación anómala.
- [ ] 8.6 Implementar detección de ejecución anterior anómala.
- [ ] 8.7 Informar del cierre anómalo al siguiente arranque sin reabrir túneles.
- [ ] 8.8 Implementar manejo de suspensión/hibernación y reconexión normal de túneles cuya intención siga siendo RUNNING.
- [ ] 8.9 Confirmar que no existe reintento especial al volver la VPN/red después de agotar N intentos.
- [ ] 8.10 Implementar notificaciones Windows para eventos relevantes.
- [ ] 8.11 Implementar setting global para activar/desactivar notificaciones.
- [ ] 8.12 Implementar selección de tema claro, oscuro y seguir Windows.
- [ ] 8.13 Implementar persistencia del tema seleccionado.
- [ ] 8.14 Implementar registro y carga de atajos configurables.
- [ ] 8.15 Implementar pantalla de remapeo de atajos.
- [ ] 8.16 Detectar y bloquear conflictos ambiguos entre atajos.
- [ ] 8.17 Mantener los valores iniciales de atajos acordados.
- [ ] 8.18 Implementar navegación completa por teclado de los flujos principales.
- [ ] 8.19 Verificar tamaño inicial aproximado 1024×700.
- [ ] 8.20 Verificar layouts a 100%, 125%, 150% y 200% de escalado Windows.
- [ ] 8.21 Verificar que ningún estado dependa exclusivamente del color.
- [ ] 8.22 Completar mapeo de errores técnicos habituales a mensajes amigables.
- [ ] 8.23 Completar `Copiar error` con timestamp, túnel, ruta y detalle técnico sin password.
- [ ] 8.24 Implementar `Abrir carpeta de logs`.
- [ ] 8.25 Configurar rolling logs de 10 MB, 10 archivos y compresión.
- [ ] 8.26 Verificar `TRACE`, `DEBUG`, `INFO`, `WARN` y `ERROR`.
- [ ] 8.27 Implementar `Exportar diagnóstico` ZIP.
- [ ] 8.28 Incluir en diagnóstico versión app, Windows, logs, configuración sanitizada, runtime y errores.
- [ ] 8.29 Verificar automáticamente que el diagnóstico no contiene passwords.
- [ ] 8.30 Añadir smoke tests JavaFX de arranque, navegación, crear host, crear túnel y búsqueda global.
- [ ] 8.31 Añadir tests de lifecycle de aplicación/instancia única cuando sean automatizables.
- [ ] 8.32 Completar la guía de usuario de ajustes, atajos, temas, notificaciones, logs, diagnóstico y recuperación.
- [ ] 8.33 Revisar toda la guía en español contra el comportamiento implementado hasta `0.5.0`.
- [ ] 8.34 Generar y verificar instalador/portable `0.5.0`.
- [ ] 8.35 Ejecutar `.\mvnw.cmd verify`.

## 9. Cierre de v1 / 1.0.0

- [ ] 9.1 Revisar cada requirement y scenario de todas las delta specs contra la implementación.
- [ ] 9.2 Añadir o corregir tests para cualquier escenario especificado que no tenga verificación suficiente.
- [ ] 9.3 Ejecutar la topología completa de integration tests SSH.
- [ ] 9.4 Ejecutar todos los tests SQLite y migraciones desde todas las versiones de schema existentes.
- [ ] 9.5 Verificar export/import completo y restore de backup en un dataset representativo de 100+ túneles.
- [ ] 9.6 Verificar funcionamiento con aproximadamente 20 túneles activos y operaciones concurrentes.
- [ ] 9.7 Verificar que búsqueda/filtros siguen siendo responsivos con 100–500 configuraciones.
- [ ] 9.8 Verificar cierre normal con múltiples túneles y ausencia de recursos SSH vivos después de salir.
- [ ] 9.9 Verificar ejecución como usuario Windows estándar sin requerir administrador para uso habitual.
- [ ] 9.10 Verificar instalación limpia del `.exe`.
- [ ] 9.11 Verificar ejecución del paquete portable desde un directorio nuevo.
- [ ] 9.12 Verificar que el equipo de prueba no necesita un JRE/JDK externo.
- [ ] 9.13 Verificar versión de aplicación, schema DB y export format como números independientes.
- [ ] 9.14 Revisar logs a nivel TRACE para confirmar ausencia de passwords.
- [ ] 9.15 Revisar paquetes de diagnóstico para confirmar ausencia de passwords.
- [ ] 9.16 Revisar todos los textos visibles para asegurar UI en español.
- [ ] 9.17 Revisar que los recursos/textos están estructurados para futura localización al inglés.
- [ ] 9.18 Revisar accesibilidad básica de teclado y comunicación de estados sin dependencia exclusiva del color.
- [ ] 9.19 Revisar y completar `docs/user-guide/es/` para todas las funcionalidades de v1.
- [ ] 9.20 Actualizar `README.md` con instrucciones finales de build, tests, packaging y estructura del proyecto.
- [ ] 9.21 Ejecutar Spotless y `.\mvnw.cmd verify` en limpio.
- [ ] 9.22 Ejecutar el job Windows de release y generar el instalador `1.0.0`.
- [ ] 9.23 Generar el ZIP portable `1.0.0`.
- [ ] 9.24 Realizar smoke test de ambos artefactos de distribución.
- [ ] 9.25 Confirmar que todos los checkboxes de `tasks.md` reflejan el estado real antes de considerar el cambio listo para archivar.
