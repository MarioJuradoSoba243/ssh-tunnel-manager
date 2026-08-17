## Why

El trabajo diario requiere abrir repetidamente numerosos túneles SSH hacia distintas aplicaciones, proyectos y entornos. Las configuraciones suelen ser estables y muchos túneles se utilizan siempre juntos según la tarea. Gestionarlos manualmente con PuTTY consume tiempo, obliga a repetir operaciones y dificulta conocer qué túneles están activos, cuánto tiempo llevan abiertos y cuáles han fallado.

Se propone una aplicación de escritorio para Windows 11, de uso personal y orientada a un desarrollador con conocimientos medios de SSH, que permita guardar, organizar, iniciar, supervisar y detener túneles SSH Local Port Forwarding desde una interfaz única.

## What Changes

### Gestión SSH y túneles

- Añadir una libreta persistente de hosts SSH reutilizables con nombre, hostname/IP, puerto SSH configurable, usuario, contraseña y confianza de host key.
- Permitir únicamente túneles SSH Local Port Forwarding en v1.
- Permitir que cada túnel defina `bindAddress`, puerto local, host destino y puerto destino de forma independiente.
- Usar `127.0.0.1` como bind local por defecto, permitiendo modificarlo.
- Permitir una ruta SSH compuesta por un host SSH final y hasta dos jump servers intermedios.
- Permitir credenciales distintas de usuario y contraseña para cada host de la ruta.
- Permitir guardar túneles permanentemente y reutilizarlos entre ejecuciones.
- Permitir opciones SSH avanzadas, además de los parámetros estructurados, sin que puedan contradecir los campos controlados por la aplicación.
- Permitir probar una configuración validando la ruta SSH completa y la alcanzabilidad del destino sin dejar un túnel abierto permanentemente.

### Grupos y operación

- Permitir que un túnel exista sin grupo y que un mismo túnel pertenezca a varios grupos.
- Tratar los grupos como acciones masivas, no como propietarios de los túneles.
- Iniciar los túneles de un grupo de forma concurrente respetando un límite global configurable.
- No volver a iniciar un túnel que ya esté abierto cuando se inicia otro grupo que también lo contiene.
- Mantener independientes los fallos de los túneles de un grupo.
- Detener todos los túneles de un grupo cuando el usuario lo solicite, aunque alguno también pertenezca a otro grupo.
- Avisar antes de guardar un grupo que contenga túneles con endpoints locales en conflicto.
- Permitir operaciones masivas sobre varios túneles y duplicar túneles y grupos.

### Ciclo de vida y supervisión

- Mantener los túneles mediante keep-alive.
- Reintentar automáticamente una conexión perdida hasta un número máximo configurable.
- Proporcionar valores globales por defecto para timeout, keep-alive y reintentos, con override por túnel.
- Modelar estados operativos como cerrado, en cola, conectando, abierto, reconectando, deteniendo y error.
- Detectar un puerto local ocupado y fallar explícitamente sin elegir otro puerto.
- Mostrar estado, tiempo abierto, progreso de reconexión, errores y un historial de eventos de la sesión actual.
- Tras suspensión o hibernación, aplicar la política normal de reconexión a los túneles cuya conexión se haya perdido.
- No implementar recuperación especial basada en detectar la vuelta de una VPN o red después de agotar los reintentos; el usuario podrá reiniciar manualmente esos túneles.

### Organización y Hub

- Soportar 100 o más túneles configurados mediante proyectos, entornos y tags.
- Permitir que proyecto y entorno sean opcionales y administrables.
- Permitir tags libres y clasificación independiente de grupos y túneles.
- Permitir favoritos para túneles y grupos.
- Mantener automáticamente los 8 túneles o grupos iniciados más recientemente mediante acciones explícitas.
- Proporcionar un Hub con acciones rápidas, favoritos, recientes, túneles activos y resumen global de estados.
- Proporcionar búsqueda global sobre túneles, grupos y hosts, con acciones directas desde los resultados.
- Proporcionar filtros y ordenación en las vistas de gestión.

### Persistencia, backup e importación

- Persistir automáticamente las operaciones confirmadas sin un comando global de guardado.
- Separar configuración persistente y estado runtime; al abrir la aplicación todos los túneles comienzan cerrados.
- Exportar e importar la configuración completa, incluidas contraseñas y fingerprints.
- Mantener las contraseñas en claro en los archivos de exportación completos, mostrando una advertencia al usuario.
- Mantener 10 backups automáticos completos, incluidas las contraseñas.
- Permitir restaurar backups, creando un backup de seguridad antes de reemplazar la configuración actual.
- Resolver conflictos de importación explícitamente mediante conservar, reemplazar o crear copia.
- Identificar entidades mediante IDs persistentes y mantener nombres únicos dentro de cada tipo de entidad.
- Detectar corrupción de datos sin sobrescribir silenciosamente la configuración y ofrecer recuperación desde backups.
- Garantizar escrituras consistentes y recuperación del estado válido anterior cuando falle una operación persistente.
- No incluir papelera en v1.

### Credenciales e identidad SSH

- Recordar las contraseñas SSH para no solicitarlas en cada inicio.
- En la instalación normal, proteger las credenciales locales mediante un mecanismo apropiado para Windows que se concretará en `design.md`.
- En modo portable, mantener credenciales transferibles junto a la configuración para que la aplicación pueda copiarse a otra máquina.
- Permitir mostrar temporalmente la contraseña almacenada desde el editor de hosts.
- Requerir autorización manual de la fingerprint en la primera conexión a un host.
- Bloquear una conexión cuando cambie una fingerprint confiable hasta que el usuario autorice explícitamente el cambio.
- Permitir eliminar la confianza almacenada para forzar una nueva autorización.
- No registrar contraseñas en logs ni incluirlas en paquetes de diagnóstico.

