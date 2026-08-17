# Apache MINA SSHD — resultado del spike obligatorio

Fecha de ejecución: 2026-08-17
Versión evaluada: Apache MINA SSHD 2.18.0
Test permanente: `MinaSshSpikeIT`

## Alcance y topología

Se añadió `sshd-core` únicamente con scope de test mientras el gate siga abierto.
La topología reproducible definida para una ejecución con Docker Compose contiene
`jump-1`, `jump-2`, `ssh-final` y `tcp-target`, con usuarios y passwords
independientes para cada host SSH.

Docker no está instalado en el entorno de esta ejecución. Por ello, los escenarios
se ejecutaron contra un fixture controlado en la misma JVM: tres servidores MINA
independientes, cada uno con su propio puerto, credencial y host key, más un
servidor TCP local para el destino. El fixture conserva la ruta real de sesiones y
forwardings MINA, pero no sustituye una prueba de interoperabilidad con
`sshd-server` en contenedores.

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

## Limitaciones y decisiones para la implementación posterior

1. El `ConnectFuture` de MINA puede completarse cuando se establece el socket
   TCP, antes de que el peer haya enviado el banner SSH. El motor productivo no
   debe confiar únicamente en `ConnectFuture.verify(...)` para el timeout por
   tramo; debe aplicar el timeout al establecimiento de sesión/handshake y
   cancelar la generación completa si vence.
2. La verificación de host key se ejercitó con un `ServerKeyVerifier` controlado
   por el test. Todavía no existe persistencia ni UI de aprobaciones; eso pertenece
   a la implementación posterior al gate.
3. La ejecución con Docker Compose queda pendiente porque `docker` y
   `docker-compose` no están disponibles en este entorno. No se ha validado aún
   la interoperabilidad con OpenSSH ni el aislamiento de red entre contenedores.
4. En Windows, el fixture puede emitir warnings asíncronos de NIO2 al cerrar
   servidores con conexiones activas (`Executor has been shut down` o errores de
   socket). Los cierres explícitos devuelven correctamente y Failsafe no registra
   fallos, pero esta condición debe revalidarse con la topología Docker antes de
   considerar cerrado el requisito de shutdown limpio.
5. No se ha implementado `SshTunnelEngine` productivo ni se ha seleccionado MINA
   como dependencia runtime; esta sesión no avanza a la Fase 3.

## Gate

El fixture MINA in-process no ha mostrado un fallo material: todos los escenarios
críticos pasan. El gate formal queda **pendiente de cierre**, no rechazado, hasta
ejecutar la misma evidencia con la topología Docker Compose aprobada. No se
selecciona ninguna librería alternativa.

## APIs relevantes

- [`SshClient` y configuración de cliente](https://github.com/apache/mina-sshd/blob/master/docs/client-setup.md)
- `SshClient.setServerKeyVerifier(...)` para validación de host key.
- `ClientSession.addPasswordIdentity(...)` y `auth()` para password authentication.
- `ClientSession.createLocalPortForwardingTracker(...)` para Local Port Forwarding.
- `SshClient.setSessionHeartbeat(...)` para keep-alive.
- `KeyUtils.getFingerPrint(...)` para fingerprint de la clave pública.
