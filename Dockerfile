# syntax=docker/dockerfile:1

# ---------- Stage 1: build the JAR with Maven ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Resolve dependencies first so this layer is cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests \
    && cp target/*.jar app.jar

# ---------- Stage 2: slim runtime image ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build --chown=spring:spring /workspace/app.jar app.jar

USER spring

ENV SERVER_PORT=8080 \
    JAVA_OPTS=""

EXPOSE 8080

# busybox wget ships with alpine, so no extra packages are needed
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
