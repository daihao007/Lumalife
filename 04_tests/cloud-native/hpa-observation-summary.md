# HPA experiment summary

- Target: merchant-service
- Namespace: lumalife
- Load: 2 workers for 5 seconds
- HPA scaling active condition: False
- HPA able-to-scale condition: True
- Scaling message: the HPA was unable to compute the replica count: failed to get cpu utilization: unable to get metrics for resource cpu: unable to fetch metrics from resource metrics API: the server could not find the requested resource (get pods.metrics.k8s.io)
- Raw observations: 04_tests/cloud-native/hpa-observation.csv
- Raw request log: 04_tests/cloud-native/hpa-observation-load.log
- Raw kubectl top log: 04_tests/cloud-native/hpa-observation-top.log

The experiment is considered complete only when Kubernetes resource metrics are
available and the observed replica transition is supported by the raw CSV.
Missing metrics are retained as N/A and are a blocked cloud-native experiment,
not a successful scale result.
