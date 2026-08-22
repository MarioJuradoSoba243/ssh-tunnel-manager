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

**Probar conexión** autentica la ruta completa y comprueba el destino remoto sin
abrir el puerto local configurado. **Iniciar** crea el forwarding; **Detener** lo
cierra; **Reintentar** inicia una nueva ejecución. El estado visible pertenece al
runtime y no se restaura como túnel activo al abrir la aplicación.

## Fingerprints

La primera conexión a un `hostname:puerto` muestra el algoritmo y la fingerprint
SHA-256 y solicita aprobación. Una fingerprint cambiada bloquea la conexión y
muestra el fallo técnico para que pueda revisarse antes de volver a intentarlo.

## Errores

Los fallos de prueba muestran el detalle técnico y permiten copiarlo. Un puerto
local ocupado se informa explícitamente; la aplicación no selecciona otro puerto
automáticamente.
