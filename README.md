# SSH Tunnel Manager

Aplicación de escritorio Windows 11 para gestionar túneles SSH Local Port Forwarding de forma persistente, organizarlos por grupos/proyectos/entornos y operar múltiples túneles desde una UI JavaFX.

## Estado del proyecto

**Fases 1 y 3 completadas: bootstrap técnico y persistencia SQLite base.** El motor SSH fue validado en la Fase 2 como spike; la implementación completa de túneles todavía pertenece a fases posteriores.

El proyecto utiliza OpenSpec como fuente de verdad. El cambio inicial está en:

```text
openspec/changes/create-ssh-tunnel-manager/
```

Contiene:

```text
proposal.md
specs/
design.md
tasks.md
```

Las reglas operativas para agentes de código están en:

```text
AGENTS.md
```

## Stack aprobado

```text
Java 25 LTS
JavaFX
Maven
SQLite + JDBC + Xerial sqlite-jdbc
Apache MINA SSHD (candidato condicionado al spike técnico)
SLF4J + Log4j2
JUnit 5 + AssertJ + Mockito + ArchUnit
Spotless + google-java-format
jpackage
GitHub Actions
```

No se utilizarán Spring, Hibernate/JPA, un contenedor de inyección de dependencias ni programación reactiva en v1.

## Arquitectura

La dirección de dependencias aprobada es:

```text
UI
 ↓
Application
 ↓
Domain
 ↑
Infrastructure
```

El proyecto será un único proyecto Maven con separación por packages y tests ArchUnit.

## Persistencia durante el desarrollo

La Fase 3 usa JDBC directo con Xerial SQLite, migraciones propias y conexiones cortas por operación. Los integration tests crean bases SQLite temporales aisladas y no utilizan datos del usuario.

Las rutas se resuelven según el modo de distribución:

