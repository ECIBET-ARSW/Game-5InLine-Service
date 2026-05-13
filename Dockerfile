# Dockerfile (multi-stage) - usa etiquetas válidas con JDK 17
# Stage 1: build con Maven + Eclipse Temurin 17
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app

# Copiamos pom primero para cachear dependencias
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

# Copiamos el código fuente y construimos
COPY src ./src
RUN mvn -B -DskipTests package

# Stage 2: runtime con JRE 17
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copiamos el JAR desde la etapa de build
COPY --from=build /app/target/*.jar /app/app.jar


ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
