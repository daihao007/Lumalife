# Merchant-service fault experiment

- Result: PASS
- Injection: merchant-service scaled from 1 to 0, then restored
- Public merchant request: before=200, during=503, after=200
- Backend readiness: before=200, during=200, after=200
- HPA: temporarily removed during injection and restored from hpa-before.yaml
- Raw evidence: 04_tests/cloud-native/fault

The expected course-project behavior is a non-200, explicit service-boundary
failure during the outage while backend readiness remains healthy and the
request recovers after the merchant deployment is restored. No data volume or
application source was deleted.
