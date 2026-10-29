FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar application.jar
COPY healthcheck.sh /app/healthcheck.sh
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
