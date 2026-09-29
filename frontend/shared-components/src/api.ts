import type {
  Cart,
  InventoryItem,
  Order,
  Page,
  Payment,
  Product,
  ReviewPage,
  SearchResult,
  TokenResponse,
  User,
  WishlistItem,
} from "./types";

const TOKEN_KEY = "ecommerce.session.v1";
const API_BASE = (
  import.meta.env.VITE_API_BASE_URL || "http://localhost:8080"
).replace(/\/$/, "");

interface StoredSession {
  accessToken: string;
  refreshToken: string;
}
interface RequestOptions extends RequestInit {
  authenticated?: boolean;
  retryAuth?: boolean;
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly details?: unknown,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export function readSession(): StoredSession | null {
  try {
    const raw = globalThis.sessionStorage?.getItem(TOKEN_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredSession;
    return parsed.accessToken && parsed.refreshToken ? parsed : null;
  } catch {
    return null;
  }
}

export function saveSession(tokens: TokenResponse): void {
  globalThis.sessionStorage?.setItem(
    TOKEN_KEY,
    JSON.stringify({
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
    } satisfies StoredSession),
  );
}

export function clearSession(): void {
  globalThis.sessionStorage?.removeItem(TOKEN_KEY);
  if (typeof window !== "undefined")
    window.dispatchEvent(new Event("ecommerce:auth-expired"));
}

async function parseError(response: Response): Promise<ApiError> {
  let body: unknown;
  try {
    body = await response.json();
  } catch {
    body = await response.text().catch(() => "");
  }
  const record =
    body && typeof body === "object" ? (body as Record<string, unknown>) : {};
  const message = String(
    record.message ??
      record.detail ??
      record.error ??
      response.statusText ??
      "Request failed",
  );
  return new ApiError(message, response.status, body);
}

async function rawRequest<T>(
  path: string,
  init: RequestInit,
  accessToken?: string,
): Promise<T> {
  const headers = new Headers(init.headers);
  if (
    init.body &&
    !(init.body instanceof FormData) &&
    !headers.has("Content-Type")
  ) {
    headers.set("Content-Type", "application/json");
  }
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  const response = await fetch(`${API_BASE}${path}`, { ...init, headers });
  if (!response.ok) throw await parseError(response);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export async function api<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const { authenticated = true, retryAuth = true, ...init } = options;
  const session = authenticated ? readSession() : null;
  try {
    return await rawRequest<T>(path, init, session?.accessToken);
  } catch (error) {
    const isAuthRoute = path.startsWith("/auth/");
    if (
      !(error instanceof ApiError) ||
      error.status !== 401 ||
      !session ||
      !retryAuth ||
      isAuthRoute
    )
      throw error;
    try {
      const next = await rawRequest<TokenResponse>("/auth/refresh", {
        method: "POST",
        body: JSON.stringify({ refreshToken: session.refreshToken }),
      });
      saveSession(next);
      return await rawRequest<T>(path, init, next.accessToken);
    } catch (refreshError) {
      clearSession();
      throw refreshError;
    }
  }
}

const query = (values: Record<string, string | number | undefined>) => {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && String(value).trim() !== "")
      params.set(key, String(value));
  });
  return params.toString();
};

