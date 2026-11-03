---
title: "Manual técnico"
subtitle: "Plataforma de gestión segura de archivos"
lang: es
---

# 1. Visión general

La plataforma permite que cada usuario administre **solo los archivos que le corresponden**, según
su rol y su área. Es una aplicación web con un **frontend** (interfaz), un **backend** (reglas de
negocio y único punto de acceso a los datos), una **base de datos** y un **almacenamiento de
objetos** para el contenido de los archivos.

La autorización **no se decide en la interfaz**: el servidor (y la propia base de datos) comprueba
siempre los permisos. 

## Tecnologías

| Área | Elección | Por qué |
|---|---|---|
| Frontend | React + TypeScript + Vite | SPA moderna, tipada y con compilación rápida; se sirve como archivos estáticos. |
| Estado y datos (frontend) | TanStack Query + axios + zod | Caché e invalidación de datos del servidor, cliente HTTP único y validación de respuestas. |
| Interfaz | Pico CSS | Estilos sencillos y semánticos; menos CSS propio. |
| Enrutado (frontend) | react-router v7 | Rutas y filtros en la URL. |
| Backend | Quarkus (Java 21) | API REST con inyección de dependencias y **compilación a imagen nativa** (arranque casi instantáneo y poca memoria). |
| Base de datos | PostgreSQL | Robusto, con **seguridad a nivel de fila (RLS)** para acotar el alcance de cada usuario. |
| Almacenamiento | S3 compatible | Guarda el contenido; la aplicación nunca expone una ruta directa. |
| Migraciones | SQL versionado | El esquema se recrea desde cero de forma reproducible. |
| Pruebas | JUnit/Quarkus (backend) y Vitest + Testing Library + MSW (frontend) | Pruebas unitarias e integración de la API y de la interfaz. |
| Integración y despliegue | GitHub Actions + Cloud Run + Firebase Hosting | Compila, prueba y publica; un *rewrite* deja frontend y backend en el **mismo origen**. |

# 2. Arquitectura

El código se organiza por **módulos de negocio** (que «gritan» el dominio: `auth`, `files`,
`users`, `areas`, `managedareas`, `audit` y `shared`) y cada módulo, en **capas concéntricas**
(Onion): `api → application → domain`, con `infrastructure` implementando los puertos que declara la
aplicación. Las dependencias apuntan **hacia dentro**: el dominio no conoce ni la base de datos ni
el HTTP.

Esta forma de organizar el código tiene una consecuencia clave: **las reglas de permisos viven en un
solo lugar** (funciones de la base de datos), y añadir un rol nuevo no obliga a tocar cada pantalla.

## 2.1. Contexto del sistema (C4, nivel 1)

![Contexto del sistema](diagramas/01-contexto.png)

## 2.2. Contenedores (C4, nivel 2)

![Contenedores](diagramas/02-contenedores.png)

## 2.3. Componentes del backend (C4, nivel 3)

![Componentes del backend](diagramas/03-componentes-backend.png)

# 3. Despliegue

## 3.1. Ambiente de pruebas (local)

Levanta todo con Docker Compose: base de datos, almacenamiento S3 de pruebas (`s3mock`), API y
frontend. El detalle de uso está en `docker/README.md`.

![Despliegue del ambiente de pruebas](diagramas/04-despliegue-pruebas.png)

## 3.2. Producción

- **Frontend** (archivos estáticos) en **Firebase Hosting**.
- **Backend** (imagen nativa) en **Cloud Run**.
- **Base de datos** gestionada (PostgreSQL) fuera de GCP.
- Un ***rewrite*** deja frontend y backend en el **mismo origen**:
  `/api/**` → Cloud Run y `/**` → la SPA. Al compartir origen, las cookies de sesión y la protección
  CSRF funcionan **sin CORS**.

> El ambiente de pruebas local usa Postgres y `s3mock` en la misma máquina; en producción se usan
> servicios gestionados equivalentes sin cambiar el código (solo variables de entorno).

# 4. Flujos críticos

## 4.1. Inicio de sesión

Antes de la primera acción que modifica estado, el navegador recibe una cookie **CSRF** (en una
petición de lectura) y la reenvía en una cabecera. La contraseña se verifica con **bcrypt** y la
sesión se guarda como un **token opaco** (en la base de datos solo se guarda su hash).

