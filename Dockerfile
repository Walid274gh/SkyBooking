# ========== STAGE 1: BUILD ==========
FROM maven:3.8-openjdk-8 AS builder

WORKDIR /build

# Variables de build
ARG BUILD_DATE
ARG VERSION
LABEL build_date=$BUILD_DATE
LABEL version=$VERSION

# Copier pom.xml en premier pour cache Docker
COPY pom.xml .

# Télécharger les dépendances (layer cachée)
RUN mvn dependency:go-offline -B

# Copier le code source
COPY src/ ./src/

# Compiler le projet avec optimisations
RUN mvn clean package -DskipTests -Dmaven.test.skip=true

# Vérifier que le build a réussi
RUN test -d target || (echo "Build failed: target directory not found" && exit 1)

# ========== STAGE 2: RUNTIME ==========
FROM eclipse-temurin:8-jdk-alpine

# Metadata
LABEL maintainer="skybooking-dev"
LABEL description="SkyBooking Backend - CORBA + REST API"

RUN apk add --no-cache \
    bash \
    curl \
    netcat-openbsd \
    tzdata \
    fontconfig \
    freetype \
    ttf-dejavu \
    msttcorefonts-installer \
    fontconfig \
    && update-ms-fonts \
    && fc-cache -f \
    && rm -rf /var/cache/apk/*

# Créer un utilisateur non-root pour sécurité
RUN addgroup -g 1001 -S skybooking && \
    adduser -S -D -H -u 1001 -h /app -s /sbin/nologin -G skybooking -g skybooking skybooking

WORKDIR /app

# Créer les répertoires avec les bonnes permissions
RUN mkdir -p logs tickets invoices temp_images lib && \
    chown -R skybooking:skybooking /app

# Copier les JARs et dépendances depuis le builder
COPY --from=builder --chown=skybooking:skybooking /build/target/*.jar ./lib/
COPY --from=builder --chown=skybooking:skybooking /build/target/lib/*.jar ./lib/

# Copier les fichiers IDL (si nécessaire)
COPY --chown=skybooking:skybooking src/main/idl/ ./idl/

# Copier le script de démarrage
COPY --chown=skybooking:skybooking src/docker/scripts/start-backend.sh ./
RUN chmod +x start-backend.sh

# Variables d'environnement par défaut
ENV MONGODB_URI=mongodb://mongodb:27017 \
    MONGODB_DB_NAME=skybooking_db \
    CORBA_NAMING_PORT=1050 \
    REST_API_PORT=8080 \
    JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -Djava.awt.headless=true" \
    TZ=Africa/Algiers \
    LANG=C.UTF-8

# Exposer les ports
EXPOSE 1050 8080

# Healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/api/health || exit 1

# Changer vers l'utilisateur non-root
USER skybooking

# Point d'entrée
ENTRYPOINT ["./start-backend.sh"]