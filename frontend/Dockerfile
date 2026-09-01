FROM --platform=$BUILDPLATFORM node:22-alpine AS build
ARG SERVICE_VERSION=dev
ARG GIT_COMMIT=unknown
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build
RUN printf '{"service":"frontend","version":"%s","gitCommit":"%s"}\n' "$SERVICE_VERSION" "$GIT_COMMIT" > dist/version.json

FROM nginxinc/nginx-unprivileged:1.27-alpine
ARG SERVICE_VERSION=dev
ARG GIT_COMMIT=unknown
LABEL org.opencontainers.image.title="LumaLife Frontend" \
      org.opencontainers.image.version="${SERVICE_VERSION}" \
      org.opencontainers.image.revision="${GIT_COMMIT}" \
      org.opencontainers.image.source="https://github.com/daihao007/Lumalife"
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
