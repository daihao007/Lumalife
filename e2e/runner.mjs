import { mkdir, writeFile } from "node:fs/promises";
import { execFile, spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import path from "node:path";

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const backendPort = Number(process.env.E2E_BACKEND_PORT || 18080);
const baseUrl = (process.env.E2E_BASE_URL || `http://localhost:${backendPort}`).replace(/\/$/, "");
const timeoutMs = Number(process.env.E2E_TIMEOUT_MS || 30000);
const reportDir = path.resolve(rootDir, process.env.E2E_REPORT_DIR || "e2e/reports");
const startBackend = process.env.E2E_START_BACKEND !== "0";
const results = [];
const requestLog = [];
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
  const startedAt = Date.now();
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
    const result = { status: response.status, body: json };
    requestLog.push({ method, endpoint, status: response.status, durationMs: Date.now() - startedAt, response: json });
    return result;
  } catch (error) {
    requestLog.push({ method, endpoint, durationMs: Date.now() - startedAt, error: error.message });
    throw error;
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

function pageRecords(body, message) {
  assert(Array.isArray(body.data?.records), `${message} returned no page records`, { response: body });
  return body.data.records;
}

async function cr01IdentityProfileAndAddress() {
  const userToken = await registerUser("CR01");
  const merchantToken = await login("13800000002", "abc123456");

  const me = expectResponse(await request("GET", "/api/v1/auth/me", userToken), 200, "current user lookup failed");
  assert(me.data?.role === "USER", "registered account must have USER role", { response: me });

  const profile = expectResponse(
    await request("POST", "/api/v1/user/profile", userToken, { nickname: "E2E CR01 用户", avatarUrl: "https://example.com/e2e-avatar.png" }),
    200,
    "profile update failed"
  );
  assert(profile.data?.nickname === "E2E CR01 用户" && profile.data?.avatarUrl === "https://example.com/e2e-avatar.png", "profile update did not persist", { response: profile });

  const addressId = await createAddress(userToken, "E2E CR01 地址路 1 号");
  const addresses = expectResponse(await request("GET", "/api/v1/user/addresses", userToken), 200, "address listing failed");
  assert(addresses.data?.some((address) => address.id === addressId), "new address must be listed", { response: addresses });
  const defaulted = expectResponse(
    await request("POST", `/api/v1/user/addresses/${addressId}/default`, userToken),
    200,
    "default address update failed"
  );
  assert(defaulted.data?.id === addressId && defaulted.data?.defaultAddress === true, "selected address must become default", { response: defaulted });

  expectResponse(await request("GET", "/api/v1/user/addresses", merchantToken), 403, "merchant must not access user addresses");
  expectResponse(await request("GET", "/api/v1/cart"), 401, "anonymous cart access must be rejected");
}

async function cr02DiscoveryAndMerchantDetail() {
  const userToken = await registerUser("CR02");
  const categories = expectResponse(await request("GET", "/api/v1/categories"), 200, "category listing failed");
  assert(categories.data?.some((category) => category.name === "咖啡茶饮"), "seed categories must include coffee", { response: categories });

  const coffee = expectResponse(
    await request("GET", "/api/v1/merchants?keyword=%E5%92%96%E5%95%A1&sort=distanceAsc&minScore=4.5"),
    200,
    "merchant keyword search failed"
  );
  const coffeeRecords = pageRecords(coffee, "coffee search");
  assert(coffeeRecords.length === 1 && coffeeRecords[0].id === 2, "coffee search must return the coffee merchant", { response: coffee });

  const recommended = expectResponse(await request("GET", "/api/v1/merchants?sort=recommend", userToken), 200, "personalized recommendation failed");
  const recommendedRecords = pageRecords(recommended, "recommendation");
  assert(recommendedRecords.length > 0 && typeof recommendedRecords[0].reason === "string", "recommendations must include explainable reasons", { response: recommended });

  const detail = expectResponse(await request("GET", "/api/v1/merchants/2"), 200, "merchant detail failed");
  assert(detail.data?.merchant?.id === 2 && detail.data?.products?.length > 0 && detail.data?.groupDeals?.length > 0, "merchant detail must include products and group deals", { response: detail });
  expectCode(await request("GET", "/api/v1/merchants/999"), 404, 40400, "unknown merchant must return not found");
}

async function cr03CartOrderPaymentAndTracking() {
  const userToken = await registerUser("CR03");
  const merchantToken = await login("13800000002", "abc123456");
  const addressId = await createAddress(userToken, "E2E CR03 订单路 1 号");

  const cart = expectResponse(await request("POST", "/api/v1/cart/items", userToken, { productId: 1001, quantity: 2 }), 200, "cart add failed");
  assert(cart.data?.some((item) => item.productId === 1001 && item.quantity === 2), "cart must contain the requested quantity", { response: cart });
  const cartDetail = expectResponse(await request("GET", "/api/v1/cart/detail", userToken), 200, "cart detail failed");
  assert(cartDetail.data?.some((item) => item.productId === 1001 && item.subtotalCent === item.priceCent * 2), "cart detail must calculate subtotal", { response: cartDetail });
  const updatedCart = expectResponse(await request("POST", "/api/v1/cart/items/1001", userToken, { quantity: 1 }), 200, "cart quantity update failed");
  assert(updatedCart.data?.some((item) => item.productId === 1001 && item.quantity === 1), "cart quantity must be updateable", { response: updatedCart });

  const created = expectResponse(await request("POST", "/api/v1/orders/delivery", userToken, { addressId }), 200, "delivery order creation failed");
  const orderId = created.data?.[0]?.id;
  assert(orderId && created.data?.[0]?.status === "PENDING_PAYMENT", "delivery order must start pending payment", { response: created });
  const clientRequestId = `e2e-cr03-${Date.now()}`;
  const paid = expectResponse(await request("POST", "/api/v1/payments", userToken, { orderId, clientRequestId }), 200, "payment failed");
  assert(paid.data?.status === "PAID" && paid.data?.statusTimeline?.PAID, "payment must move order to PAID with timeline", { response: paid });
  const repeatedPayment = expectResponse(await request("POST", "/api/v1/payments", userToken, { orderId, clientRequestId }), 200, "idempotent payment retry failed");
  assert(repeatedPayment.data?.id === orderId && repeatedPayment.data?.status === "PAID", "payment retry must be idempotent", { response: repeatedPayment });
  expectCode(await request("POST", `/api/v1/orders/${orderId}/cancel`, userToken), 409, 40900, "paid order must not be cancelled");

  const orders = expectResponse(await request("GET", "/api/v1/orders", userToken), 200, "user order listing failed");
  assert(orders.data?.some((order) => order.id === orderId && order.status === "PAID"), "paid order must appear in user order list", { response: orders });
  expectResponse(await request("GET", "/api/v1/orders", merchantToken), 403, "merchant must not access user orders");

  await request("POST", "/api/v1/cart/items", userToken, { productId: 1002, quantity: 1 });
  const pending = expectResponse(await request("POST", "/api/v1/orders/delivery", userToken, { addressId }), 200, "second delivery order creation failed");
  const pendingId = pending.data?.[0]?.id;
  const cancelled = expectResponse(await request("POST", `/api/v1/orders/${pendingId}/cancel`, userToken), 200, "pending order cancellation failed");
  assert(cancelled.data?.status === "CANCELLED", "pending order must be cancellable", { response: cancelled });
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
    ? ["/d", "/s", "/c", "mvn.cmd -q spring-boot:run -Dspring-boot.run.arguments=--lumalife.state-file= -Dspring-boot.run.fork=false"]
    : ["-q", "spring-boot:run", "-Dspring-boot.run.arguments=--lumalife.state-file=", "-Dspring-boot.run.fork=false"];
  backendProcess = spawn(command, args, {
    cwd: path.join(rootDir, "backend"),
    env: { ...process.env, SERVER_PORT: String(backendPort) },
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
    const pids = new Set([backendProcess.pid]);
    try {
      const { stdout } = await execFileAsync("netstat", ["-ano"], { timeout: 5000, windowsHide: true });
      const listeningLine = stdout.split(/\r?\n/).find((line) => line.includes(`:${backendPort}`) && line.includes("LISTENING"));
      const pid = listeningLine?.trim().split(/\s+/).at(-1);
      if (pid && /^\d+$/.test(pid)) pids.add(Number(pid));
    } catch {
      // Port inspection is best effort; the process tree is still terminated below.
    }
    await Promise.all([...pids].map(async (pid) => {
      try {
        await execFileAsync("taskkill", ["/pid", String(pid), "/t", "/f"], { timeout: 10000, windowsHide: true });
      } catch {
        // The process may already have exited during a failed startup.
      }
    }));
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
    suite: "LumaLife CR-01~CR-06 API E2E",
    baseUrl,
    completedAt,
    environment,
    total: results.length,
    passed: results.filter((item) => item.status === "passed").length,
    failed: results.filter((item) => item.status === "failed").length,
    scenarios: results
  };
  await writeFile(path.join(reportDir, "e2e-report.json"), `${JSON.stringify(report, null, 2)}\n`);
  await writeFile(path.join(reportDir, "e2e-raw.log"), requestLog.map((item) => JSON.stringify(item)).join("\n") + "\n");
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
    await runScenario("CR-01 用户认证、资料与地址管理", cr01IdentityProfileAndAddress);
    await runScenario("CR-02 分类搜索、推荐与商家详情", cr02DiscoveryAndMerchantDetail);
    await runScenario("CR-03 购物车、下单、支付与订单跟踪", cr03CartOrderPaymentAndTracking);
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
  environmentFailure = environmentFailure || { message: error.message, details: error.details || {} };
  try {
    const report = await writeReports();
    console.error(`E2E environment: ${report.environment.status}; report: ${path.relative(rootDir, reportDir)}`);
  } catch (reportError) {
    console.error(`Could not write E2E diagnostics: ${reportError.message}`);
  }
  process.exitCode = 1;
} finally {
  await stopBackendProcess();
}
