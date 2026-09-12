FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true
COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 appuser \
    && mkdir -p /data/photos \
    && chown -R appuser /data/photos
COPY --from=build /build/build/libs/*.jar app.jar
USER appuser
ENV TZ=Asia/Singapore
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=Asia/Singapore", "-jar", "app.jar"]
