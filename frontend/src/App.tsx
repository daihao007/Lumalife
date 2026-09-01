import { useEffect, useRef, useState } from "react";
import { BarChart3, CreditCard, Heart, HeartPulse, HomeIcon, MessageCircle, Package, PackageCheck, ShoppingBag, ShoppingCart, Store, UserRound } from "lucide-react";
import { api } from "./api";
import type { Address, CartLine, Category, Merchant, Order, Product, Role, User } from "./types";
import Login from "./pages/Login";
import Home from "./pages/Home";
import Detail from "./pages/Detail";
import Cart from "./pages/Cart";
import Orders from "./pages/Orders";
import Profile from "./pages/Profile";
import MerchantOrders from "./pages/MerchantOrders";
import MerchantProducts from "./pages/MerchantProducts";
import MerchantSupport from "./pages/MerchantSupport";
import MerchantShop from "./pages/MerchantShop";
import Admin from "./pages/Admin";
import Assistant from "./pages/Assistant";
import Favorites from "./pages/Favorites";
import { accessDeniedMessage, canAccess, defaultView, parseRoute, routeHash, type AppView } from "./routing";
import { createPaymentRequestId } from "./utils";

type MerchantProfilePayload = { user: User; merchant: Merchant };

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [view, setView] = useState<AppView>("home");
  const [keyword, setKeyword] = useState("");
  const [activeCategoryId, setActiveCategoryId] = useState<number | null>(null);
  const [sort, setSort] = useState("recommend");
  const [minPrice, setMinPrice] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const [minScore, setMinScore] = useState("");
  const [categories, setCategories] = useState<Category[]>([]);
  const [merchants, setMerchants] = useState<Merchant[]>([]);
  const [activeMerchant, setActiveMerchant] = useState<any>(null);
  const [assistantMerchantId, setAssistantMerchantId] = useState<number | null>(null);
  const [orders, setOrders] = useState<Order[]>([]);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [message, setMessage] = useState("欢迎来到 LumaLife");
  const [noticeVisible, setNoticeVisible] = useState(true);
  const [favoriteIds, setFavoriteIds] = useState<number[]>([]);
  const detailRequestId = useRef(0);
  const paymentRequestIds = useRef<Record<number, string>>({});
  const groupDealOrderIds = useRef<Record<number, number>>({});

  useEffect(() => {
    api<Category[]>("/api/v1/categories").then(setCategories);
    loadMerchants();
    const token = localStorage.getItem("lumalife-token");
    if (!token) {
      setAuthReady(true);
      return;
    }
    api<User>("/api/v1/auth/me")
      .then(setUser)
      .catch(() => localStorage.removeItem("lumalife-token"))
      .finally(() => setAuthReady(true));
  }, []);

  useEffect(() => {
    if (!authReady) return;

    async function applyLocation() {
      if (!window.location.hash) {
        setRoute(defaultView(user?.role ?? null), undefined, true);
        return;
      }

      const route = parseRoute(window.location.hash);
      if (!route || !canAccess(route.view, user?.role ?? null)) {
        detailRequestId.current += 1;
        setMessage(route ? accessDeniedMessage(user?.role ?? null) : "页面地址无效，已返回工作台");
        setRoute(user ? defaultView(user.role) : route ? "login" : "home", undefined, true);
        return;
      }

      setView(route.view);
      if (route.view === "detail" && route.merchantId && activeMerchant?.merchant?.id !== route.merchantId) {
        const requestedMerchantId = route.merchantId;
        const requestId = ++detailRequestId.current;
        try {
          const detail = await api(`/api/v1/merchants/${requestedMerchantId}`);
          const currentRoute = parseRoute(window.location.hash);
          if (requestId === detailRequestId.current && currentRoute?.view === "detail" && currentRoute.merchantId === requestedMerchantId) {
            setActiveMerchant(detail);
          }
        } catch (error) {
          if (requestId !== detailRequestId.current) return;
          setMessage(error instanceof Error ? error.message : "商家详情加载失败");
          setRoute("home", undefined, true);
        }
      } else if (route.view !== "detail") {
        detailRequestId.current += 1;
      }
    }

    applyLocation();
    window.addEventListener("hashchange", applyLocation);
    window.addEventListener("popstate", applyLocation);
    return () => {
      window.removeEventListener("hashchange", applyLocation);
      window.removeEventListener("popstate", applyLocation);
    };
  }, [authReady, user?.role]);

  // 用户登录/登出后重新加载商家列表（个性化推荐需要用户身份）
  useEffect(() => {
    if (user?.role === "USER") {
      loadMerchants(keyword, activeCategoryId, sort, minPrice, maxPrice, minScore);
      loadFavorites();
    } else {
      setFavoriteIds([]);
    }
  }, [user?.id]);

  useEffect(() => {
    if (user && (view === "orders" || view === "merchant-orders")) loadOrders();
  }, [user?.id, view]);

  useEffect(() => {
    if (!message) return;
    setNoticeVisible(true);
    const timer = window.setTimeout(() => setNoticeVisible(false), 2200);
    return () => window.clearTimeout(timer);
  }, [message]);

  function setRoute(nextView: AppView, merchantId?: number, replace = false) {
    if (nextView !== "detail") detailRequestId.current += 1;
    const nextHash = routeHash(nextView, merchantId);
    if (window.location.hash === nextHash) {
      setView(nextView);
      return;
    }
    const updateHistory = replace ? window.history.replaceState : window.history.pushState;
    updateHistory.call(window.history, null, "", nextHash);
    setView(nextView);
  }

  function navigate(nextView: AppView, merchantId?: number) {
    if (!canAccess(nextView, user?.role ?? null)) {
      setMessage(accessDeniedMessage(user?.role ?? null));
      setRoute(user ? defaultView(user.role) : "login");
      return;
    }
    setRoute(nextView, merchantId);
  }

  async function loadMerchants(nextKeyword = keyword, categoryId: number | null = activeCategoryId, nextSort = sort, nextMinPrice = minPrice, nextMaxPrice = maxPrice, nextMinScore = minScore) {
    const params = new URLSearchParams({ keyword: nextKeyword, page: "1", size: "20", sort: nextSort });
    if (categoryId) params.set("categoryId", String(categoryId));
    if (nextMinPrice) params.set("minPrice", nextMinPrice);
    if (nextMaxPrice) params.set("maxPrice", nextMaxPrice);
    if (nextMinScore) params.set("minScore", nextMinScore);
    const data = await api<{ records: Merchant[] }>(`/api/v1/merchants?${params}`);
    setMerchants(data.records);
  }

  async function searchMerchants() {
    setActiveCategoryId(null);
    await loadMerchants(keyword, null, sort, minPrice, maxPrice, minScore);
  }

  async function filterByCategory(categoryId: number) {
    setActiveCategoryId(categoryId);
    setKeyword("");
    await loadMerchants("", categoryId, sort, minPrice, maxPrice, minScore);
  }

  async function applyDiscoverFilters() {
    const min = minPrice ? Number(minPrice) : null;
    const max = maxPrice ? Number(maxPrice) : null;
    if ((min !== null && (!Number.isFinite(min) || min < 0)) || (max !== null && (!Number.isFinite(max) || max < 0))) {
      setMessage("请输入合法的人均价格区间");
      return;
    }
    if (min !== null && max !== null && min > max) {
      setMessage("最低人均价格不能高于最高人均价格");
      return;
    }
    await loadMerchants(keyword, activeCategoryId, sort, minPrice, maxPrice, minScore);
  }

  async function changeSort(nextSort: string) {
    setSort(nextSort);
    await loadMerchants(keyword, activeCategoryId, nextSort, minPrice, maxPrice, minScore);
  }

  async function changeMinScore(nextMinScore: string) {
    setMinScore(nextMinScore);
    await loadMerchants(keyword, activeCategoryId, sort, minPrice, maxPrice, nextMinScore);
  }

  async function clearMerchantFilter() {
    setActiveCategoryId(null);
    setKeyword("");
    setSort("recommend");
    setMinPrice("");
    setMaxPrice("");
    setMinScore("");
    await loadMerchants("", null, "recommend", "", "", "");
  }

  async function login(phone: string, password: string) {
    try {
      const data = await api<{ token: string; user: User }>("/api/v1/auth/login", { method: "POST", body: JSON.stringify({ phone, password }) });
      localStorage.setItem("lumalife-token", data.token);
      setUser(data.user);
      setMessage(`${data.user.nickname} 登录成功`);
      setRoute(defaultView(data.user.role));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "登录失败");
    }
  }

  async function register(phone: string, password: string, nickname: string, role: Role) {
    try {
      const path = role === "MERCHANT_ADMIN" ? "/api/v1/auth/register/merchant" : "/api/v1/auth/register";
      // 身份由网关按注册端点决定；冻结契约禁止客户端提交可导致提权的 role。
      const data = await api<{ token: string; user: User }>(path, { method: "POST", body: JSON.stringify({ phone, password, nickname }) });
      localStorage.setItem("lumalife-token", data.token);
      setUser(data.user);
      setMessage(`${data.user.nickname} 注册并登录成功`);
      setRoute(data.user.role === "MERCHANT_ADMIN" ? "merchant-orders" : "profile");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "注册失败");
    }
  }

  async function openMerchant(id: number) {
    const requestId = ++detailRequestId.current;
    const detail = await api(`/api/v1/merchants/${id}`);
    if (requestId !== detailRequestId.current) return;
    setActiveMerchant(detail);
    navigate("detail", id);
  }

  function contactMerchant(merchantId: number) {
    setAssistantMerchantId(merchantId);
    navigate("assistant");
  }

  async function syncMerchantProfile(profile: MerchantProfilePayload) {
    setUser(profile.user);
    setMerchants(current => current.map(merchant => merchant.id === profile.merchant.id ? profile.merchant : merchant));
    setActiveMerchant((current: any) => current?.merchant?.id === profile.merchant.id ? { ...current, merchant: profile.merchant } : current);
    await loadMerchants(keyword, activeCategoryId, sort, minPrice, maxPrice, minScore);
  }

  const accountDisplayName = user?.role === "MERCHANT_ADMIN"
    ? merchants.find(merchant => merchant.id === user.merchantId)?.name || user.nickname
    : user?.nickname;

  async function addCart(productId: number) {
    if (!user) {
      setMessage("请先登录后再加购商品");
      navigate("login");
      return;
    }
    await api("/api/v1/cart/items", { method: "POST", body: JSON.stringify({ productId, quantity: 1 }) });
    await loadCart();
    const product = activeMerchant?.products?.find((item: Product) => item.id === productId);
    setMessage(`${product?.name || "商品"} 已加入购物车`);
  }

  async function createDeliveryOrder() {
    const result = await api<Order[]>("/api/v1/orders/delivery", { method: "POST", body: JSON.stringify({ addressId: selectedAddressId }) });
    await loadOrders();
    await loadCart();
    navigate("orders");
    setMessage(result.length === 1 ? `订单 #${result[0].id} 已创建，待模拟支付` : `已按商家拆分创建 ${result.length} 个待支付订单`);
  }

  async function buyDeal(dealId: number) {
    if (!user) {
      setMessage("请先登录后再购买团购套餐");
      navigate("login");
      return;
    }
    let orderId = groupDealOrderIds.current[dealId];
    if (!orderId) {
      const order = await api<Order>("/api/v1/orders/group-buy", { method: "POST", body: JSON.stringify({ dealId, quantity: 1 }) });
      orderId = order.id;
      groupDealOrderIds.current[dealId] = orderId;
    }

    try {
      const paid = await pay(orderId);
      delete groupDealOrderIds.current[dealId];
      setMessage(`团购支付成功，券码 ${paid.couponCode}`);
    } catch (paymentError) {
      try {
        const currentOrders = await fetchOrders();
        const currentOrder = currentOrders.find(order => order.id === orderId);
        if (currentOrder && (currentOrder.status === "PAID" || currentOrder.status === "USED")) {
          delete groupDealOrderIds.current[dealId];
          delete paymentRequestIds.current[orderId];
          navigate("orders");
          setMessage(`团购支付已确认，券码 ${currentOrder.couponCode || "请在订单页查看"}`);
          return;
        }
      } catch {
        // 保留原订单号和支付幂等键；下一次点击只重试这笔订单。
      }
      const reason = paymentError instanceof Error ? paymentError.message : "支付请求失败";
      throw new Error(`团购订单 #${orderId} 已创建，支付结果未确认；请重试原订单支付（${reason}）`);
    }
  }

  async function pay(orderId: number) {
    const clientRequestId = paymentRequestIds.current[orderId] ||= createPaymentRequestId();
    const paid = await api<Order>("/api/v1/payments", { method: "POST", body: JSON.stringify({ orderId, clientRequestId }) });
    await loadOrders();
    delete paymentRequestIds.current[orderId];
    navigate("orders");
    setMessage("模拟支付成功");
    return paid;
  }

  async function cancelOrder(orderId: number) {
    await api(`/api/v1/orders/${orderId}/cancel`, { method: "POST" });
    await loadOrders();
    setMessage("订单已取消");
  }

  async function receiveOrder(orderId: number) {
    await api(`/api/v1/orders/${orderId}/receive`, { method: "POST" });
    await loadOrders();
    setMessage("已确认收货");
  }

  async function fetchOrders() {
    if (!user) return [];
    const data = await api<Order[]>(user.role === "MERCHANT_ADMIN" ? "/api/v1/merchant-admin/orders" : "/api/v1/orders");
    setOrders(data);
    return data;
  }

  async function loadOrders() {
    await fetchOrders();
  }

  async function loadCart() {
    if (!user) return;
    setCart(await api<CartLine[]>("/api/v1/cart/detail"));
    if (user.role === "USER") await loadAddresses();
  }

  async function loadAddresses() {
    const data = await api<Address[]>("/api/v1/user/addresses");
    setAddresses(data);
    const defaultAddress = data.find(address => address.defaultAddress) || data[0];
    setSelectedAddressId(current => current && data.some(address => address.id === current) ? current : defaultAddress?.id ?? null);
  }

  async function loadFavorites() {
    try {
      const data = await api<any[]>("/api/v1/user/favorites");
      setFavoriteIds(data.map((m: any) => m.id));
    } catch { setFavoriteIds([]); }
  }

  async function toggleFavorite(merchantId: number) {
    if (!user) {
      setMessage("请先登录后再收藏商家");
      setRoute("login");
      return;
    }
    if (favoriteIds.includes(merchantId)) {
      await api(`/api/v1/user/favorites/${merchantId}/delete`, { method: "POST" });
      setFavoriteIds(ids => ids.filter(id => id !== merchantId));
      setMessage("已取消收藏");
    } else {
      await api("/api/v1/user/favorites", { method: "POST", body: JSON.stringify({ merchantId }) });
      setFavoriteIds(ids => [...ids, merchantId]);
      setMessage("已收藏");
    }
  }

  async function logout() {
    localStorage.removeItem("lumalife-token");
    paymentRequestIds.current = {};
    groupDealOrderIds.current = {};
    setUser(null);
    setRoute("home");
    setFavoriteIds([]);
    await clearMerchantFilter();
    setMessage("已退出登录");
  }

  return (
    <main>
      <aside>
        <div className="brand"><HeartPulse size={26} /> <span>LumaLife</span></div>
        {(!user || user.role === "USER") && <button data-testid="nav-home" onClick={() => navigate("home")}><Store /> 发现</button>}
        {(!user || user.role === "USER") && <button data-testid="nav-cart" onClick={() => { loadCart(); navigate("cart"); }}><ShoppingCart /> 购物车</button>}
        {user?.role === "USER" && <button data-testid="nav-orders" onClick={() => { loadOrders(); navigate("orders"); }}><CreditCard /> 订单</button>}
        {user?.role === "USER" && <button data-testid="nav-favorites" onClick={() => { loadFavorites(); navigate("favorites"); }}><Heart /> 收藏</button>}
        {user?.role === "USER" && <button data-testid="nav-profile" onClick={() => navigate("profile")}><HomeIcon /> 地址</button>}
        {user?.role === "MERCHANT_ADMIN" && <button data-testid="nav-merchant-orders" onClick={() => { loadOrders(); navigate("merchant-orders"); }}><PackageCheck /> 订单</button>}
        {user?.role === "MERCHANT_ADMIN" && <button data-testid="nav-merchant-products" onClick={() => navigate("merchant-products")}><ShoppingBag /> 商品</button>}
        {user?.role === "MERCHANT_ADMIN" && <button data-testid="nav-merchant-support" onClick={() => navigate("merchant-support")}><MessageCircle /> 客服</button>}
        {user?.role === "MERCHANT_ADMIN" && <button data-testid="nav-merchant-shop" onClick={() => navigate("merchant-shop")}><Store /> 店铺</button>}
        {user?.role === "PLATFORM_ADMIN" && <button data-testid="nav-admin" onClick={() => navigate("admin")}><BarChart3 /> 看板</button>}
        {(!user || user.role === "USER") && <button data-testid="nav-assistant" onClick={() => { setAssistantMerchantId(null); navigate("assistant"); }}><MessageCircle /> 客服</button>}
        <div className="account">
          {user ? <>
            <div className="account-user">
              <div className="account-avatar">{user.avatarUrl ? <img src={user.avatarUrl} /> : <UserRound size={20} />}</div>
              <span title={accountDisplayName}>{accountDisplayName}</span>
            </div>
            <button data-testid="logout" onClick={logout}>退出</button>
          </> : <button data-testid="nav-login" onClick={() => navigate("login")}>登录</button>}
        </div>
      </aside>

      <section className="workspace">
        <header>
          <div>
            <p className="eyebrow">local service demo</p>
            <h1>生活服务，从发现到履约</h1>
          </div>
        </header>
        {noticeVisible && <div className="toast-notice" role="status">{message}</div>}
        {view === "login" && <Login onLogin={login} onRegister={register} />}
        {view === "home" && <Home categories={categories} merchants={merchants} keyword={keyword} setKeyword={setKeyword} activeCategoryId={activeCategoryId} sort={sort} setSort={changeSort} minPrice={minPrice} setMinPrice={setMinPrice} maxPrice={maxPrice} setMaxPrice={setMaxPrice} minScore={minScore} setMinScore={changeMinScore} applyDiscoverFilters={applyDiscoverFilters} searchMerchants={searchMerchants} filterByCategory={filterByCategory} clearMerchantFilter={clearMerchantFilter} openMerchant={openMerchant} favoriteIds={favoriteIds} toggleFavorite={toggleFavorite} />}
        {view === "favorites" && <Favorites merchants={merchants} favoriteIds={favoriteIds} toggleFavorite={toggleFavorite} openMerchant={openMerchant} loadFavorites={loadFavorites} />}
        {view === "detail" && activeMerchant && <Detail detail={activeMerchant} addCart={addCart} openCart={() => { loadCart(); navigate("cart"); }} buyDeal={buyDeal} backHome={() => navigate("home")} contactMerchant={contactMerchant} setMessage={setMessage} />}
        {view === "cart" && <Cart cart={cart} addresses={addresses} selectedAddressId={selectedAddressId} setSelectedAddressId={setSelectedAddressId} reload={loadCart} createDeliveryOrder={createDeliveryOrder} setMessage={setMessage} />}
        {view === "orders" && <Orders user={user} orders={orders} reload={loadOrders} pay={pay} cancelOrder={cancelOrder} receiveOrder={receiveOrder} setMessage={setMessage} />}
        {view === "profile" && user && <Profile user={user} setUser={setUser} setMessage={setMessage} />}
        {view === "merchant-orders" && user && <MerchantOrders user={user} orders={orders} reload={loadOrders} setMessage={setMessage} />}
        {view === "merchant-products" && user && <MerchantProducts user={user} setMessage={setMessage} />}
        {view === "merchant-support" && user && <MerchantSupport user={user} setMessage={setMessage} />}
        {view === "merchant-shop" && user && <MerchantShop user={user} onProfileUpdated={syncMerchantProfile} setMessage={setMessage} />}
        {view === "admin" && <Admin />}
        {view === "assistant" && <Assistant user={user} initialMerchantId={assistantMerchantId} />}
      </section>
    </main>
  );
}
