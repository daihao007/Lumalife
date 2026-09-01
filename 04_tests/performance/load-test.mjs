import { mkdir, writeFile } from "node:fs/promises";
import { dirname } from "node:path";
import process from "node:process";
import { performance } from "node:perf_hooks";

const baseUrl = (process.env.PERF_BASE_URL || "http://127.0.0.1:8080").replace(/\/$/, "");
const endpoint = process.env.PERF_ENDPOINT || "/api/v1/merchants?keyword=%E5%92%96%E5%95%A1";
const label = process.env.PERF_LABEL || "run";
const repeats = positiveInt("PERF_REPEATS", 3);
const requestsPerRepeat = positiveInt("PERF_REQUESTS", 90);
const concurrency = positiveInt("PERF_CONCURRENCY", 12);
const warmupRequests = positiveInt("PERF_WARMUP_REQUESTS", 12);
const timeoutMs = positiveInt("PERF_TIMEOUT_MS", 3000);
const maxErrorRate = Number(process.env.PERF_MAX_ERROR_RATE || "0");
const outputPath = process.env.PERF_OUTPUT || `04_tests/performance/results/${label}.json`;
const csvPath = process.env.PERF_CSV || outputPath.replace(/\.json$/i, ".csv");

if (!Number.isFinite(maxErrorRate) || maxErrorRate < 0 || maxErrorRate > 1) {
  throw new Error("PERF_MAX_ERROR_RATE must be between 0 and 1");
}

const target = new URL(endpoint, `${baseUrl}/`).toString();

async function main() {
  const startedAt = new Date().toISOString();
  await waitForHealth();
  await runRequests(warmupRequests, "warmup");

  const measurements = [];
  for (let repeat = 1; repeat <= repeats; repeat += 1) {
    measurements.push(await runRequests(requestsPerRepeat, `repeat-${repeat}`));
  }

  const result = {
    label,
    startedAt,
    completedAt: new Date().toISOString(),
    target,
    method: "GET",
    workload: { repeats, requestsPerRepeat, concurrency, warmupRequests, timeoutMs },
    measurements,
    summary: summarize(measurements),
  };

  await mkdir(dirname(outputPath), { recursive: true });
  await mkdir(dirname(csvPath), { recursive: true });
  await writeFile(outputPath, `${JSON.stringify(result, null, 2)}\n`, "utf8");
  await writeFile(csvPath, toCsv(measurements), "utf8");
  console.log(JSON.stringify(result, null, 2));

  if (result.summary.errorRate > maxErrorRate) {
    throw new Error(`error rate ${result.summary.errorRate} exceeded ${maxErrorRate}`);
  }
}

async function waitForHealth() {
  const healthUrl = new URL("/actuator/health", `${baseUrl}/`).toString();
  const deadline = Date.now() + Math.max(timeoutMs * 10, 30000);
  let lastError = "unknown error";
  while (Date.now() < deadline) {
    try {
      const response = await fetch(healthUrl, { signal: AbortSignal.timeout(timeoutMs) });
      if (response.ok) return;
      lastError = `HTTP ${response.status}`;
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`target health check failed: ${lastError}`);
}

async function runRequests(total, phase) {
  const latencies = [];
  const errors = [];
  let next = 0;
  const started = performance.now();

  async function worker() {
    while (true) {
      const index = next;
      next += 1;
      if (index >= total) return;
      const requestStarted = performance.now();
      try {
        const response = await fetch(target, {
          headers: { Accept: "application/json" },
          signal: AbortSignal.timeout(timeoutMs),
        });
        latencies.push(performance.now() - requestStarted);
        if (!response.ok) errors.push(`HTTP ${response.status}`);
        else await response.arrayBuffer();
      } catch (error) {
        latencies.push(performance.now() - requestStarted);
        errors.push(error instanceof Error ? error.name : String(error));
      }
    }
  }

  await Promise.all(Array.from({ length: Math.min(concurrency, total) }, worker));
  const elapsedMs = performance.now() - started;
  const successful = total - errors.length;
  return {
    phase,
    requests: total,
    successful,
    failed: errors.length,
    errorRate: total === 0 ? 0 : errors.length / total,
    elapsedMs: round(elapsedMs),
    throughputRps: round(total / Math.max(elapsedMs / 1000, 0.001)),
    averageMs: round(average(latencies)),
    p95Ms: round(percentile(latencies, 0.95)),
    maxMs: round(Math.max(...latencies, 0)),
    errors: errors.slice(0, 10),
  };
}

function summarize(measurements) {
  const total = measurements.reduce((sum, item) => sum + item.requests, 0);
  const successful = measurements.reduce((sum, item) => sum + item.successful, 0);
  return {
    requests: total,
    successful,
    failed: total - successful,
    errorRate: total === 0 ? 0 : round((total - successful) / total, 6),
    averageMs: round(average(measurements.map((item) => item.averageMs))),
    p95Ms: round(percentile(measurements.map((item) => item.p95Ms), 0.95)),
    throughputRps: round(average(measurements.map((item) => item.throughputRps))),
  };
}

function toCsv(measurements) {
  const columns = ["phase", "requests", "successful", "failed", "errorRate", "elapsedMs", "throughputRps", "averageMs", "p95Ms", "maxMs"];
  return `${columns.join(",")}\n${measurements.map((item) => columns.map((column) => csv(item[column])).join(",")).join("\n")}\n`;
}

function csv(value) {
  return typeof value === "string" && value.includes(",") ? `"${value.replaceAll('"', '""')}"` : String(value ?? "");
}

function percentile(values, ratio) {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.min(sorted.length - 1, Math.max(0, Math.ceil(sorted.length * ratio) - 1))];
}

function average(values) {
  return values.length === 0 ? 0 : values.reduce((sum, value) => sum + value, 0) / values.length;
}

function positiveInt(name, fallback) {
  const value = Number(process.env[name] || fallback);
  if (!Number.isInteger(value) || value <= 0) throw new Error(`${name} must be a positive integer`);
  return value;
}

function round(value) {
  return Math.round(value * 100) / 100;
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
});