![Secuencia de inicio de sesión](diagramas/05-secuencia-login.png)

## 4.2. Descarga de un archivo (con validación de permisos)

El alcance se aplica **en la base de datos**: si el archivo no existe o queda fuera del alcance, la
consulta devuelve cero filas y la respuesta es **404** (no distingue ambos casos, para no dar
pistas). Cada intento denegado se registra en el historial.

![Secuencia de descarga](diagramas/06-secuencia-descarga.png)

# 5. Modelo de datos

Siete tablas. El **historial** (`audit.action_logs`) es inmutable (solo se añade) y **sin claves
foráneas con cascada**: conserva copias del usuario, el área y el archivo para seguir siendo legible
aunque cambien o se eliminen.

![Modelo entidad-relación](diagramas/07-er.png)

# 6. Modelo de permisos

Cada usuario tiene **exactamente un rol**, y los roles son jerárquicos: **estándar < jefe de área <
gerente**, con el **administrador** aparte. Cada nivel accede a lo que corresponde a los niveles
inferiores dentro de su alcance; nunca a lo de un nivel superior.

| Operación | Estándar | Jefe de área | Gerente | Administrador |
|---|---|---|---|---|
| Cargar | Sí | Sí | Sí | Sí |
| Ver y descargar propios | Sí | Sí | Sí | Sí |
| Ver y descargar otros | No | Estándar de su área | Todos los de sus áreas | Todos |
| Eliminar propios | Sí | Sí | Sí | Sí |
| Eliminar otros | No | No | Todos los de sus áreas | Todos |
| Consultar historial | No | No | El suyo y el de sus áreas | Todo |
| Administrar usuarios y áreas | No | No | No | Sí |

Reglas relevantes:

- **Área de un archivo**: es el área de su dueño **al cargarlo**; si el dueño cambia de área, sus
  archivos anteriores se quedan donde estaban.
- **Alcance del gerente**: solo actúa sobre las áreas que tiene asignadas.
- **Cambios de permisos**: se aplican en la siguiente acción, sin esperar a que el usuario vuelva a
  iniciar sesión.
- **Usuario desactivado**: pierde el acceso de inmediato, incluso con sesión abierta.

## Cómo se aplica

En **dos barreras** que comparten la misma lógica:

1. **En la API**: antes de devolver datos, se comprueba el permiso y se devuelve el error uniforme.
2. **En la base de datos (RLS)**: aunque la API se equivocara, las **políticas de seguridad a nivel
   de fila** solo dejan ver o modificar las filas del alcance del usuario.

Las reglas están centralizadas en funciones de PostgreSQL (por ejemplo `app.can_read_file`,
`app.can_delete_file`, `app.is_manager_of`) y se usan tanto en la API como en las políticas RLS.

# 7. Registros de decisiones de arquitectura (ADR)

## ADR-01 · Tecnología del backend

**Contexto.** Un gestor de archivos con pocas peticiones por usuario, que debe arrancar rápido y
consumir poca memoria en un entorno gestionado.

**Opciones.** (a) Java tradicional sobre servidor de aplicaciones; (b) framework JVM moderno con
imagen nativa; (c) Node.js.

**Decisión.** **Quarkus compilado a imagen nativa** (Java 21). Arranque en decenas de milisegundos y
huella de memoria baja, lo que abarata el despliegue elástico.

**Consecuencias.** El frontend y el backend comparten el mismo lenguaje de tipos si se quiere, y la
API es única. A cambio, la compilación nativa es más lenta y exige cuidado con la reflexión y con las
propiedades leídas en el arranque (se resuelven leyéndolas en tiempo de ejecución).

## ADR-02 · Almacenamiento de archivos

**Contexto.** Los archivos pueden llegar a 20 MB y no deben exponerse por rutas ni URL directas.

**Opciones.** (a) Guardar el binario en la base de datos; (b) guardarlo en el sistema de archivos del
servidor; (c) almacenamiento de objetos compatible con S3.

**Decisión.** **Almacenamiento de objetos (S3)**. En la base de datos solo se guardan los metadatos y
una **clave interna**. **Toda descarga pasa por la aplicación**: nunca se entrega una URL firmada.

**Consecuencias.** Escala bien y separa el contenido de los datos. El coste principal pasa a ser la
**salida de red por descargas**. Si en el futuro el volumen de descargas dominara, podría evaluarse
entregar URL firmadas de vida corta, aceptando el coste de perder el control total del acceso.

