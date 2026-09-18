# ===== Stage 1: Build =====
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom first for better layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ===== Stage 2: Runtime =====
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy the built jar
COPY --from=build /app/target/*.jar app.jar

# Expose port
EXPOSE 8080

# Health / JVM options for small containers (Render free tier ~512MB)
ENV JAVA_OPTS="-Xms128m -Xmx384m -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
