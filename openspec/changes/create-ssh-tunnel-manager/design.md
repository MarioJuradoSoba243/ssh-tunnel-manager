## Context

La aplicación será un gestor personal de túneles SSH para Windows 11 orientado a un desarrollador que trabaja habitualmente con más de 100 configuraciones persistidas y hasta aproximadamente 20 túneles simultáneamente activos.

El producto debe permitir Local Port Forwarding mediante autenticación usuario/contraseña, con cero, uno o dos jump servers intermedios y credenciales independientes por cada host de la ruta. Los túneles podrán iniciarse individualmente o mediante grupos, se mantendrán mediante keep-alive, podrán reconectar con un número limitado de intentos y deberán exponer su estado en una interfaz JavaFX responsiva.

La aplicación tendrá dos distribuciones Windows: instalador y portable. La configuración persistente utilizará SQLite. Las contraseñas son datos recuperables dentro del contexto de esta aplicación y pueden formar parte de backups/exportaciones, pero nunca deben escribirse en logs ni paquetes de diagnóstico.

Este documento define cómo se implementarán las capacidades descritas por las specs del cambio `create-ssh-tunnel-manager`.

## Goals / Non-Goals

### Goals

- Construir una aplicación de escritorio Windows 11 mantenible por un desarrollador con experiencia principal en Java.
- Mantener una arquitectura modular monolítica, explícita y comprensible, sin frameworks empresariales innecesarios.
- Separar estrictamente dominio, casos de uso, infraestructura y presentación.
- Mantener JavaFX, SQLite y el motor SSH fuera del modelo de dominio.
- Controlar todo el ciclo de vida de túneles desde un único coordinador runtime.
- Soportar 0, 1 o 2 jump servers y credenciales distintas por salto.
- Mantener cada túnel runtime aislado del resto y sin compartir conexiones SSH en v1.
- Proporcionar persistencia transaccional, migraciones, backups, importación/exportación y recuperación ante corrupción.
- Mantener la UI responsiva durante operaciones de red, persistencia, logging y operaciones masivas.
- Proteger las fronteras arquitectónicas mediante tests automáticos.
- Generar distribuciones Windows autocontenidas, tanto instalable como portable.
- Mantener el diseño preparado para añadir inglés en el futuro aunque v1 solo exponga español.

### Non-Goals

- Microservicios, backend HTTP o servidor local.
- Spring, Spring Boot o un contenedor de inyección de dependencias.
- Hibernate/JPA u otro ORM.
- Programación reactiva con Reactor/RxJava.
- Compartición de una misma conexión SSH entre varios túneles en v1.
- Persistir el estado runtime `OPEN`, `RECONNECTING`, etc. entre ejecuciones.
- Soportar Windows distinto de Windows 11 x64 en v1.
- Integrar actualización automática.
- Proporcionar una terminal SSH interactiva.
- Introducir una contraseña maestra o protección DPAPI de credenciales en v1.
- Automatizar exhaustivamente cada detalle visual de JavaFX.
- Fijar un porcentaje global de cobertura como criterio de build.

## Decisions

### Decision 1: Java 25 LTS como runtime y lenguaje base

La aplicación se implementará con Java 25 LTS.

**Why**

- El mantenedor principal domina Java.
- Una versión LTS reduce cambios de plataforma innecesarios.
- Permite utilizar virtual threads para operaciones de I/O bloqueantes.
- `jpackage` proporciona el mecanismo de distribución autocontenida requerido.

**Alternatives considered**

- C#/.NET/WinUI: descartado porque aumenta la fricción de mantenimiento para el desarrollador principal.
- Java no-LTS: descartado para evitar actualizar la plataforma con demasiada frecuencia.

---

### Decision 2: JavaFX como framework de interfaz

La UI se implementará exclusivamente con JavaFX.

Se utilizarán FXML para la estructura de las vistas y CSS para styling/temas. No se mezclará JavaFX con Swing.

La organización aproximada será:

```text
ui/
├── shell/
├── hub/
├── tunnels/
├── groups/
├── hosts/
├── organization/
├── settings/
└── common/
```

Los estilos se dividirán como mínimo en:

```text
base.css
light.css
dark.css
```

No se adoptará inicialmente una librería de componentes visuales externa como dependencia arquitectónica.

**Why**

- Permite una UI desktop nativa del ecosistema Java.
- FXML separa estructura visual y código.
- CSS facilita temas claro/oscuro.
- `TableView`, `FilteredList` y `SortedList` cubren bien el volumen esperado.

---

### Decision 3: Maven y un único proyecto

El proyecto será un único proyecto Maven, no un reactor multi-módulo.

Se incluirá Maven Wrapper:

```text
mvnw
mvnw.cmd
.mvn/
```

La separación arquitectónica se realizará mediante packages y tests de arquitectura.

Estructura aproximada:

```text
src/main/java/
└── <base-package>/
    ├── domain/
    ├── application/
    ├── infrastructure/
    └── ui/
```

**Why**

- Reduce ceremonia para una herramienta personal.
- Mantiene un build sencillo.
- Las fronteras lógicas siguen siendo explícitas sin multiplicar artefactos JAR.

