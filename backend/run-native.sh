#!/usr/bin/env bash
#
# Compila (si hace falta) y ejecuta el binario nativo del backend con Podman,
# levantando PostgreSQL (con el esquema y datos de prueba) y un S3 local.
#
# Uso:
#   ./run-native.sh            # compila si falta el binario y arranca todo
#   ./run-native.sh --build    # fuerza la recompilación nativa
#   ./run-native.sh --logs     # muestra los registros de la aplicación
#   ./run-native.sh --down     # para y limpia contenedores y red
#   ./run-native.sh --help
#
# Variables de entorno (opcionales):
#   PORT          puerto local del API                (por defecto 8090)
#   BASE_IMAGE    imagen base para el binario         (glibc; por defecto ubi9-minimal)
#   S3_IMAGE      imagen del S3 local                 (por defecto adobe/s3mock)
#   PG_IMAGE      imagen de PostgreSQL                (por defecto postgres:18)
#
# Nota (macOS + Podman): al compilar en contenedor, Quarkus añade
# `--uidmap/--gidmap` que podman-machine rechaza. Este script instala un shim de
# `podman` (y `docker`) que descarta esos mapeos y lo antepone al PATH.
#
# El binario nativo es de Linux. En macOS se ejecuta dentro de un contenedor
# glibc; por eso este script lo lanza con Podman en lugar de directamente.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PORT="${PORT:-8090}"
BASE_IMAGE="${BASE_IMAGE:-registry.access.redhat.com/ubi9/ubi-minimal}"
S3_IMAGE="${S3_IMAGE:-docker.io/adobe/s3mock:latest}"
PG_IMAGE="${PG_IMAGE:-docker.io/library/postgres:18}"

SHIM_DIR="$SCRIPT_DIR/build/podman-shim"
MIGRATIONS_DIR="$SCRIPT_DIR/../database/migrations"

NET="native-net"
PG="native-pg"
S3="s3mock"
APP="native-app"

BUCKET="files"
DB_USER="postgres"
DB_PASSWORD="postgres"
DB_NAME="postgres"

CONTENT_DIR="$SCRIPT_DIR/build/run-native"

BUILD=0
DOWN=0
LOGS=0

usage() {
  awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "${BASH_SOURCE[0]}"
}

for arg in "$@"; do
  case "$arg" in
    --build) BUILD=1 ;;
    --down|--stop) DOWN=1 ;;
    --logs) LOGS=1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opción desconocida: $arg" >&2; usage >&2; exit 1 ;;
  esac
done

# ---------------------------------------------------------------------------
# Shim de podman
# ---------------------------------------------------------------------------

# Escribe un `podman` (y un `docker`) que descartan los mapeos de usuario que
# podman-machine no admite, y deja el directorio del shim al frente del PATH.
install_shim() {
  mkdir -p "$SHIM_DIR"

  local real
  real="$(PATH="$(printf '%s' "$PATH" | tr ':' '\n' | grep -vF "$SHIM_DIR" | paste -sd: -)" command -v podman || true)"
  if [ -z "$real" ]; then
    echo "No encuentro podman en el PATH. Instálalo antes de continuar." >&2
    exit 1
  fi

  cat > "$SHIM_DIR/podman" <<EOF
#!/usr/bin/env bash
# Generado por run-native.sh. Quarkus marca Podman como rootful y añade
# --uidmap/--gidmap; podman-machine los rechaza ("invalid mappings specified").
# En macOS la propiedad de los ficheros del bind-mount no depende de ellos, así
# que se descartan y se llama al podman real.
real="$real"
args=()
skip=0
for a in "\$@"; do
  if [ "\$skip" = 1 ]; then skip=0; continue; fi
  case "\$a" in
    --uidmap|--gidmap) skip=1 ;;
    --uidmap=*|--gidmap=*|--userns=keep-id:*) ;;
    *) args+=("\$a") ;;
  esac
done
exec "\$real" "\${args[@]}"
EOF
  chmod +x "$SHIM_DIR/podman"

  cat > "$SHIM_DIR/docker" <<EOF