## ADR-03 · Gestión de sesión

**Contexto.** Autenticación por navegador, sin depender de un servicio externo, y con necesidad de
cerrar sesiones y de invalidarlas al desactivar a un usuario.

**Opciones.** (a) Token en `localStorage` con cabecera `Authorization`; (b) sesión propia con cookie
`HttpOnly` y token opaco guardado en la base de datos.

**Decisión.** **Cookie `HttpOnly` + `Secure` + `SameSite=Lax`** con un **token opaco**; en la base de
datos se guarda solo su **hash**. La protección **CSRF** es de **doble envío** (cookie legible +
cabecera). El **mismo origen** evita CORS.

**Consecuencias.** El JavaScript no puede leer el token (menos riesgo de robo por XSS) y las sesiones
se pueden **revocar** de inmediato. Exige HTTPS, la protección CSRF y una cookie que no viaje entre
sitios.

## ADR-04 · Modelo de autorización

**Contexto.** Cuatro roles con jerarquía y alcance por área; las reglas deben estar en un solo lugar y
no duplicarse en cada pantalla.

**Opciones.** (a) Comprobar permisos en cada endpoint; (b) usar solo la base de datos con **RLS**;
(c) **RLS + funciones centralizadas** compartidas por la API y las políticas.

**Decisión.** **Roles jerárquicos + alcance por área**, con las reglas en **funciones de PostgreSQL**
usadas por la API **y** por las **políticas RLS** (segunda barrera). El área del gerente se calcula
desde sus áreas asignadas.

**Consecuencias.** Añadir un rol nuevo no obliga a tocar cada pantalla; y aunque un endpoint olvidara
la comprobación, RLS protege las filas. A cambio, hay que cuidar el rendimiento de las funciones y de
los índices.

## ADR-05 · Estrategia de despliegue

**Contexto.** Ambiente de pruebas accesible por HTTPS y reconstruible desde cero, con el menor coste
posible.

**Opciones.** (a) Servidor propio con balanceador; (b) contenedores serverless + *hosting* estático
con *rewrite*; (c) máquina virtual única.

**Decisión.** **Frontend estático en Firebase Hosting** y **backend en Cloud Run**, en el **mismo
origen** mediante *rewrite*. Las migraciones y el despliegue se ejecutan en el **pipeline**.

**Consecuencias.** Muy bajo coste en reposo y HTTPS gestionado. El orden de los *rewrites* importa y
el arranque en frío debe tolerarse (o pagar instancias mínimas). Un balanceador de carga añadiría un
coste fijo innecesario.

## ADR-06 · Base de datos y migraciones

**Contexto.** El modelo es relacional y conviene poder aplicar cambios de forma versionada.

**Opciones.** (a) Esquema aplicado a mano; (b) migraciones versionadas con base gestionada.

**Decisión.** **PostgreSQL gestionado** y **migraciones SQL versionadas** aplicadas por el pipeline.
Datos de prueba incluidos como semilla.

**Consecuencias.** El ambiente se recrea desde cero de forma reproducible y los cambios quedan
auditables. A cambio, durante el desarrollo conviene poder resetear el volumen local fácilmente.

# 8. Modelo de amenazas

| Amenaza | Riesgo | Mitigación |
|---|---|---|
| **Acceso a archivos ajenos** (cambiar el id en la URL) | Alto | Identificadores **no secuenciales**; el alcance se aplica en la **base de datos** (RLS) y en la API; respuesta **404 uniforme**; se registra el intento. |
| **Robo de sesión** (XSS, red) | Alto | Cookie **`HttpOnly` + `Secure` + `SameSite=Lax`**; token **opaco** guardado **hasheado**; expiración y **revocación** inmediata; **HTTPS** y cabeceras de seguridad. |
| **CSRF** | Medio | **Doble envío** (cookie legible + cabecera) en métodos que modifican estado, más `SameSite=Lax`. |
| **Carga de archivos maliciosos** | Medio | Límite de **20 MB**; tipos permitidos; validación por **firma real** del contenido (no por el nombre); el contenido se guarda aislado y nunca se ejecuta. |
| **Fuerza bruta en el acceso** | Medio | Contraseñas con **bcrypt**; **limitador** por HMAC(correo|IP) con ventana temporal; mensaje **idéntico** en los tres casos de fallo (no revela si el correo existe). |
| **Escalada de privilegios** | Alto | Rol **jerárquico**; un administrador **no puede** cambiar su propio rol ni desactivarse; los cambios se aplican en la siguiente acción. |
| **Manipulación del historial** | Medio | Tabla **solo de añadido**; **sin** privilegios de `UPDATE`/`DELETE`; un *trigger* rechaza modificar o borrar; se escribe por una función controlada. |
| **Fuga de información en errores o registros** | Medio | Cuerpo de error **uniforme** (`{code, message, requestId}`); los registros **no** incluyen contraseñas, tokens ni contenido. |
| **Abuso del almacenamiento** | Bajo | Límite de tamaño y de tipos; el contenido se valida antes de guardarse. |

