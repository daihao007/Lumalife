# HPA experiment summary

- Status: **PASS**
- Git commit: 8c335eb7d79400c1f56630bd5c6530ac86e25cf2
- Target image: ghcr.io/daihao007/lumalife-merchant-service:sha-8c335eb
- Target: merchant-service
- Namespace: lumalife
- Load: 20 workers for 120 seconds
- HPA scaling active condition: True
- HPA able-to-scale condition: True
- Scaling message: the HPA was able to successfully calculate a replica count from cpu resource utilization (percentage of request)
- Requests: 14467
- Errors: 0
- Error rate: 0.00%
- Scale-up observed in raw CSV: true
- Scale-down observed in raw CSV: true
- Raw observations: 04_tests/cloud-native/hpa-observation-20260902-8c335eb.csv
- Raw request log: 04_tests/cloud-native/hpa-observation-20260902-8c335eb-load.log
- Raw kubectl top log: 04_tests/cloud-native/hpa-observation-20260902-8c335eb-top.log
- Raw kubectl transcript: 04_tests/cloud-native/hpa-observation-20260902-8c335eb-kubectl.log
- Raw events transcript: 04_tests/cloud-native/hpa-observation-20260902-8c335eb-events.log
- Raw merchant-service log transcript: 04_tests/cloud-native/hpa-observation-20260902-8c335eb-service.log

The experiment is considered complete only when Kubernetes resource metrics are
available, the observed replica transition is supported by the raw CSV, and the
load generated at least one successful HTTP response. The script emits PASS
only when metrics.k8s.io is active, both HPA and ready replica scale-up and
scale-down transitions are observed, and every recorded request returned HTTP
200; otherwise it emits BLOCKED.
