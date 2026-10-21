# syntax=docker/dockerfile:1

# ---- build ----
FROM node:22.23.3-alpine3.24 AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---- runtime ----
FROM nginx:1.30.6-alpine3.24

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/front/browser /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q --spider http://127.0.0.1/ || exit 1