# 9. Escenarios de evolución

## 9.1. Diez veces más usuarios y 1 TB de archivos

- **Base de datos**: separar lecturas (réplicas), **particionar** el historial por fecha y afinar
  índices; revisar el plan de la base gestionada (o migrar a una mayor). Paginación por **cursor**
  ya está preparada para volúmenes altos.
- **Almacenamiento**: reglas de ciclo de vida (mover lo antiguo a un nivel más barato), y **subida
  directa** al almacenamiento con credenciales de vida corta si el tamaño crece (manteniendo el
  control de permisos con permisos temporales por archivo).
- **Descargas**: usar **CDN** delante del almacenamiento para reducir el coste de salida de red, que
  pasa a ser el gasto dominante.
- **Backend**: mantener Cloud Run con **más concurrencia** por instancia y algún ajuste de CPU; solo
  si aparece trabajo pesado (por ejemplo antivirus) se añadiría una **cola** y procesamiento aparte.
- **Cuándo dejaría de bastar**: si el número de peticiones o el tráfico de descargas creciera mucho,
  el modelo «todo pasa por la aplicación» se volvería caro; ahí se justificaría delegar la descarga
  en URL firmadas y asumir el cambio en el control.

## 9.2. Iniciar sesión con la cuenta corporativa (Microsoft Entra ID)

- Delegar la **autenticación** en Entra ID mediante **OpenID Connect**, manteniendo en la aplicación
  los **usuarios, roles y áreas** (que son de negocio).
- Mapear **grupos o roles de Entra** a los roles de la aplicación; el primer acceso aprovisiona o
  vincula al usuario.
- La **sesión** sigue siendo propia (cookie) tras el intercambio OIDC, así no se reescribe el resto
  del sistema.
- Se conserva el historial de accesos; el limitador de intentos pasa a ser responsabilidad del
  proveedor. **A consultar** con el área de identidad corporativa.

## 9.3. Conservar los archivos eliminados y purgarlos automáticamente

- La eliminación ya es **lógica**: el archivo se conserva con quién y cuándo lo eliminó.
- Añadir una **retención** configurable y una **tarea programada** que, pasada la retención, borre el
  objeto del almacenamiento y marque el registro como purgado (manteniendo el rastro en el historial,
  que es inmutable).
- **A consultar** la política de retención con el área legal/negocio (¿30, 90 días…?).

# 10. Operación

## Copias de seguridad y recuperación

- **Base de datos**: copias automáticas y **recuperación a un punto en el tiempo** (PITR) del servicio
  gestionado; **prueba de restauración** periódica. En local, los datos viven en un volumen de Docker
  que se puede recrear desde las migraciones y la semilla.
- **Archivos**: versionado del *bucket* y reglas de ciclo de vida. En local, `s3mock` es efímero (no
  requiere respaldo).
- **Recuperación ante desastre**: reconstruir el backend desde la imagen y las variables de entorno,
  aplicar migraciones y restaurar el *bucket*.

## Monitoreo

- **Registros estructurados (JSON)** con un `requestId` en cada petición, para correlacionar todo lo
  que provocó una petición.
- **Endpoint de salud** (`/health/live`, `/health/ready`) que verifica **base de datos** y
  **almacenamiento**.
- **Alertas** sobre errores 5xx, latencia y uso; panel con métricas de peticiones, descargas y
  almacenamiento.

## Coste mensual aproximado

