#!/usr/bin/env bash
#
# Prepara el ambiente productivo del backend en Google Cloud a partir de un
# archivo .env.deploy. Reúne todo lo que hay que hacer UNA VEZ, a mano, antes de montar
# el CI/CD:
#
#   1. Habilita las APIs necesarias.
#   2. Crea el repositorio de Artifact Registry.
#   3. Crea las cuentas de servicio (despliegue y ejecución) y sus roles.
#   4. Sube los secretos a Secret Manager.
#   5. Da a la cuenta de ejecución acceso a esos secretos.
#   6. Configura Workload Identity Federation para el repositorio de GitHub.
#   7. Deja el servicio de Cloud Run con sus variables de entorno y secretos
#      (lo crea con una imagen de relleno si aún no existe).
#
# Es idempotente: se puede volver a ejecutar sin duplicar nada.
#
# Uso:
#   ./configure-environment.sh                    # lee ./.env.deploy
#   ./configure-environment.sh --env-file RUTA
#   ./configure-environment.sh --dry-run          # no cambia nada, solo muestra
#   ./configure-environment.sh --no-create-service
#   ./configure-environment.sh --help
#
# Requisitos:
#   - gcloud instalado y autenticado (gcloud auth login), con permisos de
#     propietario del proyecto (para habilitar APIs, IAM y cuentas de servicio).
#   - Un archivo .env.deploy (copia .env.deploy.example) con los valores reales.

set -euo pipefail

# --- Qué es secreto y qué no ------------------------------------------------

# Secretos: se suben a Secret Manager. El nombre del secreto es el de la
# variable de entorno, que es válido en Secret Manager.
SECRET_KEYS=(
  QUARKUS_DATASOURCE_PASSWORD
  QUARKUS_S3_AWS_CREDENTIALS_STATIC_PROVIDER_SECRET_ACCESS_KEY
  APP_AUTH_RATE_LIMIT_KEY
)

# Variables no secretas: se configuran en Cloud Run como entorno.
VAR_KEYS=(
  QUARKUS_DATASOURCE_JDBC_URL
  QUARKUS_DATASOURCE_USERNAME
  APP_DATABASE_RUNTIME_ROLE
  APP_STORAGE_BUCKET
  QUARKUS_S3_AWS_CREDENTIALS_TYPE
  QUARKUS_S3_ENDPOINT_OVERRIDE
  QUARKUS_S3_AWS_REGION
  QUARKUS_S3_AWS_CREDENTIALS_STATIC_PROVIDER_ACCESS_KEY_ID
)

# Imagen de relleno para crear el servicio si aún no existe. El CI la sustituye
# por la imagen real en el primer despliegue.
PLACEHOLDER_IMAGE="us-docker.pkg.dev/cloudrun/container/hello"

# --- Argumentos --------------------------------------------------------------

ENV_FILE="./.env.deploy"
DRY_RUN=0
CREATE_SERVICE=1

uso() {
  awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "${BASH_SOURCE[0]}"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --env-file) ENV_FILE="$2"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    --no-create-service) CREATE_SERVICE=0; shift ;;
    -h|--help) uso; exit 0 ;;
    *) echo "Opción desconocida: $1" >&2; uso >&2; exit 1 ;;
  esac
done

# --- Ayudas ------------------------------------------------------------------

# Ejecuta un comando de gcloud, o lo muestra si es un ensayo.
gc() {
  if [ "$DRY_RUN" = 1 ]; then
    echo "  [dry-run] gcloud $*"
  else
    gcloud "$@"
  fi
}

# Igual que `gc`, pero sin volcar la salida (para los *bindings* de IAM).
gcq() {
  if [ "$DRY_RUN" = 1 ]; then
    echo "  [dry-run] gcloud $*"
  else
    gcloud "$@" >/dev/null
  fi
}

titulo() {
  echo
  echo "== $* =="
}