---

### Decision 4: Java puro y constructor injection manual

No se utilizará Spring, Spring Boot, Guice, Dagger u otro contenedor DI.

Las dependencias se proporcionarán mediante constructores y se ensamblarán en un punto explícito de bootstrap, por ejemplo:

```text
ApplicationBootstrap
```

El bootstrap construirá:

```text
Database
Repositories
SshTunnelEngine
TunnelRuntimeManager
Application services
ViewModels / Controllers
```

**Why**

- El grafo de dependencias es pequeño.
- Facilita comprender y depurar el proyecto.
- Evita ocultar dependencias mediante reflexión/configuración implícita.

---

### Decision 5: Arquitectura modular monolítica por capas

Las capas serán:

```text
UI
 ↓
Application
 ↓
Domain
 ↑
Infrastructure
```

Reglas principales:

- `domain` no depende de JavaFX, JDBC, SQLite ni motor SSH.
- `application` no depende de `ui`.
- `ui` puede depender de `application` y modelos de dominio apropiados.
- `infrastructure` implementa puertos/interfaces definidos por las capas internas.
- Controllers JavaFX no contienen reglas de negocio, JDBC ni código SSH.

Dentro de cada capa se favorecerá package-by-feature cuando ayude a mantener cohesión:

```text
domain/
├── host/
├── tunnel/
├── group/
├── organization/
└── shared/
```

**Why**

- Mantiene el dominio independiente de detalles tecnológicos.
- Facilita sustituir motor SSH o persistencia.
- Reduce el riesgo de controllers/services monolíticos.

---

### Decision 6: Modelo de dominio independiente de JavaFX y persistencia

Entidades como:

```text
Tunnel
SshHost
TunnelGroup
SshRoute
Project
Environment
```

serán objetos Java normales.

No contendrán:

```text
StringProperty
ObservableList
@FXML
ResultSet
```

Los ViewModels podrán utilizar propiedades JavaFX y adaptarán los datos necesarios para bindings.

---

### Decision 7: MVVM ligero sobre JavaFX

Se utilizará una variante ligera:

```text
FXML View
   ↕
Controller
   ↕
ViewModel
   ↕
Application Services
```

Responsabilidades del Controller:

- binding y eventos de UI;
- navegación;
- apertura/cierre de diálogos;
- delegación al ViewModel.

Responsabilidades que no pertenecen al Controller:

- JDBC;
- reglas de dominio;
- lifecycle SSH;
- transacciones;
- import/export.

No se utilizará un framework MVVM externo.

---

### Decision 8: Shell principal de una sola ventana

La aplicación tendrá una única ventana principal con navegación lateral y contenido reemplazable.

Secciones principales:

```text
Inicio
Túneles
Grupos
Hosts SSH
Organización
Ajustes
```

`Organización` reunirá proyectos, entornos y tags.

El editor de túneles y el editor de grupos se mostrarán como vistas amplias dentro del shell.

El editor de host, confirmaciones y solicitudes de fingerprint podrán utilizar diálogos modales.

---

### Decision 9: TableView y filtrado en memoria

Las listas principales utilizarán `TableView`.

El flujo típico será:

```text
SQLite
  ↓
List<TunnelSummary>
  ↓
ObservableList
  ↓
FilteredList
  ↓
SortedList
  ↓
TableView
```

Las búsquedas y filtros de 100–500 configuraciones se realizarán en memoria en vez de ejecutar una query SQLite por cada pulsación.

Los cambios persistentes actualizarán la colección observable correspondiente.

---

### Decision 10: Búsqueda global estilo command palette

`Ctrl+K` será el valor inicial de la búsqueda global, pero los atajos podrán remapearse.

La búsqueda global se mostrará como overlay/modal ligero y devolverá túneles, grupos y hosts con acciones directas cuando sean aplicables.

Los atajos iniciales podrán incluir:

```text
Ctrl+K  búsqueda global
Ctrl+N  nuevo túnel
Ctrl+F  buscar/filtrar vista actual
F5      refrescar vista local
Enter   abrir/editar selección
Esc     cerrar diálogo / volver
```

Los atajos configurables se persistirán en ajustes y la UI impedirá configuraciones ambiguas sin resolución explícita.

No se definirá un atajo por defecto para `Detener todos`.

---

### Decision 11: Estado runtime separado de la configuración

SQLite almacenará configuración, no la máquina de estados activa.

Se utilizará un `TunnelRuntimeManager` en memoria con registros de runtime aproximadamente así:

```text
TunnelRuntime
├── tunnelId
├── desiredState
├── actualState
├── startedAt
├── connectionEstablishedAt
├── retryAttempt
├── lastError
└── executionGeneration
```

`desiredState`:

```text
RUNNING
STOPPED
```

`actualState`:

```text
CLOSED
QUEUED
CONNECTING
OPEN
RECONNECTING
STOPPING
ERROR
```

Al iniciar la aplicación, el manager comienza vacío y los túneles se consideran cerrados.

---

### Decision 12: El tiempo visible representa la intención activa

La UI principal mostrará el tiempo transcurrido desde que el usuario solicitó iniciar el túnel (`startedAt`).

