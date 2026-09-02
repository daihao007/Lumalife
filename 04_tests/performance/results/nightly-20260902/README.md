# Nightly performance comparison

- Same host and Compose data volume: lumalife-main
- Same workload program: 04_tests/performance/load-test.mjs
- Modes: microservices (prod,remote) and monolith (explicit monolith compatibility profile)
- APIs: merchant search, categories, merchant detail
- Repeats per API/mode: 3; requests per repeat: 10; concurrency: 4
- CPU/memory: backend Docker stats samples in each *-resources.csv
- Summary: comparison-summary.csv
- Failed load invocations: 0

The numbers are raw measurements from this run. They are not a claim of
production capacity and should not be compared with a different machine,
data volume, endpoint set, or workload configuration.
