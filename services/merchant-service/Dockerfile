FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
RUN mvn -q -DskipTests package
FROM eclipse-temurin:17-jre
COPY --from=build /src/target/merchant-service-*.jar /app.jar
EXPOSE 8082
ENTRYPOINT ["java","-jar","/app.jar"]
