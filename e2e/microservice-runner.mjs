import { mkdir, writeFile } from "node:fs/promises";
import { execFile } from "node:child_process";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import path from "node:path";

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const baseUrl = (process.env.MS_E2E_BASE_URL || "http://127.0.0.1:18080").replace(/\/$/, "");
const reportDir = path.resolve(rootDir, process.env.MS_E2E_REPORT_DIR || "04_tests/e2e/microservices/latest");
const timeoutMs = Number(process.env.MS_E2E_TIMEOUT_MS || 10000);
const sagaTimeoutMs = Number(process.env.MS_E2E_SAGA_TIMEOUT_MS || 60000);
const runId = process.env.MS_E2E_RUN_ID || `${new Date().toISOString().replace(/[-:.]/g, "")}-${process.pid}`;
const composeProject = process.env.MS_E2E_COMPOSE_PROJECT || "";
const kubernetesNamespace = process.env.MS_E2E_KUBERNETES_NAMESPACE || "";
const composeFiles = ["-f", path.join(rootDir, "docker-compose.yml"), "-f", path.join(rootDir, "docker-compose.e2e.yml")];
const results = [];
const requestLog = [];
const health = {};
const state = {};
let sequence = 0;
let environmentFailure = null;
const suiteStartedAt = new Date().toISOString();
const execFileAsync = promisify(execFile);

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

function bodySummary(body) {
  if (body === null || body === undefined) return { type: "empty" };
  if (typeof body !== "object") return { type: typeof body };
  const data = body.data;
  return {
    type: Array.isArray(body) ? "array" : "object",
    ...(typeof body.code === "number" ? { code: body.code } : {}),
    ...(typeof body.message === "string" ? { message: body.message } : {}),
    ...(Array.isArray(data) ? { dataType: "array", dataCount: data.length } : {}),
    ...(data && typeof data === "object" && !Array.isArray(data) ? { dataType: "object" } : {})
  };
}

function redact(value, key = "") {
  if (["authorization", "token", "password", "phone", "contactname", "address"].includes(key.toLowerCase())) return "[REDACTED]";
  if (key.toLowerCase() === "response") return bodySummary(value);
  if (Array.isArray(value)) return value.map((item) => redact(item));
  if (value && typeof value === "object") return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, redact(v, k)]));
  if (typeof value === "string" && value.length > 500) return `${value.slice(0, 500)}…`;
  return value;
}

