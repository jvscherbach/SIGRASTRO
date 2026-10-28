# ---- build ----
FROM eclipse-temurin:24.0.2_12-jdk-noble AS build
WORKDIR /build

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package

# ---- runtime ----
FROM eclipse-temurin:24.0.2_12-jre-noble

# LibreOffice headless (Writer e Calc, sem interface gráfica) para a conversão de documentos (S3) e curl para o
# HEALTHCHECK. Fontes livres para acentuação e compatibilidade métrica com as do Office: Liberation (Arial, Times New
# Roman, Courier New), Carlito (Calibri), Caladea (Cambria) e DejaVu. O conversor roda como o usuário vistas, com o
# perfil do LibreOffice no diretório temporário e HOME gravável (/home/vistas) para o cache de fontes.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        libreoffice-writer-nogui \
        libreoffice-calc-nogui \
        fonts-liberation \
        fonts-dejavu-core \
        fonts-crosextra-carlito \
        fonts-crosextra-caladea \
        fontconfig \
        curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system vistas \
    && useradd --system --gid vistas --create-home --home-dir /home/vistas vistas \
    && mkdir -p /var/tmp/vistas \
    && chown vistas:vistas /var/tmp/vistas

WORKDIR /app
COPY --from=build /build/target/siga-vistas-api.jar app.jar

USER vistas
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
