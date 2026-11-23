# ==============================================================================
# Dockerfile: ms-orders (Java 21 Spring Boot - Multi-Stage Non-Root)
# ==============================================================================

# Etapa 1: Compilación con Maven
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml ./
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# Etapa 2: Imagen mínima de ejecución con JRE 21
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

RUN apk add --no-cache wget && \
    addgroup -g 10001 spring && \
    adduser -u 10001 -G spring -s /bin/sh -D spring

COPY --from=builder /build/target/ms-orders-*.jar ./app.jar

RUN chown -R spring:spring /app

USER spring

ENV SERVER_PORT=8003

EXPOSE 8003

HEALTHCHECK --interval=20s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8003/actuator/health || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
