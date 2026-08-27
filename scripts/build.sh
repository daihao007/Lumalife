#!/usr/bin/env sh
set -e
(cd backend && mvn test package)
(mvn -B -ntp -f services/pom.xml verify)
(cd frontend && npm ci && npm run build)
