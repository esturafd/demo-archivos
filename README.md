# Archivos

Plataforma de gestión segura de archivos: cada usuario administra **solo los archivos que le
corresponden**, según su rol y su área. La autorización se comprueba **siempre en el servidor**, no
en la interfaz.

## Tecnologías

- **Frontend:** React + TypeScript + Vite (SPA), con Pico CSS.
- **Backend:** Quarkus (Java) compilado a **imagen nativa**; API REST.
- **Datos:** PostgreSQL, con seguridad a nivel de fila para acotar el alcance por usuario.
- **Archivos:** almacenamiento compatible con S3 (el contenido nunca se expone por URL directa).
- **Entrega:** contenedores e integración/despliegue continuos con GitHub Actions.

## Ejecutar en local

Requisitos: **Docker Desktop** con Compose v2.

```bash
docker compose up
```

Las imágenes se descargan de Docker Hub (`esturafd/archivos-backend:latest` y
`esturafd/archivos-frontend:latest`). Al primer arranque, la base de datos se migra y se crean los
usuarios y áreas de prueba.

Abre **http://localhost:8081**.

### Usuarios de prueba

Contraseña (todas): `password`.

| Correo | Rol | Área |
|---|---|---|
| `admin@example.com` | Administrador | — |
| `manager@example.com` | Gerente | gestiona el Área A |
| `head.a@example.com` | Jefe de área | Área A |
| `standard.a@example.com` | Estándar | Área A |
| `standard.b@example.com` | Estándar | Área B |

## Documentación

- `documentation/manuales/` — manual técnico, manual de usuario y plan de coordinación (en Markdown y PDF).
- `docker/README.md` — detalle del ambiente local.
- `documentation/backend/` y `documentation/frontend/` — planes, decisiones y contrato de la API.
