# HPA experiment summary

## Latest final acceptance

- Status: **PASS**
- Date: 2026-09-02 (Asia/Shanghai)
- Target: `merchant-service` in namespace `lumalife`
- Cluster: K3s `v1.36.3+k3s1`, context `default`
- Metrics: `v1beta1.metrics.k8s.io` APIService `Available=True`; `kubectl top` returned node and Pod metrics
- Load: 20 workers for 180 seconds; cooldown 180 seconds; 10 second sampling
- Scale-up observed in raw CSV: `1/1 → 2/2 → 3/3` (HPA current/desired also reached `2/2` and `3/3`)
- Scale-down observed in raw CSV: `3/3 → 1/1` after cooldown (HPA current/desired `1/1`)
- Requests: 24,857; errors: 0; error rate: 0.00%
- Raw observations: `hpa-observation-20260902.csv`
- Raw request log: `hpa-observation-20260902-load.log`
- Raw kubectl top log: `hpa-observation-20260902-top.log`
- Raw kubectl transcript: `hpa-observation-20260902-kubectl.log`
- Raw events transcript: `hpa-observation-20260902-events.log`
- Raw merchant-service log transcript: `hpa-observation-20260902-service.log`

The PASS status is based on the raw observations and Kubernetes events. The
previous no-context `hpa-observation-20260902-blocked*` files remain preserved
as a separate failed preflight and are not used as evidence for this result.
