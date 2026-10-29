
# Proyecto: plataforma de gestión segura de archivos por usuario, rol y área

Diseña y construye una aplicación web en la que cada usuario administre únicamente los archivos que le corresponden según su rol y el área a la que pertenece.

El sistema es el mismo que pedimos a los perfiles de desarrollo, pero para este puesto evaluamos sobre todo cómo piensas, decides y organizas el trabajo: la arquitectura que propones y por qué, cómo aseguras la calidad y la seguridad, cómo lo llevarías a producción y cómo lo construiría un equipo bajo tu coordinación. La aplicación debe funcionar y ser segura, pero tu documentación de arquitectura y tu plan de trabajo pesan tanto como el código.

## Funcionalidades requeridas

La aplicación debe permitir que los usuarios puedan:
1. Iniciar sesión con su correo y contraseña.
2. Cargar archivos.
3. Ver el listado de archivos que le corresponden según su rol.
4. Descargar archivos según su rol.
5. Eliminar archivos según su rol.
6. Cerrar sesión.
7. Consultar el historial de acciones, según su rol.
8. Administrar usuarios y áreas (solo el administrador).

## Roles y permisos

El sistema debe contemplar como mínimo cuatro roles. Cada usuario tiene exactamente un rol. El usuario estándar y el jefe de área pertenecen obligatoriamente a un área; el gerente gestiona una o varias áreas, y un área puede tener más de un gerente. Los roles siguen una jerarquía: usuario estándar, jefe de área y gerente. Cada nivel accede a lo que corresponde a los niveles inferiores dentro de su alcance, nunca a lo de un nivel superior; por ejemplo, el jefe de área no ve los archivos del gerente.

|Acción|Usuario estándar|Jefe de área|Gerente Administrador|
|---|---|---|---|
|Cargar archivos|Sí|Sí|Sí|Sí|
|Ver y descargar|Solo los propios|Los propios y los de los usuarios estándar de su área|Los propios y todos los de las áreas que gestiona, incluidos los de los jefes de área|Todos|
|Eliminar|Solo los propios|Solo los propios|Los propios y todos los de las áreas que gestiona, incluidos los de los jefes de área|Todos|
|Consultar historial|No|No|El propio y el de los usuarios de las áreas que gestiona|Todo el historial|
|Administrar usuarios y áreas|No|No|No|Sí|

Reglas que aplican a todos los roles:

* Área de un archivo: es el área de su dueño al momento de cargarlo. Si el usuario cambia de área, sus archivos anteriores permanecen en el área donde se cargaron.
* Alcance del gerente: solo actúa sobre las áreas que tiene asignadas. No puede ver, descargar ni eliminar archivos de otras áreas.
* Administrador: puede cargar sus propios archivos, que quedan sin área asignada. Administra usuarios (crear, editar, asignar rol, área y áreas gestionadas, activar y desactivar) y áreas (crear, editar, activar y desactivar). No puede desactivarse ni cambiar su propio rol.
* Usuario desactivado: pierde el acceso de inmediato, incluso con una sesión abierta. Sus archivos y su historial se conservan.
* Cambios de permisos: un cambio de rol, área o áreas gestionadas aplica en la siguiente acción del usuario, sin esperar a que inicie sesión de nuevo.

## Historial de acciones

El historial debe registrar como mínimo estas acciones: inicio de sesión, inicio de sesión fallido, cierre de sesión, carga, descarga, eliminación e intento de acceso denegado (un usuario que intenta descargar o eliminar un archivo fuera de su alcance).

Cada registro debe guardar la fecha y hora, la acción, el usuario que la realizó, el archivo afectado cuando corresponda y la dirección IP de origen. El historial no se puede modificar ni eliminar desde la aplicación, y debe seguir siendo legible aunque después cambien o se eliminen el usuario o el archivo.

La pantalla de historial debe permitir filtrar por tipo de acción y paginar los resultados.

## Requerimientos funcionales

* El proyecto debe contar con un mínimo de cinco usuarios de prueba: uno por rol y un segundo usuario estándar de otra área, para poder comprobar el aislamiento entre áreas.
* Debe haber al menos dos áreas, y el gerente de prueba debe gestionar una de ellas como mínimo.
* Cada archivo debe contar con un identificador único no secuencial, que no permita adivinar el de otros archivos.
* Ningún usuario puede ver, descargar ni eliminar archivos fuera de su alcance, aunque conozca su identificador o modifique la URL o la petición. Los permisos se validan siempre en el servidor; ocultar botones en la interfaz no es suficiente.
* Los archivos se almacenan de forma controlada: ninguna ruta física ni URL directa al archivo se expone al cliente, y toda descarga pasa por la aplicación.
* La eliminación es lógica: el archivo deja de estar disponible para todos los usuarios, pero se conserva su registro con quién y cuándo lo eliminó.
* Los archivos tienen un tamaño máximo de 20 MB. Los tipos permitidos quedan a tu criterio y deben validarse en el servidor y documentarse.
* El listado de archivos debe permitir buscar por nombre y paginar.
* Los mensajes de error deben ser claros para el usuario, sin revelar información interna del sistema.

## Requerimientos no funcionales

* Seguridad: las contraseñas se almacenan con un algoritmo de hash seguro. Las credenciales y secretos se configuran por variables de entorno y no se versionan. El inicio de sesión limita los intentos fallidos y la aplicación aplica cabeceras de seguridad HTTP. La sesión debe poder invalidarse al cerrarla o al desactivar al usuario.
* Tecnología: puedes usar el lenguaje, framework o librería web que prefieras, y cualquier motor de base de datos. Justifica la elección.
* Modelo de datos: debe representar correctamente la relación entre usuarios, roles, áreas, áreas gestionadas, archivos e historial. El esquema se gestiona con migraciones versionadas.
* Mantenibilidad: separa responsabilidades y centraliza las reglas de permisos en un solo lugar, de forma que agregar un rol nuevo no obligue a modificar cada pantalla o endpoint.
* Pruebas: pruebas unitarias de las reglas de permisos y pruebas de integración que recorran la API con cada rol, incluidos los intentos de acceso fuera de alcance.
* Integración y despliegue continuos: un pipeline que, en cada cambio, compile, ejecute el análisis estático y las pruebas, y despliegue automáticamente al ambiente de pruebas solo si todo pasa.
* Despliegue reproducible: la aplicación se empaqueta en contenedores, y el ambiente se puede recrear desde cero siguiendo la documentación.
* Observabilidad: registros estructurados con identificador de petición, y un endpoint de estado de salud que verifique la base de datos y el almacenamiento.
* Datos de prueba: usa únicamente datos ficticios. No cargues documentos personales ni información real en el ambiente de pruebas.
* Herramientas de apoyo: puedes apoyarte en documentación, foros o herramientas de IA. En la revisión en vivo deberás defender tus decisiones y explicar cualquier parte del código, así que asegúrate de dominar cada una.


