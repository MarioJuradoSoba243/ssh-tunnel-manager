# Apache MINA SSHD — resultado del spike obligatorio

Fecha de ejecución: 2026-08-22
Versión evaluada: Apache MINA SSHD 2.18.0
Test permanente: `MinaSshSpikeIT`

## Alcance y topología

Se evaluó `sshd-core` 2.18.0 y, tras superar el gate, queda disponible como
dependencia de runtime para la implementación productiva.
La topología reproducible definida para una ejecución con Docker Compose contiene
`jump-1`, `jump-2`, `ssh-final` y `tcp-target`, con usuarios y passwords
independientes para cada host SSH.

La topología se ejecutó con Docker Desktop mediante la ruta explícita del CLI
`%LOCALAPPDATA%\Programs\DockerDesktop\resources\bin\docker.exe`.
Publica `jump-1` en 2221, `jump-2` en 2222 y `ssh-final` en 2223; `tcp-target`
solo está disponible dentro de la red Docker en 5432. Los servidores SSH son
OpenSSH sobre Alpine y tienen credenciales y host keys independientes.

Además de la topología Docker, se conserva el fixture controlado en la misma JVM
para los escenarios de timeout de banner SSH y para la cobertura detallada de
forwarding y cierre.

## Resultados

Los 14 tests del spike pasaron mediante Failsafe.

| Escenario | Resultado | Evidencia |
| --- | --- | --- |
| Password directo | PASS | `supportsDirectPasswordAuthentication` |
| Un jump con credenciales independientes | PASS | `supportsOneJumpWithIndependentCredentials` |
| Dos jumps con credenciales independientes en los tres hosts | PASS | `supportsTwoJumpsWithIndependentCredentialsOnAllHosts` |
| Local Port Forwarding directo | PASS | `supportsDirectLocalPortForwarding` |
| Local Port Forwarding a través de un jump | PASS | `supportsLocalPortForwardingThroughOneJump` |
| Local Port Forwarding a través de dos jumps | PASS | `supportsLocalPortForwardingThroughTwoJumps` |
| `destinationHost` distinto del SSH final | PASS | `validatesDestinationIndependentlyFromFinalSshHost` usa `localhost` como destino TCP |
| Fingerprint SHA-256 de cada host de la ruta | PASS | `exposesAndValidatesSha256FingerprintForEveryRouteHost` |
| Host key desconocida sin aprobación | PASS: la conexión es rechazada | `rejectsUnknownHostKeyUntilExplicitApproval` |
| Aprobación explícita de host key | PASS: la conexión continúa | `rejectsUnknownHostKeyUntilExplicitApproval` |
| Cambio de fingerprint | PASS: la conexión es bloqueada | `blocksConnectionWhenTrustedFingerprintChanges` |
| Keep-alive de diseño y pérdida de sesión | PASS | `configuresTheDesignKeepAliveAndCanDetectSessionLoss`; heartbeat de 30 s |
| Timeout frente a peer que no envía banner SSH | PASS | `enforcesAConnectionTimeoutWhenThePeerNeverSendsAnSshBanner`; `NIO2_READ_TIMEOUT` y `ClientSessionEvent.TIMEOUT` |
| Cancelación, cierre y forwarding activo | PASS | `supportsCancellationAndCleanShutdownWithForwardingActive` |
| Destino TCP inalcanzable | PASS: el forwarding no entrega datos | `reportsAnUnreachableForwardingDestination` |

La prueba Docker permanente `MinaSshDockerSpikeIT` ejecutó 5/5 escenarios:

| Escenario Docker | Resultado |
| --- | --- |
| Conexión directa, un jump y dos jumps con credenciales independientes | PASS |
| Local Port Forwarding hacia `tcp-target:5432`, distinto de `ssh-final` | PASS |
| Rechazo de host key desconocida, aprobación explícita y bloqueo tras cambio | PASS |
| Pérdida del contenedor SSH final y cierre limpio | PASS |
| Destino inalcanzable y cancelación del forwarding | PASS |

## Limitaciones y decisiones para la implementación posterior

1. El `ConnectFuture` de MINA puede completarse cuando se establece el socket
   TCP, antes de que el peer haya enviado el banner SSH. El motor productivo no
   debe confiar únicamente en `ConnectFuture.verify(...)` para el timeout por
   tramo; debe aplicar el timeout al establecimiento de sesión/handshake y
   cancelar la generación completa si vence.
2. La verificación de host key se ejercitó con un `ServerKeyVerifier` controlado
   por el test. Todavía no existe persistencia ni UI de aprobaciones; eso pertenece
   a la implementación posterior al gate.
3. En Windows, MINA puede emitir warnings asíncronos de NIO2 al cerrar
   servidores con conexiones activas (`Executor has been shut down` o errores de
   socket). Los cierres explícitos devuelven correctamente y Failsafe no registra
   fallos. En Docker también aparecen errores de socket Windows al cancelar un
   cliente local; corresponden a la carrera normal de cierre del forwarding y no
   dejan contenedores fallidos ni tests rojos.
4. El watcher de identidades por defecto puede advertir sobre una clave
   `~/.ssh/id_ed25519` local con un tipo no soportado. Los escenarios usan
   exclusivamente password authentication y no dependen de esa clave.
5. El `SshTunnelEngine` productivo se implementará en la Fase 4; el gate solo
   acepta la viabilidad técnica del motor y no adelanta esa implementación.

## Gate

MINA queda **ACEPTADO**. Todos los escenarios críticos pasan en el fixture
in-process (14/14) y la topología Docker Compose interoperable con OpenSSH (5/5).
No se selecciona ninguna librería alternativa. La versión fijada para producción
es 2.18.0.

## APIs relevantes

- [`SshClient` y configuración de cliente](https://github.com/apache/mina-sshd/blob/master/docs/client-setup.md)
- `SshClient.setServerKeyVerifier(...)` para validación de host key.
- `ClientSession.addPasswordIdentity(...)` y `auth()` para password authentication.
- `ClientSession.createLocalPortForwardingTracker(...)` para Local Port Forwarding.
- `SshClient.setSessionHeartbeat(...)` para keep-alive.
- `KeyUtils.getFingerPrint(...)` para fingerprint de la clave pública.
