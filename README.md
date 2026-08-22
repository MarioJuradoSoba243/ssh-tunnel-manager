# SSH Tunnel Manager

Aplicación de escritorio Windows 11 para gestionar túneles SSH Local Port Forwarding de forma persistente, organizarlos por grupos/proyectos/entornos y operar múltiples túneles desde una UI JavaFX.

## Estado del proyecto

**Milestone 0.5.0 en validación final.** Apache MINA SSHD 2.18.0 superó el gate técnico Docker/in-process. La aplicación dispone del vertical slice de hosts, túneles individuales, grupos, organización, Hub, búsqueda, filtros, acciones masivas, configuración Windows, diagnóstico sanitizado, exportación/importación JSON, backups SQLite y recuperación de configuración; el estado exacto de packaging y las tareas pendientes se mantiene en `tasks.md`.

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
Apache MINA SSHD 2.18.0 (gate técnico aceptado)
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

Apache MINA SSHD 2.18.0 fue aceptado tras el spike obligatorio in-process y Docker.

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

## Exportación, importación y backups

Desde **Ajustes** se puede exportar la configuración completa a JSON e importarla
con una vista previa. La exportación incluye las contraseñas SSH recuperables y
la aplicación muestra una advertencia antes de crear el archivo. El formato
declara `formatVersion: 1` y no incluye estados runtime de túneles.

La importación clasifica los registros por UUID y permite conservar el estado
actual, reemplazarlo o crear una copia con UUID y nombre nuevos. Una importación
o restauración exige que no haya túneles activos; la UI ofrece detenerlos todos y
continuar. Antes de ambas operaciones se crea un snapshot de seguridad.

Los backups son snapshots SQLite consistentes creados con `VACUUM INTO`, no una
copia directa del archivo en uso. Se conservan los diez más recientes y se crea
un backup periódico cuando hay cambios. Si la base no puede abrirse, la
aplicación entra en modo de recuperación y permite restaurar un backup válido
sin sobrescribir silenciosamente la base dañada.

El modo instalado usa `%LOCALAPPDATA%\SSH Tunnel Manager\` y el portable usa
`<directorio-de-la-aplicacion>\data\` para la base, `backups\` y `logs\`. Para
mover el portable, cierra la aplicación y copia la carpeta completa; el archivo
`portable.flag` debe permanecer junto al ejecutable.

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

Desde PowerShell, situado en la raíz del repositorio. El toolchain soportado es el JDK 25 permanente:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

La comprobación debe mostrar Java 25 para `java`, `javac`, `jpackage` y `.\mvnw.cmd -version`.

Para ejecutar desde Maven:

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

Los integration tests SSH requieren Docker Desktop. Cuando `docker` no esté en
el PATH del proceso, utiliza el CLI explícito:

```powershell
$docker = "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin\docker.exe"
& $docker compose -f src/test/resources/ssh-spike/docker-compose.yml up --build -d
```

No uses `SshTunnelManagerApplication` como Main class de jpackage o de la
configuración **Application** de IntelliJ: esa clase es el `Application` JavaFX;
`ApplicationLauncher` es el punto de entrada plano común a Maven, IntelliJ y el
portable.

Para generar la application image, el instalador `.exe` y el ZIP portable en Windows 11 x64:

```powershell
.\scripts\package-windows.ps1 -Type all
```

El script requiere `jpackage` del JDK 25 permanente anterior. Para el instalador
Windows también se requieren `candle.exe` y `light.exe` de WiX Toolset en `PATH`.
El instalador se genera como instalación por usuario (`--win-per-user-install`),
por lo que no requiere privilegios de administrador para el uso habitual. La
application image, el instalador y el ZIP portable se generan en `dist/`.

El ejecutable del portable está en la raíz de la carpeta extraída:

```powershell
   .\dist\ssh-tunnel-manager-0.5.0-windows-x64-portable\ssh-tunnel-manager.exe
```

Si se parte del ZIP, extráelo completamente antes de ejecutar el `.exe`; no
ejecutes el archivo desde dentro del visor comprimido.

Los cambios relacionados con SSH deberán ejecutar además los integration tests correspondientes.

Los cambios de distribución deberán validar `jpackage` en Windows.

### Ajustes Windows y diagnóstico 0.5.0

La sección **Ajustes** permite seleccionar tema claro, oscuro o seguir Windows,
activar las notificaciones, elegir el nivel de logging y remapear los atajos. Los
atajos se validan para impedir combinaciones duplicadas. También permite abrir la
carpeta de logs y generar un ZIP de diagnóstico sin contraseñas.

La aplicación usa una instancia por directorio de datos. Abrir de nuevo el
portable activa la instancia ya abierta; las instalaciones y portables ubicados
en directorios distintos mantienen instancias independientes. Al cerrar se
detienen los túneles y los recursos SSH antes de terminar el proceso. Una
ejecución anómala se comunica en el siguiente arranque y no reabre túneles.

Los logs se guardan en `%LOCALAPPDATA%\SSH Tunnel Manager\logs` en modo instalado y
en `data\logs` junto al portable. La rotación es de 10 MB, conserva 10 archivos y
comprime los anteriores.

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
