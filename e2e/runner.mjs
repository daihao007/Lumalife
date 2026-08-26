import { mkdir, writeFile } from "node:fs/promises";
import { execFile, spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import path from "node:path";

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const baseUrl = (process.env.E2E_BASE_URL || "http://localhost:8080").replace(/\/$/, "");
const timeoutMs = Number(process.env.E2E_TIMEOUT_MS || 30000);
const reportDir = path.resolve(rootDir, process.env.E2E_REPORT_DIR || "e2e/reports");
const startBackend = process.env.E2E_START_BACKEND !== "0";
const results = [];
const execFileAsync = promisify(execFile);
let backendProcess;
let sequence = 0;
let environmentFailure;

class E2EFailure extends Error {
  constructor(message, details = {}) {
    super(message);
    this.name = "E2EFailure";
    this.details = details;
  }
}

function assert(condition, message, details = {}) {
  if (!condition) throw new E2EFailure(message, details);
}

async function request(method, endpoint, token, body, requestTimeoutMs = timeoutMs) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), requestTimeoutMs);
  try {
    const response = await fetch(`${baseUrl}${endpoint}`, {
      method,
      signal: controller.signal,
      headers: {
        Accept: "application/json",
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    const text = await response.text();
    let json;
    try {
      json = text ? JSON.parse(text) : {};
    } catch {
      json = { raw: text };
    }
    return { status: response.status, body: json };
  } finally {
    clearTimeout(timer);
  }
}

function expectResponse(actual, status, message) {
  assert(actual.status === status, message, {
    expectedStatus: status,
    actualStatus: actual.status,
    response: actual.body
  });
  return actual.body;
}

function expectCode(actual, status, code, message) {
  const body = expectResponse(actual, status, message);
  assert(body.code === code, `${message}: unexpected API code`, {
    expectedCode: code,
    actualCode: body.code,
    response: body
  });
  return body;
}

async function login(phone, password) {
  const response = await request("POST", "/api/v1/auth/login", undefined, { phone, password });
  const body = expectResponse(response, 200, `login failed for ${phone}`);
  assert(body.data?.token, `login returned no token for ${phone}`, { response: body });
  return body.data.token;
}

async function registerUser(label) {
  const phone = `e2e-${Date.now()}-${sequence++}`;
  const response = await request("POST", "/api/v1/auth/register", undefined, {
    phone,
    password: "abc123456",
    nickname: `E2E ${label}`
  });
  const body = expectResponse(response, 200, `register failed for ${label}`);
  assert(body.data?.token, `registration returned no token for ${label}`, { response: body });
  return body.data.token;
}

async function createAddress(token, detail) {
  const response = await request("POST", "/api/v1/user/addresses", token, {
    contactName: "E2E 用户",
    phone: "13900000001",
    detail,
    defaultAddress: true
  });
  const body = expectResponse(response, 200, "address creation failed");
  assert(body.data?.id, "address creation returned no id", { response: body });
  return body.data.id;
}

async function createAndPayGroupOrder(token, clientRequestId) {
  const created = expectResponse(
    await request("POST", "/api/v1/orders/group-buy", token, { dealId: 1, quantity: 1 }),
    200,
    "group-buy order creation failed"
  );
  const orderId = created.data?.id;
  assert(orderId, "group-buy order returned no id", { response: created });
  return expectResponse(
    await request("POST", "/api/v1/payments", token, { orderId, clientRequestId }),
    200,
    "group-buy payment failed"
  ).data;
}

async function createCompletedDeliveryOrder(token, merchantToken, addressId, productId, requestId) {
  expectResponse(
    await request("POST", "/api/v1/cart/items", token, { productId, quantity: 1 }),
    200,
    "cart add failed"
  );
  const created = expectResponse(
    await request("POST", "/api/v1/orders/delivery", token, { addressId }),
    200,
    "delivery order creation failed"
  );
  const orderId = created.data?.[0]?.id;
  assert(orderId, "delivery order returned no id", { response: created });
  expectResponse(
    await request("POST", "/api/v1/payments", token, { orderId, clientRequestId: requestId }),
    200,
    "delivery payment failed"
  );
  for (const next of ["ACCEPTED", "DELIVERING", "COMPLETED"]) {
    expectResponse(
      await request("POST", `/api/v1/merchant-admin/orders/${orderId}/transition`, merchantToken, { next }),
      200,
      `merchant transition to ${next} failed`
    );
  }
  expectResponse(
    await request("POST", `/api/v1/orders/${orderId}/receive`, token),
    200,
    "order receive failed"
  );
  return orderId;
}

async function cr04GroupBuyAndCouponLifecycle() {
  const userToken = await registerUser("CR04");
  const ownerToken = await login("13800000002", "abc123456");
  const otherMerchantToken = await login("13800000003", "abc123456");

  expectCode(
    await request("POST", "/api/v1/orders/group-buy", userToken, { dealId: 1, quantity: 0 }),
    409,
    40900,
    "zero-quantity group-buy order must be rejected"
  );
  const paid = await createAndPayGroupOrder(userToken, `e2e-cr04-${Date.now()}`);
  assert(/^\d{12}$/.test(paid.couponCode || ""), "paid group order must generate a 12-digit coupon", { response: paid });

  expectCode(
    await request("POST", "/api/v1/merchant-admin/coupons/verify", otherMerchantToken, { code: paid.couponCode }),
    403,
    40300,
    "other merchant must not verify the coupon"
  );
  const verified = expectResponse(
    await request("POST", "/api/v1/merchant-admin/coupons/verify", ownerToken, { code: paid.couponCode }),
    200,
    "own merchant coupon verification failed"
  );
  assert(verified.data?.status === "USED", "verified coupon must become USED", { response: verified });
  expectCode(
    await request("POST", "/api/v1/merchant-admin/coupons/verify", ownerToken, { code: paid.couponCode }),
    409,
    40900,
    "coupon must not be verified twice"
  );
  expectCode(
    await request("POST", "/api/v1/merchant-admin/coupons/verify", ownerToken, { code: "000000000000" }),
    404,
    40400,
    "unknown coupon must return not found"
  );
}

async function cr05ReviewLifecycle() {
  const userToken = await registerUser("CR05");
  const merchantToken = await login("13800000002", "abc123456");
  const addressId = await createAddress(userToken, "E2E CR05 评价路 1 号");
  const orderId = await createCompletedDeliveryOrder(userToken, merchantToken, addressId, 1001, `e2e-cr05-${Date.now()}`);

  expectCode(
    await request("POST", "/api/v1/reviews", userToken, {
      orderId,
      score: 6,
      tasteScore: 5,
      serviceScore: 5,
      content: "非法评分"
    }),
    400,
    40000,
    "review scores outside 1-5 must be rejected"
  );
  const review = expectResponse(
    await request("POST", "/api/v1/reviews", userToken, {
      orderId,
      score: 5,
      tasteScore: 5,
      serviceScore: 4,
      content: "真实 HTTP E2E 评价"
    }),
    200,
    "review creation failed"
  );
  assert(review.data?.orderId === orderId, "review must point to the completed order", { response: review });
  expectCode(
    await request("POST", "/api/v1/reviews", userToken, {
      orderId,
      score: 5,
      tasteScore: 5,
      serviceScore: 5,
      content: "重复评价"
    }),
    409,
    40900,
    "one order must not receive two reviews"
  );

  const merchantReviews = expectResponse(
    await request("GET", "/api/v1/merchant-admin/reviews", merchantToken),
    200,
    "merchant review listing failed"
  );
  assert(merchantReviews.data?.some((item) => item.orderId === orderId), "merchant must see its own review", { response: merchantReviews });
}

async function cr06MerchantFulfillmentAndBoundaries() {
  const userToken = await registerUser("CR06");
  const ownerToken = await login("13800000002", "abc123456");
  const otherMerchantToken = await login("13800000003", "abc123456");
  const platformToken = await login("13800000000", "admin123456");
  const addressId = await createAddress(userToken, "E2E CR06 工作台路 2 号");

  expectResponse(
    await request("GET", "/api/v1/merchant-admin/orders", userToken),
    403,
    "normal user must not access merchant orders"
  );
  const orderId = await (async () => {
    expectResponse(await request("POST", "/api/v1/cart/items", userToken, { productId: 1001, quantity: 1 }), 200, "CR06 cart add failed");
    const created = expectResponse(await request("POST", "/api/v1/orders/delivery", userToken, { addressId }), 200, "CR06 order creation failed");
    const id = created.data?.[0]?.id;
    assert(id, "CR06 order returned no id", { response: created });
    expectResponse(await request("POST", "/api/v1/payments", userToken, { orderId: id, clientRequestId: `e2e-cr06-${Date.now()}` }), 200, "CR06 payment failed");
    return id;
  })();

  expectCode(
    await request("POST", `/api/v1/merchant-admin/orders/${orderId}/transition`, otherMerchantToken, { next: "ACCEPTED" }),
    403,
    40300,
    "other merchant must not transition the order"
  );
  for (const next of ["ACCEPTED", "DELIVERING", "COMPLETED"]) {
    expectResponse(await request("POST", `/api/v1/merchant-admin/orders/${orderId}/transition`, ownerToken, { next }), 200, `CR06 transition to ${next} failed`);
  }
  expectCode(
    await request("POST", `/api/v1/merchant-admin/orders/${orderId}/transition`, ownerToken, { next: "ACCEPTED" }),
    409,
    40900,
    "completed order must reject an invalid transition"
  );

  expectCode(
    await request("POST", "/api/v1/merchant-admin/products", ownerToken, { name: "invalid", description: "invalid", priceCent: 0, stock: 1, listed: true }),
    400,
    40000,
    "invalid product price must be rejected"
  );
  expectCode(
    await request("POST", "/api/v1/merchant-admin/group-deals", ownerToken, { title: "invalid", description: "invalid", priceCent: 4990, stock: -1, active: true }),
    400,
    40000,
    "negative group-deal stock must be rejected"
  );
  const metrics = expectResponse(await request("GET", "/api/v1/admin/metrics", platformToken), 200, "platform metrics failed");
  assert(metrics.data?.health?.status === "UP", "platform metrics must report UP health", { response: metrics });
}

async function waitForHealth(maxWaitMs) {
  const deadline = Date.now() + maxWaitMs;
  while (Date.now() < deadline) {
    try {
      const response = await request("GET", "/actuator/health", undefined, undefined, Math.min(1000, maxWaitMs));
      if (response.status === 200) return true;
    } catch {
      // The server may still be starting.
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  return false;
}

function startBackendProcess() {
  const windows = process.platform === "win32";
  const command = windows ? "cmd.exe" : "mvn";
  const args = windows
    ? ["/d", "/s", "/c", "mvn.cmd -q spring-boot:run -Dspring-boot.run.arguments=--lumalife.state-file="]
    : ["-q", "spring-boot:run", "-Dspring-boot.run.arguments=--lumalife.state-file="];
  backendProcess = spawn(command, args, {
    cwd: path.join(rootDir, "backend"),
    stdio: ["ignore", "pipe", "pipe"],
    windowsHide: true
  });
  const chunks = [];
  backendProcess.stdout.on("data", (chunk) => chunks.push(chunk.toString()));
  backendProcess.stderr.on("data", (chunk) => chunks.push(chunk.toString()));
  backendProcess.on("error", (error) => chunks.push(`${error.name}: ${error.message}\n`));
  backendProcess.__logs = chunks;
}

async function stopBackendProcess() {
  if (!backendProcess) return;
  if (backendProcess.killed) return;
  if (process.platform === "win32") {
    try {
      await execFileAsync("taskkill", ["/pid", String(backendProcess.pid), "/t", "/f"]);
    } catch {
      // The process may already have exited during a failed startup.
    }
  } else {
    backendProcess.kill("SIGTERM");
  }
  await new Promise((resolve) => setTimeout(resolve, 500));
}

async function runScenario(name, scenario) {
  const startedAt = new Date().toISOString();
  const start = Date.now();
  try {
    await scenario();
    results.push({ name, status: "passed", startedAt, durationMs: Date.now() - start });
  } catch (error) {
    results.push({
      name,
      status: "failed",
      startedAt,
      durationMs: Date.now() - start,
      error: {
        message: error.message,
        details: error.details || {}
      }
    });
  }
}

function xmlEscape(value) {
  return String(value).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;");
}

async function writeReports() {
  await mkdir(reportDir, { recursive: true });
  const completedAt = new Date().toISOString();
  const environment = environmentFailure
    ? {
        status: "failed",
        error: environmentFailure
      }
    : { status: "passed" };
  const report = {
    suite: "LumaLife CR-04~CR-06 API E2E",
    baseUrl,
    completedAt,
    environment,
    total: results.length,
    passed: results.filter((item) => item.status === "passed").length,
    failed: results.filter((item) => item.status === "failed").length,
    scenarios: results
  };
  await writeFile(path.join(reportDir, "e2e-report.json"), `${JSON.stringify(report, null, 2)}\n`);
  const cases = results.map((item) => {
    if (item.status === "passed") return `    <testcase name="${xmlEscape(item.name)}" time="${(item.durationMs / 1000).toFixed(3)}" />`;
    return `    <testcase name="${xmlEscape(item.name)}" time="${(item.durationMs / 1000).toFixed(3)}"><failure message="${xmlEscape(item.error.message)}">${xmlEscape(JSON.stringify(item.error.details))}</failure></testcase>`;
  }).join("\n");
  const environmentError = environmentFailure
    ? `    <error message="${xmlEscape(environmentFailure.message)}">${xmlEscape(JSON.stringify(environmentFailure.details || {}))}</error>`
    : "";
  await writeFile(path.join(reportDir, "e2e-report.xml"), [
    `<?xml version="1.0" encoding="UTF-8"?>`,
    `<testsuite name="${xmlEscape(report.suite)}" tests="${report.total}" failures="${report.failed}" errors="${environmentFailure ? 1 : 0}">`,
    environmentError,
    cases,
    "</testsuite>",
    ""
  ].join("\n"));
  const lines = [
    `# ${report.suite}`,
    "",
    `- 被测地址：\`${baseUrl}\``,
    `- 完成时间：\`${completedAt}\``,
    `- 环境启动：**${environment.status === "passed" ? "通过" : "失败"}**`,
    `- 结果：${environmentFailure ? "**环境启动失败，未执行业务场景**" : `**${report.passed}/${report.total} 通过**，失败 ${report.failed} 项。`}`,
    "",
    "## 场景",
    "",
    "| 场景 | 结果 | 耗时 |",
    "| --- | --- | ---: |",
    ...results.map((item) => `| ${item.name} | ${item.status === "passed" ? "通过" : "失败"} | ${item.durationMs} ms |`),
    "",
    ...(environmentFailure ? ["## 启动诊断", "", `- ${environmentFailure.message}`, `- 详情：\`${JSON.stringify(environmentFailure.details || {})}\``, ""] : []),
    "失败详情见 `e2e-report.json` 和 `e2e-report.xml`。",
    ""
  ];
  await writeFile(path.join(reportDir, "e2e-summary.md"), lines.join("\n"));
  return report;
}

async function main() {
  try {
    if (!startBackend) {
      assert(await waitForHealth(timeoutMs), "backend is not healthy and E2E_START_BACKEND=0");
    } else {
      startBackendProcess();
      assert(await waitForHealth(timeoutMs), "backend did not become healthy", {
        backendLogs: backendProcess?.__logs?.join("").slice(-4000)
      });
    }
  } catch (error) {
    environmentFailure = {
      message: error.message,
      details: error.details || {}
    };
  }

  if (!environmentFailure) {
    await runScenario("CR-04 团购购买、券码核销与异常边界", cr04GroupBuyAndCouponLifecycle);
    await runScenario("CR-05 完成订单评价、评分校验与重复评价限制", cr05ReviewLifecycle);
    await runScenario("CR-06 商家履约、商品/套餐校验与角色边界", cr06MerchantFulfillmentAndBoundaries);
  }
  const report = await writeReports();
  console.log(`E2E ${report.passed}/${report.total} passed; environment: ${report.environment.status}; report: ${path.relative(rootDir, reportDir)}`);
  if (environmentFailure || report.failed > 0) process.exitCode = 1;
}

try {
  await main();
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  await stopBackendProcess();
}
