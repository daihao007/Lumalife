# Microservice E2E Summary

- Run ID: `20260901T181647Z-85603`
- Git commit: `cfde357583100763eb73ee4ee9e4faf171597c27`
- Backend mode: `prod,remote`
- Started: 2026-09-01T18:17:37.992Z
- Completed: 2026-09-01T18:18:08.961Z

## Environment

- Base URL: `http://127.0.0.1:18080`
- Service DBs: life_assistant_identity, life_assistant_merchant, life_assistant_order
- Migration routes: identity=remote-service, merchant=remote-service, order=remote-service

## UC01–UC09

| UC | Status | Duration |
|---|---|---:|
| UC01 | PASSED | 584 ms |
| UC02 | PASSED | 364 ms |
| UC03 | PASSED | 8656 ms |
| UC04 | PASSED | 10391 ms |
| UC05 | PASSED | 9631 ms |
| UC06 | PASSED | 206 ms |
| UC07 | PASSED | 269 ms |
| UC08 | PASSED | 444 ms |
| UC09 | PASSED | 247 ms |

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
    "durationMs": 64
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
    "durationMs": 9
  },
  "order": {
    "url": "http://127.0.0.1:18083",
    "status": 200,
    "body": {
      "type": "object"
    },
    "durationMs": 12
  },
  "assistant": {
    "url": "http://127.0.0.1:18084",
    "status": 200,
    "body": {
      "type": "object"
    },
    "durationMs": 12
  }
}
```

Result: READY FOR CLOUD-NATIVE EXPERIMENTS

