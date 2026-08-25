#!/usr/bin/env sh
set -e
(cd backend && mvn test package)
(cd frontend && npm ci && npm run build)