Las reconexiones automáticas no reiniciarán ese contador.

Una parada manual seguida de un nuevo inicio sí lo reiniciará.

`connectionEstablishedAt` podrá mantenerse internamente para diagnóstico, pero no será el tiempo principal mostrado.

---

### Decision 13: Casos de uso explícitos

La capa Application favorecerá casos de uso pequeños y concretos en vez de servicios enormes.

Ejemplos:

```text
StartTunnelUseCase
StopTunnelUseCase
StartGroupUseCase
StopGroupUseCase
SaveTunnelUseCase
DeleteTunnelUseCase
TestTunnelConnectionUseCase
ImportConfigurationUseCase
RestoreBackupUseCase
```

Los casos de uso podrán utilizar servicios compartidos cuando sea necesario.

---

### Decision 14: TunnelRuntimeManager como único coordinador del lifecycle

Ningún Controller, ViewModel ni caso de uso abrirá directamente conexiones SSH.

Flujo:

```text
StartTunnelUseCase
       ↓
TunnelRuntimeManager
       ↓
SshTunnelEngine
```

El manager será responsable de:

- idempotencia de start/stop;
- estado deseado y estado real;
- colas de conexión;
- límite de concurrencia;
- reconexión;
- backoff;
- cancelación;
- generación de ejecución;
- publicación de eventos runtime.

---

### Decision 15: Start y stop idempotentes

`start(tunnelId)` no creará una segunda ejecución si el túnel ya está:

```text
QUEUED
CONNECTING
OPEN
RECONNECTING
```

`stop(tunnelId)` sobre un túnel cerrado será válido y garantizará:

```text
desiredState = STOPPED
actualState = CLOSED
```

Esto permite que grupos distintos compartan un mismo túnel sin duplicarlo.

---

### Decision 16: Operaciones serializadas por túnel

Las operaciones lifecycle de un mismo `tunnelId` se serializarán mediante un coordinador/lock por túnel.

No se utilizará un lock global.

Ejemplo:

```text
Tunnel A start + Tunnel A stop → serializados
Tunnel B start                 → puede ejecutarse en paralelo
```

---

### Decision 17: Virtual threads para operaciones SSH

Las operaciones de red/bloqueantes se ejecutarán mediante virtual threads.

JavaFX conservará su Application Thread exclusivamente para interacción/UI.

El flujo de actualización será:

```text
SSH worker
  ↓
application event
  ↓
Platform.runLater(...)
  ↓
ViewModel / JavaFX
```

No se utilizarán Reactor o RxJava en v1.

---

### Decision 18: Límite de conexiones mediante un coordinador equivalente a Semaphore

El límite global configurable controlará tanto conexiones iniciales como reconexiones.

Un túnel sin permiso de conexión estará en:

```text
QUEUED
```

cuando pueda iniciar:

```text
CONNECTING
```

o, si procede, ejecutará su intento de `RECONNECTING`.

El diseño inicial puede implementarse mediante `Semaphore` más una cola FIFO.

No habrá prioridad entre una acción manual y una reconexión en v1: se utilizará orden FIFO.

**Why**

Una caída de red con 20 túneles no debe producir 20 reconstrucciones multi-hop simultáneas si el límite configurado es menor.

---

### Decision 19: Cancelación cooperativa y generaciones de ejecución

`stop()`:

1. establece `desiredState = STOPPED`;
2. cancela esperas y backoff;
3. solicita cierre del handle SSH;
4. interrumpe operaciones bloqueantes cuando sea seguro;
5. invalida resultados de operaciones anteriores.

Cada nueva ejecución utilizará un identificador/generación.

Los callbacks que pertenezcan a una generación anterior no podrán modificar el runtime actual.

---

### Decision 20: Política de reconexión fuera del motor SSH

`SshTunnelEngine` ejecutará un intento individual de conexión.

La política:

- número de reintentos;
- backoff;
- estado `RECONNECTING`;
- cancelación;
- agotamiento de intentos;

pertenecerá a `TunnelRuntimeManager`.

Ante pérdida de cualquier tramo, la reconexión destruirá la ruta antigua y reconstruirá desde cero:

```text
Jump 1
 ↓
Jump 2
 ↓
SSH final
 ↓
Local forward
```

No se intentará reparar únicamente el salto roto.

---

### Decision 21: Defaults de conectividad

Valores iniciales:

```text
keep-alive        30 segundos
connection timeout 15 segundos por tramo SSH
retry count        5
```

Backoff de reconexión inicial:

```text
2s → 5s → 10s → 20s → 30s
```

El máximo será 30 segundos.

Los tres primeros valores tendrán configuración global y overrides por túnel según las specs.

La secuencia de backoff no será configurable en v1.

---

### Decision 22: Abstracción del motor SSH

Se definirá un puerto de infraestructura equivalente a:

```java
interface SshTunnelEngine {
    TunnelHandle openTunnel(TunnelConnectionRequest request);
    ConnectionTestResult testConnection(TunnelConnectionRequest request);
    void closeTunnel(TunnelHandle handle);
}
```

