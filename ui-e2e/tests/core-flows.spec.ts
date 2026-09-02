import { expect, test, type Page } from "@playwright/test";

async function clearAndOpen(page: Page, hash = "#login") {
  await page.goto("/");
  await page.evaluate(() => localStorage.clear());
  await page.reload();
  if (hash === "#login") await page.getByTestId("nav-login").click();
}

async function login(page: Page, phone: string, expectedNav = "nav-home", password = "abc123456") {
  await clearAndOpen(page);
  await page.getByTestId("auth-phone").fill(phone);
  await page.getByTestId("auth-password").fill(password);
  await page.getByTestId("auth-submit").click();
  await expect(page.getByTestId(expectedNav)).toBeVisible();
}

async function register(page: Page, label: string) {
  const nickname = `UI E2E ${label}`;
  await clearAndOpen(page);
  await page.getByTestId("register-mode").click();
  await page.getByTestId("auth-phone").fill(`ui-e2e-${Date.now()}-${label}`);
  await page.getByTestId("auth-password").fill("abc123456");
  await page.getByTestId("auth-confirm-password").fill("abc123456");
  await page.getByTestId("auth-nickname").fill(nickname);
  await page.getByTestId("auth-submit").click();
  await expect(page).toHaveURL(/#\/profile$/);
  return nickname;
}

test("用户可以通过真实页面登录并打开商家详情", async ({ page }) => {
  await login(page, "13800000001");
  await expect(page.getByTestId("merchant-card-1")).toBeVisible();
  await page.getByTestId("merchant-card-1").click();
  await expect(page.getByRole("heading", { name: "巷口川味研究所" })).toBeVisible();
  await expect(page.getByTestId("add-cart-1001")).toBeVisible();
});

test("用户可以在页面维护地址并创建待支付订单", async ({ page }) => {
  await register(page, "order");
  await page.getByTestId("nav-profile").click();
  await page.getByTestId("address-detail").fill("UI E2E 测试地址");
  await page.getByTestId("address-save").click();
  await expect(page.getByText("UI E2E 测试地址")).toBeVisible();
  await page.getByTestId("nav-home").click();
  await page.getByTestId("merchant-card-1").click();
  await page.getByTestId("add-cart-1001").click();
  await page.getByTestId("detail-open-cart").click();
  await page.getByTestId("checkout-submit").click();
  await expect(page).toHaveURL(/#\/orders$/);
  await expect(page.getByText("待支付").first()).toBeVisible();
});

test("用户和商家可以在两个真实浏览器上下文中完成客服往返", async ({ browser }, testInfo) => {
  const userContext = await browser.newContext();
  const merchantContext = await browser.newContext();
  const userPage = await userContext.newPage();
  const merchantPage = await merchantContext.newPage();
  const runId = `${Date.now()}-${testInfo.retry}`;
  const userMessage = `UI E2E 客服提问 ${runId}`;
  const merchantReply = `UI E2E 客服回复 ${runId}`;
  try {
    const userNickname = await register(userPage, `support-${runId}`);
    await userPage.getByTestId("nav-home").click();
    await userPage.getByTestId("merchant-card-1").click();
    await userPage.getByTestId("contact-merchant").click();
    await userPage.getByTestId("user-chat-input").fill(userMessage);
    await userPage.getByTestId("user-chat-send").click();
    await expect(userPage.locator(".chat-messages").getByText(userMessage, { exact: true })).toBeVisible();

    await login(merchantPage, "13800000002", "nav-merchant-orders");
    await merchantPage.getByTestId("nav-merchant-support").click();
    const conversation = merchantPage.getByRole("button", { name: new RegExp(userNickname) }).first();
    await expect(conversation).toBeVisible();
    await conversation.click();
    await expect(merchantPage.locator(".chat-messages").getByText(userMessage, { exact: true })).toBeVisible();
    await merchantPage.getByTestId("merchant-chat-input").fill(merchantReply);
    await merchantPage.getByTestId("merchant-chat-send").click();
    await expect(merchantPage.locator(".chat-messages").getByText(merchantReply, { exact: true })).toBeVisible();

    await userPage.reload();
    const refreshedConversation = userPage.getByRole("button", { name: new RegExp(merchantReply) });
    await expect(refreshedConversation).toBeVisible();
    await refreshedConversation.click();
    await expect(userPage.locator(".chat-messages").getByText(merchantReply, { exact: true })).toBeVisible();
  } finally {
    await userContext.close();
    await merchantContext.close();
  }
});
