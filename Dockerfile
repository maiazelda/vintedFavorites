# Étape 1: Build avec Maven
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copier les fichiers de configuration Maven d'abord (pour le cache des dépendances)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copier le code source et builder
COPY src ./src
RUN mvn clean package -DskipTests -B

# Étape 2: Image de production légère
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Créer un utilisateur non-root pour la sécurité
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copier le JAR depuis l'étape de build
COPY --from=builder /app/target/vintedFavorites-0.0.1-SNAPSHOT.jar app.jar

# Changer le propriétaire
RUN chown -R appuser:appgroup /app

USER appuser

# Port exposé
EXPOSE 8080

# Healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/favorites || exit 1

# Lancer l'application
ENTRYPOINT ["java", "-jar", "app.jar"]
