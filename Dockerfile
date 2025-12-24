# Étape 1: Build avec Maven
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copier les fichiers de configuration Maven d'abord (pour le cache des dépendances)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copier le code source et builder
COPY src ./src
RUN mvn clean package -DskipTests -B

# Étape 2: Image de production avec Node.js pour Playwright
FROM eclipse-temurin:17-jdk

# Installer Node.js 20.x
RUN apt-get update && apt-get install -y curl gnupg \
    && curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y nodejs \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Installer les dépendances Playwright (navigateurs)
RUN npx playwright install-deps chromium \
    && npx playwright install chromium

WORKDIR /app

# Créer un utilisateur non-root pour la sécurité
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Copier le script Playwright et installer les dépendances
COPY scripts/package*.json ./scripts/
WORKDIR /app/scripts
RUN npm install --omit=dev

# Copier le script Playwright
COPY scripts/vinted-session-manager.js ./

WORKDIR /app

# Copier le JAR depuis l'étape de build
COPY --from=builder /app/target/vintedFavorites-0.0.1-SNAPSHOT.jar app.jar

# Changer le propriétaire
RUN chown -R appuser:appgroup /app

# Créer le répertoire pour Playwright browser cache
RUN mkdir -p /home/appuser/.cache && chown -R appuser:appgroup /home/appuser

USER appuser

# Variables d'environnement pour Playwright
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
ENV HEADLESS=true

# Port exposé
EXPOSE 8080

# Healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/favorites || exit 1

# Lancer l'application
ENTRYPOINT ["java", "-jar", "app.jar"]
