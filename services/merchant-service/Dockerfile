FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY . .
RUN mvn -q -pl merchant-service -am -DskipTests package
FROM eclipse-temurin:17-jre
ARG SERVICE_VERSION=dev
ARG GIT_COMMIT=unknown
LABEL org.opencontainers.image.title="LumaLife Merchant Service" \
      org.opencontainers.image.version="${SERVICE_VERSION}" \
      org.opencontainers.image.revision="${GIT_COMMIT}" \
      org.opencontainers.image.source="https://github.com/daihao007/Lumalife"
COPY --from=build /src/merchant-service/target/merchant-service-*.jar /app.jar
EXPOSE 8082
ENTRYPOINT ["java","-jar","/app.jar"]