async function request(method, endpoint, token, body, requestTimeoutMs = timeoutMs) {
  const started = Date.now();
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
    let parsed = {};
    try { parsed = text ? JSON.parse(text) : {}; } catch { parsed = { raw: text }; }
    requestLog.push({ type: "http", method, endpoint, status: response.status, durationMs: Date.now() - started, response: bodySummary(parsed) });
    return { status: response.status, body: parsed };
  } catch (error) {
    requestLog.push({ type: "http", method, endpoint, durationMs: Date.now() - started, error: error.message });
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

function expect(actual, status, message) {
  assert(actual.status === status, message, { expectedStatus: status, actualStatus: actual.status, response: bodySummary(actual.body) });
  return actual.body;
}

function expectError(actual, statuses, message) {
  assert(statuses.includes(actual.status), message, { expectedStatuses: statuses, actualStatus: actual.status, response: bodySummary(actual.body) });
  return actual.body;
}

function pageRecords(body, message) {
  assert(Array.isArray(body.data?.records), message, { response: body });
  return body.data.records;
}

async function gitCommit() {
  try {
    const { stdout } = await execFileAsync("git", ["rev-parse", "HEAD"], { cwd: rootDir, timeout: 5000 });
    return stdout.trim();
  } catch { return "unknown"; }
}

async function dbScalar(database, sql) {
  const mysqlScript = 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP --host=127.0.0.1 --user="$MYSQL_USER" --database="$1" --batch --skip-column-names --raw --execute="$2"';
  if (kubernetesNamespace) {
    const databasePods = new Map([
      [process.env.MYSQL_IDENTITY_DATABASE || "life_assistant_identity", "mysql-identity-0"],
      [process.env.MYSQL_MERCHANT_DATABASE || "life_assistant_merchant", "mysql-merchant-0"],
      [process.env.MYSQL_ORDER_DATABASE || "life_assistant_order", "mysql-order-0"]
    ]);
    const pod = databasePods.get(database);
    assert(pod, `Kubernetes 数据库 ${database} 没有对应的 StatefulSet Pod`);
    const args = ["-n", kubernetesNamespace, "exec", `pod/${pod}`, "--", "sh", "-c", mysqlScript, "sh", database, sql];
    const { stdout } = await execFileAsync("kubectl", args, { cwd: rootDir, timeout: timeoutMs });
    return stdout.trim().split(/\r?\n/).filter(Boolean).at(-1) || "";
  }
  assert(composeProject, "数据库一致性检查需要 MS_E2E_COMPOSE_PROJECT 或 MS_E2E_KUBERNETES_NAMESPACE");
  const args = ["compose", "-p", composeProject, ...composeFiles, "exec", "-T", "mysql", "sh", "-c", mysqlScript, "sh", database, sql];
  const { stdout } = await execFileAsync("docker", args, { cwd: rootDir, timeout: timeoutMs });
  return stdout.trim().split(/\r?\n/).filter(Boolean).at(-1) || "";
}

async function poll(label, operation, predicate, maxWaitMs = 20000, intervalMs = 500) {
  const deadline = Date.now() + maxWaitMs;
  let last;
  let lastError;
  while (Date.now() < deadline) {
    try {
      last = await operation();
      if (predicate(last)) return last;
    } catch (error) { lastError = error; }
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  throw new E2EFailure(`${label} 未在规定时间内达到目标状态`, { last: redact(last), lastError: lastError?.message });
}

async function login(phone, password) {
  const body = expect(await request("POST", "/api/v1/auth/login", undefined, { phone, password }), 200, `登录失败: ${phone}`);
  assert(body.data?.token && body.data?.user?.id, "登录响应缺少 token/user", { response: body });
  return { token: body.data.token, user: body.data.user };
}

async function register(label) {
  const phone = `139${Date.now()}${String(sequence++).padStart(3, "0")}`;
  const body = expect(await request("POST", "/api/v1/auth/register", undefined, {
    phone, password: "abc123456", nickname: `E2E ${label}`
  }), 200, `${label} 注册失败`);
  assert(body.data?.token && body.data?.user?.id, "注册响应缺少 token/user", { response: body });
  return { token: body.data.token, user: body.data.user, phone };
}

async function addAddress(token, detail) {
  const body = expect(await request("POST", "/api/v1/user/addresses", token, {
    contactName: "E2E 用户", phone: "13900000001", detail, defaultAddress: true
  }), 200, "新增地址失败");
  assert(body.data?.id, "新增地址未返回 id", { response: body });
  return body.data.id;
}

async function createPaidDelivery(user, productId, label) {
  const addressId = await addAddress(user.token, `Microservice ${label} 地址 ${sequence++}`);
  expect(await request("POST", "/api/v1/cart/items", user.token, { productId, quantity: 1 }), 200, `${label} 加购失败`);
  const created = expect(await request("POST", "/api/v1/orders/delivery", user.token, { addressId }), 200, `${label} 下单失败`);
  const order = created.data?.[0];
  assert(order?.id && order.status === "PENDING_PAYMENT", `${label} 订单未处于待支付`, { response: created });
  const clientRequestId = `ms-e2e-${label.toLowerCase()}-${Date.now()}-${sequence++}`;
  const paid = expect(await request("POST", "/api/v1/payments", user.token, { orderId: order.id, clientRequestId }), 200, `${label} 支付失败`);
  assert(paid.data?.id === order.id && paid.data?.status === "PAID", `${label} 支付后订单状态错误`, { response: paid });
  return { orderId: order.id, clientRequestId, paid: paid.data, addressId };
}

async function sagaState(orderId) {
  const [saga, payment, reservation, order] = await Promise.all([
    dbScalar(process.env.MYSQL_ORDER_DATABASE || "life_assistant_order", `SELECT status FROM order_inventory_saga WHERE order_id=${orderId}`),
    dbScalar(process.env.MYSQL_ORDER_DATABASE || "life_assistant_order", `SELECT status FROM service_payment WHERE order_id=${orderId} ORDER BY paid_at DESC LIMIT 1`),
    dbScalar(process.env.MYSQL_MERCHANT_DATABASE || "life_assistant_merchant", `SELECT status FROM inventory_reservation WHERE order_id=${orderId}`),
    dbScalar(process.env.MYSQL_ORDER_DATABASE || "life_assistant_order", `SELECT status FROM order_record WHERE id=${orderId}`)
  ]);
  return { saga, payment, reservation, order };
}

async function uc01IdentityProfileAndAddress() {
  const user = await register("UC01");
  const loggedIn = await login(user.phone, "abc123456");
  assert(loggedIn.user.id === user.user.id && loggedIn.user.role === "USER", "UC01 登录用户身份错误", { user: loggedIn.user });
  const me = expect(await request("GET", "/api/v1/auth/me", loggedIn.token), 200, "UC01 auth/me 失败");
  assert(me.data?.id === user.user.id && me.data.role === "USER", "UC01 auth/me 未返回当前用户", { response: me });
  const profile = expect(await request("POST", "/api/v1/user/profile", loggedIn.token, { nickname: "UC01 已更新用户", avatarUrl: "https://example.com/uc01.png" }), 200, "UC01 profile 更新失败");
  assert(profile.data?.nickname === "UC01 已更新用户", "UC01 profile 未持久化", { response: profile });
  const addressId = await addAddress(loggedIn.token, "Microservice UC01 地址");
  const addresses = expect(await request("GET", "/api/v1/user/addresses", loggedIn.token), 200, "UC01 地址列表失败");
  assert(addresses.data?.some((item) => item.id === addressId), "UC01 新地址未出现在列表", { response: addresses });
  const defaulted = expect(await request("POST", `/api/v1/user/addresses/${addressId}/default`, loggedIn.token), 200, "UC01 默认地址设置失败");
  assert(defaulted.data?.id === addressId && defaulted.data.defaultAddress === true, "UC01 默认地址状态错误", { response: defaulted });
  expectError(await request("POST", "/api/v1/auth/login", undefined, { phone: user.phone, password: "wrong-password" }), [401], "UC01 错误密码必须拒绝");
  expectError(await request("POST", "/api/v1/auth/register", undefined, { phone: user.phone, password: "abc123456", nickname: "duplicate" }), [409], "UC01 重复注册必须拒绝");
  expectError(await request("GET", "/api/v1/user/addresses", (await login("13800000002", "abc123456")).token), [403], "UC01 商家不应读取用户地址");
  return { userId: user.user.id, addressId };
}

async function uc02DiscoveryFavoriteAndDetail() {
  const user = await register("UC02");
  const categories = expect(await request("GET", "/api/v1/categories"), 200, "UC02 分类查询失败");
  assert(categories.data?.length >= 4, "UC02 分类种子不足", { response: categories });
  const search = expect(await request("GET", "/api/v1/merchants?keyword=%E5%92%96%E5%95%A1&sort=distanceAsc"), 200, "UC02 商家搜索失败");
  assert(pageRecords(search, "UC02 商家搜索").some((item) => item.id === 2), "UC02 搜索未返回咖啡商家", { response: search });
  const detail = expect(await request("GET", "/api/v1/merchants/1"), 200, "UC02 商家详情失败");
  assert(detail.data?.merchant?.id === 1 && detail.data.products?.length > 0 && detail.data.groupDeals?.length > 0 && Array.isArray(detail.data.reviews), "UC02 商家详情投影不完整", { response: detail });
  expect(await request("POST", "/api/v1/user/favorites", user.token, { merchantId: 1 }), 200, "UC02 收藏商家失败");
  const favorites = expect(await request("GET", "/api/v1/user/favorites", user.token), 200, "UC02 收藏列表失败");
  assert(favorites.data?.some((item) => item.id === 1), "UC02 收藏列表未返回目标商家", { response: favorites });
  expectError(await request("POST", "/api/v1/user/favorites", user.token, { merchantId: 1 }), [409], "UC02 重复收藏必须拒绝");
  expectError(await request("POST", "/api/v1/user/favorites", (await login("13800000002", "abc123456")).token, { merchantId: 1 }), [403], "UC02 商家不应操作用户收藏");
  expect(await request("POST", "/api/v1/user/favorites/1/delete", user.token), 200, "UC02 取消收藏失败");
  return { userId: user.user.id, merchantId: 1 };
}

async function uc03DeliveryPaymentSaga() {
  const user = await register("UC03");
  const addressId = await addAddress(user.token, "Microservice UC03 订单地址");
  expect(await request("POST", "/api/v1/cart/items", user.token, { productId: 1001, quantity: 2 }), 200, "UC03 加购失败");
  const detail = expect(await request("GET", "/api/v1/cart/detail", user.token), 200, "UC03 购物车详情失败");
  assert(detail.data?.some((item) => item.productId === 1001 && item.subtotalCent === item.priceCent * 2), "UC03 购物车金额计算错误", { response: detail });
  expect(await request("POST", "/api/v1/cart/items/1001", user.token, { quantity: 1 }), 200, "UC03 修改购物车失败");
  const created = expect(await request("POST", "/api/v1/orders/delivery", user.token, { addressId }), 200, "UC03 外卖订单创建失败");
  const orderId = created.data?.[0]?.id;
  assert(orderId && created.data[0].status === "PENDING_PAYMENT", "UC03 订单初始状态错误", { response: created });
  const clientRequestId = `ms-e2e-uc03-${Date.now()}-${sequence++}`;
  const paid = expect(await request("POST", "/api/v1/payments", user.token, { orderId, clientRequestId }), 200, "UC03 支付失败");
  assert(paid.data?.status === "PAID", "UC03 支付后订单未变为 PAID", { response: paid });
  await poll("UC03 Saga CONFIRMED", async () => sagaState(orderId), (value) => value.saga === "CONFIRMED" && value.payment === "SUCCESS" && value.reservation === "CONFIRMED" && value.order === "PAID", sagaTimeoutMs);
  const repeated = expect(await request("POST", "/api/v1/payments", user.token, { orderId, clientRequestId }), 200, "UC03 幂等支付重试失败");
  assert(repeated.data?.id === orderId && repeated.data.status === "PAID", "UC03 幂等支付结果错误", { response: repeated });
  expectError(await request("POST", `/api/v1/orders/${orderId}/cancel`, user.token), [409], "UC03 已支付订单不应取消");
  const second = await createPendingDeliveryForCancel(user, 1002);
  const cancelled = expect(await request("POST", `/api/v1/orders/${second}/cancel`, user.token), 200, "UC03 待支付订单取消失败");
  assert(cancelled.data?.status === "CANCELLED", "UC03 取消后订单状态错误", { response: cancelled });
  state.uc03 = { orderId, clientRequestId };
  return { orderId, saga: "CONFIRMED", payment: "SUCCESS", reservation: "CONFIRMED", pendingCancelledOrderId: second };
}

async function createPendingDeliveryForCancel(user, productId) {
  const addressId = await addAddress(user.token, `Microservice UC03 cancel ${sequence++}`);
  expect(await request("POST", "/api/v1/cart/items", user.token, { productId, quantity: 1 }), 200, "UC03 第二次加购失败");
  const created = expect(await request("POST", "/api/v1/orders/delivery", user.token, { addressId }), 200, "UC03 第二个订单创建失败");
  assert(created.data?.[0]?.id, "UC03 第二个订单未返回 id", { response: created });
  return created.data[0].id;
}

async function uc04FulfillmentAndReview() {
  const user = await register("UC04");
  const merchant = await login("13800000002", "abc123456");
  const otherMerchant = await login("13800000003", "abc123456");
  const created = await createPaidDelivery(user, 1002, "UC04");
  assert((await poll("UC04 Saga CONFIRMED", async () => sagaState(created.orderId), (value) => value.saga === "CONFIRMED" && value.reservation === "CONFIRMED", sagaTimeoutMs)).order === "PAID", "UC04 支付与 Saga 状态不一致");
  const merchantOrders = expect(await request("GET", "/api/v1/merchant-admin/orders", merchant.token), 200, "UC04 商家订单列表失败");
  assert(merchantOrders.data?.some((item) => item.id === created.orderId), "UC04 商家未看到自己的订单", { response: merchantOrders });
  expectError(await request("POST", `/api/v1/merchant-admin/orders/${created.orderId}/transition`, otherMerchant.token, { next: "ACCEPTED" }), [403], "UC04 其他商家不应处理订单");
  for (const next of ["ACCEPTED", "DELIVERING", "COMPLETED"]) expect(await request("POST", `/api/v1/merchant-admin/orders/${created.orderId}/transition`, merchant.token, { next }), 200, `UC04 流转到 ${next} 失败`);
  expect(await request("POST", `/api/v1/orders/${created.orderId}/receive`, user.token), 200, "UC04 用户收货失败");
  const me = expect(await request("GET", "/api/v1/auth/me", user.token), 200, "UC04 用户信息失败");
  const review = expect(await request("POST", "/api/v1/reviews", user.token, { orderId: created.orderId, score: 5, tasteScore: 5, serviceScore: 4, content: "Microservice E2E 真实评价" }), 200, "UC04 提交评价失败");
  assert(review.data?.orderId === created.orderId, "UC04 评价未关联订单", { response: review });
  expectError(await request("POST", "/api/v1/reviews", user.token, { orderId: created.orderId, score: 5, tasteScore: 5, serviceScore: 5, content: "重复评价" }), [409], "UC04 重复评价必须拒绝");
  const reviews = expect(await request("GET", "/api/v1/merchant-admin/reviews", merchant.token), 200, "UC04 商家评价列表失败");
  assert(reviews.data?.some((item) => item.orderId === created.orderId), "UC04 商家未看到评价", { response: reviews });
  const reviewCount = await dbScalar(process.env.MYSQL_ORDER_DATABASE || "life_assistant_order", `SELECT COUNT(*) FROM service_review WHERE order_id=${created.orderId}`);
  assert(reviewCount === "1", "UC04 service_review 未保持一条记录", { reviewCount });
  return { orderId: created.orderId, reviewId: review.data?.id, userId: me.data?.id };
}

async function uc05GroupBuyPayment() {
  const user = await register("UC05");
  const created = expect(await request("POST", "/api/v1/orders/group-buy", user.token, { dealId: 1, quantity: 1 }), 200, "UC05 团购订单创建失败");
  const orderId = created.data?.id;
  assert(orderId && created.data.type === "GROUP_BUY", "UC05 团购订单类型错误", { response: created });
  const clientRequestId = `ms-e2e-uc05-${Date.now()}-${sequence++}`;
  const paid = expect(await request("POST", "/api/v1/payments", user.token, { orderId, clientRequestId }), 200, "UC05 团购支付失败");
  const couponCode = paid.data?.couponCode;
  assert(paid.data?.status === "PAID" && /^\d{12}$/.test(couponCode || ""), "UC05 支付未生成 12 位券码", { response: paid });
  await poll("UC05 Saga CONFIRMED", async () => sagaState(orderId), (value) => value.saga === "CONFIRMED" && value.payment === "SUCCESS" && value.reservation === "CONFIRMED", sagaTimeoutMs);
  const repeated = expect(await request("POST", "/api/v1/payments", user.token, { orderId, clientRequestId }), 200, "UC05 团购幂等支付失败");
  assert(repeated.data?.couponCode === couponCode, "UC05 幂等支付重复生成券码", { response: repeated });
  const couponStatus = await dbScalar(process.env.MYSQL_ORDER_DATABASE || "life_assistant_order", `SELECT status FROM service_coupon WHERE code='${couponCode}'`);
  assert(couponStatus === "UNUSED", "UC05 券码初始状态错误", { couponStatus });
  state.groupCoupon = couponCode;
  return { orderId, couponCode, saga: "CONFIRMED", couponStatus };
}

async function uc06CouponVerification() {
  assert(state.groupCoupon, "UC06 缺少 UC05 生成的团购券码");
  const owner = await login("13800000002", "abc123456");
  const other = await login("13800000003", "abc123456");
  expectError(await request("POST", "/api/v1/merchant-admin/coupons/verify", other.token, { code: state.groupCoupon }), [403], "UC06 其他商家不应核销券码");
  const verified = expect(await request("POST", "/api/v1/merchant-admin/coupons/verify", owner.token, { code: state.groupCoupon }), 200, "UC06 自有券码核销失败");
  assert(verified.data?.status === "USED", "UC06 核销后订单状态不是 USED", { response: verified });
  expectError(await request("POST", "/api/v1/merchant-admin/coupons/verify", owner.token, { code: state.groupCoupon }), [409], "UC06 券码重复核销必须拒绝");
  expectError(await request("POST", "/api/v1/merchant-admin/coupons/verify", owner.token, { code: "000000000000" }), [404], "UC06 无效券码必须返回 404");
  return { couponCode: state.groupCoupon, finalOrderStatus: "USED" };
}

async function uc07MerchantCatalogManagement() {
  const owner = await login("13800000002", "abc123456");
  const other = await login("13800000003", "abc123456");
  const before = expect(await request("GET", "/api/v1/merchant-admin/profile", owner.token), 200, "UC07 商家资料查询失败");
  const originalName = before.data?.merchant?.name;
  const renamed = `巷口川味研究所-${Date.now()}`;
  const updated = expect(await request("POST", "/api/v1/merchant-admin/profile", owner.token, { nickname: renamed }), 200, "UC07 商家资料更新失败");
  assert(updated.data?.merchant?.name === renamed, "UC07 商家资料更新未生效", { response: updated });
  const product = expect(await request("POST", "/api/v1/merchant-admin/products", owner.token, { name: `E2E 商品 ${Date.now()}`, description: "微服务 E2E 商品", priceCent: 1990, stock: 8, listed: false }), 200, "UC07 商品创建失败");
  const productId = product.data?.id;
  assert(productId && product.data.listed === false, "UC07 商品未以上架状态创建", { response: product });
  const published = expect(await request("POST", `/api/v1/merchant-admin/products/${productId}/toggle`, owner.token), 200, "UC07 商品上架失败");
  assert(published.data?.listed === true, "UC07 商品上架状态错误", { response: published });
  const unpublished = expect(await request("POST", `/api/v1/merchant-admin/products/${productId}/toggle`, owner.token), 200, "UC07 商品下架失败");
  assert(unpublished.data?.listed === false, "UC07 商品下架状态错误", { response: unpublished });
  expectError(await request("POST", `/api/v1/merchant-admin/products/${productId}/toggle`, other.token), [403], "UC07 其他商家不应维护商品");
  expect(await request("POST", "/api/v1/merchant-admin/profile", owner.token, { nickname: originalName }), 200, "UC07 恢复商家资料失败");
  expect(await request("POST", `/api/v1/merchant-admin/products/${productId}/delete`, owner.token), 200, "UC07 清理测试商品失败");
  return { merchantId: owner.user.merchantId, productId, publishStates: [false, true, false] };
}

async function uc08CustomerServiceAndAssistant() {
  const user = await register("UC08");
  const merchant = await login("13800000002", "abc123456");
  const merchantId = merchant.user.merchantId;
  const question = `Microservice E2E 客服消息 ${Date.now()}-${sequence++}`;
  const sent = expect(await request("POST", `/api/v1/conversations/${merchantId}/messages`, user.token, { content: question }), 200, "UC08 用户发送客服消息失败");
  assert(sent.data?.some((item) => item.senderRole === "USER" && item.content === question), "UC08 用户消息未持久化");
  assert(sent.data?.some((item) => item.senderRole === "MERCHANT_AI" && item.content), "UC08 assistant-service 未返回确定性回复", { response: sent });
  const merchantList = expect(await request("GET", "/api/v1/merchant-admin/conversations", merchant.token), 200, "UC08 商家会话列表失败");
  assert(merchantList.data?.some((item) => item.userId === user.user.id && item.merchantId === merchantId), "UC08 商家未看到用户会话", { response: merchantList });
  const reply = `Microservice E2E 人工回复 ${Date.now()}`;
  expect(await request("POST", `/api/v1/merchant-admin/conversations/${user.user.id}/messages`, merchant.token, { content: reply }), 200, "UC08 商家人工回复失败");
  const conversation = expect(await request("GET", `/api/v1/conversations/${merchantId}`, user.token), 200, "UC08 用户读取会话失败");
  assert(conversation.data?.some((item) => item.senderRole === "MERCHANT" && item.content === reply), "UC08 用户未看到商家人工回复", { response: conversation });
  const assistant = expect(await request("POST", "/api/v1/assistant/ask", undefined, { question: "支付如何保证幂等？" }), 200, "UC08 平台助手调用失败");
  assert(assistant.data?.answer, "UC08 assistant-service 回复为空", { response: assistant });
  expectError(await request("GET", "/api/v1/merchant-admin/conversations", user.token), [403], "UC08 普通用户不应读取商家会话");
  const merchantB = await login("13800000003", "abc123456");
  expectError(await request("GET", `/api/v1/merchant-admin/conversations/${user.user.id}`, merchantB.token), [403, 404], "UC08 其他商家不应读取会话");
  return { userId: user.user.id, merchantId, assistant: "remote assistant-service", senderRoles: ["USER", "MERCHANT_AI", "MERCHANT"] };
}

async function uc09PlatformMetrics() {
  const admin = await login("13800000000", "admin123456");
  const metrics = expect(await request("GET", "/api/v1/admin/metrics", admin.token), 200, "UC09 平台指标失败");
  const overview = metrics.data?.overview;
  assert(metrics.data?.health?.status === "UP", "UC09 指标健康状态不是 UP", { response: metrics });
  assert(Number(overview?.users) >= 1 && Number(overview?.merchants) >= 4 && Number(overview?.orders) >= 1, "UC09 指标不是来自实际服务聚合", { response: metrics });
  const normal = await register("UC09-NORMAL");
  expectError(await request("GET", "/api/v1/admin/metrics", normal.token), [403], "UC09 普通用户必须被拒绝");
  return { overview: { users: overview.users, merchants: overview.merchants, orders: overview.orders, amountCent: overview.amountCent } };
}

async function checkHealth(name, url) {
  const started = Date.now();
  try {
    const response = await fetch(`${url}/actuator/health`, { signal: AbortSignal.timeout(timeoutMs) });
    const text = await response.text();
    let body = {};
    try { body = JSON.parse(text); } catch { body = { raw: text }; }
    health[name] = { url, status: response.status, body: bodySummary(body), durationMs: Date.now() - started };
    assert(response.status === 200 && body.status === "UP", `${name} health 未达到 UP`, { response: health[name] });
  } catch (error) {
    health[name] = { url, durationMs: Date.now() - started, error: error.message };
    throw error;
  }
}

async function preflight() {
  const endpoints = {
    backend: process.env.MS_E2E_BASE_URL || "http://127.0.0.1:18080",
    identity: process.env.MS_E2E_IDENTITY_URL || "http://127.0.0.1:18081",
    merchant: process.env.MS_E2E_MERCHANT_URL || "http://127.0.0.1:18082",
    order: process.env.MS_E2E_ORDER_URL || "http://127.0.0.1:18083",
    assistant: process.env.MS_E2E_ASSISTANT_URL || "http://127.0.0.1:18084"
  };
  for (const [name, url] of Object.entries(endpoints)) await poll(`${name} readiness`, () => checkHealth(name, url), () => health[name]?.status === 200 && health[name]?.body?.type === "object", 30000, 500);
  const migration = expect(await request("GET", "/internal/migration/status"), 200, "远程迁移状态检查失败");
  assert(Object.values(migration).filter((value) => value === "remote-service").length === 3, "backend 没有全部启用 remote 服务路由", { response: migration });
  state.migration = { identity: migration.identity, merchant: migration.merchant, order: migration.order };
  const categories = expect(await request("GET", "/api/v1/categories"), 200, "远程 BFF 预检分类失败");
  assert(categories.data?.length > 0, "远程 BFF 预检未取得商家服务数据");
  return { services: health, migration: state.migration };
}

async function runScenario(name, operation) {
  const startedAt = new Date().toISOString();
  const started = Date.now();
  try {
    const evidence = await operation();
    results.push({ name, status: "passed", startedAt, durationMs: Date.now() - started, evidence: redact(evidence) });
    console.log(`PASS ${name}`);
  } catch (error) {
    results.push({ name, status: "failed", startedAt, durationMs: Date.now() - started, error: redact({ message: error.message, details: error.details || {} }) });
    console.error(`FAIL ${name}: ${error.message}`);
  }
}

function markdownReport(report) {
  const lines = [
    "# Microservice E2E Summary", "", `- Run ID: \`${report.runId}\``, `- Git commit: \`${report.gitCommit}\``,
    `- Backend mode: \`${report.environment.backendProfile}\``, `- Started: ${report.startedAt}`, `- Completed: ${report.completedAt}`, "",
    "## Environment", "", `- Base URL: \`${report.environment.baseUrl}\``, `- Service DBs: ${report.environment.databases.identity}, ${report.environment.databases.merchant}, ${report.environment.databases.order}`,
    `- Migration routes: identity=${report.health.migration?.identity}, merchant=${report.health.migration?.merchant}, order=${report.health.migration?.order}`, "",
    "## UC01–UC09", "", "| UC | Status | Duration |", "|---|---|---:|",
    ...report.scenarios.map((item) => `| ${item.name} | ${item.status.toUpperCase()} | ${item.durationMs} ms |`), "",
    `- Total: ${report.total}`, `- Passed: ${report.passed}`, `- Failed: ${report.failed}`, "",
    "## Health", "", "```json", JSON.stringify(report.health.services, null, 2), "```", "",
    report.failed === 0 && !report.environmentFailure ? "Result: READY FOR CLOUD-NATIVE EXPERIMENTS" : "Result: MICROSERVICE E2E NOT COMPLETE", ""
  ];
  return `${lines.join("\n")}\n`;
}

async function writeReports() {
  await mkdir(reportDir, { recursive: true });
  const completedAt = new Date().toISOString();
  const report = {
    suite: "Microservice E2E UC01–UC09",
    runId, gitCommit: await gitCommit(), startedAt: suiteStartedAt, completedAt,
    environmentFailure: environmentFailure ? redact(environmentFailure) : null,
    environment: {
      backendProfile: "prod,remote", baseUrl,
      remoteFlags: { identity: true, merchant: true, order: true, assistant: true },
      compatibilityStore: false,
      databases: {
        identity: process.env.MYSQL_IDENTITY_DATABASE || "life_assistant_identity",
        merchant: process.env.MYSQL_MERCHANT_DATABASE || "life_assistant_merchant",
        order: process.env.MYSQL_ORDER_DATABASE || "life_assistant_order"
      },
      composeProject: composeProject || null,
      kubernetesNamespace: kubernetesNamespace || null,
      requestTimeoutMs: timeoutMs,
      sagaTimeoutMs
    },
    health: { services: health, migration: state.migration || null },
    total: results.length, passed: results.filter((item) => item.status === "passed").length,
    failed: results.filter((item) => item.status === "failed").length,
    scenarios: results,
    requestCount: requestLog.length
  };
  const exitCode = report.failed === 0 && !environmentFailure ? 0 : 1;
  report.exitCode = exitCode;
  await writeFile(path.join(reportDir, "microservice-e2e-summary.json"), `${JSON.stringify(report, null, 2)}\n`);
  await writeFile(path.join(reportDir, "microservice-e2e-summary.md"), markdownReport(report));
  await writeFile(path.join(reportDir, "microservice-e2e-raw.log"), requestLog.map((item) => JSON.stringify(item)).join("\n") + "\n");
  return { report, exitCode };
}

async function main() {
  try {
    await preflight();
  } catch (error) {
    environmentFailure = { message: error.message, details: error.details || {} };
    console.error(`Microservice E2E preflight failed: ${error.message}`);
  }
  if (!environmentFailure) {
    await runScenario("UC01", uc01IdentityProfileAndAddress);
    await runScenario("UC02", uc02DiscoveryFavoriteAndDetail);
    await runScenario("UC03", uc03DeliveryPaymentSaga);
    await runScenario("UC04", uc04FulfillmentAndReview);
    await runScenario("UC05", uc05GroupBuyPayment);
    await runScenario("UC06", uc06CouponVerification);
    await runScenario("UC07", uc07MerchantCatalogManagement);
    await runScenario("UC08", uc08CustomerServiceAndAssistant);
    await runScenario("UC09", uc09PlatformMetrics);
  } else {
    for (const name of ["UC01", "UC02", "UC03", "UC04", "UC05", "UC06", "UC07", "UC08", "UC09"]) {
      results.push({ name, status: "failed", startedAt: new Date().toISOString(), durationMs: 0, error: { message: "环境预检失败，场景未执行", details: environmentFailure } });
    }
  }
  const { report, exitCode } = await writeReports();
  console.log(`Microservice E2E: total=${report.total} passed=${report.passed} failed=${report.failed}`);
  process.exitCode = exitCode;
}

main().catch(async (error) => {
  environmentFailure = { message: error.message, details: error.details || {} };
  const { exitCode } = await writeReports();
  process.exitCode = exitCode || 1;
});
