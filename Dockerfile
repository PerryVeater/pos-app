# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS build

# Set working directory
WORKDIR /build

# Copy Maven wrapper and pom.xml first (for better layer caching)
COPY app/pom.xml .
COPY app/.mvn ./.mvn
COPY app/mvnw .
RUN chmod +x ./mvnw

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY app/src ./src

# Build the application (produces posapp-1.0.0.jar in target/)
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-alpine

# Install wget for health checks and create non-root user
RUN apk add --no-cache wget && \
    addgroup -S spring && \
    adduser -S spring -G spring

# Set working directory
WORKDIR /app

# Copy the JAR from build stage and set ownership
COPY --from=build --chown=spring:spring /build/target/posapp-*.jar app.jar

# Switch to non-root user for security
USER spring:spring

# Expose port 8080
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
