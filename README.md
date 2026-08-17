# SSH Tunnel Manager

Aplicación de escritorio Windows 11 para gestionar túneles SSH Local Port Forwarding de forma persistente, organizarlos por grupos/proyectos/entornos y operar múltiples túneles desde una UI JavaFX.

## Estado del proyecto

**Definición completada; implementación todavía no iniciada.**

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

El proyecto Maven todavía no existe: se creará en la Fase 1 de implementación.

Una vez completado el bootstrap, los comandos canónicos deberán ser equivalentes a:

```powershell
.\mvnw.cmd spotless:apply
.\mvnw.cmd test
.\mvnw.cmd verify
```

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
