---
title: "Manual de usuario"
subtitle: "Plataforma de gestión segura de archivos"
lang: es
---

# Cómo entrar

La aplicación se abre en el navegador. En la pantalla de acceso se pide **correo y contraseña**. Si los datos no son correctos, el mensaje
es el mismo en todos los casos («Correo o contraseña incorrectos») para no dar pistas. Tras varios
intentos fallidos, el sistema pide esperar unos minutos antes de volver a intentarlo.

### Usuarios de prueba

Todas las cuentas usan la contraseña **`password`**.

| Correo | Rol | Área |
|---|---|---|
| `admin@example.com` | Administrador | — |
| `manager@example.com` | Gerente | gestiona el Área A |
| `head.a@example.com` | Jefe de área | Área A |
| `standard.a@example.com` | Estándar | Área A |
| `standard.b@example.com` | Estándar | Área B |

> Son datos ficticios. No se deben subir documentos reales al ambiente de pruebas.

# La pantalla

La aplicación tiene una **barra lateral** a la izquierda y el contenido a la derecha.

- Arriba de la barra aparece **el nombre de la aplicación** («Archivos») y, justo debajo, **tu
  usuario** con su rol. Al pulsar tu tarjeta se ven **las sesiones abiertas** (los dispositivos
  desde los que has entrado) y puedes cerrar las que no reconozcas.
- Debajo están los enlaces a las secciones: **Archivos**, **Áreas** y, según tu rol, **Áreas
  gestionadas**, **Usuarios** e **Historial**.
- Abajo del todo está **Cerrar sesión**.

Solo verás las secciones que tu rol permite; el resto no aparecen.

# Qué puede hacer cada rol

| Acción | Estándar | Jefe de área | Gerente | Administrador |
|---|---|---|---|---|
| Cargar archivos | Sí | Sí | Sí | Sí |
| Ver y descargar | Solo los tuyos | Los tuyos y los del estándar de tu área | Los tuyos y los de las áreas que gestionas | Todos |
| Eliminar | Solo los tuyos | Solo los tuyos | Los tuyos y los de las áreas que gestionas | Todos |
| Ver el historial | No | No | El tuyo y el de tus áreas | Todo |
| Administrar usuarios y áreas | No | No | No | Sí |

El sistema comprueba estos permisos **siempre en el servidor**. Aunque alguien cambie la dirección
web a mano, no podrá ver ni descargar archivos que no le correspondan.

# Archivos

## Consultar

Al entrar en **Archivos** ves la lista de los archivos a los que tienes acceso. Cada fila muestra el
nombre, el tipo, el tamaño, el propietario y la fecha de carga.

- **Buscar por nombre**: escribe en el cuadro de búsqueda; la lista se actualiza sola.
- **Ver archivos de otra persona** (jefe de área, gerente y administrador): usa el selector
  «Ver archivos de». Por defecto aparecen los tuyos. El usuario estándar no ve este selector.
- **Páginas**: si hay muchos archivos, usa «Siguiente» y «Anterior».

## Cargar

1. Pulsa **Cargar archivo**.
2. Elige el archivo (o arrástralo al recuadro).
3. Pulsa **Cargar** y espera a que termine la barra de progreso.

Reglas de los archivos:

- Tamaño máximo: **20 MB**.
- Tipos permitidos: **PDF, PNG, JPEG, GIF, WebP, TXT y CSV**.
- El sistema comprueba el **contenido real** del archivo, no solo su nombre. Si no coincide, lo
  rechaza con un mensaje claro.

## Descargar

En la fila del archivo, pulsa el botón de **descargar** (flecha hacia abajo). La descarga pasa
siempre por la aplicación; no se entrega ninguna dirección directa al archivo.

## Eliminar

1. En la fila del archivo, pulsa el botón de **eliminar** (papelera).
2. Confirma en la ventana que aparece.

La eliminación es **lógica**: el archivo deja de estar disponible para todos, pero se conserva el
registro de quién lo eliminó y cuándo. Esa información queda en el historial.

# Historial

Disponible para **gerente y administrador**. Muestra las acciones registradas: inicios de sesión
(correctos y fallidos), cierres de sesión, cargas, descargas, eliminaciones e intentos de acceso
denegado.

- **Filtrar por acción** o **por usuario** con los selectores de arriba.
- Cada fila se puede **desplegar** para ver el detalle (usuario, área, archivo, dirección IP y
  referencia de la petición).
- El historial **no se puede modificar ni borrar** desde la aplicación.

El gerente ve sus acciones y las de los usuarios de las áreas que gestiona; el administrador ve
todo.

# Administrar usuarios y áreas (solo administrador)

## Usuarios

En **Usuarios** se listan las cuentas. Puedes **filtrar por área** y **buscar** por nombre o correo.

- **Nuevo usuario**: pide correo, contraseña, nombre y rol; si el rol es **estándar** o **jefe de
  área**, hay que elegir un **área**.
- **Editar**: cambia nombre, rol, área y si la cuenta está **activa**. Desactivar un usuario le
  quita el acceso **de inmediato**, aunque tenga una sesión abierta.
- **Desactivar/Activar** con el botón de la fila (con confirmación).
- El enlace de cada fila abre **los archivos de ese usuario**.

El administrador **no puede cambiarse el rol ni desactivar su propia cuenta**.

## Áreas

En **Áreas** se listan las áreas (las ve cualquier usuario).

- Solo el administrador puede **crear**, **editar** y **activar/desactivar** áreas.
- Un área no se borra: se **desactiva**, y conserva sus archivos y su historial.
- El enlace de cada fila abre **los usuarios de esa área**.

## Áreas gestionadas

En **Áreas gestionadas**:

- Un **gerente** ve las áreas que gestiona (solo consulta).
- El **administrador** elige un gerente y puede **editar** el conjunto de áreas que gestiona. No se
  puede dejar el conjunto vacío.

# Mensajes frecuentes

| Mensaje | Qué significa |
|---|---|
| «Correo o contraseña incorrectos» | El correo no existe, la contraseña no es correcta o la cuenta está desactivada. |
| «Demasiados intentos. Vuelve a intentarlo más tarde» | Se superó el límite de intentos fallidos. |
| «El archivo supera el límite de 20 MB» | El archivo es demasiado grande. |
| «Ese tipo de archivo no está permitido» | El tipo no está entre los permitidos. |
| «El contenido no coincide con el tipo declarado» | El contenido real no se corresponde con el tipo del archivo. |
| «Sin permisos» | Tu rol no tiene acceso a esa sección. |

> En esta edición el manual es de solo texto; no se incluyen capturas de pantalla.
