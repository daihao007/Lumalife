FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
RUN mvn -q -pl identity-service -am -DskipTests package
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 --create-home lumalife \
    && mkdir -p /app/data \
    && chown -R 10001:10001 /app
COPY --from=build /src/identity-service/target/identity-service-*.jar /app.jar
ENV LUMALIFE_IDENTITY_STATE_FILE=/app/data/identity-state.json
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app.jar"]
