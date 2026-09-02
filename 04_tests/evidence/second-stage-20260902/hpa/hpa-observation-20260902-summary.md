# HPA experiment summary

- Status: **PASS**
- Target: merchant-service
- Namespace: lumalife
- Load: 20 workers for 180 seconds
- HPA scaling active condition: True
- HPA able-to-scale condition: True
- Scaling message: the HPA was able to successfully calculate a replica count from cpu resource utilization (percentage of request)
- Scale-up observed in raw CSV: true
- Scale-down observed in raw CSV: true
- Raw observations: /root/hpa-observation-20260902.csv
- Raw request log: /root/hpa-observation-20260902-load.log
- Raw kubectl top log: /root/hpa-observation-20260902-top.log
- Raw kubectl transcript: /root/hpa-observation-20260902-kubectl.log
- Raw events transcript: /root/hpa-observation-20260902-events.log
- Raw merchant-service log transcript: /root/hpa-observation-20260902-service.log

The experiment is considered complete only when Kubernetes resource metrics are
available and the observed replica transition is supported by the raw CSV.
The script emits PASS only when metrics.k8s.io is active and both HPA and ready
replica scale-up and scale-down transitions are observed; otherwise it emits
BLOCKED.