- instalado: `%LOCALAPPDATA%\SSH Tunnel Manager\`;
- portable: `<directorio-de-la-aplicacion>\data\`.

En ambos casos la base se llama `ssh-tunnel-manager.db` y los snapshots consistentes se guardan en `backups`. Los backups se generan con `VACUUM INTO`, se validan con `PRAGMA integrity_check` y se conservan los 10 más recientes.

## Motor SSH

Apache MINA SSHD es el candidato inicial, pero **no está aprobado definitivamente**.

La Fase 2 de `tasks.md` contiene un spike obligatorio para verificar:

- autenticación usuario/password;
- conexión directa;
- uno y dos jump servers;
- credenciales independientes por cada host;
- validación/aprobación de host keys;
- Local Port Forwarding;
- keep-alive;
- timeouts;
- cancelación;
- pérdida de conexión;
- destino remoto inaccesible.

Si el spike falla materialmente, debe detenerse el desarrollo SSH y revisarse `design.md` antes de seleccionar otra implementación.

## Roadmap de implementación

```text
Fase 1  Bootstrap técnico
Fase 2  Spike SSH (gate)
Fase 3  SQLite y persistencia base
0.1.0   Vertical slice de túneles individuales
0.2.0   Grupos y operaciones masivas
0.3.0   Hub, organización, búsqueda y filtros
0.4.0   Import/export, backups y portable
0.5.0   Experiencia Windows, diagnóstico y polish
1.0.0   Todas las specs del cambio verificadas
```

El detalle completo está en:

```text
openspec/changes/create-ssh-tunnel-manager/tasks.md
```

## Build

Los comandos canónicos del bootstrap son:


```powershell
.\mvnw.cmd spotless:apply
.\mvnw.cmd test
.\mvnw.cmd verify
```

El wrapper descarga Maven 3.9.11 y el proyecto requiere un JDK 25.

## Ejecutar durante el desarrollo

El launcher de desarrollo es:

```text
com.ssh.tunnelmanager.ui.ApplicationLauncher
```

Desde PowerShell, situado en la raíz del repositorio:

```powershell
.\mvnw.cmd javafx:run
```

Para ejecutar y depurar desde IntelliJ IDEA:

1. Abre la raíz del repositorio y deja que IntelliJ importe el proyecto como Maven.
2. Configura el Project SDK y el SDK del módulo con un JDK 25.
3. Crea una configuración **Application** con estos valores:
   - **Main class:** `com.ssh.tunnelmanager.ui.ApplicationLauncher`
   - **Use classpath of module:** `ssh-tunnel-manager`
   - **JRE:** el JDK 25 configurado para el proyecto
   - **Working directory:** la raíz del repositorio
   - **VM options:** ninguna; no añadas opciones `--module-path` ni `--add-modules`
4. Pulsa **Run** para ejecutar o **Debug** para depurar. Los breakpoints pueden
   colocarse en `ApplicationLauncher`, `SshTunnelManagerApplication` o cualquier
   clase de la aplicación.

También puedes crear una configuración **Maven** con:

```text
Command line: javafx:run
```

No uses `SshTunnelManagerApplication` como Main class de jpackage o de la
configuración **Application** de IntelliJ: esa clase es el `Application` JavaFX;
`ApplicationLauncher` es el punto de entrada plano común a Maven, IntelliJ y el
portable.

Para generar la application image, el instalador `.exe` y el ZIP portable en Windows 11 x64:

```powershell
.\scripts\package-windows.ps1 -Type all
```

El script requiere `jpackage` de un JDK 25. La application image, el instalador y el ZIP portable se generan en `dist/`.

El ejecutable del portable está en la raíz de la carpeta extraída:

```powershell
.\dist\ssh-tunnel-manager-0.1.0-windows-x64-portable\ssh-tunnel-manager.exe
```

Si se parte del ZIP, extráelo completamente antes de ejecutar el `.exe`; no
ejecutes el archivo desde dentro del visor comprimido.

Los cambios relacionados con SSH deberán ejecutar además los integration tests correspondientes.

Los cambios de distribución deberán validar `jpackage` en Windows.

## Distribución objetivo

Cada release estable deberá generar:

```text
ssh-tunnel-manager-<version>-windows-x64-installer.exe
ssh-tunnel-manager-<version>-windows-x64-portable.zip
```

El usuario final no deberá necesitar un JDK/JRE instalado externamente.

## Documentación

La documentación de uso vive en:

```text
docs/user-guide/es/
```

La UI de v1 y la guía serán en español. La estructura deberá permitir una futura traducción al inglés:

```text
docs/user-guide/en/
```

Los cambios visibles para el usuario deben actualizar la guía correspondiente en el mismo cambio.

## Estructura del código

El proyecto es un único artefacto Maven con separación por packages:

```text
src/main/java/com/ssh/tunnelmanager/
├── application/       # casos de uso y ViewModels sin JavaFX
├── domain/            # modelo de dominio independiente
├── infrastructure/    # adaptadores externos
└── ui/                # JavaFX, FXML y temas
```

El bootstrap ensambla el grafo explícitamente mediante constructores; no hay contenedor DI.

## Trabajo con Codex

Antes de modificar código, leer:

1. `AGENTS.md`
2. `openspec/changes/create-ssh-tunnel-manager/proposal.md`
3. las specs relevantes
4. `openspec/changes/create-ssh-tunnel-manager/design.md`
5. `openspec/changes/create-ssh-tunnel-manager/tasks.md`

OpenSpec es la fuente de verdad. Si la implementación descubre una contradicción o una decisión no definida, se debe detener la parte afectada y revisar los artefactos en lugar de inventar comportamiento.

## Definition of Done

Como mínimo, una tarea debe cumplir cuando corresponda:

```text
comportamiento implementado
tests relevantes añadidos
formato correcto
mvnw verify pasa
ArchUnit pasa
sin passwords en logs/diagnósticos
documentación de usuario actualizada
tasks.md actualizado
```

Para SSH se añaden los integration tests correspondientes.

Para packaging se añade la validación de `jpackage`.
