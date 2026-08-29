FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
RUN mvn -q -pl identity-service -am -DskipTests package
FROM eclipse-temurin:17-jre
COPY --from=build /src/identity-service/target/identity-service-*.jar /app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app.jar"]
