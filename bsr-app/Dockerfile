# Build context: the bsr-app repository root. CloudBase service port: 8080.
# Deploy a single instance: sessions are currently held in application memory.
FROM node:24.18.0-bookworm-slim AS web
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build/backend
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
COPY backend/src/main/ src/main/
COPY --from=web /build/frontend/dist/ /build/frontend/dist/
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
    && ./mvnw -B -Pcloud,unit -DskipTests package

FROM eclipse-temurin:25-jre-noble AS runtime
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/backend/target/bsr-backend-0.1.0-SNAPSHOT.jar /app/bsr.jar
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -Dfile.encoding=UTF-8"
EXPOSE 8080
# DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD are runtime inputs.
# Do not put secrets in build arguments or this image.
ENTRYPOINT ["java", "-jar", "/app/bsr.jar"]
