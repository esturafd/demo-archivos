# Ambiente de pruebas local

Levanta el proyecto entero con Docker: base de datos, almacenamiento de objetos
(S3), la API y la SPA. Sirve para probar el recorrido completo sin depender de
Neon, Cloud Storage ni Cloud Run.

## Piezas

| Servicio | Imagen | Qué es |
|---|---|---|
| `db` | `postgres:17-alpine` | PostgreSQL; al primer arranque crea los roles técnicos (`app_migrator`, `app_runtime`), aplica las migraciones de `database/migrations/` y siembra unas cuentas de prueba. |
| `s3` | `adobe/s3mock:latest` | S3 compatible en memoria, con el bucket `files` (se pierde al parar el contenedor). |
| `backend` | `esturafd/archivos-backend:latest` | La API Quarkus, imagen **nativa**. |
| `frontend` | `esturafd/archivos-frontend:latest` | La SPA servida por nginx, con `/api` reenviado al backend (mismo origen: cookies de sesión y CSRF como en producción). |

## Requisitos

Docker Engine o Docker Desktop con Compose v2 (`docker compose version`).

## Las imágenes

Se publican en **Docker Hub** como `esturafd/archivos-backend:latest` y
`esturafd/archivos-frontend:latest`. `docker compose up` las **descarga
automáticamente** si no las tienes. Para traerlas a mano:

```bash
docker pull esturafd/archivos-backend:latest
docker pull esturafd/archivos-frontend:latest
```

Para construirlas tú mismo:

```bash
# Backend (binario nativo; requiere Docker)
cd backend
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.package.jar.enabled=false
docker build -f src/main/docker/Dockerfile.native -t esturafd/archivos-backend:latest .

# Frontend
cd ..
docker build -t esturafd/archivos-frontend:latest frontend
```

## Levantar

```bash
docker compose up
# o en segundo plano:
docker compose up -d
```

Cuando `db` pase su *healthcheck*, arrancan `backend` y `frontend`. Abre:

- **Aplicación:** http://localhost:8081
- **API:** http://localhost:8080 (documento OpenAPI en `/q/openapi`)
- **S3 (s3mock):** http://localhost:9090
- **PostgreSQL:** `localhost:5432`

Los puertos y el resto de valores se cambian copiando `docker/.env.example` como
`.env` en la raíz.

## Cuentas de prueba

Contraseña (todas): `password`.

| Correo | Rol | Área |
|---|---|---|
| `admin@example.com` | Administrador | — |
| `manager@example.com` | Gerente | gestiona el Área A |
| `head.a@example.com` | Jefe de área | Área A |
| `standard.a@example.com` | Estándar | Área A |
| `standard.b@example.com` | Estándar | Área B |

## Parar y reiniciar

```bash
docker compose down        # para; conserva la base de datos
docker compose down -v     # para y borra la base (vuelve a migrar y sembrar)
```

La semilla y las migraciones solo se ejecutan cuando el volumen de datos está
vacío (primer arranque). Para empezar de cero, `docker compose down -v`.
