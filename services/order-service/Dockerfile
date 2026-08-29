FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
RUN mvn -q -pl order-service -am -DskipTests package
FROM eclipse-temurin:17-jre
COPY --from=build /src/order-service/target/order-service-*.jar /app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","/app.jar"]
