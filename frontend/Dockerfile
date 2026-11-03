# syntax=docker/dockerfile:1

# Imagen del frontend: compila la SPA y la sirve con nginx. En el mismo origen,
# nginx reenvía /api al backend (así las cookies de sesión y CSRF funcionan
# igual que en producción, donde lo hace Firebase Hosting).

# --- Compilación -------------------------------------------------------------
FROM node:24-slim AS build

WORKDIR /app

# pnpm es el gestor del proyecto; se fija la misma versión que en `packageManager`.
RUN npm install -g pnpm@12.16.1

# Primero las dependencias (mejor caché): solo se reinstalan si cambia el lockfile.
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# Luego el código y la compilación.
COPY . .
RUN pnpm build

# --- Servido -----------------------------------------------------------------
FROM nginx:alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
