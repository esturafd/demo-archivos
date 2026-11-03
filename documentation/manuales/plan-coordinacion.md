---
title: "Plan de coordinación"
subtitle: "Plataforma de gestión segura de archivos"
lang: es
---

# 1. Equipo y forma de trabajo

- **2 desarrolladores backend** (API, base de datos, permisos, despliegue).
- **1 desarrollador frontend** (interfaz).
- **1 analista de calidad** (pruebas, criterios de aceptación, recorrido completo).
- **Coordinación** (este documento): prioriza, revisa y desbloquea.

Trabajo en **sprints**, con una reunión breve al inicio (qué se va a hacer) y otra al
cierre (qué se ha hecho y qué sigue). El tablero tiene tres columnas: **por hacer**, **en curso
máximo 2 por persona**, **hecho**. Todo cambio entra por **rama y pull request**.

# 2. Plan de trabajo

## 2.1. Backlog (historias de usuario)

Estimación en **puntos** (1 = trivial, 8 = grande). Los criterios de aceptación van resumidos; el
detalle fino se escribe al empezar cada historia.

| ID | Historia | Criterios de aceptación (resumen) | Est. | Sprint | Depende de |
|---|---|---|---|---|---|
| US-01 | Como usuario quiero **iniciar sesión** | Los datos correctos entran; los incorrectos dan el mismo mensaje; varios fallos bloquean temporalmente | 5 | 1 | — |
| US-02 | Como usuario quiero **cerrar sesión** y ver mis **sesiones** | Al cerrar, la sesión deja de servir; se listan y revocan dispositivos | 3 | 1 | US-01 |
| US-03 | Como usuario quiero **cargar archivos** | Valida tipo y tamaño antes de enviar; el tipo real se comprueba en el servidor; mensajes claros | 5 | 2 | US-01 |
| US-04 | Como usuario quiero **ver y buscar** mis archivos | Lista solo los que me corresponden; busca por nombre; pagina | 5 | 2 | US-03 |
| US-05 | Como usuario quiero **descargar** un archivo | Solo si está en mi alcance; el contenido pasa por la aplicación | 3 | 2 | US-04 |
| US-06 | Como usuario quiero **eliminar** un archivo | Eliminación lógica con confirmación; queda registrada | 3 | 2 | US-04 |
| US-11 | Como sistema debo **aplicar las reglas de permisos** | Cada rol ve lo suyo; accesos fuera de alcance devuelven 404 y se registran | 8 | 2 | US-01 |
| US-12 | Como equipo necesito **migraciones y datos de prueba** | Esquema reproducible desde cero; 5 usuarios y 2 áreas | 5 | 1 | — |
| US-13 | Como sistema debo **protegerme de CSRF** y enviar **cabeceras de seguridad** | Todo método que modifica estado exige token; cabeceras presentes | 5 | 1 | US-01 |
| US-14 | Como sistema debo **limitar los intentos de acceso** | Tras varios fallos responde «demasiados intentos» | 3 | 1 | US-01 |
| US-18 | Como usuario quiero **el armazón de la interfaz** (acceso y navegación) | Barra lateral con marca, usuario y enlaces según rol | 5 | 1 | — |
| US-07 | Como gerente quiero **consultar el historial** | Filtra por acción y usuario; pagina; solo mi alcance | 5 | 4 | US-11 |
| US-08 | Como administrador quiero **administrar usuarios** | Crear, editar, activar y desactivar; no puedo desactivarme | 8 | 3 | US-11 |
| US-09 | Como administrador quiero **administrar áreas** | Crear, editar, activar y desactivar; enlaza a sus usuarios | 5 | 3 | US-11 |
| US-10 | Como administrador quiero **asignar áreas a un gerente** | El conjunto no puede quedar vacío; el cambio aplica sin re-loguear | 5 | 4 | US-08 |
| US-19 | Como usuario quiero **las pantallas de cada módulo** | CRUD con modales; estados de carga, vacío y error | 13 | 3–4 | US-18 |
| US-15 | Como equipo necesitamos **observabilidad** | Registros con `requestId`; salud de base y almacenamiento | 5 | 5 | US-11 |
| US-16 | Como equipo necesitamos **integración y despliegue continuos** | En cada cambio: compila, prueba y despliega solo si todo pasa | 8 | 5 | US-12 |
| US-17 | Como equipo necesitamos el **contrato de la API** documentado | API publicada con sus códigos y ejemplos | 3 | 5 | US-11 |
| US-20 | Como equipo queremos un **recorrido completo y accesible** | Recorrido con los 4 roles y 2 áreas; usable con teclado y en móvil | 5 | 6 | todos |
| US-21 | Como equipo queremos el **ambiente de pruebas publicado** | Accesible por HTTPS, con usuarios de prueba, desplegado por el pipeline | 5 | 6 | US-16 |
| US-22 | Como equipo queremos los **manuales** | Manual técnico, de usuario y de coordinación | 5 | 6 | todos |

## 2.2. Distribución por sprints

| Sprint | Contenido | Foco |
|---|---|---|
| 1 | US-01, US-02, US-12, US-13, US-14, US-18 | Andamiaje, base de datos y acceso. |
| 2 | US-03, US-04, US-05, US-06, US-11 | El **flujo central**: archivos y permisos. |
| 3 | US-08, US-09 (inicio de US-19) | Administración de usuarios y áreas. |
| 4 | US-10, US-07 (resto de US-19) | Áreas gestionadas, historial y pantallas. |
| 5 | US-15, US-16, US-17 | Seguridad operativa, pipeline y contrato. |
| 6 | US-20, US-21, US-22 | Cierre: recorrido, despliegue y manuales. |