#!/usr/bin/env bash
# Docker es un alias de podman en esta máquina.
exec "$SHIM_DIR/podman" "\$@"
EOF
  chmod +x "$SHIM_DIR/docker"

  export PATH="$SHIM_DIR:$PATH"
}

# ---------------------------------------------------------------------------
# Compilación nativa
# ---------------------------------------------------------------------------

native_binary() {
  ls "$SCRIPT_DIR"/build/*-runner 2>/dev/null | head -1 || true
}

build_native() {
  echo ">> Compilando la imagen nativa (con -Ob, que usa menos memoria)..."
  ./gradlew nativeBuild -x test -Dquarkus.native.additional-build-args=-Ob --console=plain
}

# ---------------------------------------------------------------------------
# Dependencias: PostgreSQL con el esquema y S3 local
# ---------------------------------------------------------------------------

wait_for_postgres() {
  for _ in $(seq 1 60); do
    podman exec "$PG" pg_isready -U "$DB_USER" >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "PostgreSQL no respondió a tiempo." >&2
  exit 1
}

seed() {
  # Mismos datos ficticios que las pruebas de base de datos. La contraseña de
  # todos los usuarios es `password`.
  podman exec -i "$PG" psql -U "$DB_USER" -v ON_ERROR_STOP=1 -q <<'SQL'
INSERT INTO app.areas (id, name) VALUES
  ('11111111-1111-1111-1111-111111111111', 'Area A'),
  ('22222222-2222-2222-2222-222222222222', 'Area B');

INSERT INTO app.users (id, email, password_hash, name, role, area_id) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', 'activo@demo.local',  '$2a$10$Uc.SZ0hvGJQlYdsAp7be1.lFjmOnc7aAr4L0YY3/VN3oK.F8zJHRG', 'Activo',  'standard',  '11111111-1111-1111-1111-111111111111'),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', 'otro@demo.local',    '$2a$10$Uc.SZ0hvGJQlYdsAp7be1.lFjmOnc7aAr4L0YY3/VN3oK.F8zJHRG', 'Otro',    'standard',  '22222222-2222-2222-2222-222222222222'),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa4', 'jefe@demo.local',    '$2a$10$Uc.SZ0hvGJQlYdsAp7be1.lFjmOnc7aAr4L0YY3/VN3oK.F8zJHRG', 'Jefe',    'area_head', '11111111-1111-1111-1111-111111111111'),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa5', 'admin@demo.local',   '$2a$10$Uc.SZ0hvGJQlYdsAp7be1.lFjmOnc7aAr4L0YY3/VN3oK.F8zJHRG', 'Admin',   'admin',      NULL),
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6', 'gerente@demo.local', '$2a$10$Uc.SZ0hvGJQlYdsAp7be1.lFjmOnc7aAr4L0YY3/VN3oK.F8zJHRG', 'Gerente', 'manager',   NULL);

INSERT INTO app.manager_areas (manager_id, area_id) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa6', '11111111-1111-1111-1111-111111111111');
SQL
}

start_services() {
  podman network exists "$NET" >/dev/null 2>&1 || podman network create "$NET" >/dev/null
  podman rm -f "$APP" "$PG" "$S3" >/dev/null 2>&1 || true

  echo ">> PostgreSQL ($PG_IMAGE)"
  podman run -d --name "$PG" --network "$NET" \
    -e POSTGRES_PASSWORD="$DB_PASSWORD" -e POSTGRES_DB="$DB_NAME" \
    "$PG_IMAGE" >/dev/null
  wait_for_postgres

  echo ">> Rol de ejecución y migraciones"
  echo "CREATE ROLE app_runtime NOLOGIN;" \
    | podman exec -i "$PG" psql -U "$DB_USER" -v ON_ERROR_STOP=1 -q
  if [ ! -d "$MIGRATIONS_DIR" ]; then
    echo "No encuentro las migraciones en $MIGRATIONS_DIR" >&2
    exit 1
  fi
  for file in "$MIGRATIONS_DIR"/V*.sql; do
    podman exec -i "$PG" psql -U "$DB_USER" -v ON_ERROR_STOP=1 -q < "$file"
  done

  echo ">> Semilla de datos de prueba"
  seed

  echo ">> S3 local ($S3_IMAGE)"
  podman run -d --name "$S3" --network "$NET" \
    -e COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS="$BUCKET" \
    "$S3_IMAGE" >/dev/null
}

# ---------------------------------------------------------------------------
# Ejecución del binario
# ---------------------------------------------------------------------------

run_app() {
  local binary
  binary="$(native_binary)"
  if [ -z "$binary" ] || [ ! -x "$binary" ]; then
    echo "No hay binario nativo en build/. Compílalo con: ./run-native.sh --build" >&2
    exit 1
  fi

  podman rm -f "$APP" >/dev/null 2>&1 || true
  echo ">> Arrancando el binario nativo en http://localhost:$PORT"
  podman run -d --name "$APP" --network "$NET" -p "$PORT:8080" \
    -v "$binary:/app/app" \
    -e QUARKUS_HTTP_HOST=0.0.0.0 \
    -e APP_DATABASE_RUNTIME_ROLE=app_runtime \
    -e QUARKUS_DATASOURCE_JDBC_URL="jdbc:postgresql://$PG:5432/$DB_NAME" \
    -e QUARKUS_DATASOURCE_USERNAME="$DB_USER" \
    -e QUARKUS_DATASOURCE_PASSWORD="$DB_PASSWORD" \
    -e QUARKUS_S3_AWS_CREDENTIALS_TYPE=static \
    -e QUARKUS_S3_AWS_CREDENTIALS_STATIC_PROVIDER_ACCESS_KEY_ID=test \
    -e QUARKUS_S3_AWS_CREDENTIALS_STATIC_PROVIDER_SECRET_ACCESS_KEY=test \
    -e QUARKUS_S3_ENDPOINT_OVERRIDE="http://$S3:9090" \
    -e APP_STORAGE_BUCKET="$BUCKET" \
    "$BASE_IMAGE" /app/app >/dev/null

  for _ in $(seq 1 120); do
    curl -sf "http://localhost:$PORT/health/live" >/dev/null 2>&1 && return 0
    sleep 0.5
  done
  echo "La aplicación no respondió a tiempo. Registros:" >&2
  podman logs "$APP" >&2 || true
  exit 1
}

summary() {
  mkdir -p "$CONTENT_DIR"
  printf 'contenido nativo' > "$CONTENT_DIR/ejemplo.txt"

  cat <<EOF

Listo. Detalles de la instantánea nativa:

  start        $(podman logs "$APP" 2>&1 | grep -o 'started in [0-9.]*s' | tail -1 || true)
  binario      $(ls -lh "$(native_binary)" | awk '{print $5}')
  salud        http://localhost:$PORT/health/live
  contraseña   de los usuarios de prueba: password

Prueba rápida:

  jar=\$(mktemp)
  curl -s -c "\$jar" http://localhost:$PORT/api/auth/sessions/current -o /dev/null
  csrf=\$(awk '\$6=="csrf-token"{print \$7}' "\$jar")
  curl -s -b "\$jar" -c "\$jar" -H "X-CSRF-TOKEN: \$csrf" -H "Content-Type: application/json" \\
    -d '{"email":"activo@demo.local","password":"password"}' http://localhost:$PORT/api/auth/sessions
  curl -s -b "\$jar" -H "X-CSRF-TOKEN: \$csrf" \\
    -F "file=@$CONTENT_DIR/ejemplo.txt;type=text/plain" http://localhost:$PORT/api/files

Registros:  ./run-native.sh --logs
Parar:      ./run-native.sh --down
EOF
}

down() {
  podman rm -f "$APP" "$PG" "$S3" >/dev/null 2>&1 || true
  podman network rm "$NET" >/dev/null 2>&1 || true
  echo "Contenedores y red eliminados."
}

# ---------------------------------------------------------------------------
# Principal
# ---------------------------------------------------------------------------

install_shim

if [ "$DOWN" = 1 ]; then
  down
  exit 0
fi

if [ "$LOGS" = 1 ]; then
  podman logs -f "$APP"
  exit 0
fi

if [ "$BUILD" = 1 ] || [ -z "$(native_binary)" ]; then
  build_native
fi

start_services
run_app
summary