### Logging y diagnóstico

- Utilizar niveles de log estándar como `TRACE`, `DEBUG`, `INFO`, `WARN` y `ERROR`.
- Aplicar rotación para impedir crecimiento ilimitado de logs.
- Permitir abrir la carpeta de logs, ver y copiar detalles de errores.
- Permitir generar un paquete de diagnóstico con versión de aplicación, Windows, logs, configuración sanitizada, estado runtime y errores recientes.
- Permitir que el diagnóstico incluya hostnames, IPs, puertos y usuarios, excluyendo contraseñas.

### Aplicación Windows y experiencia de usuario

- Soportar oficialmente Windows 11 x64.
- Distribuir la aplicación tanto con instalador como en formato portable.
- No requerir privilegios de administrador para el uso habitual.
- Ejecutar una única instancia; una segunda ejecución deberá activar la existente.
- Mantener la interfaz responsiva con 100 o más configuraciones y aproximadamente 20 túneles activos.
- No iniciar conexiones SSH al arrancar la aplicación.
- No incluir actualización integrada en v1.
- Soportar tema claro, oscuro y seguimiento del tema de Windows, con selección manual.
- Permitir las acciones principales mediante teclado y proporcionar atajos para operaciones frecuentes.
- Usar español como único idioma visible en v1, preparando la arquitectura de recursos para una futura traducción al inglés.
- Mantener documentación de uso de la aplicación en español y actualizarla cuando cambie comportamiento visible para el usuario; su estructura deberá permitir una futura versión en inglés.
- Utilizar 1024×700 como tamaño inicial de ventana y soportar correctamente escalado habitual de Windows.
- No depender exclusivamente del color para comunicar estados.
- Mostrar notificaciones Windows únicamente para eventos operativos relevantes y permitir desactivarlas.
- Mostrar progreso real de operaciones masivas mediante conteos y estados, sin bloquear la UI.

### Cierre y recuperación

- Al cerrar la aplicación con túneles activos, solicitar confirmación.
- Si se confirma el cierre, detener todos los túneles antes de terminar.
- No permanecer residente en la bandeja del sistema tras cerrar.
- Evitar que procesos o recursos SSH gestionados permanezcan activos inadvertidamente después del cierre.
- Si la ejecución anterior terminó de forma anómala, informar al usuario al volver a abrir.
- No restaurar automáticamente los túneles que estaban activos antes de un crash o apagado.

### Fuera de alcance inicial

- Remote Port Forwarding (`-R`).
- Dynamic/SOCKS Forwarding (`-D`).
- Inicio automático con Windows.
- Inicio automático de túneles al abrir la aplicación.
- Ejecución residente en la bandeja del sistema después de cerrar.
- Importación de `~/.ssh/config`.
- Autenticación mediante claves privadas o certificados SSH.
- Terminal SSH interactiva integrada.
- Métricas avanzadas de tráfico.
- Sincronización cloud o uso multiusuario.
- Actualización automática o integrada de la aplicación.

## Capabilities

### New Capabilities

- `ssh-host-management`: libreta de hosts SSH reutilizables y gestión de su configuración.
- `tunnel-configuration`: definición, validación, edición y duplicado de túneles Local Port Forwarding.
- `ssh-routing`: rutas SSH con hasta dos jump servers, host final y prueba completa de conectividad.
- `tunnel-groups`: grupos reutilizables y operaciones masivas de inicio y parada.
- `tunnel-lifecycle`: estados, inicio, parada, cola, keep-alive, reconexión y errores.
- `tunnel-monitoring`: estado observable, tiempos, errores, eventos de sesión y progreso operativo.
- `credential-security`: contraseñas, autenticación y confianza de host keys.
- `application-settings`: valores globales, overrides y preferencias configurables.
- `workspace-organization`: proyectos, entornos, tags, favoritos, recientes, Hub, búsqueda y filtros.
- `data-portability`: persistencia, exportación/importación, backups, restauración y recuperación.
- `diagnostics`: logging, rotación, detalles técnicos y paquetes de diagnóstico.
- `desktop-experience`: comportamiento de la UI Windows, temas, teclado, accesibilidad y rendimiento.
- `application-lifecycle`: instancia única, cierre, terminación de túneles y recuperación tras ejecución anómala.

### Modified Capabilities

Ninguna. Se trata de una aplicación nueva y no existen specs previas que modificar.

## Impact

- Se introduce una nueva aplicación de escritorio para Windows 11 y su modelo persistente de configuración.
- El diseño utilizará SQLite como mecanismo de persistencia local; la estructura, ubicación y estrategia transaccional se concretarán en `design.md`.
- Será necesario seleccionar un mecanismo SSH compatible con Local Port Forwarding, usuario/contraseña, rutas multi-hop de hasta dos saltos, keep-alive, cancelación y control del ciclo de vida.
- La arquitectura deberá soportar modos instalado y portable, incluyendo credenciales transferibles en el modo portable.
- La aplicación deberá mantener separadas la configuración persistente y el estado runtime de hasta aproximadamente 20 túneles simultáneos.
- Será necesario un subsistema de logging estructurado, rotación, backup, importación/exportación y diagnóstico.
- La UI deberá permanecer responsiva durante conexiones SSH, operaciones masivas, búsquedas, filtros, logging y persistencia.
- La arquitectura deberá permitir introducir localización en inglés en una versión futura sin reescribir la UI.
- No se fija todavía el framework de UI ni el motor SSH; estas decisiones pertenecen a `design.md`.
