FROM gradle:8-jdk21 AS build

WORKDIR /app
COPY --chown=gradle:gradle . /app
RUN gradle clean build -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=build /app/build/libs/api-gateway-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
