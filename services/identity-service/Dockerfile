FROM --platform=$BUILDPLATFORM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src

COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl identity-service -am -DskipTests package

FROM eclipse-temurin:17-jre
ARG SERVICE_VERSION=dev
ARG GIT_COMMIT=unknown
LABEL org.opencontainers.image.title="LumaLife Identity Service" \
      org.opencontainers.image.version="${SERVICE_VERSION}" \
      org.opencontainers.image.revision="${GIT_COMMIT}" \
      org.opencontainers.image.source="https://github.com/daihao007/Lumalife"
WORKDIR /app
RUN useradd --system --uid 10001 --create-home lumalife \
    && install -d --owner=10001 --group=0 /app/data
COPY --from=build /src/identity-service/target/identity-service-*.jar app.jar
USER 10001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
