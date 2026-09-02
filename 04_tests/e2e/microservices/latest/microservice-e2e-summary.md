# Microservice E2E Summary

- Run ID: `20260902T090344Z-k3s-8c335eb`
- Git commit: `8c335eb7d79400c1f56630bd5c6530ac86e25cf2`
- Backend mode: `prod,remote`
- Started: 2026-09-02T09:03:44.355Z
- Completed: 2026-09-02T09:04:18.858Z

## Environment

- Base URL: `http://127.0.0.1:18080`
- Service DBs: life_assistant_identity, life_assistant_merchant, life_assistant_order
- Migration routes: identity=remote-service, merchant=remote-service, order=remote-service

## UC01–UC09

| UC | Status | Duration |
|---|---|---:|
| UC01 | PASSED | 886 ms |
| UC02 | PASSED | 576 ms |
| UC03 | PASSED | 10365 ms |
| UC04 | PASSED | 10325 ms |
| UC05 | PASSED | 10059 ms |
| UC06 | PASSED | 420 ms |
| UC07 | PASSED | 468 ms |
| UC08 | PASSED | 851 ms |
| UC09 | PASSED | 426 ms |

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
    "durationMs": 61
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
    "durationMs": 10
  },
  "order": {
    "url": "http://127.0.0.1:18083",
    "status": 200,
    "body": {
      "type": "object"
    },
    "durationMs": 9
  },
  "assistant": {
    "url": "http://127.0.0.1:18084",
    "status": 200,
    "body": {
      "type": "object"
    },
    "durationMs": 9
  }
}
```

Result: READY FOR CLOUD-NATIVE EXPERIMENTS