## 2.3. Paralelización y dependencias

- **US-11 (permisos)** es la base de casi todo; se ataca pronto y en pareja (backend).
- **Frontend**: empieza por el armazón (US-18) y el acceso (US-01) mientras el backend prepara
  archivos; a partir del sprint 3 puede avanzar en paralelo con el backend porque el **contrato de la
  API** (`documentation/backend/entities-resume.md`) ya está fijado.
- **Calidad**: escribe los criterios de aceptación en el sprint anterior y prepara los datos de
  prueba; el recorrido completo depende de que existan todos los módulos.
- **US-12 (migraciones)** bloquea a US-11; **US-11** bloquea a US-07/08/09/10; **US-16** bloquea a
  US-21.

## 2.4. Riesgos del plan

| Riesgo | Impacto | Mitigación |
|---|---|---|
| La compilación nativa es lenta | Retrasa las entregas del backend | Compilar en la integración; en desarrollo usar el modo JVM. |
| Las reglas de alcance por área son sutiles | Fallos de seguridad | Centralizarlas en la base de datos y cubrirlas con pruebas por rol. |
| Deriva entre frontend y contrato | Retrabajo | Contrato fijado y validación de respuestas con esquemas. |
| Prerrequisitos externos (nube, base, hosting) | Bloquea el despliegue | Pedirlos en el sprint 1 y usar el ambiente local mientras tanto. |
| Cuotas del pipeline | Despliegues lentos | Compilar por caché y no reejecutar pruebas innecesarias. |

# 3. Guía de contribución

## Ramas

- `main` es la rama estable y **está protegida**: no se empuja directamente.
- Ramas **cortas** desde `main`, con nombre `tipo/descripcion`:
  `feat/`, `fix/`, `docs/`, `test/`, `chore/`. Ejemplo: `feat/listado-archivos`.
- Una rama = una historia o un arreglo. Se integra con **pull request** y se borra al fusionar.

## Mensajes de commit

Convención **Conventional Commits** (prefijo en inglés, descripción en español):

```
feat(files): añade la carga de archivos con validación de tipo
fix(auth): evita filtrar si el correo existe en el login
test(users): cubre el intento de acceso fuera de alcance
docs(manual): describe el flujo de descarga
```

## Pull request

1. Título claro y descripción con **qué** cambia y **por qué**; enlace a la historia.
2. Cambios **pequeños** (idealmente menos de 400 líneas); si es grande, se parte.
3. **CI en verde** antes de pedir revisión.
4. Revisión de **al menos una persona** (en este equipo, el líder). El autor no revisa su propio
   cambio.
5. Se fusiona con **squash** para mantener un historial limpio.

### Lista de verificación de la revisión

- [ ] ¿Hace lo que dice la historia y cumple sus criterios de aceptación?
- [ ] ¿Las reglas de permisos se comprueban **en el servidor** (y en la base)?
- [ ] ¿Hay pruebas para el caso nuevo y para el intento fuera de alcance?
- [ ] ¿Los errores devuelven el cuerpo uniforme y no revelan información interna?
- [ ] ¿No se registran ni versionan secretos?
- [ ] ¿La interfaz usa los componentes y estilos existentes?
- [ ] ¿Los nombres están en inglés y los textos al usuario en español?

### Definición de terminado (DoD)

- [ ] Código y pruebas que pasan (unitarias e integración).
- [ ] CI en verde; revisado y fusionado.
- [ ] Criterios de aceptación cumplidos y comprobados por calidad.
- [ ] Documentación actualizada si cambia el contrato o el esquema.

# 4. Estrategia de pruebas

| Nivel | Qué se prueba | Responsable |
|---|---|---|
| **Unitario** | Reglas puras: permisos, validación de archivos, formato de datos; en el frontend, componentes y lógica aislada. | Desarrolladores (backend y frontend). |
| **Integración** | La API con cada rol, incluidos los accesos **fuera de alcance** (deben fallar); base de datos con RLS. | Desarrolladores backend. |
| **Extremo a extremo** | El recorrido completo con los 4 roles y las 2 áreas, desde la interfaz. | Analista de calidad. |

Cada cambio debe cumplir, **para llegar al ambiente de pruebas**:

1. **CI en verde**: compila, análisis estático y todas las pruebas.
2. **Migraciones aplicadas** y ambiente recreable desde cero.
3. **Pull request revisado y fusionado**.
4. **Criterios de aceptación** comprobados.

> Los datos de prueba son ficticios y se cargan con la semilla. No se suben documentos reales.

# 5. Incorporación (menos de 30 minutos)

Objetivo: un desarrollador nuevo ejecuta el proyecto en su equipo en menos de media hora.

```bash
# 1. Requisitos: Docker Desktop, Node + pnpm (para el frontend sin Docker), git.   (~5 min)

# 2. Clonar el repositorio
git clone <URL-del-repositorio> archivos && cd archivos   #                             (~1 min)

# 3. Cargar las imágenes y levantar el ambiente local
docker compose up                                          #                          (~5 min)

# 4. Abrir http://localhost:8081 y entrar con admin@example.com / password   #          (~1 min)
```

Para trabajar en el día a día:

```bash
# Backend (modo desarrollo)
cd backend && ./gradlew quarkusDev

# Frontend
cd frontend && pnpm install && pnpm dev
```

**Comprobación rápida**: el endpoint de salud responde `UP` en `http://localhost:8080/health/ready`
y `docker compose ps` muestra los cuatro servicios en marcha. Si algo falla, `docker compose down -v`
y repetir vuelve a migrar y sembrar desde cero.
