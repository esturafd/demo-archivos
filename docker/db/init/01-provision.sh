#!/bin/bash
#
# Aprovisiona la base de datos del ambiente local. Lo ejecuta PostgreSQL una
# sola vez, cuando el volumen de datos está vacío (docker-entrypoint-initdb.d).
#
# Réplica de `database/provision_environment.py` más la aplicación de las
# migraciones: crea los roles técnicos (app_migrator, app_runtime), da permiso de
# creación a app_migrator y aplica las migraciones como app_migrator, igual que
# haría Flyway.

set -Eeuo pipefail

: "${POSTGRES_USER:?falta POSTGRES_USER}"
: "${POSTGRES_DB:?falta POSTGRES_DB}"
: "${APP_MIGRATOR_PASSWORD:?falta APP_MIGRATOR_PASSWORD}"
: "${APP_RUNTIME_PASSWORD:?falta APP_RUNTIME_PASSWORD}"

echo "== Creando los roles técnicos =="
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
CREATE ROLE app_migrator LOGIN PASSWORD '${APP_MIGRATOR_PASSWORD}';
CREATE ROLE app_runtime  LOGIN PASSWORD '${APP_RUNTIME_PASSWORD}';
GRANT CREATE ON DATABASE "${POSTGRES_DB}" TO app_migrator;
SQL

echo "== Aplicando las migraciones como app_migrator =="
for file in /migrations/V*.sql; do
  echo "-- $(basename "$file")"
  psql -v ON_ERROR_STOP=1 --username app_migrator --dbname "$POSTGRES_DB" -f "$file"
done

echo "== Base de datos lista =="
