# Guía de usuario

## Estado actual

El milestone 0.2.0 permite gestionar hosts SSH, túneles individuales Local Port
Forwarding y grupos reutilizables. La organización avanzada, el Hub y las acciones
de gran volumen todavía pertenecen a fases posteriores.

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

## Grupos

En **Grupos** pulsa **Nuevo grupo** para abrir el editor dentro del shell. Escribe
un nombre y utiliza el buscador para seleccionar los túneles miembros. Un túnel
puede pertenecer a varios grupos y también puede existir sin grupo; el grupo no
se convierte en propietario de la configuración del túnel.

Antes de guardar, la aplicación avisa si dos miembros usan el mismo endpoint local
efectivo, por ejemplo `127.0.0.1:5432`. Puedes corregir la selección o confirmar el
guardado entendiendo que esos túneles no podrán escuchar simultáneamente en ese
endpoint.

**Iniciar grupo** solicita el inicio de todos sus miembros. Los túneles ya abiertos,
conectando, reconectando o en cola no se duplican. Un fallo queda aislado en ese
túnel y los demás continúan. La tabla muestra el progreso por conteos (`OPEN`,
`CONNECTING`, `QUEUED` y `ERROR`) y estados resumidos `CLOSED`, `STARTING`, `OPEN`,
`PARTIAL` o `ERROR`.

**Detener grupo** solicita la parada de todos sus miembros activos o iniciándose,
incluso cuando un túnel también pertenece a otro grupo. Esto no borra ni modifica
la configuración de los otros grupos. **Duplicar** crea otro grupo con un ID y
nombre nuevos, conservando inicialmente sus referencias a túneles.

## Fingerprints

La primera conexión a un `hostname:puerto` muestra el algoritmo y la fingerprint
SHA-256 y solicita aprobación. Una fingerprint cambiada bloquea la conexión y
muestra la fingerprint anterior y la nueva. Desde **Hosts SSH** puedes usar
**Eliminar confianza**; la siguiente conexión volverá a pedir aprobación.

## Errores

Los fallos de prueba muestran el detalle técnico y permiten copiarlo. Un puerto
local ocupado se informa explícitamente; la aplicación no selecciona otro puerto
automáticamente.
