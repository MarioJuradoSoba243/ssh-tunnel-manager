# Guía de usuario

## Estado actual

El milestone 0.1.0 permite gestionar hosts SSH y túneles individuales Local Port
Forwarding. Los grupos, la organización avanzada y las operaciones masivas todavía
pertenecen a fases posteriores.

## Hosts SSH

En **Hosts SSH** pulsa **Nuevo host** e indica nombre, hostname, puerto SSH,
usuario y contraseña. El puerto por defecto es 22. **Mostrar contraseña** alterna
la visualización bajo demanda. Un host referenciado por un túnel no puede
eliminarse; primero hay que eliminar o editar la referencia.

## Túneles individuales

En **Túneles** pulsa **Nuevo túnel**. Define el bind local (por defecto
`127.0.0.1`), puerto local, destino TCP, puerto destino, SSH final y, si procede,
Jump 1 y Jump 2. Cada host conserva sus propias credenciales. El sistema admite
cero, uno o dos jumps.

El editor se muestra dentro del shell y separa **General**, **Local forwarding**,
**Ruta SSH** y **Opciones avanzadas**. En la última sección puedes indicar
timeout por tramo, keep-alive, número de reintentos y opciones SSH como
`clave=valor`. Las opciones que contradicen los campos estructurados se rechazan.
La validación local impide guardar mientras falten campos, haya puertos inválidos
o la ruta sea incoherente.

**Probar conexión** autentica la ruta completa y comprueba el destino remoto sin
abrir el puerto local configurado. **Iniciar** crea el forwarding; **Detener** lo
cierra; **Reintentar** inicia una nueva ejecución. El estado visible pertenece al
runtime y no se restaura como túnel activo al abrir la aplicación.

La tabla muestra `CLOSED`, `QUEUED`, `CONNECTING`, `OPEN`, `RECONNECTING n/5`,
`STOPPING` o `ERROR`, además del tiempo activo desde el inicio. **Ver detalles**
muestra el historial de eventos de la sesión actual y el error técnico, que se
puede copiar. **Duplicar** conserva la configuración con un nuevo identificador.
Si editas un túnel activo, guardar requiere elegir **Guardar y reiniciar**; los
cambios se aplican a una nueva ejecución.

## Fingerprints

La primera conexión a un `hostname:puerto` muestra el algoritmo y la fingerprint
SHA-256 y solicita aprobación. Una fingerprint cambiada bloquea la conexión y
muestra la fingerprint anterior y la nueva. Desde **Hosts SSH** puedes usar
**Eliminar confianza**; la siguiente conexión volverá a pedir aprobación.

## Errores

Los fallos de prueba muestran el detalle técnico y permiten copiarlo. Un puerto
local ocupado se informa explícitamente; la aplicación no selecciona otro puerto
automáticamente.
