# HPA experiment summary

- Status: **BLOCKED**
- Target: merchant-service
- Namespace: lumalife
- Blocked before load generation: Kubernetes context, metrics.k8s.io, target Deployment, or target HPA preflight failed; see 04_tests/cloud-native/hpa-observation-20260902-blocked-kubectl.log.
- Raw CSV: 04_tests/cloud-native/hpa-observation-20260902-blocked.csv
- Raw request log: 04_tests/cloud-native/hpa-observation-20260902-blocked-load.log
- Raw kubectl transcript: 04_tests/cloud-native/hpa-observation-20260902-blocked-kubectl.log
- Raw events transcript: 04_tests/cloud-native/hpa-observation-20260902-blocked-events.log
- Raw merchant-service log transcript: 04_tests/cloud-native/hpa-observation-20260902-blocked-service.log

No load was started and no replica transition was observed in this run. This
run must not be reported as an HPA PASS. A PASS requires metrics.k8s.io to be
available and the raw CSV to show HPA current/desired and merchant-service
Ready replicas moving from 1 to at least 2 under load and returning to 1 after
cooldown.
