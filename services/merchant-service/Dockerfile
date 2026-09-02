FROM --platform=$BUILDPLATFORM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src

COPY . .
RUN --mount=type=cache,id=lumalife-maven,sharing=locked,target=/root/.m2 \
    mvn -B -ntp -pl merchant-service -am -DskipTests package

FROM eclipse-temurin:17-jre
ARG SERVICE_VERSION=0.1.0
LABEL org.opencontainers.image.title="LumaLife Merchant Service" \
      org.opencontainers.image.version="${SERVICE_VERSION}"
WORKDIR /app
RUN useradd --system --uid 10001 --create-home lumalife
COPY --from=build /src/merchant-service/target/merchant-service-*.jar app.jar
USER 10001
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