# Lee el valor de una clave del .env.deploy (sin comillas ni espacios sobrantes).
value_of() {
  local key="$1" line value
  line="$(grep -E "^[[:space:]]*${key}[[:space:]]*=" "$ENV_FILE" 2>/dev/null | head -1 || true)"
  if [ -z "$line" ]; then
    return 0
  fi
  value="${line#*=}"
  value="$(printf '%s' "$value" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
  case "$value" in
    \"*\") value="${value#\"}"; value="${value%\"}" ;;
    \'*\') value="${value#\'}"; value="${value%\'}" ;;
  esac
  printf '%s' "$value"
}

# Existe un recurso (cuenta de servicio, repositorio, secreto, servicio...).
existe() {
  "$@" >/dev/null 2>&1
}

# --- Lectura y validación del .env.deploy -------------------------------------------

if [ ! -f "$ENV_FILE" ]; then
  echo "No encuentro el archivo $ENV_FILE. Copia .env.deploy.example como .env.deploy." >&2
  exit 1
fi

faltan=""
for key in "${SECRET_KEYS[@]}" "${VAR_KEYS[@]}" \
           GCP_PROJECT_ID GCP_REGION GCP_SERVICE GCP_ARTIFACT_REPO \
           GCP_SA_DEPLOY GCP_SA_RUNTIME GITHUB_REPO WIF_POOL WIF_PROVIDER; do
  if [ -z "$(value_of "$key")" ]; then
    faltan="${faltan} ${key}"
  fi
done
if [ -n "$faltan" ]; then
  echo "Faltan valores en $ENV_FILE:${faltan}" >&2
  exit 1
fi

PROJECT="$(value_of GCP_PROJECT_ID)"
REGION="$(value_of GCP_REGION)"
SERVICE="$(value_of GCP_SERVICE)"
AR_REPO="$(value_of GCP_ARTIFACT_REPO)"
SA_DEPLOY="$(value_of GCP_SA_DEPLOY)"
SA_RUNTIME="$(value_of GCP_SA_RUNTIME)"
GITHUB_REPO="$(value_of GITHUB_REPO)"
WIF_POOL="$(value_of WIF_POOL)"
WIF_PROVIDER="$(value_of WIF_PROVIDER)"

SA_DEPLOY_EMAIL="${SA_DEPLOY}@${PROJECT}.iam.gserviceaccount.com"
SA_RUNTIME_EMAIL="${SA_RUNTIME}@${PROJECT}.iam.gserviceaccount.com"

echo "Proyecto:        $PROJECT"
echo "Región:          $REGION"
echo "Servicio:        $SERVICE"
echo "Artifact Reg.:   $AR_REPO"
echo "SA despliegue:   $SA_DEPLOY_EMAIL"
echo "SA ejecución:    $SA_RUNTIME_EMAIL"
echo "GitHub:          $GITHUB_REPO"
[ "$DRY_RUN" = 1 ] && echo "Modo:            ENSAYO (no cambia nada)"

# --- 1. APIs -----------------------------------------------------------------

titulo "1. Habilitar APIs"
gc services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  iam.googleapis.com \
  iamcredentials.googleapis.com \
  sts.googleapis.com \
  cloudresourcemanager.googleapis.com \
  --project "$PROJECT"

# --- 2. Artifact Registry ----------------------------------------------------

titulo "2. Repositorio de Artifact Registry"
if existe gcloud artifacts repositories describe "$AR_REPO" --location "$REGION" --project "$PROJECT"; then
  echo "ya existe: $AR_REPO"
else
  gc artifacts repositories create "$AR_REPO" \
    --project "$PROJECT" --location "$REGION" \
    --repository-format=docker \
    --description="Imágenes del backend"
fi

# --- 3. Cuentas de servicio --------------------------------------------------

titulo "3. Cuentas de servicio"

if existe gcloud iam service-accounts describe "$SA_DEPLOY_EMAIL" --project "$PROJECT"; then
  echo "ya existe: $SA_DEPLOY_EMAIL"
else
  gc iam service-accounts create "$SA_DEPLOY" \
    --project "$PROJECT" --display-name="Despliegue desde CI"
fi

# La cuenta de despliegue publica imágenes, gobierna Cloud Run y puede actuar
# como la cuenta de ejecución.
for role in roles/run.admin roles/artifactregistry.writer \
            roles/iam.serviceAccountUser roles/serviceusage.serviceUsageConsumer; do
  gcq projects add-iam-policy-binding "$PROJECT" \
    --member="serviceAccount:$SA_DEPLOY_EMAIL" --role="$role" --condition=None
done

if existe gcloud iam service-accounts describe "$SA_RUNTIME_EMAIL" --project "$PROJECT"; then
  echo "ya existe: $SA_RUNTIME_EMAIL"
else
  gc iam service-accounts create "$SA_RUNTIME" \
    --project "$PROJECT" --display-name="Ejecución del backend"
fi

# --- 4. Secretos -------------------------------------------------------------

titulo "4. Secretos en Secret Manager"
for key in "${SECRET_KEYS[@]}"; do
  if existe gcloud secrets describe "$key" --project "$PROJECT"; then
    if [ "$DRY_RUN" = 1 ]; then
      echo "  [dry-run] añadiría versión a $key"
    else
      value_of "$key" | gcloud secrets versions add "$key" \
        --project "$PROJECT" --data-file=- >/dev/null
      echo "actualizado: $key"
    fi
  else
    if [ "$DRY_RUN" = 1 ]; then
      echo "  [dry-run] crear $key"
    else
      value_of "$key" | gcloud secrets create "$key" \
        --project "$PROJECT" --replication-policy=automatic --data-file=- >/dev/null
      echo "creado:      $key"
    fi
  fi
done

# --- 5. Acceso de la cuenta de ejecución a los secretos ----------------------

titulo "5. Permiso de la cuenta de ejecución sobre los secretos"
for key in "${SECRET_KEYS[@]}"; do
  gcq secrets add-iam-policy-binding "$key" \
    --project "$PROJECT" \
    --member="serviceAccount:$SA_RUNTIME_EMAIL" \
    --role="roles/secretmanager.secretAccessor"
done

# --- 6. Workload Identity Federation -----------------------------------------

titulo "6. Workload Identity Federation"
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT" --format='value(projectNumber)' 2>/dev/null || true)"
PROJECT_NUMBER="${PROJECT_NUMBER:-NUMERO_DE_PROYECTO}"

if existe gcloud iam workload-identity-pools describe "$WIF_POOL" \
    --project "$PROJECT" --location=global; then
  echo "ya existe el pool: $WIF_POOL"
else
  gc iam workload-identity-pools create "$WIF_POOL" \
    --project "$PROJECT" --location=global --display-name="GitHub Actions"
fi

if existe gcloud iam workload-identity-pools providers describe "$WIF_PROVIDER" \
    --project "$PROJECT" --location=global --workload-identity-pool="$WIF_POOL"; then
  echo "ya existe el proveedor: $WIF_PROVIDER"
else
  gc iam workload-identity-pools providers create-oidc "$WIF_PROVIDER" \
    --project "$PROJECT" --location=global --workload-identity-pool="$WIF_POOL" \
    --display-name="GitHub" \
    --issuer-uri="https://token.actions.githubusercontent.com" \
    --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
    --attribute-condition="assertion.repository=='$GITHUB_REPO'"
fi

# Solo este repositorio puede actuar como la cuenta de despliegue.
gcq iam service-accounts add-iam-policy-binding "$SA_DEPLOY_EMAIL" \
  --project "$PROJECT" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${WIF_POOL}/attribute.repository/${GITHUB_REPO}"

WIF_PROVIDER_RESOURCE="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${WIF_POOL}/providers/${WIF_PROVIDER}"

# --- 7. Servicio de Cloud Run: variables y secretos --------------------------

titulo "7. Servicio de Cloud Run"

env_vars=""
for key in "${VAR_KEYS[@]}"; do
  value="$(value_of "$key")"
  if [ -n "$value" ]; then
    env_vars="${env_vars:+$env_vars,}$key=$value"
  fi
done

set_secrets=""
for key in "${SECRET_KEYS[@]}"; do
  set_secrets="${set_secrets:+$set_secrets,}$key=$key:latest"
done

if existe gcloud run services describe "$SERVICE" --region "$REGION" --project "$PROJECT"; then
  echo "el servicio ya existe; se actualizan variables y secretos"
  gc run services update "$SERVICE" \
    --project "$PROJECT" --region "$REGION" \
    --update-env-vars "$env_vars" \
    --update-secrets "$set_secrets" \
    --service-account "$SA_RUNTIME_EMAIL"
elif [ "$CREATE_SERVICE" = 1 ]; then
  echo "el servicio no existe; se crea con una imagen de relleno"
  echo "(el CI la sustituirá por la imagen real en el primer despliegue)"
  gc run deploy "$SERVICE" \
    --project "$PROJECT" --region "$REGION" \
    --image "$PLACEHOLDER_IMAGE" \
    --service-account "$SA_RUNTIME_EMAIL" \
    --set-env-vars "$env_vars" \
    --set-secrets "$set_secrets" \
    --allow-unauthenticated
else
  echo "el servicio no existe y no se crea (--no-create-service)."
  echo "Lo creará el CI en el primer despliegue."
fi

# --- Resumen -----------------------------------------------------------------

cat <<EOF

Listo. Configura ahora estas variables y secretos en GitHub
(Settings > Environments > production):

  Variables (vars):
    GCP_PROJECT_ID=$PROJECT
    GCP_REGION=$REGION
    GCP_ARTIFACT_REPO=$AR_REPO
    GCP_SERVICE=$SERVICE
    GCP_WORKLOAD_IDENTITY_PROVIDER=$WIF_PROVIDER_RESOURCE
    GCP_SERVICE_ACCOUNT=$SA_DEPLOY_EMAIL

  Secretos de la base de datos y de la app: ninguno.
  (Los valores viven en Secret Manager; Cloud Run los referencia y el CI solo
   usa Workload Identity para desplegar.)
EOF
