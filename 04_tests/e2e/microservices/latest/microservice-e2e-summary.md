# Microservice E2E Summary

- Run ID: `20260902T101500Z-review-fixes`
- Git commit: `c8e45db5a7d33064966a2b3488fd0b66b404255c`
- Backend mode: `prod,remote`
- Started: 2026-09-02T02:11:42.280Z
- Completed: 2026-09-02T02:12:22.692Z

## Environment

- Base URL: `http://127.0.0.1:18080`
- Service DBs: life_assistant_identity, life_assistant_merchant, life_assistant_order
- Migration routes: identity=remote-service, merchant=remote-service, order=remote-service

## UC01–UC09

| UC | Status | Duration |
|---|---|---:|
| UC01 | PASSED | 618 ms |
| UC02 | PASSED | 404 ms |
| UC03 | PASSED | 17576 ms |
| UC04 | PASSED | 10273 ms |
| UC05 | PASSED | 9911 ms |
| UC06 | PASSED | 232 ms |
| UC07 | PASSED | 294 ms |
| UC08 | PASSED | 457 ms |
| UC09 | PASSED | 458 ms |

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
    "durationMs": 53
  },
  "identity": {
    "url": "http://127.0.0.1:18081",
    "status": 200,
    "body": {
      "type": "object"
    },
    "durationMs": 8
  },
  "merchant": {
    "url": "http://127.0.0.1:18082",
    "status": 200,
    "body": {
      "type": "object"
    },
    "durationMs": 11
  },
  "order": {
    "url": "http://127.0.0.1:18083",
    "status": 200,
    "body": {
      "type": "object"
    },
    "durationMs": 8
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