| Escenario | Firebase Hosting | Cloud Run | Registro de imágenes | Salida de red | Total aprox. |
|---|---|---|---|---|---|
| Demostración / tráfico bajo | $0 | $0 (nivel gratuito) | $0 | ~$0 | **$0** |
| Producción pequeña | $0–2 | ~$0–5 | ~$0 | ~$2–10 | **~$5–15** |
| Con balanceador en vez de Hosting | — | — | — | — | **+$18 fijos/mes** |

Notas: la **salida de red por descargas** es el principal coste variable; la base gestionada arranca
en torno a **$19/mes** si se sale del nivel gratuito. Detalle en `documentation/backend/precios-despliegue.md`.

# 11. Variables de entorno

Todos los valores sensibles llegan por variables de entorno y **no** se versionan.

| Variable | Para qué | Ejemplo |
|---|---|---|
| `QUARKUS_DATASOURCE_JDBC_URL` | Dirección de la base de datos | `jdbc:postgresql://db:5432/app?sslmode=disable` |
| `QUARKUS_DATASOURCE_USERNAME` | Usuario de la API | `app_runtime` |
| `QUARKUS_DATASOURCE_PASSWORD` | Contraseña del usuario | *(secreto)* |
| `APP_DATABASE_RUNTIME_ROLE` | Rol con el que se aplica RLS | `app_runtime` |
| `QUARKUS_S3_ENDPOINT_OVERRIDE` | Servicio de almacenamiento | `http://s3:9090` |
| `QUARKUS_S3_AWS_REGION` | Región de la firma | `us-east-1` |
| `QUARKUS_S3_AWS_CREDENTIALS_STATIC_PROVIDER_ACCESS_KEY_ID` | Credencial de almacenamiento | *(secreto)* |
| `QUARKUS_S3_AWS_CREDENTIALS_STATIC_PROVIDER_SECRET_ACCESS_KEY` | Credencial de almacenamiento | *(secreto)* |
| `APP_STORAGE_BUCKET` | Nombre del contenedor | `files` |
| `APP_AUTH_COOKIE_SECURE` | Marca la cookie como segura (HTTPS) | `false` en local, `true` en producción |
| `APP_AUTH_RATE_LIMIT_KEY` | Clave del limitador de intentos | *(secreto)* |
| `VITE_API_BASE_URL` | Base de la API en el frontend (por defecto `/api`) | `/api` |

# 12. Instalación y despliegue

## Local

```bash
docker compose up        # abre http://localhost:8081
```

Alternativa sin Docker para el backend: compilar con GraalVM y ejecutar el binario nativo; para el
frontend, `pnpm install && pnpm dev`. El detalle está en `docker/README.md` y en
`documentation/docker-local.md`.

## Producción

1. **Backend**: compilar la imagen nativa y desplegarla en Cloud Run.
2. **Migraciones**: aplicarlas como parte del pipeline antes de desplegar.
3. **Frontend**: `pnpm build` y `firebase deploy --only hosting` (con *canal de vista previa* por
   *pull request*).
4. **Variables** y secretos: configurados en cada servicio.

# 13. Supuestos

Decisiones tomadas donde la especificación no lo resolvía. En un proyecto real, cada una se
consultaría con la persona indicada.

| # | Supuesto | A quién consultaría |
|---|---|---|
| 1 | El administrador también carga archivos propios, que quedan **sin área**. | Producto / negocio |
| 2 | Los archivos eliminados **no se purgan** automáticamente (la eliminación es lógica y se conserva). | Legal / negocio |
| 3 | Tipos permitidos: PDF, PNG, JPEG, GIF, WebP, TXT y CSV. | Seguridad / negocio |
| 4 | Límite de tamaño 20 MB y de intentos de acceso 5 por 15 minutos. | Seguridad / negocio |
| 5 | Cada usuario pertenece a **una sola área** (no se contempla pertenecer a varias). | Negocio / RR. HH. |
| 6 | La sesión dura **12 horas**. | Seguridad |
| 7 | El ambiente de pruebas usa datos ficticios y almacenamiento efímero. | Responsable del ambiente |
| 8 | El gerente sin áreas asignadas solo ve lo suyo. | Negocio |
| 9 | El borrado lógico no cifra los objetos (se confía en el cifrado del almacenamiento). | Seguridad |
| 10 | El correo es único y no se reutiliza tras desactivar una cuenta. | Recursos Humanos / identidad |
