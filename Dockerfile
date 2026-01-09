# Multi-stage build for AI Agent Service
FROM eclipse-temurin:21-jdk-alpine AS builder

# Install build dependencies
RUN apk add --no-cache maven python3 py3-pip

WORKDIR /build

# Copy Maven files
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install runtime dependencies
RUN apk add --no-cache \
    python3 \
    py3-pip \
    openssh-client \
    git \
    bash \
    curl

# Create app user
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser

WORKDIR /app

# Copy built jar from builder
COPY --from=builder /build/target/*.jar app.jar

# Copy Python client
COPY python ./python

# Setup Python virtual environment
RUN cd python && \
    python3 -m venv venv && \
    source venv/bin/activate && \
    pip install --no-cache-dir anthropic==0.40.0

# Create directories
RUN mkdir -p /app/generated-apps /app/template /app/keys && \
    chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/api/agent/health || exit 1

# Run application
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
