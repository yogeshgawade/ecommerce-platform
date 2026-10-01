FROM gradle:9.7.1-jdk21 AS build

WORKDIR /workspace

COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon

COPY src src
RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
