# Guía de usuario

## Estado actual

El milestone 0.4.0 permite gestionar hosts SSH, túneles individuales Local Port
Forwarding, grupos reutilizables, organización, favoritos, recientes, búsqueda,
filtros, acciones de gran volumen, exportación/importación y backups SQLite.

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

## Organización

En **Organización** puedes crear, editar y eliminar proyectos, entornos y
etiquetas. Los proyectos y entornos son opcionales y pueden asignarse desde los
editores de túneles y grupos. Un túnel o grupo puede tener varias etiquetas.

Si una clasificación está en uso, la aplicación muestra sus dependencias antes
de confirmar el borrado; las referencias de proyecto o entorno quedan vacías.

## Inicio, favoritos y recientes

**Inicio** muestra los favoritos, los ocho elementos iniciados más recientemente
y los túneles activos. Los recientes se actualizan al pulsar **Iniciar**
explícitamente; una reconexión automática no crea una entrada nueva.

El resumen permite navegar a los túneles abiertos, reconectando o con error.
**Detener todos** solicita confirmación antes de parar las conexiones activas.

## Búsqueda, filtros y acciones masivas

Pulsa **Ctrl+K** para buscar túneles, grupos y hosts SSH en el índice local y
navegar directamente al tipo de resultado. La lista de túneles permite filtrar
por texto, proyecto, entorno, grupo, estado y una o varias etiquetas. Las
columnas de las tablas se pueden ordenar pulsando sus cabeceras.

La tabla de túneles admite selección múltiple para iniciar o detener, marcar o
quitar favoritos, asignar proyecto/entorno, y añadir o quitar la selección de un
grupo. Estas acciones se ejecutan fuera del hilo de JavaFX y las relaciones se
guardan de forma transaccional.

## Fingerprints

La primera conexión a un `hostname:puerto` muestra el algoritmo y la fingerprint
SHA-256 y solicita aprobación. Una fingerprint cambiada bloquea la conexión y
muestra la fingerprint anterior y la nueva. Desde **Hosts SSH** puedes usar
**Eliminar confianza**; la siguiente conexión volverá a pedir aprobación.

## Errores

Los fallos de prueba muestran el detalle técnico y permiten copiarlo. Un puerto
local ocupado se informa explícitamente; la aplicación no selecciona otro puerto
automáticamente.

## Exportar e importar

En **Ajustes**, **Exportar configuración JSON** crea un archivo con hosts,
credenciales, túneles, grupos, proyectos, entornos, etiquetas, fingerprints,
favoritos, recientes y atajos. Las contraseñas SSH son datos recuperables en
esta exportación completa: guárdala como información sensible y no la adjuntes
en diagnósticos.

**Importar configuración JSON** muestra primero cuántos elementos son nuevos,
no han cambiado o entran en conflicto. Para cada conflicto se puede elegir
**Conservar actual**, **Reemplazar** o **Crear copia**. Crear copia genera un
UUID y un nombre no conflictivo para entidades que admiten duplicación. Las
trusted host keys, settings y atajos tienen identidad única y solo permiten
conservar o reemplazar. Una importación inválida se rechaza sin dejar cambios
parciales. Si hay túneles activos, puedes cancelar o elegir **Detener todos y
continuar**.

## Backups y restauración

En **Ajustes**, **Ver backups y restaurar** lista los snapshots SQLite válidos.
Se crean con un snapshot consistente (`VACUUM INTO`) y se conservan los diez
últimos. Si existen cambios, se intenta un backup periódico cada 30 minutos;
además se crea uno antes de importar, restaurar o migrar el esquema. La
restauración crea primero un snapshot de seguridad del estado actual y también
requiere detener los túneles activos o cancelar.

Si la base principal está dañada o no puede abrirse, la aplicación no crea una
configuración vacía ni la sobrescribe automáticamente. Muestra un modo de
recuperación con los backups utilizables. Tras restaurar, conserva la base
dañada con un nombre `.corrupt-<timestamp>.db` para facilitar una investigación
posterior.

## Instalado y portable

En una instalación normal, la base y sus datos se guardan en
`%LOCALAPPDATA%\SSH Tunnel Manager\`. En el portable se guardan bajo
`data\` junto al ejecutable: `data\ssh-tunnel-manager.db`,
`data\backups\` y `data\logs\`. Para trasladarlo, cierra la aplicación y copia
la carpeta completa a otro directorio Windows compatible; mantén `portable.flag`
junto a `ssh-tunnel-manager.exe`.