El contrato real podrá ajustarse durante implementación para contemplar asincronía/cancelación, pero el resto de la aplicación no dependerá de clases específicas de la librería SSH.

El motor conoce:

- construir una ruta;
- autenticar;
- validar host keys mediante colaboración;
- abrir Local Port Forwarding;
- probar destino;
- cerrar recursos;
- reportar pérdida de conexión.

El motor no conoce:

- grupos;
- favoritos;
- política de N reintentos;
- backoff de negocio;
- notificaciones;
- `RECONNECTING 3/5`.

---

### Decision 23: Apache MINA SSHD como candidato condicionado a spike

La implementación candidata será:

```text
MinaSshTunnelEngine
```

sobre Apache MINA SSHD.

MINA no se considerará definitivamente aceptado hasta superar un spike técnico obligatorio que demuestre:

```text
✓ conexión directa con usuario/password
✓ 1 jump con credenciales independientes
✓ 2 jumps con credenciales independientes en los 3 hosts
✓ fingerprint de cada host
✓ rechazo de fingerprint desconocida
✓ autorización manual
✓ Local Port Forwarding
✓ destinationHost distinto del host SSH final
✓ keep-alive
✓ detección de desconexión
✓ cierre/cancelación limpia
✓ timeout de conexión
✓ destino inaccesible
```

Si existe un fallo serio o una complejidad desproporcionada en estos requisitos, la decisión se reabrirá antes de construir el subsistema completo.

Orden de alternativas:

```text
1. mwiede/JSch
2. Plink
3. OpenSSH externo
```

---

### Decision 24: Una ruta SSH independiente por túnel

Cada túnel abierto tendrá sus propias sesiones/ruta SSH.

Dos túneles que comparten los mismos hosts no compartirán físicamente conexiones SSH en v1.

**Why**

- Evita reference counting.
- Evita ownership de rutas compartidas.
- Aísla fallos y reconexiones.
- Mantiene la semántica de grupos simple.
- El máximo previsto de aproximadamente 20 túneles hace aceptable el coste.

Si un jump compartido cae, cada túnel afectado entrará individualmente en su ciclo de reconexión.

---

### Decision 25: Snapshot inmutable de configuración al iniciar

Al comenzar una ejecución se construirá un `TunnelConnectionRequest` inmutable con la configuración efectiva:

```text
endpoint local
destino
ruta de hosts
credenciales necesarias
timeouts
keep-alive
retry settings
opciones SSH
```

Una edición posterior de la configuración persistida no modificará una conexión viva.

Los cambios se aplicarán mediante `Guardar y reiniciar`.

---

### Decision 26: Gestión de fingerprints fuera de la UI y de MINA

La confianza se basará internamente en la host public key real asociada a:

```text
hostname + sshPort
```

Se almacenarán:

```text
host
port
key algorithm
public host key
```

La fingerprint SHA-256 se calculará para presentación.

La infraestructura SSH emitirá una solicitud equivalente a:

```text
HostKeyApprovalRequest
```

Un `HostKeyApprovalCoordinator` de la capa de aplicación/presentación serializará las solicitudes de autorización.

MINA no abrirá ventanas JavaFX directamente.

Si varios túneles descubren el mismo host simultáneamente, una aceptación persistida se reutilizará y los demás no volverán a preguntar.

---

### Decision 27: Probar conexión reutiliza la misma ruta SSH

`TestTunnelConnectionUseCase` utilizará el mismo mecanismo de construcción de ruta que una conexión real.

Una vez autenticado el host final, intentará abrir una conexión TCP remota a:

```text
destinationHost:destinationPort
```

No abrirá el listener del `localPort`.

Después cerrará todos los recursos temporales.

Esto permite probar la conectividad remota aunque el puerto local configurado esté ocupado.

---

### Decision 28: Eventos internos ligeros

Se utilizará un `ApplicationEventPublisher` interno para desacoplar consumidores.

Eventos orientativos:

```text
TunnelStateChanged
TunnelOpened
TunnelClosed
TunnelReconnectStarted
TunnelError
GroupStartCompleted
```

El runtime actualizará primero su estado interno y publicará después el evento.

Los listeners podrán incluir:

```text
UI
logging
notifications
Hub
```

Las excepciones de un listener se aislarán y registrarán; no afectarán al túnel ni a los demás listeners.

No se utilizará infraestructura externa de mensajería.

---

### Decision 29: OperationId para acciones masivas

Cada inicio/parada masivo tendrá un `operationId` UUID.

Los eventos y logs relacionados podrán correlacionarse mediante ese identificador.

Una operación de `StartGroup` se considerará terminada cuando todos los túneles solicitados hayan alcanzado un resultado estable del intento inicial:

```text
OPEN
ERROR
CLOSED por cancelación
```

No esperará reconexiones futuras.

---

### Decision 30: SQLite + JDBC directo

La persistencia utilizará:

```text
SQLite
JDBC
org.xerial sqlite-jdbc
```

No se utilizarán JPA, Hibernate ni jOOQ.

La capa de persistencia expondrá repositories explícitos.

Ejemplo conceptual:

```java
interface TunnelRepository {
    Optional<Tunnel> findById(UUID id);
    List<Tunnel> findAll();
    void save(Tunnel tunnel);
    void delete(UUID id);
}
```

