# Delta for Data Portability

## ADDED Requirements

### Requirement: Persistencia automática de configuración
El sistema SHALL persistir automáticamente cada operación de configuración confirmada y SHALL NOT requerir una acción global de `Guardar todo`.

El estado runtime de túneles SHALL permanecer separado de la configuración persistente.

#### Scenario: Editar un túnel
- GIVEN una modificación válida de un túnel
- WHEN el usuario confirma `Guardar`
- THEN el cambio queda persistido
- AND no necesita guardar de nuevo desde un menú global

### Requirement: Estado inicial cerrado
Al iniciar una nueva ejecución, el sistema SHALL considerar todos los túneles `CLOSED`, independientemente del estado que tuvieran en la sesión anterior.

#### Scenario: Reiniciar después de una sesión normal
- GIVEN túneles que estuvieron abiertos antes de cerrar la aplicación
- WHEN se vuelve a abrir
- THEN ninguno se inicia automáticamente
- AND todos aparecen inicialmente cerrados

### Requirement: Exportación completa legible
El sistema SHALL permitir exportar la configuración completa a un formato legible que incluya hosts, usuarios, contraseñas, fingerprints, túneles, grupos, proyectos, entornos, tags, favoritos y ajustes.

El sistema SHALL advertir que el archivo contiene contraseñas recuperables.

#### Scenario: Exportar configuración completa
- GIVEN una configuración con credenciales almacenadas
- WHEN el usuario exporta
- THEN el archivo contiene los datos necesarios para reconstruir la configuración
- AND incluye las contraseñas
- AND se muestra una advertencia de seguridad

### Requirement: Importación con resolución de conflictos
El sistema SHALL detectar conflictos durante una importación y SHALL permitir resolverlos mediante conservar la versión actual, reemplazarla o crear una copia.

El sistema SHALL ofrecer acciones para aplicar una decisión a varios conflictos.

#### Scenario: Mismo ID con contenido distinto
- GIVEN una entidad importada con el mismo ID que una entidad local pero contenido diferente
- WHEN se analiza la importación
- THEN se presenta como conflicto
- AND el usuario puede elegir conservar, reemplazar o crear copia

#### Scenario: ID desconocido
- GIVEN una entidad importada cuyo ID no existe localmente
- WHEN se analiza la importación
- THEN se clasifica como elemento nuevo

### Requirement: IDs persistentes y nombres únicos
Las entidades persistentes SHALL conservar identificadores únicos entre exportaciones e importaciones.

Los nombres SHALL ser únicos dentro de su tipo de entidad cuando dicha entidad se presenta al usuario por nombre.

#### Scenario: Crear copia durante importación
- GIVEN un conflicto importado
- WHEN el usuario elige `Crear copia`
- THEN la nueva entidad recibe un identificador distinto
- AND su nombre se ajusta de forma que no viole la unicidad

### Requirement: Backups automáticos completos
El sistema SHALL mantener automáticamente las 10 copias de seguridad completas más recientes, incluidas contraseñas.

Cuando se cree una undécima copia, la más antigua SHALL poder eliminarse.

#### Scenario: Rotación del backup número once
- GIVEN que existen 10 backups automáticos
- WHEN se genera un nuevo backup
- THEN se conserva el nuevo
- AND el sistema mantiene como máximo las 10 copias más recientes

### Requirement: Restauración segura
El sistema SHALL permitir restaurar un backup automático y SHALL crear un backup de la configuración actual antes de reemplazarla.

#### Scenario: Restaurar un backup
- GIVEN un backup válido seleccionado
- WHEN el usuario confirma la restauración
- THEN se crea primero una copia de seguridad del estado actual
- AND después se sustituye la configuración por la seleccionada

### Requirement: Importación y restauración sin túneles activos
El sistema SHALL impedir que una importación o restauración reemplace configuración mientras existan túneles activos sin resolver previamente su cierre.

#### Scenario: Restaurar con túneles abiertos
- GIVEN que existen túneles activos
- WHEN el usuario solicita restaurar
- THEN el sistema ofrece cancelar o detener todos y continuar
- AND no sustituye datos mientras los túneles sigan activos

### Requirement: Recuperación ante corrupción
Si la configuración principal no puede cargarse, el sistema SHALL NOT sobrescribirla silenciosamente ni iniciar una configuración vacía como si fuera normal.

El sistema SHALL informar del problema y SHALL ofrecer los backups disponibles para recuperación.

#### Scenario: Base de datos dañada
- GIVEN que los datos persistentes no pueden cargarse correctamente
- WHEN se inicia la aplicación
- THEN el usuario recibe un error explícito
- AND puede elegir una copia de seguridad disponible
- AND los datos dañados no se sobrescriben automáticamente

### Requirement: Operaciones persistentes atómicas
Una operación persistente SHALL conservar un estado válido completo aunque la escritura falle.

#### Scenario: Fallo al guardar
- GIVEN una configuración válida existente
- WHEN falla la persistencia de una modificación
- THEN la configuración válida anterior sigue disponible
- AND el usuario recibe un error
- AND no queda un cambio parcial

### Requirement: Modo portable autocontenido
La distribución portable SHALL conservar junto a la aplicación los datos necesarios para trasladar configuración, credenciales y fingerprints a otra máquina Windows 11.

#### Scenario: Trasladar directorio portable
- GIVEN una configuración portable funcional
- WHEN se copia todo el directorio a otra máquina compatible
- THEN la aplicación puede cargar sus datos sin depender de una instalación previa en el equipo original

### Requirement: Eliminaciones definitivas
El sistema SHALL NOT proporcionar papelera en v1.

#### Scenario: Confirmar eliminación
- GIVEN una entidad que puede eliminarse
- WHEN el usuario confirma la eliminación
- THEN se elimina definitivamente de la configuración actual
- AND su recuperación requiere un backup si existe