export const apiClient = {
  async login(email: string, password: string) {
    const tokens = await api<TokenResponse>("/auth/login", {
      method: "POST",
      authenticated: false,
      body: JSON.stringify({ email, password }),
    });
    saveSession(tokens);
    return api<User>("/auth/me");
  },
  async register(email: string, password: string) {
    await api<unknown>("/auth/register", {
      method: "POST",
      authenticated: false,
      body: JSON.stringify({ email, password }),
    });
    return this.login(email, password);
  },
  async logout() {
    const session = readSession();
    try {
      if (session)
        await api("/auth/logout", {
          method: "POST",
          body: JSON.stringify({ refreshToken: session.refreshToken }),
        });
    } finally {
      clearSession();
    }
  },
  me: () => api<User>("/auth/me"),
  products: (page = 0, size = 20, search?: string) =>
    api<Page<Product>>(`/api/products?${query({ page, size, search })}`, {
      authenticated: false,
    }),
  product: (id: string) =>
    api<Product>(`/api/products/${encodeURIComponent(id)}`, {
      authenticated: false,
    }),
  search: (filters: {
    q?: string;
    category?: string;
    brand?: string;
    min_price?: number;
    max_price?: number;
    min_rating?: number;
    page?: number;
    size?: number;
    sort?: string;
  }) =>
    api<SearchResult>(
      `/search?${query(filters as Record<string, string | number | undefined>)}`,
      { authenticated: false },
    ),
  createProduct: (product: Partial<Product>) =>
    api<Product>("/api/products", {
      method: "POST",
      body: JSON.stringify(product),
    }),
  updateProduct: (id: string, product: Partial<Product>) =>
    api<Product>(`/api/products/${encodeURIComponent(id)}`, {
      method: "PUT",
      body: JSON.stringify(product),
    }),
  deleteProduct: (id: string) =>
    api<void>(`/api/products/${encodeURIComponent(id)}`, { method: "DELETE" }),
  cart: (userId: string) =>
    api<Cart>(`/api/carts/${encodeURIComponent(userId)}`),
  addCartItem: (userId: string, productId: string, quantity = 1) =>
    api<Cart>(`/api/carts/${encodeURIComponent(userId)}/items`, {
      method: "POST",
      body: JSON.stringify({ product_id: productId, quantity }),
    }),
  updateCartItem: (userId: string, productId: string, quantity: number) =>
    api<Cart>(
      `/api/carts/${encodeURIComponent(userId)}/items/${encodeURIComponent(productId)}`,
      { method: "PATCH", body: JSON.stringify({ quantity }) },
    ),
  removeCartItem: (userId: string, productId: string) =>
    api<Cart>(
      `/api/carts/${encodeURIComponent(userId)}/items/${encodeURIComponent(productId)}`,
      { method: "DELETE" },
    ),
  clearCart: (userId: string) =>
    api<void>(`/api/carts/${encodeURIComponent(userId)}`, { method: "DELETE" }),
  wishlist: (page = 0, size = 100) =>
    api<{ items: WishlistItem[] }>(`/api/wishlist?${query({ page, size })}`),
  addWishlist: (productId: string) =>
    api<WishlistItem>(`/api/wishlist/items/${encodeURIComponent(productId)}`, {
      method: "PUT",
    }),
  removeWishlist: (productId: string) =>
    api<void>(`/api/wishlist/items/${encodeURIComponent(productId)}`, {
      method: "DELETE",
    }),
  orders: (page = 0, size = 20, userId?: string) =>
    api<Page<Order>>(`/api/orders?${query({ page, size, userId })}`),
  order: (orderId: string) =>
    api<Order>(`/api/orders/${encodeURIComponent(orderId)}`),
  createOrder: (
    items: { productId: string; quantity: number }[],
    paymentMethodId: string,
    idempotencyKey: string,
  ) =>
    api<Order>("/api/orders", {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify({ items, paymentMethodId }),
    }),
  cancelOrder: (orderId: string) =>
    api<Order>(`/api/orders/${encodeURIComponent(orderId)}/cancel`, {
      method: "PATCH",
    }),
  payment: (orderId: string) =>
    api<Payment>(`/api/payments/orders/${encodeURIComponent(orderId)}`),
  reviews: (productId: string, page = 0, size = 20) =>
    api<ReviewPage>(
      `/api/products/${encodeURIComponent(productId)}/reviews?${query({ page, size })}`,
      { authenticated: false },
    ),
  createReview: (
    productId: string,
    body: { order_id: string; rating: number; title: string; body: string },
  ) =>
    api(`/api/products/${encodeURIComponent(productId)}/reviews`, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  updateReview: (
    productId: string,
    body: { rating: number; title: string; body: string },
  ) =>
    api(`/api/products/${encodeURIComponent(productId)}/reviews/me`, {
      method: "PUT",
      body: JSON.stringify(body),
    }),
  deleteReview: (productId: string) =>
    api<void>(`/api/products/${encodeURIComponent(productId)}/reviews/me`, {
      method: "DELETE",
    }),
  inventory: (page = 0, size = 100) =>
    api<Page<InventoryItem>>(`/api/inventory?${query({ page, size })}`),
  inventoryItem: (productId: string) =>
    api<InventoryItem>(`/api/inventory/${encodeURIComponent(productId)}`),
  setInventory: (productId: string, quantity: number) =>
    api<InventoryItem>(`/api/inventory/${encodeURIComponent(productId)}`, {
      method: "PUT",
      body: JSON.stringify({ quantity }),
    }),
};
