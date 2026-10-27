# ---- build ----
FROM node:26.12.1-alpine3.24 AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---- runtime ----
FROM nginx:1.30.6-alpine3.24

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/front/browser /usr/share/nginx/html

# Sem root: o usuário nginx da imagem base precisa escrever só o cache/temporários e o PID.
# A diretiva "user" do nginx.conf base só vale para root, então sai (evita o aviso na partida).
RUN sed -i '/^user /d' /etc/nginx/nginx.conf \
    && chown -R nginx:nginx /var/cache/nginx \
    && touch /run/nginx.pid \
    && chown nginx:nginx /run/nginx.pid

USER nginx
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q --spider http://127.0.0.1:8080/ || exit 1
