FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
RUN mvn -q -pl merchant-service -am -DskipTests package
FROM eclipse-temurin:17-jre
COPY --from=build /src/merchant-service/target/merchant-service-*.jar /app.jar
EXPOSE 8082
ENTRYPOINT ["java","-jar","/app.jar"]