---

### Decision 31: Esquema relacional normalizado

Tablas previstas:

```text
ssh_host
credential
tunnel
tunnel_ssh_option
tunnel_group
tunnel_group_member
project
environment
tag
tunnel_tag
group_tag
trusted_host_key
recent_item
application_setting
keyboard_shortcut
schema_version
```

Podrán aparecer tablas auxiliares adicionales durante la definición física, pero no se almacenará toda la configuración como un JSON monolítico dentro de SQLite.

Los campos avanzados conocidos se representarán como columnas nullable:

```text
keep_alive_override
connection_timeout_override
retry_count_override
```

`NULL` significará usar el valor global.

Las opciones SSH libres se almacenarán en `tunnel_ssh_option`.

---

### Decision 32: UUID persistidos como texto

Las entidades persistentes usarán UUID generados en la aplicación y almacenados como texto.

**Why**

- sobreviven export/import;
- son legibles;
- no dependen de autoincrementos;
- el volumen de datos no justifica optimización a BLOB.

---

### Decision 33: Modelo de credential separado

`ssh_host` referenciará un registro `credential`.

Para v1 el modelo será simple:

```text
credential
├── id
├── username
└── password
```

La contraseña será recuperable desde SQLite tanto en modo instalado como portable.

No se utilizará DPAPI/JNA ni contraseña maestra en v1.

La separación de entidad se mantiene porque facilita evolucionar posteriormente hacia otros tipos de autenticación.

Las contraseñas nunca se escribirán en logs ni diagnósticos.

---

### Decision 34: Transacciones y foreign keys

Las operaciones de dominio que modifiquen varios registros se ejecutarán dentro de una única transacción.

Ejemplos:

- eliminar túnel y relaciones;
- duplicar túnel/grupo;
- importar;
- restaurar;
- borrar proyecto/entorno;
- cambios N:M.

Si ocurre un error:

```text
ROLLBACK
```

Se activará:

```sql
PRAGMA foreign_keys = ON;
```

Las políticas `ON DELETE` se definirán según el dominio.

Ejemplo:

```text
Tunnel → Project      ON DELETE SET NULL
Tunnel → Environment  ON DELETE SET NULL
```

Los hosts SSH referenciados no podrán eliminarse mientras existan dependencias.

---

### Decision 35: WAL y conexiones JDBC cortas

La base se abrirá con:

```sql
PRAGMA journal_mode = WAL;
```

Cada operación repository obtendrá una conexión JDBC corta desde una fábrica/DataSource y la cerrará al finalizar.

No se compartirá un único `Connection` entre virtual threads.

No se necesita un pool grande de conexiones para v1.

---

### Decision 36: Migrador SQLite propio y simple

No se utilizará Flyway en v1.

Se implementará un `DatabaseMigrator` pequeño con scripts SQL versionados y una tabla/versionado de esquema.

Ejemplo:

```text
V001__initial_schema.sql
V002__...
```

El migrador:

1. lee `schema_version`;
2. ejecuta migraciones pendientes en orden;
3. actualiza la versión solo después de una migración correcta.

La aplicación creará un backup antes de una migración de schema.

---

### Decision 37: Exportación JSON independiente del schema SQLite

La exportación manual será un formato JSON legible independiente del schema físico.

Ejemplo conceptual:

```json
{
  "formatVersion": 1,
  "applicationVersion": "...",
  "exportedAt": "...",
  "hosts": [],
  "credentials": [],
  "tunnels": [],
  "groups": []
}
```

Contendrá toda la información definida por las specs, incluidas contraseñas.

`formatVersion` se versionará independientemente de:

```text
applicationVersion
databaseSchemaVersion
```

Las importaciones resolverán conflictos a partir de IDs persistentes.

---

### Decision 38: Backups automáticos como snapshots SQLite

Los backups automáticos serán copias consistentes de la base SQLite, no exports JSON.

```text
backup-<timestamp>.db
```

Se mantendrán los 10 más recientes.

La copia deberá producir un snapshot consistente incluso cuando SQLite use WAL; no se realizará una copia ingenua del archivo principal mientras existen escrituras activas.

El mecanismo concreto de snapshot podrá utilizar la API de backup/copia segura disponible en la capa SQLite.

---

### Decision 39: Política de backup

Se mantendrá un flag lógico `dirty` cuando haya modificaciones desde el último backup.

Si existen cambios, se generará como máximo un backup periódico cada 30 minutos.

Además se realizará un backup inmediatamente antes de:

- migración de schema;
- importación;
- restauración;
- eliminación masiva.

No será obligatorio crear otro backup al cerrar si ya existe uno suficientemente reciente.

---

### Decision 40: Ubicaciones instalado y portable

Modo instalado:

```text
%LOCALAPPDATA%/
└── SSH Tunnel Manager/
    ├── ssh-tunnel-manager.db
    ├── backups/
    └── logs/
```

Modo portable:

```text
<application>/
└── data/
    ├── ssh-tunnel-manager.db
    ├── backups/
    └── logs/
```

