FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY order-service/pom.xml order-service/pom.xml
COPY order-service/src order-service/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -f order-service/pom.xml -DskipTests package

FROM eclipse-temurin:17-jre
ARG SERVICE_VERSION=0.1.0
LABEL org.opencontainers.image.title="LumaLife Order Service" \
      org.opencontainers.image.version="${SERVICE_VERSION}"
WORKDIR /app
RUN useradd --system --uid 10001 --create-home lumalife
COPY --from=build /workspace/order-service/target/order-service-*.jar app.jar
USER 10001
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
