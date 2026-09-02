# Microservice E2E Summary

- Run ID: `20260902T102500Z-p1-retry`
- Git commit: `4f22cc73c1edd5c52dbc7b75e2558fbf508a4cd2`
- Backend mode: `prod,remote`
- Started: 2026-09-02T02:21:30.133Z
- Completed: 2026-09-02T02:22:02.769Z

## Environment

- Base URL: `http://127.0.0.1:18080`
- Service DBs: life_assistant_identity, life_assistant_merchant, life_assistant_order
- Migration routes: identity=remote-service, merchant=remote-service, order=remote-service

## UC01–UC09

| UC | Status | Duration |
|---|---|---:|
| UC01 | PASSED | 824 ms |
| UC02 | PASSED | 377 ms |
| UC03 | PASSED | 8931 ms |
| UC04 | PASSED | 11164 ms |
| UC05 | PASSED | 9777 ms |
| UC06 | PASSED | 226 ms |
| UC07 | PASSED | 337 ms |
| UC08 | PASSED | 503 ms |
| UC09 | PASSED | 268 ms |

- Total: 9
- Passed: 9
- Failed: 0

## Health

```json
{
  "backend": {
    "url": "http://127.0.0.1:18080",
    "status": 200,
    "body": {
      "type": "object"
    },
    "durationMs": 55
  },
  "identity": {
    "url": "http://127.0.0.1:18081",
    "status": 200,
    "body": {
      "type": "object"
    },
    "durationMs": 7
  },
  "merchant": {
    "url": "http://127.0.0.1:18082",
    "status": 200,
    "body": {
      "type": "object"
    },
    "durationMs": 8
  },
  "order": {
    "url": "http://127.0.0.1:18083",
    "status": 200,
    "body": {
      "type": "object"
    },
    "durationMs": 10
  },
  "assistant": {
    "url": "http://127.0.0.1:18084",
    "status": 200,
    "body": {
      "type": "object"
    },
    "durationMs": 11
  }
}
```

Result: READY FOR CLOUD-NATIVE EXPERIMENTS