La edición se determinará por el paquete/distribución y no mediante una preferencia que el usuario cambie accidentalmente.

En modo instalado habrá una única base por usuario de Windows.

---

### Decision 41: Recientes y favoritos

Favoritos:

```text
tunnel.is_favorite
tunnel_group.is_favorite
```

Recientes:

```text
recent_item
├── item_type
├── item_id
└── last_started_at
```

Los timestamps técnicos persistentes se almacenarán en UTC y la UI los convertirá a hora local.

---

### Decision 42: Logging con SLF4J + Log4j2

La API utilizada por la aplicación será SLF4J y Log4j2 será la implementación.

Niveles:

```text
TRACE
DEBUG
INFO
WARN
ERROR
```

Nivel por defecto:

```text
INFO
```

Los logs serán texto legible, no JSON.

Ejemplo:

```text
2026-08-11 10:23:42.381 INFO [tunnel-...] TunnelRuntimeManager
Tunnel opened tunnelId=... name="PostgreSQL PROD" local=127.0.0.1:15432
```

Se añadirá contexto como:

```text
tunnelId
hostId
groupId
operationId
```

cuando corresponda.

MINA utilizará la misma infraestructura SLF4J y sus categorías se configurarán según el nivel global de diagnóstico.

---

### Decision 43: Rotación de logs

Configuración inicial:

```text
máximo 10 MB por archivo
10 archivos retenidos
compresión de históricos
```

La contraseña se excluirá de todos los niveles, incluido TRACE.

---

### Decision 44: Paquete de diagnóstico ZIP

`Exportar diagnóstico` generará un ZIP parecido a:

```text
system.txt
runtime-state.json
configuration.json
logs/
```

Podrá contener:

- versión de aplicación;
- versión de Windows;
- hosts;
- usuarios;
- IPs;
- puertos;
- rutas;
- ajustes;
- estados;
- errores recientes.

No contendrá contraseñas.

---

### Decision 45: Contraseñas bajo demanda en memoria

La aplicación evitará mantener un catálogo global de passwords descifradas/duplicadas en memoria.

Cuando necesite una contraseña:

1. la recuperará de persistencia;
2. la entregará al motor SSH o a la UI solicitante;
3. descartará referencias innecesarias cuanto antes.

No se intentará garantizar borrado criptográfico perfecto de memoria JVM.

---

### Decision 46: Coordinación de fingerprint serializada

Las solicitudes interactivas de host key se procesarán una a una para evitar múltiples diálogos concurrentes durante el inicio masivo de grupos.

Los túneles que esperan autorización seguirán en una fase de conexión.

Al aceptar una fingerprint, se persistirá inmediatamente para que otras conexiones al mismo `host:port` puedan continuar sin repetir la pregunta.

---

### Decision 47: Errores no bloqueantes

Los errores operativos ordinarios se mostrarán mediante estado de fila y toast/banner.

Los diálogos modales se reservarán para decisiones que requieran una respuesta:

- fingerprint;
- confirmaciones destructivas;
- importación/conflictos;
- restauración;
- cierre con túneles activos.

---

### Decision 48: Testing base

Frameworks:

```text
JUnit 5
AssertJ
Mockito
ArchUnit
```

Mockito se utilizará cuando aporte valor; para dominio se favorecerán objetos reales y fakes pequeños.

Los ViewModels se probarán como Java normal siempre que sea posible.

---

### Decision 49: Unit tests e integration tests separados

Maven Surefire ejecutará tests rápidos/unitarios.

Maven Failsafe ejecutará integration tests siguiendo una convención como `*IT`.

Comandos:

```text
mvn test
mvn verify
```

`verify` será el criterio general de calidad antes de considerar una tarea completada.

---

### Decision 50: Tests SQLite contra SQLite real

Los repositories y migraciones se probarán sobre bases SQLite temporales reales.

Los tests cubrirán, entre otros:

- foreign keys;
- transacciones;
- rollback;
- N:M;
- migraciones;
- import/export;
- recuperación.

No se mockeará JDBC para simular estas garantías.

---

### Decision 51: Spike e integración SSH reproducibles

Los tests SSH completos utilizarán una topología controlada equivalente a:

```text
Client
 ↓
Jump 1
 ↓
Jump 2
 ↓
SSH Final
 ↓
TCP target
```

con credenciales independientes por host.

Se utilizará Docker Compose exclusivamente para estos tests cuando resulte la opción más reproducible.

Docker no será requisito para ejecutar la aplicación.

El spike de MINA será una condición previa para comprometer la implementación completa del motor SSH.

---

### Decision 52: Cobertura orientada al riesgo

Se generarán reportes mediante JaCoCo, pero inicialmente el build no fallará por un porcentaje global arbitrario.

Se exigirá cobertura especialmente fuerte de:

- dominio;
- máquina de estados;
- idempotencia;
- retry/backoff;
- grupos;
- migraciones;
- repositories;
- import/export;
- resolución de conflictos.

No se perseguirá cobertura artificial de getters, bindings triviales o FXML.

---

### Decision 53: Formato determinista

Se utilizará:

```text
Spotless
+
google-java-format
```

El build verificará el formato.

