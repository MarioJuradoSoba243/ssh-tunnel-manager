# Delta for Application Lifecycle

## ADDED Requirements

### Requirement: Instancia única
El sistema SHALL permitir una única instancia activa de la aplicación por sesión de usuario.

Si se ejecuta de nuevo mientras ya existe una instancia, SHALL activar o traer al frente la instancia existente en lugar de crear otra independiente.

#### Scenario: Segunda ejecución
- GIVEN una instancia ya abierta
- WHEN el usuario vuelve a ejecutar la aplicación
- THEN no se crea un segundo gestor independiente
- AND la instancia existente se lleva al frente

### Requirement: Cierre con túneles activos
Si existen túneles en estados activos o de transición, el sistema SHALL solicitar confirmación antes de cerrar la aplicación.

#### Scenario: Cancelar cierre
- GIVEN varios túneles abiertos
- WHEN el usuario pulsa cerrar y después cancela la confirmación
- THEN la aplicación permanece abierta
- AND los túneles continúan en su estado previo

#### Scenario: Confirmar cierre
- GIVEN túneles activos
- WHEN el usuario confirma `Cerrar y salir`
- THEN se detienen las reconexiones pendientes
- AND se detienen los túneles
- AND la aplicación finaliza después de la parada ordenada

### Requirement: Sin residencia tras el cierre
La aplicación SHALL NOT permanecer ejecutándose en la bandeja del sistema después de un cierre confirmado.

#### Scenario: Cierre normal
- GIVEN que el usuario confirma el cierre
- WHEN termina la parada de túneles
- THEN la aplicación deja de ejecutarse

### Requirement: Terminación de recursos SSH
En un cierre normal, los procesos, conexiones o recursos SSH iniciados por la aplicación SHALL finalizar antes de completar la salida.

La implementación SHALL minimizar también la posibilidad de procesos SSH huérfanos ante un cierre anómalo en la medida que permita Windows.

#### Scenario: Cierre normal con procesos SSH
- GIVEN túneles gestionados mediante recursos SSH activos
- WHEN la aplicación se cierra correctamente
- THEN dichos recursos dejan de ejecutarse

### Requirement: Detección de ejecución anterior anómala
El sistema SHALL poder detectar que la ejecución anterior no terminó correctamente y SHALL informar al usuario en la siguiente apertura.

El sistema SHALL NOT reabrir automáticamente los túneles que estaban activos antes del fallo.

#### Scenario: Apertura después de un crash
- GIVEN que la ejecución anterior terminó de forma inesperada
- WHEN se inicia de nuevo la aplicación
- THEN se informa al usuario del cierre anómalo
- AND todos los túneles comienzan `CLOSED`