El análisis inicial se limitará a:

- compilador/warnings;
- ArchUnit;
- Spotless;
- tests.

SpotBugs/Sonar/PMD no se incorporarán por defecto en v1 salvo necesidad demostrada.

---

### Decision 54: Dependencias y plugins fijados

El `pom.xml` fijará versiones concretas de dependencias y plugins.

No se utilizarán:

```text
LATEST
rangos dinámicos
versiones flotantes
```

Esto incluye:

- JavaFX;
- Apache MINA SSHD, si supera el spike;
- sqlite-jdbc;
- SLF4J;
- Log4j2;
- JUnit;
- AssertJ;
- Mockito;
- ArchUnit;
- plugins Maven.

---

### Decision 55: jpackage para distribución Windows

Se utilizará `jpackage`.

Cada release producirá:

```text
ssh-tunnel-manager-<version>-windows-x64-installer.exe
ssh-tunnel-manager-<version>-windows-x64-portable.zip
```

El ZIP portable contendrá una application image autocontenida con el runtime Java requerido.

El build final de distribución se ejecutará en Windows; no se intentará generar el `.exe` final mediante cross-compilation desde Linux.

---

### Decision 56: Versionado separado

Se utilizará SemVer para la aplicación:

```text
0.1.0
0.2.0
...
1.0.0
```

Se mantendrán separadas:

```text
applicationVersion
databaseSchemaVersion
exportFormatVersion
```

`0.1.0` será el primer build usable interno, no una declaración de producto terminado.

---

### Decision 57: CI con GitHub Actions

El repositorio tendrá CI desde v1.

Jobs mínimos:

```text
compile
unit tests
SQLite integration tests
ArchUnit
format check
package
```

Los integration tests SSH con Docker se ejecutarán en un job separado.

El build de artefactos `jpackage` se realizará en un runner Windows.

---

### Decision 58: Tests JavaFX limitados y orientados a flujos críticos

No se automatizará exhaustivamente la UI.

Se priorizarán:

```text
ViewModels → unit tests
UI JavaFX → smoke/integration tests seleccionados
```

Flujos candidatos:

- arranque;
- navegación;
- crear host;
- crear túnel;
- búsqueda global.

---

### Decision 59: Definition of Done técnica

Antes de considerar completada una tarea OpenSpec, el cambio deberá poder superar como mínimo:

```text
.\mvnw.cmd verify
```

Cuando afecte al empaquetado:

```text
build jpackage
```

Cuando afecte al motor SSH:

```text
SSH integration tests
```

Estas reglas deberán repetirse en `AGENTS.md` para que Codex las respete.

---

### Decision 60: Tests de arquitectura obligatorios

ArchUnit protegerá como mínimo:

```text
domain
  NO depende de ui
  NO depende de infrastructure
  NO depende de JavaFX

application
  NO depende de ui
```

Se añadirán reglas adicionales si durante la implementación aparecen dependencias no deseadas.

### Decision 61: Documentación de usuario versionada junto al código

La aplicación mantendrá una guía de uso en español dentro del repositorio.

Estructura inicial:

```text
docs/
└── user-guide/
    └── es/
        └── README.md
```

La estructura deja espacio para una futura versión:

```text
docs/
└── user-guide/
    ├── es/
    └── en/
```

La guía deberá cubrir progresivamente, cuando dichas funciones existan:

- primera ejecución;
- creación y edición de hosts SSH;
- creación, prueba, inicio, parada y edición de túneles;
- jump servers y autorización de fingerprints;
- grupos y operaciones masivas;
- Hub, favoritos, recientes, búsqueda y filtros;
- proyectos, entornos y tags;
- ajustes globales y overrides;
- atajos configurables;
- importación, exportación, backups y restauración;
- logging y diagnóstico;
- modo instalado y portable;
- errores frecuentes y acciones de recuperación.

La documentación se mantendrá en el mismo cambio que modifica comportamiento visible para el usuario.

`README.md` del repositorio estará orientado al proyecto, build y desarrollo. La guía de usuario estará separada bajo `docs/user-guide/`.

La documentación de uso no deberá describir funcionalidades futuras como si ya estuvieran implementadas.

## Risks / Trade-offs

### Apache MINA SSHD todavía no está definitivamente validado

**Risk**

El requisito de dos jump servers con credenciales distintas, aprobación individual de host keys y Local Port Forwarding puede exponer limitaciones o complejidad no detectada en una lectura superficial de la API.

**Mitigation**

Ejecutar el spike SSH antes de construir el subsistema completo. Si no supera los escenarios definidos, evaluar `mwiede/JSch`, después Plink y finalmente OpenSSH externo.

---

### Una conexión SSH completa por túnel consume más recursos

**Trade-off**

Con 20 túneles que comparten rutas podrían existir muchas sesiones SSH redundantes.

**Benefit**

El lifecycle queda aislado, no existe reference counting y los fallos/reintentos de un túnel no alteran la semántica de otro.

**Mitigation**

Aceptar el coste en v1 y medir. Solo introducir multiplexación si los datos demuestran una necesidad real.

---

### Contraseñas recuperables en SQLite

**Risk**

Una persona con acceso a los datos locales/portables puede recuperar credenciales.

**Reason accepted**

En el ámbito definido, las credenciales no se consideran suficientemente sensibles como para justificar DPAPI, JNA o una contraseña maestra.

**Mitigation**

- advertencias en portable/exportación;
- nunca escribir passwords en logs;
- nunca incluir passwords en diagnósticos;
- mantener la arquitectura preparada para añadir otro `CredentialStore` si el contexto cambia.

---

### SQLite + acceso directo JDBC exige disciplina de repositorios

**Trade-off**

Se escribe más SQL manual que con un ORM.

**Benefit**

Menos dependencias, comportamiento explícito y un modelo pequeño.

**Mitigation**

Repositories, integration tests sobre SQLite real y migraciones versionadas.

---

### Migrador propio implica responsabilidad de mantenimiento

**Risk**

Errores en el migrador podrían dañar configuraciones existentes.

**Mitigation**

- scripts pequeños e inmutables;
- backup obligatorio antes de migrar;
- ejecución transaccional cuando SQLite lo permita;
- tests de migración desde todas las versiones soportadas.

---

### WAL y múltiples virtual threads pueden producir contención de escritura

**Risk**

SQLite solo permite un escritor efectivo a la vez.

**Mitigation**

- operaciones cortas;
- transacciones explícitas;
- conexiones cortas;
- no ejecutar persistencia pesada en el hilo JavaFX;
- monitorizar `SQLITE_BUSY` y diseñar timeout/retry técnico limitado si fuese necesario.

---

### FXML añade una segunda representación de las pantallas

**Trade-off**

La navegación entre FXML, Controller y ViewModel añade archivos frente a UI puramente programática.

**Benefit**

Mejor separación visual y estilos más mantenibles.

**Mitigation**

Controllers finos, convenciones de nombres y tests de ViewModels.

---

### JavaFX es una dependencia de UI específica

**Trade-off**

La capa UI no es portable a otro toolkit sin reescritura.

**Mitigation**

El dominio, application services, persistence y motor SSH permanecen independientes de JavaFX.

---

### Backups SQLite no son formato de intercambio

**Trade-off**

Un snapshot puede depender del schema/version de base.

**Mitigation**

Backups se utilizan para recuperación exacta; JSON versionado se utiliza para portabilidad entre versiones/máquinas.

---

### Los tests SSH con Docker incrementan el tiempo de CI

**Trade-off**

El job de integración será más lento y complejo que los unit tests.

**Mitigation**

Mantenerlo como job separado y ejecutar `mvn test` rápidamente durante desarrollo local; reservar la topología completa para cambios SSH y CI.

## Migration Plan

Se trata de una aplicación greenfield, por lo que no existe una base de datos de una versión anterior que migrar inicialmente.

La estrategia de evolución desde v1 será:

1. Crear schema SQLite inicial como versión `1`.
2. Crear `exportFormatVersion = 1`.
3. Introducir `DatabaseMigrator` antes de que existan datos de usuario relevantes.
4. Antes de cada futura migración:
   - crear snapshot consistente;
   - ejecutar scripts pendientes;
   - actualizar la versión solo al completar correctamente.
5. Mantener importadores de formatos de exportación antiguos cuando se modifique `formatVersion`.
6. Mantener `applicationVersion`, `databaseSchemaVersion` y `exportFormatVersion` independientes.

Para la adopción del motor SSH:

1. Implementar primero el spike de Apache MINA SSHD.
2. Validar todos los escenarios establecidos en la Decision 23.
3. Si supera el spike, fijar versión concreta y continuar con `MinaSshTunnelEngine`.
4. Si no lo supera, documentar la causa y reabrir únicamente esa decisión de diseño antes de evaluar la siguiente alternativa.

Para distribución:

1. Desarrollar y verificar mediante Maven Wrapper.
2. Generar application image autocontenida.
3. Validar ejecución portable.
4. Generar instalador con `jpackage` en Windows.
5. Validar arranque sin JDK/JRE instalado externamente.
6. Validar persistencia correcta en modo instalado y portable.

## Open Questions

### Motor SSH definitivo

Apache MINA SSHD es el candidato preferido, pero queda condicionado al spike técnico.

Esta es la única decisión arquitectónica principal deliberadamente abierta antes de implementación.

El spike debe cerrarla antes de que se implemente el subsistema SSH completo.

### Valores exactos de algunas opciones SSH avanzadas

Las specs permiten opciones avanzadas libres, pero el catálogo concreto de claves admitidas dependerá del motor SSH finalmente elegido.

El diseño obliga a que:

- sean validadas;
- no puedan contradecir configuración estructurada;
- permanezcan encapsuladas en infraestructura SSH.

El catálogo puede definirse tras aceptar el motor.

### Librería concreta de iconos JavaFX

Se utilizarán iconos vectoriales encapsulados en UI, pero la librería concreta no es una decisión arquitectónica y puede seleccionarse durante implementación.

### Detalles exactos de los smoke tests JavaFX

Los flujos críticos están identificados, pero la herramienta concreta de automatización UI podrá elegirse cuando exista el primer shell JavaFX funcional.
