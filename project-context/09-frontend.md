# 09-frontend


---

## File: `frontend/admin-dashboard/package.json`

```json
{
  "name": "@ecommerce/admin-dashboard",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite --host 0.0.0.0 --port 3001",
    "build": "tsc --noEmit && vite build",
    "preview": "vite preview --host 0.0.0.0 --port 3001",
    "test": "vitest run",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "@ecommerce/shared": "*"
  },
  "devDependencies": {
    "@testing-library/react": "^16.1.0",
    "@testing-library/user-event": "^14.5.2",
    "@types/react": "^18.3.12",
    "@types/react-dom": "^18.3.1",
    "jsdom": "^25.0.1",
    "typescript": "~5.6.3",
    "vite": "^5.4.11",
    "vitest": "^2.1.5"
  }
}

```

---

## File: `frontend/admin-dashboard/src/AdminApp.tsx`

```typescript
import { Navigate, Route, Routes } from "react-router-dom";
import { AdminFrame, AdminLogin } from "./AdminFrame";
import { OverviewPage } from "./OverviewPage";
import { ProductsPage } from "./ProductsPage";
import { InventoryPage } from "./InventoryPage";
import { OrdersPage } from "./OrdersPage";
export function AdminApp() {
  return (
    <Routes>
      <Route path="/login" element={<AdminLogin />} />
      <Route element={<AdminFrame />}>
        <Route index element={<OverviewPage />} />
        <Route path="products" element={<ProductsPage />} />
        <Route path="inventory" element={<InventoryPage />} />
        <Route path="orders" element={<OrdersPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

```

---

## File: `frontend/admin-dashboard/src/AdminFrame.tsx`

```typescript
import { useState, type FormEvent } from "react";
import { Navigate, NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  Button,
  Field,
  Footer,
  Loading,
  ToastRegion,
  useSession,
} from "@ecommerce/shared";
export function AdminFrame() {
  const { user, ready, signOut } = useSession();
  const navigate = useNavigate();
  if (!ready) return <Loading label="Checking administrator access" />;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== "ADMIN")
    return (
      <div className="app-shell">
        <main className="app-main">
          <div className="panel role-denied">
            <p className="eyebrow">Access restricted</p>
            <h1>This space is for the studio team.</h1>
            <p className="muted">
              Your account does not have administrator access. Switch accounts
              or return to the storefront.
            </p>
            <div className="form-actions">
              <Button
                variant="secondary"
                onClick={() => void signOut().then(() => navigate("/login"))}
              >
                Switch account
              </Button>
              <Button
                onClick={() => window.location.assign("http://localhost:3000")}
              >
                Go to storefront
              </Button>
            </div>
          </div>
        </main>
        <ToastRegion />
      </div>
    );
  return (
    <div className="app-shell">
      <header className="site-header admin-topbar">
        <a className="brand-lockup admin-logo" href="/">
          <span className="brand-mark">m</span>
          <span>
            Maison / studio<small>Commerce console</small>
          </span>
        </a>
        <div className="admin-user">
          <span>{user.email}</span>
          <span className="admin-avatar">
            {user.email.slice(0, 1).toUpperCase()}
          </span>
          <Button
            variant="quiet"
            className="button-small"
            onClick={() => void signOut()}
          >
            Sign out
          </Button>
        </div>
      </header>
      <main className="app-main">
        <div className="admin-layout">
          <nav className="admin-sidebar" aria-label="Admin navigation">
            <NavLink end to="/">
              Overview
            </NavLink>
            <NavLink to="/products">Products</NavLink>
            <NavLink to="/inventory">Inventory</NavLink>
            <NavLink to="/orders">Orders</NavLink>
          </nav>
          <section className="admin-content">
            <Outlet />
          </section>
        </div>
      </main>
      <Footer />
      <ToastRegion />
    </div>
  );
}
export function AdminLogin() {
  const { user, ready, signIn } = useSession();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  if (ready && user?.role === "ADMIN") return <Navigate to="/" replace />;
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const account = await signIn(email, password);
      if (account.role !== "ADMIN")
        throw new Error("This account does not have administrator access.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not sign in.");
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="app-shell">
      <main className="app-main">
        <div className="panel admin-prompt">
          <p className="eyebrow">Maison / studio</p>
          <h1>Welcome to the back office.</h1>
          <p className="muted">
            Sign in with an administrator account to manage the catalog,
            inventory, and orders.
          </p>
          <form className="form-stack" onSubmit={submit}>
            <Field
              label="Email address"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
            <Field
              label="Password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
            {error && (
              <div role="alert" className="error-banner">
                {error}
              </div>
            )}
            <Button disabled={busy}>
              {busy ? "Signing in…" : "Sign in to studio"}
            </Button>
          </form>
          <a className="text-link" href="http://localhost:3000">
            Back to storefront ↗
          </a>
        </div>
      </main>
      <ToastRegion />
    </div>
  );
}

```

---

## File: `frontend/admin-dashboard/src/helpers.ts`

```typescript
import type { Order } from "@ecommerce/shared";
export function calculateRevenue(orders: Order[]) {
  return orders
    .filter((order) => order.status === "CONFIRMED")
    .reduce((total, order) => total + Number(order.totalAmount), 0);
}

```

---

## File: `frontend/admin-dashboard/src/InventoryPage.tsx`

```typescript
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  EmptyState,
  ErrorState,
  Field,
  Loading,
  PageTitle,
  ProductVisual,
  apiClient,
  notify,
  type InventoryItem,
  type Product,
} from "@ecommerce/shared";
export function InventoryPage() {
  const queryClient = useQueryClient();
  const inventoryQuery = useQuery({
    queryKey: ["inventory"],
    queryFn: () => apiClient.inventory(0, 100),
  });
  const productsQuery = useQuery({
    queryKey: ["admin-products"],
    queryFn: () => apiClient.products(0, 100),
  });
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [search, setSearch] = useState("");
  const update = useMutation({
    mutationFn: ({ id, quantity }: { id: string; quantity: number }) =>
      apiClient.setInventory(id, quantity),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["inventory"] });
      notify("Stock level updated");
    },
    onError: (error) =>
      notify(
        error instanceof Error ? error.message : "Could not update stock",
        "error",
      ),
  });
  if (inventoryQuery.isPending || productsQuery.isPending)
    return <Loading label="Reading stock levels" />;
  const error = inventoryQuery.error ?? productsQuery.error;
  if (error)
    return (
      <ErrorState
        error={error}
        retry={() => {
          void inventoryQuery.refetch();
          void productsQuery.refetch();
        }}
      />
    );
  if (!inventoryQuery.data || !productsQuery.data)
    return <ErrorState error={new Error("Inventory data is incomplete.")} />;
  const productMap = new Map(
    productsQuery.data.content.map((product) => [product.id, product]),
  );
  const inventoryMap = new Map(
    inventoryQuery.data.content.map((item) => [item.productId, item]),
  );
  const allRows = productsQuery.data.content.map(
    (product) =>
      inventoryMap.get(product.id) ??
      ({
        productId: product.id,
        quantity: 0,
        reservedQuantity: 0,
        availableQuantity: 0,
        lastUpdatedAt: "",
      } satisfies InventoryItem),
  );
  const filtered = allRows.filter((item) =>
    `${item.productId} ${productMap.get(item.productId)?.name ?? ""}`
      .toLowerCase()
      .includes(search.toLowerCase()),
  );
  return (
    <>
      <PageTitle
        eyebrow="Catalog / availability"
        title="Inventory"
        description="Adjust total stock. Reserved units are protected from accidental overselling."
      />
      <div className="admin-filters">
        <form
          className="search-input"
          onSubmit={(event) => event.preventDefault()}
        >
          <input
            aria-label="Filter inventory"
            placeholder="Find a product"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <button aria-label="Filter">⌕</button>
        </form>
        <span className="small">{filtered.length} tracked products</span>
      </div>
      {filtered.length ? (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Piece</th>
                <th>Total stock</th>
                <th>Reserved</th>
                <th>Available</th>
                <th>Set total stock</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((item) => (
                <InventoryRow
                  key={item.productId}
                  item={item}
                  product={productMap.get(item.productId)}
                  value={quantities[item.productId] ?? String(item.quantity)}
                  onChange={(value) =>
                    setQuantities((current) => ({
                      ...current,
                      [item.productId]: value,
                    }))
                  }
                  onSave={(quantity) =>
                    update.mutate({ id: item.productId, quantity })
                  }
                  busy={update.isPending}
                />
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <EmptyState
          title="Add products to your catalog first"
          body="Inventory is managed for products in the catalog."
        />
      )}
    </>
  );
}
function InventoryRow({
  item,
  product,
  value,
  onChange,
  onSave,
  busy,
}: {
  item: InventoryItem;
  product?: Product;
  value: string;
  onChange(value: string): void;
  onSave(quantity: number): void;
  busy: boolean;
}) {
  return (
    <tr>
      <td>
        <div className="table-product">
          {product && <ProductVisual compact product={product} />}
          <span className="table-product-copy">
            <strong>{product?.name ?? "Unknown product"}</strong>
            <small>{item.productId}</small>
          </span>
        </div>
      </td>
      <td>{item.quantity}</td>
      <td>{item.reservedQuantity}</td>
      <td>
        <strong
          className={`inventory-quantity ${item.availableQuantity <= 5 ? "inventory-low" : ""}`}
        >
          {item.availableQuantity}
        </strong>
      </td>
      <td>
        <form
          className="inline-form inventory-form"
          onSubmit={(event) => {
            event.preventDefault();
            onSave(Number(value));
          }}
        >
          <Field
            label="Total units"
            aria-label={`Total units for ${product?.name ?? item.productId}`}
            type="number"
            min={item.reservedQuantity}
            step="1"
            value={value}
            onChange={(event) => onChange(event.target.value)}
            required
          />
          <Button
            className="button-small"
            disabled={busy || !value || Number(value) < item.reservedQuantity}
          >
            Update
          </Button>
        </form>
      </td>
    </tr>
  );
}

```

---

## File: `frontend/admin-dashboard/src/main.tsx`

```typescript
import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { SessionProvider } from "@ecommerce/shared";
import { AdminApp } from "./AdminApp";
import "@ecommerce/shared/styles.css";
import "./admin.css";
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 15000, refetchOnWindowFocus: false, retry: 1 },
  },
});
ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <BrowserRouter>
          <AdminApp />
        </BrowserRouter>
      </SessionProvider>
    </QueryClientProvider>
  </React.StrictMode>,
);

```

---

## File: `frontend/admin-dashboard/src/OrdersPage.tsx`

```typescript
import { Modal } from "./ProductForm";
import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  EmptyState,
  ErrorState,
  Loading,
  PageTitle,
  SelectField,
  StatusPill,
  apiClient,
  formatMoney,
  notify,
  type Order,
} from "@ecommerce/shared";
export function OrdersPage() {
  const queryClient = useQueryClient();
  const [userFilter, setUserFilter] = useState("");
  const [filter, setFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [selected, setSelected] = useState<Order | null>(null);
  const ordersQuery = useQuery({
    queryKey: ["admin-orders", filter],
    queryFn: () => apiClient.orders(0, 100, filter || undefined),
  });
  const cancel = useMutation({
    mutationFn: apiClient.cancelOrder,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin-orders"] });
      notify("Order cancelled");
      setSelected(null);
    },
    onError: (error) =>
      notify(
        error instanceof Error ? error.message : "Could not cancel order",
        "error",
      ),
  });
  const orders = (ordersQuery.data?.content ?? []).filter(
    (order) => !statusFilter || order.status === statusFilter,
  );
  return (
    <>
      <PageTitle
        eyebrow="Customer care / fulfillment"
        title="Orders"
        description="Follow each order through stock reservation and payment."
      />
      <div className="admin-filters">
        <form
          className="search-input"
          onSubmit={(event) => {
            event.preventDefault();
            setFilter(userFilter.trim());
          }}
        >
          <input
            aria-label="Find orders by customer ID"
            placeholder="Filter by customer user ID"
            value={userFilter}
            onChange={(event) => setUserFilter(event.target.value)}
          />
          <button aria-label="Search orders">⌕</button>
        </form>
        <SelectField
          label="Status"
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value)}
        >
          <option value="">All statuses</option>
          {[
            "PENDING_INVENTORY",
            "PENDING_PAYMENT",
            "CONFIRMED",
            "CANCELLED",
          ].map((status) => (
            <option key={status} value={status}>
              {status.replaceAll("_", " ")}
            </option>
          ))}
        </SelectField>
      </div>
      {ordersQuery.isPending ? (
        <Loading label="Gathering orders" />
      ) : ordersQuery.isError ? (
        <ErrorState
          error={ordersQuery.error}
          retry={() => void ordersQuery.refetch()}
        />
      ) : orders.length ? (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Order</th>
                <th>Customer</th>
                <th>Placed</th>
                <th>Items</th>
                <th>Total</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {orders.map((order) => (
                <tr key={order.orderId}>
                  <td>
                    <strong>{order.orderId.slice(0, 8).toUpperCase()}</strong>
                  </td>
                  <td>{order.userId}</td>
                  <td>{new Date(order.createdAt).toLocaleDateString()}</td>
                  <td>{order.items.length}</td>
                  <td>{formatMoney(order.totalAmount, order.currency)}</td>
                  <td>
                    <StatusPill status={order.status} />
                  </td>
                  <td>
                    <Button
                      className="button-small"
                      variant="secondary"
                      onClick={() => setSelected(order)}
                    >
                      Details
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <EmptyState
          title="No orders found"
          body="New orders will appear here as customers check out."
        />
      )}
      {selected && (
        <Modal
          title={`Order ${selected.orderId.slice(0, 8).toUpperCase()}`}
          onClose={() => setSelected(null)}
        >
          <div className="order-detail-panel">
            <div className="summary-line">
              <span>Customer</span>
              <strong>{selected.userId}</strong>
            </div>
            <div className="summary-line">
              <span>Status</span>
              <StatusPill status={selected.status} />
            </div>
            <div className="summary-line">
              <span>Placed</span>
              <span>{new Date(selected.createdAt).toLocaleString()}</span>
            </div>
            <div className="order-items">
              {selected.items.map((item) => (
                <div className="order-item-line" key={item.productId}>
                  <span>
                    {item.productName} × {item.quantity}
                  </span>
                  <strong>
                    {formatMoney(item.lineTotal, selected.currency)}
                  </strong>
                </div>
              ))}
            </div>
            <div className="summary-line summary-total">
              <span>Order total</span>
              <strong>
                {formatMoney(selected.totalAmount, selected.currency)}
              </strong>
            </div>
            {selected.status === "PENDING_INVENTORY" && (
              <Button
                variant="danger"
                disabled={cancel.isPending}
                onClick={() => cancel.mutate(selected.orderId)}
              >
                Cancel pending order
              </Button>
            )}
          </div>
        </Modal>
      )}
    </>
  );
}

```

---

## File: `frontend/admin-dashboard/src/OverviewPage.tsx`

```typescript
import { calculateRevenue } from "./helpers";
import { useQuery } from "@tanstack/react-query";
import {
  ErrorState,
  Loading,
  StatusPill,
  apiClient,
  formatMoney,
  type Order,
} from "@ecommerce/shared";
export function OverviewPage() {
  const products = useQuery({
    queryKey: ["admin-products"],
    queryFn: () => apiClient.products(0, 100),
  });
  const orders = useQuery({
    queryKey: ["admin-orders"],
    queryFn: () => apiClient.orders(0, 100),
  });
  const inventory = useQuery({
    queryKey: ["inventory"],
    queryFn: () => apiClient.inventory(0, 100),
  });
  const pending = products.isPending || orders.isPending || inventory.isPending;
  const failed = products.isError
    ? products.error
    : orders.isError
      ? orders.error
      : inventory.isError
        ? inventory.error
        : null;
  if (pending) return <Loading label="Gathering the studio view" />;
  if (failed)
    return (
      <ErrorState
        error={failed}
        retry={() => {
          void products.refetch();
          void orders.refetch();
          void inventory.refetch();
        }}
      />
    );
  if (!products.data || !orders.data || !inventory.data)
    return (
      <ErrorState error={new Error("The studio overview is incomplete.")} />
    );
  const allOrders = orders.data.content;
  const revenue = calculateRevenue(allOrders);
  const confirmed = allOrders.filter(
    (order) => order.status === "CONFIRMED",
  ).length;
  const lowStock = inventory.data.content.filter(
    (item) => item.availableQuantity <= 5,
  ).length;
  const lastOrders = [...allOrders]
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .slice(0, 5);
  return (
    <>
      <div className="admin-welcome">
        <p className="eyebrow">
          Monday,{" "}
          {new Date().toLocaleDateString(undefined, {
            month: "long",
            day: "numeric",
          })}
        </p>
        <h1>Good morning, studio.</h1>
        <p className="muted">Here’s a quiet look at how things are moving.</p>
      </div>
      <div className="stat-grid">
        <StatCard
          label="Catalog pieces"
          value={products.data.totalElements}
          note="Products currently listed"
        />
        <StatCard
          label="Orders"
          value={orders.data.totalElements}
          note={`${confirmed} confirmed`}
        />
        <StatCard
          label="Gross sales"
          value={formatMoney(revenue)}
          note="From confirmed orders in this view"
        />
        <StatCard
          label="Low availability"
          value={lowStock}
          note="5 or fewer available units"
        />
      </div>
      <div className="dashboard-grid">
        <div className="panel">
          <div className="panel-title">
            <div>
              <h2>Recent orders</h2>
              <p className="small">The latest customer activity</p>
            </div>
            <a className="text-link" href="/orders">
              All orders ↗
            </a>
          </div>
          {lastOrders.length ? (
            <div className="activity-list">
              {lastOrders.map((order) => (
                <ActivityOrder key={order.orderId} order={order} />
              ))}
            </div>
          ) : (
            <p className="small">New orders will show up here.</p>
          )}
        </div>
        <div className="panel">
          <div className="panel-title">
            <div>
              <h2>Availability</h2>
              <p className="small">Products that need a closer look</p>
            </div>
            <a className="text-link" href="/inventory">
              Manage ↗
            </a>
          </div>
          {lowStock ? (
            <div className="activity-list">
              {inventory.data.content
                .filter((item) => item.availableQuantity <= 5)
                .slice(0, 6)
                .map((item) => (
                  <div className="activity-item" key={item.productId}>
                    <span
                      className="activity-dot"
                      style={{
                        background:
                          item.availableQuantity === 0 ? "#bd6250" : "#b99a51",
                      }}
                    />
                    <span>
                      {item.productId}
                      <small>{item.availableQuantity} available</small>
                    </span>
                  </div>
                ))}
            </div>
          ) : (
            <p className="success-banner">
              All tracked products have more than five available units.
            </p>
          )}
        </div>
      </div>
    </>
  );
}
function StatCard({
  label,
  value,
  note,
}: {
  label: string;
  value: string | number;
  note: string;
}) {
  return (
    <div className="stat-card">
      <span className="stat-label">{label}</span>
      <div className="stat-value">{value}</div>
      <span className="stat-note">{note}</span>
    </div>
  );
}
function ActivityOrder({ order }: { order: Order }) {
  return (
    <div className="activity-item">
      <span className="activity-dot" />
      <span className="activity-copy">
        Order {order.orderId.slice(0, 8).toUpperCase()}
        <small>
          {new Date(order.createdAt).toLocaleDateString()} ·{" "}
          {formatMoney(order.totalAmount, order.currency)}
        </small>
      </span>
      <StatusPill status={order.status} />
    </div>
  );
}

```

---

## File: `frontend/admin-dashboard/src/pages.test.tsx`

```typescript
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ProductForm } from "./ProductForm";
import { calculateRevenue } from "./helpers";
import type { Order } from "@ecommerce/shared";
describe("ProductForm", () => {
  it("submits a backend-shaped product with parsed attributes", () => {
    const onSave = vi.fn();
    render(<ProductForm onSave={onSave} onCancel={vi.fn()} />);
    fireEvent.change(screen.getByLabelText("Product name"), {
      target: { value: "Arc lamp" },
    });
    fireEvent.change(screen.getByLabelText("Brand"), {
      target: { value: "Forma" },
    });
    fireEvent.change(screen.getByLabelText("Category"), {
      target: { value: "Lighting" },
    });
    fireEvent.change(screen.getByLabelText("Price (USD)"), {
      target: { value: "89.50" },
    });
    fireEvent.change(screen.getByLabelText(/Attributes/), {
      target: { value: "Material: Brass\nFinish: Satin" },
    });
    fireEvent.submit(
      screen.getByRole("button", { name: "Create product" }).closest("form")!,
    );
    expect(onSave).toHaveBeenCalledWith({
      name: "Arc lamp",
      description: "",
      brand: "Forma",
      category: "Lighting",
      price: 89.5,
      attributes: { Material: "Brass", Finish: "Satin" },
    });
  });
  it("does not submit a non-positive price", () => {
    const onSave = vi.fn();
    render(<ProductForm onSave={onSave} onCancel={vi.fn()} />);
    fireEvent.change(screen.getByLabelText("Product name"), {
      target: { value: "Arc lamp" },
    });
    fireEvent.change(screen.getByLabelText("Brand"), {
      target: { value: "Forma" },
    });
    fireEvent.change(screen.getByLabelText("Category"), {
      target: { value: "Lighting" },
    });
    fireEvent.change(screen.getByLabelText("Price (USD)"), {
      target: { value: "0" },
    });
    fireEvent.submit(
      screen.getByRole("button", { name: "Create product" }).closest("form")!,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Enter a price greater than zero",
    );
    expect(onSave).not.toHaveBeenCalled();
  });
});
describe("calculateRevenue", () => {
  it("counts confirmed order totals and excludes pending or cancelled orders", () => {
    const base = {
      orderId: "id",
      userId: "user",
      totalAmount: 20,
      currency: "USD",
      items: [],
      createdAt: "2026-01-01",
      updatedAt: "2026-01-01",
    };
    const orders = [
      { ...base, status: "CONFIRMED", totalAmount: 25 },
      { ...base, status: "PENDING_PAYMENT", totalAmount: 10 },
      { ...base, status: "CANCELLED", totalAmount: 50 },
    ] as Order[];
    expect(calculateRevenue(orders)).toBe(25);
  });
});

```

---

## File: `frontend/admin-dashboard/src/ProductForm.tsx`

```typescript
import { useState, type FormEvent, type ReactNode } from "react";
import { Button, Field, type Product } from "@ecommerce/shared";
export function ProductForm({
  product,
  onSave,
  onCancel,
  busy = false,
}: {
  product?: Product;
  onSave: (value: ProductInput) => void;
  onCancel: () => void;
  busy?: boolean;
}) {
  const [name, setName] = useState(product?.name ?? "");
  const [description, setDescription] = useState(product?.description ?? "");
  const [category, setCategory] = useState(product?.category ?? "");
  const [brand, setBrand] = useState(product?.brand ?? "");
  const [price, setPrice] = useState(
    product?.price != null ? String(product.price) : "",
  );
  const [attributes, setAttributes] = useState(
    Object.entries(product?.attributes ?? {})
      .map(([key, value]) => `${key}: ${value}`)
      .join("\n"),
  );
  const [error, setError] = useState("");
  const submit = (event: FormEvent) => {
    event.preventDefault();
    setError("");
    const amount = Number(price);
    if (!Number.isFinite(amount) || amount <= 0) {
      setError("Enter a price greater than zero.");
      return;
    }
    const attributeMap = Object.fromEntries(
      attributes
        .split("\n")
        .map((line) => line.split(":"))
        .filter((parts) => parts[0]?.trim() && parts[1]?.trim())
        .map(([key, ...value]) => [key.trim(), value.join(":").trim()]),
    );
    onSave({
      name: name.trim(),
      description: description.trim(),
      category: category.trim(),
      brand: brand.trim(),
      price: amount,
      attributes: attributeMap,
    });
  };
  return (
    <form className="form-stack" onSubmit={submit}>
      <div className="field-grid">
        <Field
          className="field-full"
          label="Product name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          maxLength={200}
          required
        />
        <Field
          label="Brand"
          value={brand}
          onChange={(event) => setBrand(event.target.value)}
          maxLength={100}
          required
        />
        <Field
          label="Category"
          value={category}
          onChange={(event) => setCategory(event.target.value)}
          maxLength={100}
          required
        />
        <Field
          label="Price (USD)"
          type="number"
          min="0.01"
          step="0.01"
          value={price}
          onChange={(event) => setPrice(event.target.value)}
          required
        />
        <label className="field field-full">
          <span>Description</span>
          <textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            maxLength={5000}
          />
        </label>
        <label className="field field-full">
          <span>Attributes</span>
          <textarea
            placeholder="Material: Oak\nFinish: Natural"
            value={attributes}
            onChange={(event) => setAttributes(event.target.value)}
          />
          <small>One attribute per line, in “Name: Value” format.</small>
        </label>
      </div>
      {error && (
        <div className="error-banner" role="alert">
          {error}
        </div>
      )}
      <div className="form-actions">
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button disabled={busy}>
          {busy ? "Saving…" : product ? "Save changes" : "Create product"}
        </Button>
      </div>
    </form>
  );
}
export interface ProductInput {
  name: string;
  description: string;
  category: string;
  brand: string;
  price: number;
  attributes: Record<string, string>;
}
export function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <div
      className="modal-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <div className="modal-header">
          <div>
            <p className="eyebrow">Maison catalog</p>
            <h2>{title}</h2>
          </div>
          <button
            className="modal-close"
            aria-label="Close dialog"
            onClick={onClose}
          >
            ×
          </button>
        </div>
        {children}
      </section>
    </div>
  );
}

```

---

## File: `frontend/admin-dashboard/src/ProductsPage.tsx`

```typescript
import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  EmptyState,
  ErrorState,
  Loading,
  PageTitle,
  ProductVisual,
  apiClient,
  formatMoney,
  notify,
  type Product,
} from "@ecommerce/shared";
import { ProductForm, Modal, type ProductInput } from "./ProductForm";
export function ProductsPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<Product | null | "new">(null);
  const productsQuery = useQuery({
    queryKey: ["admin-products"],
    queryFn: () => apiClient.products(0, 100),
  });
  const save = useMutation({
    mutationFn: (input: ProductInput) =>
      editing && editing !== "new"
        ? apiClient.updateProduct(editing.id, input)
        : apiClient.createProduct(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin-products"] });
      void queryClient.invalidateQueries({ queryKey: ["products"] });
      setEditing(null);
      notify("Catalog updated");
    },
    onError: (error) =>
      notify(
        error instanceof Error ? error.message : "Could not save product",
        "error",
      ),
  });
  const remove = useMutation({
    mutationFn: apiClient.deleteProduct,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin-products"] });
      notify("Product removed");
    },
    onError: (error) =>
      notify(
        error instanceof Error ? error.message : "Could not remove product",
        "error",
      ),
  });
  const filtered = useMemo(
    () =>
      productsQuery.data?.content.filter((product) =>
        `${product.name} ${product.brand} ${product.category}`
          .toLowerCase()
          .includes(search.toLowerCase()),
      ) ?? [],
    [productsQuery.data, search],
  );
  return (
    <>
      <PageTitle
        eyebrow="Catalog / assortment"
        title="Products"
        description="Keep the collection thoughtful, current, and well described."
        action={
          <Button onClick={() => setEditing("new")}>＋ New product</Button>
        }
      />
      <div className="admin-filters">
        <form
          className="search-input"
          onSubmit={(event) => event.preventDefault()}
        >
          <input
            aria-label="Filter products"
            placeholder="Filter by name, brand, or category"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <button aria-label="Filter">⌕</button>
        </form>
        <span className="small">{filtered.length} shown</span>
      </div>
      {productsQuery.isPending ? (
        <Loading label="Opening the catalog" />
      ) : productsQuery.isError ? (
        <ErrorState
          error={productsQuery.error}
          retry={() => void productsQuery.refetch()}
        />
      ) : filtered.length ? (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Piece</th>
                <th>Category</th>
                <th>Price</th>
                <th>Updated</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((product) => (
                <tr key={product.id}>
                  <td>
                    <div className="table-product">
                      <ProductVisual compact product={product} />
                      <span className="table-product-copy">
                        <strong>{product.name}</strong>
                        <small>{product.brand}</small>
                      </span>
                    </div>
                  </td>
                  <td>{product.category}</td>
                  <td>{formatMoney(product.price)}</td>
                  <td>
                    {product.updatedAt
                      ? new Date(product.updatedAt).toLocaleDateString()
                      : "—"}
                  </td>
                  <td>
                    <div className="product-actions">
                      <Button
                        className="button-small"
                        variant="secondary"
                        onClick={() => setEditing(product)}
                      >
                        Edit
                      </Button>
                      <Button
                        className="button-small"
                        variant="quiet"
                        onClick={() => {
                          if (
                            window.confirm(
                              `Remove “${product.name}” from the catalog?`,
                            )
                          )
                            remove.mutate(product.id);
                        }}
                      >
                        Delete
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <EmptyState
          title="No pieces found"
          body={
            search
              ? "Try another search."
              : "Create the first item in your collection."
          }
          action={
            !search && (
              <Button onClick={() => setEditing("new")}>
                Create a product
              </Button>
            )
          }
        />
      )}
      {editing && (
        <Modal
          title={editing === "new" ? "Add a new piece" : "Edit product"}
          onClose={() => setEditing(null)}
        >
          <ProductForm
            product={editing === "new" ? undefined : editing}
            onCancel={() => setEditing(null)}
            busy={save.isPending}
            onSave={(input) => save.mutate(input)}
          />
        </Modal>
      )}
    </>
  );
}

```

---

## File: `frontend/admin-dashboard/src/test-setup.ts`

```typescript
import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";
afterEach(cleanup);

```

---

## File: `frontend/admin-dashboard/tsconfig.json`

```json
{ "extends": "../tsconfig.json", "include": ["src", "vite.config.ts"] }

```

---

## File: `frontend/admin-dashboard/vite.config.ts`

```typescript
import { defineConfig } from "vitest/config";

export default defineConfig({
  server: { port: 3001 },
  test: { environment: "jsdom", setupFiles: "./src/test-setup.ts", css: true },
});

```

---

## File: `frontend/customer-app/package.json`

```json
{
  "name": "@ecommerce/customer-app",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite --host 0.0.0.0 --port 3000",
    "build": "tsc --noEmit && vite build",
    "preview": "vite preview --host 0.0.0.0 --port 3000",
    "test": "vitest run",
    "typecheck": "tsc --noEmit"
  },
  "dependencies": {
    "@ecommerce/shared": "*"
  },
  "devDependencies": {
    "@testing-library/react": "^16.1.0",
    "@testing-library/user-event": "^14.5.2",
    "@types/react": "^18.3.12",
    "@types/react-dom": "^18.3.1",
    "jsdom": "^25.0.1",
    "typescript": "~5.6.3",
    "vite": "^5.4.11",
    "vitest": "^2.1.5"
  }
}

```

---

## File: `frontend/customer-app/src/AccountPage.tsx`

```typescript
import { Navigate, useNavigate } from "react-router-dom";
import { Button, PageTitle, StatusPill, useSession } from "@ecommerce/shared";
export function AccountPage() {
  const { user, signOut } = useSession();
  const navigate = useNavigate();
  if (!user)
    return <Navigate to="/login" replace state={{ from: "/account" }} />;
  return (
    <div className="panel account-card">
      <PageTitle
        eyebrow="Your account"
        title="Welcome back."
        description="Your details are kept safe and your orders are always close by."
      />
      <div className="summary-line">
        <span>Email</span>
        <strong>{user.email}</strong>
      </div>
      <div className="summary-line">
        <span>Account type</span>
        <StatusPill status={user.role} />
      </div>
      <div className="form-actions">
        <Button variant="secondary" onClick={() => navigate("/orders")}>
          View orders
        </Button>
        <Button
          variant="quiet"
          onClick={() => void signOut().then(() => navigate("/"))}
        >
          Sign out
        </Button>
      </div>
    </div>
  );
}

```

---

## File: `frontend/customer-app/src/api.test.ts`

```typescript
import { afterEach, describe, expect, it, vi } from "vitest";
import { api, clearSession, saveSession } from "@ecommerce/shared";
afterEach(() => {
  clearSession();
  vi.restoreAllMocks();
});
describe("shared API client", () => {
  it("returns decoded successful responses and sends JSON content type", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(
        new Response(JSON.stringify({ ok: true }), { status: 200 }),
      );
    await expect(
      api<{
        ok: boolean;
      }>("/health", { authenticated: false }),
    ).resolves.toEqual({ ok: true });
    expect(fetchMock.mock.calls[0][1]?.headers).toBeInstanceOf(Headers);
  });
  it("refreshes an expired access token once and retries the original request", async () => {
    saveSession({
      accessToken: "expired",
      refreshToken: "refresh-1",
      tokenType: "Bearer",
      expiresIn: 900,
    });
    const fetchMock = vi.spyOn(globalThis, "fetch");
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ message: "Expired" }), { status: 401 }),
    );
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          accessToken: "fresh",
          refreshToken: "refresh-2",
          tokenType: "Bearer",
          expiresIn: 900,
        }),
        { status: 200 },
      ),
    );
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ id: "user-1" }), { status: 200 }),
    );
    await expect(
      api<{
        id: string;
      }>("/private"),
    ).resolves.toEqual({
      id: "user-1",
    });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(
      new Headers(fetchMock.mock.calls[2][1]?.headers).get("Authorization"),
    ).toBe("Bearer fresh");
  });
  it("preserves backend validation details on failed requests", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ detail: "Quantity must be positive" }), {
        status: 422,
      }),
    );
    await expect(
      api("/api/cart", { authenticated: false }),
    ).rejects.toMatchObject({
      status: 422,
      message: "Quantity must be positive",
    });
  });
});

```

---

## File: `frontend/customer-app/src/AuthPage.tsx`

```typescript
import { useState, type FormEvent, type ReactNode } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import {
  Button,
  Field,
  Footer,
  SiteHeader,
  ToastRegion,
  useSession,
} from "@ecommerce/shared";
import { messageOf } from "./helpers";
export function AuthPage({ mode }: { mode: "login" | "register" }) {
  const { user, ready, signIn, signUp } = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const destination =
    (
      location.state as {
        from?: string;
      } | null
    )?.from || "/account";
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  if (ready && user) return <Navigate to={destination} replace />;
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      if (mode === "login") await signIn(email, password);
      else await signUp(email, password);
      navigate(destination, { replace: true });
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  };
  const register = mode === "register";
  return (
    <div className="auth-layout">
      <div className="auth-art">
        <div className="auth-art-copy">
          <p className="eyebrow">The Maison list</p>
          <h2>Good taste is better shared.</h2>
          <p>
            Save your edit, follow your orders, and find your way back to the
            things you love.
          </p>
        </div>
      </div>
      <div className="auth-panel">
        <div className="auth-card">
          <p className="eyebrow">
            {register ? "Start your collection" : "Welcome back"}
          </p>
          <h1>{register ? "Make yourself at home." : "Come on in."}</h1>
          <p className="muted">
            {register
              ? "Create an account to keep all your good finds together."
              : "Sign in to find your saved pieces and orders."}
          </p>
          <form onSubmit={submit}>
            <Field
              label="Email address"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
              maxLength={254}
            />
            <Field
              label="Password"
              type="password"
              autoComplete={register ? "new-password" : "current-password"}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              minLength={register ? 8 : 1}
              maxLength={100}
              hint={register ? "At least 8 characters" : undefined}
            />
            {error && (
              <div className="error-banner" role="alert">
                {error}
              </div>
            )}
            <Button className="button-wide" disabled={busy}>
              {busy ? "One moment…" : register ? "Create account" : "Sign in"}
            </Button>
          </form>
          <p className="auth-switch">
            {register ? "Already have an account?" : "New to Maison?"}
            <a href={register ? "/login" : "/register"}>
              {register ? "Sign in" : "Create an account"}
            </a>
          </p>
        </div>
      </div>
    </div>
  );
}
export function AuthFrame({ children }: { children: ReactNode }) {
  return (
    <div className="app-shell">
      <SiteHeader />
      <main className="app-main">{children}</main>
      <Footer />
      <ToastRegion />
    </div>
  );
}

```

---

## File: `frontend/customer-app/src/CartPage.tsx`

```typescript
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  EmptyState,
  ErrorState,
  Loading,
  PageTitle,
  ProductVisual,
  apiClient,
  formatMoney,
  notify,
  useSession,
} from "@ecommerce/shared";
import { messageOf } from "./helpers";
export function CartPage() {
  const { user } = useSession();
  const queryClient = useQueryClient();
  const cartQuery = useQuery({
    queryKey: ["cart", user?.id],
    queryFn: () => apiClient.cart(user!.id),
  });
  const update = useMutation({
    mutationFn: ({
      productId,
      quantity,
    }: {
      productId: string;
      quantity: number;
    }) => apiClient.updateCartItem(user!.id, productId, quantity),
    onSuccess: (cart) => queryClient.setQueryData(["cart", user!.id], cart),
    onError: (error) => notify(messageOf(error), "error"),
  });
  const remove = useMutation({
    mutationFn: (productId: string) =>
      apiClient.removeCartItem(user!.id, productId),
    onSuccess: (cart) => {
      queryClient.setQueryData(["cart", user!.id], cart);
      notify("Item removed");
    },
    onError: (error) => notify(messageOf(error), "error"),
  });
  if (cartQuery.isPending) return <Loading label="Opening your bag" />;
  if (cartQuery.isError)
    return (
      <ErrorState
        error={cartQuery.error}
        retry={() => void cartQuery.refetch()}
      />
    );
  const items = cartQuery.data.items;
  const subtotal = items.reduce(
    (sum, item) => sum + item.price * item.quantity,
    0,
  );
  if (!items.length)
    return (
      <>
        <PageTitle eyebrow="Your edit" title="Your bag" />
        <EmptyState
          className="cart-empty"
          icon="↗"
          title="Your bag is taking a breather"
          body="Find a piece that feels like you and it will appear here."
          action={
            <Button onClick={() => window.location.assign("/")}>
              Browse the collection
            </Button>
          }
        />
      </>
    );
  return (
    <>
      <PageTitle
        eyebrow="Your edit"
        title="Your bag"
        description={`${items.length} ${items.length === 1 ? "piece" : "pieces"} selected.`}
      />
      <div className="cart-layout">
        <div className="cart-list">
          {items.map((item) => (
            <div className="cart-row" key={item.product_id}>
              <ProductVisual
                compact
                product={{
                  id: item.product_id,
                  name: item.name,
                  category: "Selected",
                  brand: item.name,
                  price: item.price,
                }}
              />
              <div className="cart-row-copy">
                <strong>{item.name}</strong>
                <small>{formatMoney(item.price)} each</small>
              </div>
              <div className="cart-quantity">
                <button
                  aria-label={`Decrease ${item.name} quantity`}
                  disabled={item.quantity <= 1 || update.isPending}
                  onClick={() =>
                    update.mutate({
                      productId: item.product_id,
                      quantity: item.quantity - 1,
                    })
                  }
                >
                  −
                </button>
                <span>{item.quantity}</span>
                <button
                  aria-label={`Increase ${item.name} quantity`}
                  disabled={update.isPending}
                  onClick={() =>
                    update.mutate({
                      productId: item.product_id,
                      quantity: item.quantity + 1,
                    })
                  }
                >
                  ＋
                </button>
              </div>
              <strong className="cart-row-price">
                {formatMoney(item.price * item.quantity)}
              </strong>
              <Button
                variant="quiet"
                aria-label={`Remove ${item.name}`}
                onClick={() => remove.mutate(item.product_id)}
              >
                ×
              </Button>
            </div>
          ))}
        </div>
        <aside className="summary-card">
          <h2>Your total</h2>
          <div className="summary-line">
            <span>Subtotal</span>
            <span>{formatMoney(subtotal)}</span>
          </div>
          <div className="summary-line">
            <span>Delivery</span>
            <span>
              {subtotal >= 100 ? "Complimentary" : "Calculated at checkout"}
            </span>
          </div>
          <div className="summary-line summary-total">
            <span>Total</span>
            <span>{formatMoney(subtotal)}</span>
          </div>
          <p className="summary-note">
            Final shipping and taxes are confirmed at checkout. Your payment is
            processed securely by Stripe.
          </p>
          <Button
            className="button-wide"
            onClick={() => window.location.assign("/checkout")}
          >
            Continue to checkout <span>→</span>
          </Button>
        </aside>
      </div>
    </>
  );
}

```

---

## File: `frontend/customer-app/src/CheckoutPage.tsx`

```typescript
import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
  EmptyState,
  ErrorState,
  Loading,
  PageTitle,
  CardElement,
  Elements,
  useElements,
  useStripe,
  apiClient,
  formatMoney,
  notify,
  useSession,
} from "@ecommerce/shared";
import { messageOf } from "./helpers";
import { stripePromise } from "./stripe";
export function CheckoutPage() {
  const { user } = useSession();
  const cart = useQuery({
    queryKey: ["cart", user?.id],
    queryFn: () => apiClient.cart(user!.id),
  });
  if (cart.isPending) return <Loading label="Preparing your checkout" />;
  if (cart.isError)
    return <ErrorState error={cart.error} retry={() => void cart.refetch()} />;
  if (!cart.data.items.length)
    return (
      <EmptyState
        title="Your bag is empty"
        body="Add something from the collection before checking out."
        action={
          <Button onClick={() => window.location.assign("/")}>
            Explore the collection
          </Button>
        }
      />
    );
  const total = cart.data.items.reduce(
    (sum, item) => sum + item.price * item.quantity,
    0,
  );
  return (
    <>
      <PageTitle
        eyebrow="Almost yours"
        title="A good choice."
        description="Review your items and complete your payment securely."
      />
      <div className="cart-layout">
        <div className="panel">
          <div className="panel-title">
            <h2>Payment</h2>
            <span className="eyebrow-pill">Secure checkout</span>
          </div>
          {stripePromise ? (
            <Elements
              stripe={stripePromise}
              options={{
                appearance: {
                  theme: "stripe",
                  variables: {
                    colorPrimary: "#596347",
                    borderRadius: "9px",
                    fontFamily: "DM Sans, sans-serif",
                  },
                },
              }}
            >
              <CheckoutForm items={cart.data.items} />
            </Elements>
          ) : (
            <div className="stripe-config-note">
              Card payments need a Stripe publishable test key. Set{" "}
              <code>VITE_STRIPE_PUBLISHABLE_KEY</code> in{" "}
              <code>frontend/customer-app/.env.local</code>, then restart the
              customer app.
            </div>
          )}
        </div>
        <aside className="summary-card">
          <h2>In your bag</h2>
          {cart.data.items.map((item) => (
            <div className="summary-line" key={item.product_id}>
              <span>
                {item.name} × {item.quantity}
              </span>
              <span>{formatMoney(item.price * item.quantity)}</span>
            </div>
          ))}
          <div className="summary-line summary-total">
            <span>Total due</span>
            <span>{formatMoney(total)}</span>
          </div>
          <p className="summary-note">
            We verify stock before charging your card. Your order status will
            update as each step completes.
          </p>
        </aside>
      </div>
    </>
  );
}
export function CheckoutForm({
  items,
}: {
  items: {
    product_id: string;
    quantity: number;
    price: number;
    name: string;
  }[];
}) {
  const stripe = useStripe();
  const elements = useElements();
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [requestKey] = useState(
    () =>
      globalThis.crypto?.randomUUID?.() ??
      `checkout-${Date.now()}-${Math.random().toString(36).slice(2)}`,
  );
  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    if (!stripe || !elements) {
      setError("Secure card entry is still loading.");
      return;
    }
    const card = elements.getElement(CardElement);
    if (!card) {
      setError("Enter your card details to continue.");
      return;
    }
    setBusy(true);
    try {
      const result = await stripe.createPaymentMethod({ type: "card", card });
      if (result.error || !result.paymentMethod)
        throw new Error(result.error?.message ?? "Could not verify your card.");
      const order = await apiClient.createOrder(
        items.map((item) => ({
          productId: item.product_id,
          quantity: item.quantity,
        })),
        result.paymentMethod.id,
        requestKey,
      );
      notify("Your order is in motion");
      navigate(`/orders/${encodeURIComponent(order.orderId)}`);
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  };
  const cardOptions = {
    hidePostalCode: true,
    style: {
      base: {
        fontSize: "15px",
        color: "#20221e",
        fontFamily: "DM Sans, sans-serif",
        "::placeholder": { color: "#9a9c94" },
      },
    },
  };
  return (
    <form className="form-stack" onSubmit={onSubmit}>
      <label className="field">
        <span>Card details</span>
        <div className="stripe-card">
          <CardElement options={cardOptions} />
        </div>
      </label>
      <p className="small">
        Use Stripe test cards while developing. Your card details go directly to
        Stripe and never touch our servers.
      </p>
      {error && (
        <div className="error-banner" role="alert">
          {error}
        </div>
      )}
      <Button className="button-wide" disabled={!stripe || busy}>
        {busy ? "Placing your order…" : "Place order securely"}
      </Button>
    </form>
  );
}

```

---

## File: `frontend/customer-app/src/components.test.tsx`

```typescript
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { ProductCard, StatusPill } from "@ecommerce/shared";
const product = {
  id: "lamp-1",
  name: "Arc table lamp",
  brand: "Forma",
  category: "Lighting",
  price: 129.5,
};
describe("ProductCard", () => {
  it("shows product details and calls add when the add control is activated", () => {
    const onAdd = vi.fn();
    render(
      <MemoryRouter>
        <ProductCard product={product} onAdd={onAdd} />
      </MemoryRouter>,
    );
    expect(screen.getByText("Arc table lamp")).toBeInTheDocument();
    expect(screen.getByText("$129.50")).toBeInTheDocument();
    fireEvent.click(
      screen.getByRole("button", { name: "Add Arc table lamp to cart" }),
    );
    expect(onAdd).toHaveBeenCalledOnce();
  });
  it("exposes a wishlist action with its current state", () => {
    const onFavorite = vi.fn();
    render(
      <MemoryRouter>
        <ProductCard product={product} onFavorite={onFavorite} favorite />
      </MemoryRouter>,
    );
    const button = screen.getByRole("button", { name: "Remove from wishlist" });
    fireEvent.click(button);
    expect(onFavorite).toHaveBeenCalledOnce();
  });
});
describe("StatusPill", () => {
  it("formats saga states and marks successful orders accessibly", () => {
    render(<StatusPill status="CONFIRMED" />);
    expect(screen.getByText("confirmed")).toBeInTheDocument();
    expect(screen.getByText("confirmed").closest("span")).toHaveClass(
      "status-success",
    );
  });
});

```

---

## File: `frontend/customer-app/src/CustomerApp.tsx`

```typescript
import { Navigate, Route, Routes } from "react-router-dom";
import { StoreLayout, RequireCustomer } from "./StoreLayout";
import { HomePage } from "./HomePage";
import { ProductPage } from "./ProductPage";
import { WishlistPage } from "./WishlistPage";
import { CartPage } from "./CartPage";
import { CheckoutPage } from "./CheckoutPage";
import { OrdersPage } from "./OrdersPage";
import { OrderDetailPage } from "./OrderDetailPage";
import { AccountPage } from "./AccountPage";
import { AuthPage, AuthFrame } from "./AuthPage";
export function CustomerApp() {
  return (
    <Routes>
      <Route
        path="/login"
        element={
          <AuthFrame>
            <AuthPage mode="login" />
          </AuthFrame>
        }
      />
      <Route
        path="/register"
        element={
          <AuthFrame>
            <AuthPage mode="register" />
          </AuthFrame>
        }
      />
      <Route element={<StoreLayout />}>
        <Route index element={<HomePage />} />
        <Route path="products/:productId" element={<ProductPage />} />
        <Route
          path="wishlist"
          element={
            <RequireCustomer>
              <WishlistPage />
            </RequireCustomer>
          }
        />
        <Route
          path="cart"
          element={
            <RequireCustomer>
              <CartPage />
            </RequireCustomer>
          }
        />
        <Route
          path="checkout"
          element={
            <RequireCustomer>
              <CheckoutPage />
            </RequireCustomer>
          }
        />
        <Route
          path="orders"
          element={
            <RequireCustomer>
              <OrdersPage />
            </RequireCustomer>
          }
        />
        <Route
          path="orders/:orderId"
          element={
            <RequireCustomer>
              <OrderDetailPage />
            </RequireCustomer>
          }
        />
        <Route path="account" element={<AccountPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}

```

---

## File: `frontend/customer-app/src/helpers.ts`

```typescript
export function isFinalOrder(status?: string) {
  return status === "CONFIRMED" || status === "CANCELLED";
}
export function messageOf(error: unknown) {
  return error instanceof Error
    ? error.message
    : "Something went wrong. Please try again.";
}
export function normalizeProduct(
  product: import("@ecommerce/shared").Product | Record<string, unknown>,
): import("@ecommerce/shared").Product {
  const record = product as Record<string, unknown>;
  const typed = product as import("@ecommerce/shared").Product;
  return {
    ...typed,
    id: typed.id || String(record.productId ?? record.product_id ?? ""),
    name: typed.name || "Untitled piece",
    category: typed.category || "Collection",
    brand: typed.brand || "Maison",
    price: Number(typed.price) || 0,
    rating: typed.rating == null ? null : Number(typed.rating),
    reviewCount: Number(typed.reviewCount ?? record.review_count ?? 0),
  };
}

```

---

## File: `frontend/customer-app/src/HomePage.tsx`

```typescript
import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  EmptyState,
  ErrorState,
  Loading,
  ProductCard,
  SectionHeading,
  SelectField,
  apiClient,
  notify,
  useSession,
} from "@ecommerce/shared";
import { messageOf, normalizeProduct } from "./helpers";
export function HomePage() {
  const { user } = useSession();
  const [params, setParams] = useSearchParams();
  const query = params.get("q") ?? "";
  const [category, setCategory] = useState("");
  const [brand, setBrand] = useState("");
  const [sort, setSort] = useState("relevance");
  const [page, setPage] = useState(0);
  const navigate = useNavigate();
  const productQuery = useQuery({
    queryKey: ["shop", query, category, brand, sort, page],
    queryFn: async () => {
      if (query || category || brand || sort !== "relevance") {
        const result = await apiClient.search({
          q: query || undefined,
          category: category || undefined,
          brand: brand || undefined,
          sort,
          page,
          size: 16,
        });
        return {
          products: result.content.map(normalizeProduct),
          total: result.totalElements,
          pages: result.totalPages,
          facets: result.facets,
        };
      }
      const result = await apiClient.products(page, 16);
      return {
        products: result.content,
        total: result.totalElements,
        pages: result.totalPages,
        facets: undefined,
      };
    },
  });
  const favoriteQuery = useQuery({
    queryKey: ["wishlist-ids", user?.id],
    queryFn: () => apiClient.wishlist(0, 100),
    enabled: !!user,
  });
  const queryClient = useQueryClient();
  const addCart = useMutation({
    mutationFn: (productId: string) => {
      if (!user) {
        navigate("/login", { state: { from: "/" } });
        throw new Error("Sign in to add products to your bag.");
      }
      return apiClient.addCartItem(user.id, productId);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["cart"] });
      notify("Added to your bag");
    },
    onError: (error) => notify(messageOf(error), "error"),
  });
  const favoriteMutation = useMutation({
    mutationFn: async (productId: string) => {
      if (!user) {
        navigate("/login", { state: { from: "/" } });
        throw new Error("Sign in to save products.");
      }
      const saved = favoriteQuery.data?.items.some(
        (item) => item.product_id === productId,
      );
      if (saved) await apiClient.removeWishlist(productId);
      else await apiClient.addWishlist(productId);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wishlist-ids"] });
      notify("Wishlist updated");
    },
    onError: (error) => notify(messageOf(error), "error"),
  });
  const favorites = new Set(
    favoriteQuery.data?.items.map((item) => item.product_id) ?? [],
  );
  const products = productQuery.data?.products ?? [];
  const categories = [
    ...new Set(products.map((item) => item.category).filter(Boolean)),
  ];
  const brands = [
    ...new Set(products.map((item) => item.brand).filter(Boolean)),
  ];
  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <p className="eyebrow">A little more considered</p>
          <h1>Good things for the way you live.</h1>
          <p>
            Objects with a point of view, chosen to make the everyday feel a bit
            more special.
          </p>
          <Button
            onClick={() =>
              document
                .getElementById("collection")
                ?.scrollIntoView({ behavior: "smooth" })
            }
          >
            Explore the collection <span>↓</span>
          </Button>
        </div>
        <div className="hero-art">
          <span className="hero-spark" />
        </div>
      </section>
      <div id="collection">
        <SectionHeading
          title={query ? `Results for “${query}”` : "The considered edit"}
          detail={`${productQuery.data?.total ?? "Curated"} pieces to make room for.`}
        />
        <div className="search-facets">
          <span className="filter-label">Refine</span>
          <SelectField
            label="Category"
            value={category}
            onChange={(event) => {
              setCategory(event.target.value);
              setPage(0);
            }}
          >
            <option value="">All categories</option>
            {categories.map((value) => (
              <option key={value}>{value}</option>
            ))}
          </SelectField>
          <SelectField
            label="Brand"
            value={brand}
            onChange={(event) => {
              setBrand(event.target.value);
              setPage(0);
            }}
          >
            <option value="">All brands</option>
            {brands.map((value) => (
              <option key={value}>{value}</option>
            ))}
          </SelectField>
          <SelectField
            label="Sort by"
            value={sort}
            onChange={(event) => {
              setSort(event.target.value);
              setPage(0);
            }}
          >
            <option value="relevance">Recommended</option>
            <option value="price_asc">Price: low to high</option>
            <option value="price_desc">Price: high to low</option>
            <option value="name_asc">Name</option>
            <option value="rating_desc">Top rated</option>
          </SelectField>
        </div>
        {productQuery.isPending ? (
          <Loading label="Finding your next favorite" />
        ) : productQuery.isError ? (
          <ErrorState
            error={productQuery.error}
            retry={() => void productQuery.refetch()}
          />
        ) : products.length ? (
          <div className="product-grid">
            {products.map((product) => (
              <ProductCard
                key={product.id}
                product={product}
                favorite={favorites.has(product.id)}
                onAdd={() => addCart.mutate(product.id)}
                onFavorite={() => favoriteMutation.mutate(product.id)}
              />
            ))}
          </div>
        ) : (
          <EmptyState
            title="Nothing in this edit yet"
            body="Try a different search or clear the filters to see more of the collection."
            action={
              <Button
                variant="secondary"
                onClick={() => {
                  setParams({});
                  setCategory("");
                  setBrand("");
                  setSort("relevance");
                }}
              >
                Clear filters
              </Button>
            }
          />
        )}
        {!!productQuery.data?.pages && productQuery.data.pages > 1 && (
          <div className="pagination">
            <Button
              variant="secondary"
              disabled={page === 0}
              onClick={() => setPage((value) => value - 1)}
            >
              ← Previous
            </Button>
            <span>
              Page {page + 1} of {productQuery.data.pages}
            </span>
            <Button
              variant="secondary"
              disabled={page + 1 >= productQuery.data.pages}
              onClick={() => setPage((value) => value + 1)}
            >
              Next →
            </Button>
          </div>
        )}
      </div>
    </>
  );
}

```

---

## File: `frontend/customer-app/src/main.tsx`

```typescript
import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { SessionProvider } from "@ecommerce/shared";
import { CustomerApp } from "./CustomerApp";
import "@ecommerce/shared/styles.css";
import "./storefront.css";
const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 20000, refetchOnWindowFocus: false, retry: 1 },
  },
});
ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <BrowserRouter>
          <CustomerApp />
        </BrowserRouter>
      </SessionProvider>
    </QueryClientProvider>
  </React.StrictMode>,
);

```

---

## File: `frontend/customer-app/src/OrderDetailPage.tsx`

```typescript
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ApiError,
  Button,
  ErrorState,
  Loading,
  PageTitle,
  StatusPill,
  apiClient,
  formatMoney,
  notify,
  useSession,
} from "@ecommerce/shared";
import { isFinalOrder, messageOf } from "./helpers";
import { stripePromise } from "./stripe";
export function OrderDetailPage() {
  const { orderId = "" } = useParams();
  const { user } = useSession();
  const queryClient = useQueryClient();
  const orderQuery = useQuery({
    queryKey: ["order", orderId],
    queryFn: () => apiClient.order(orderId),
    refetchInterval: (query) =>
      isFinalOrder(query.state.data?.status) ? false : 2200,
  });
  const order = orderQuery.data;
  const paymentQuery = useQuery({
    queryKey: ["payment", orderId],
    queryFn: () => apiClient.payment(orderId),
    enabled: !!order && ["PENDING_PAYMENT", "CONFIRMED"].includes(order.status),
    retry: (count, error) =>
      error instanceof ApiError && error.status === 404 && count < 8,
    retryDelay: 1000,
    refetchInterval: (query) =>
      query.state.data &&
      ["SUCCEEDED", "FAILED"].includes(query.state.data.status)
        ? false
        : 2200,
  });
  const cancel = useMutation({
    mutationFn: () => apiClient.cancelOrder(orderId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["order", orderId] });
      notify("Order cancelled");
    },
    onError: (error) => notify(messageOf(error), "error"),
  });
  useEffect(() => {
    if (order?.status === "CONFIRMED" && user)
      void apiClient
        .clearCart(user.id)
        .then(() =>
          queryClient.invalidateQueries({ queryKey: ["cart", user.id] }),
        )
        .catch(() => undefined);
  }, [order?.status, user, queryClient]);
  if (orderQuery.isPending) return <Loading label="Opening your order" />;
  if (orderQuery.isError || !order)
    return (
      <ErrorState
        error={orderQuery.error ?? new Error("Order not found")}
        retry={() => void orderQuery.refetch()}
      />
    );
  const stage =
    order.status === "CANCELLED"
      ? -1
      : order.status === "CONFIRMED"
        ? 4
        : order.status === "PENDING_PAYMENT"
          ? 2
          : 1;
  return (
    <>
      <PageTitle
        eyebrow="Order tracking"
        title={
          order.status === "CONFIRMED"
            ? "It’s confirmed."
            : order.status === "CANCELLED"
              ? "Order update"
              : "We’re on it."
        }
        description={`Order ${order.orderId}`}
        action={<StatusPill status={order.status} />}
      />
      <div className="order-timeline">
        {[
          "Order placed",
          "Stock reserved",
          "Payment confirmed",
          "Ready for you",
        ].map((label, index) => (
          <div
            className={`timeline-node ${stage > index ? "done" : ""}`}
            key={label}
          >
            {label}
          </div>
        ))}
      </div>
      <div className="cart-layout">
        <section className="panel">
          <div className="panel-title">
            <h2>Items in this order</h2>
            <span className="small">
              Placed {new Date(order.createdAt).toLocaleString()}
            </span>
          </div>
          <div className="order-items">
            {order.items.map((item) => (
              <div className="order-item-line" key={item.productId}>
                <span>
                  {item.productName} × {item.quantity}
                </span>
                <strong>{formatMoney(item.lineTotal, order.currency)}</strong>
              </div>
            ))}
          </div>
          {order.status === "PENDING_INVENTORY" && (
            <div className="success-banner">
              We’re checking stock with our warehouse partners. This page
              updates automatically.
            </div>
          )}
          {order.status === "PENDING_PAYMENT" && (
            <div className="success-banner">
              Stock is reserved. Your payment is being confirmed securely.
            </div>
          )}
          {paymentQuery.data?.status === "REQUIRES_ACTION" &&
            paymentQuery.data.clientSecret && (
              <StripeAction clientSecret={paymentQuery.data.clientSecret} />
            )}
          {paymentQuery.data?.status === "FAILED" && (
            <div className="error-banner">
              Payment wasn’t completed. {paymentQuery.data.failureReason}
            </div>
          )}
          {order.status === "PENDING_INVENTORY" && (
            <Button
              variant="secondary"
              disabled={cancel.isPending}
              onClick={() => cancel.mutate()}
            >
              Cancel order
            </Button>
          )}
        </section>
        <aside className="summary-card">
          <h2>Order summary</h2>
          <div className="summary-line">
            <span>Items</span>
            <span>{order.items.length}</span>
          </div>
          <div className="summary-line summary-total">
            <span>Total</span>
            <span>{formatMoney(order.totalAmount, order.currency)}</span>
          </div>
          <p className="summary-note">
            You’ll receive an email when the order is confirmed or if we need to
            make an adjustment.
          </p>
        </aside>
      </div>
    </>
  );
}
export function StripeAction({ clientSecret }: { clientSecret: string }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const onConfirm = async () => {
    if (!stripePromise) {
      setError("Stripe is not configured for this storefront.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const stripe = await stripePromise;
      if (!stripe) throw new Error("Stripe could not be loaded.");
      const result = await stripe.confirmCardPayment(clientSecret);
      if (result.error)
        throw new Error(
          result.error.message ?? "The payment could not be confirmed.",
        );
      notify("Verification complete. Updating your order…");
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="panel">
      <h3>One last secure check</h3>
      <p className="small">
        Your bank needs you to verify this payment before it can complete.
      </p>
      {error && (
        <div role="alert" className="error-banner">
          {error}
        </div>
      )}
      <Button disabled={busy} onClick={() => void onConfirm()}>
        {busy ? "Verifying…" : "Complete secure verification"}
      </Button>
    </div>
  );
}

```

---

## File: `frontend/customer-app/src/OrdersPage.tsx`

```typescript
import { useQuery } from "@tanstack/react-query";
import {
  Button,
  EmptyState,
  ErrorState,
  Loading,
  PageTitle,
  StatusPill,
  apiClient,
  formatMoney,
  useSession,
  type Order,
} from "@ecommerce/shared";
export function OrdersPage() {
  const { user } = useSession();
  const orders = useQuery({
    queryKey: ["orders", user?.id],
    queryFn: () => apiClient.orders(0, 50),
  });
  if (orders.isPending) return <Loading label="Finding your orders" />;
  if (orders.isError)
    return (
      <ErrorState error={orders.error} retry={() => void orders.refetch()} />
    );
  return (
    <>
      <PageTitle
        eyebrow="The story so far"
        title="Your orders"
        description="Keep an eye on every order, from the first check to the front door."
      />
      {orders.data.content.length ? (
        <div className="order-list">
          {orders.data.content.map((order) => (
            <OrderCard order={order} key={order.orderId} />
          ))}
        </div>
      ) : (
        <EmptyState
          title="Nothing ordered just yet"
          body="When you find something worth keeping, we’ll keep its details here."
          action={
            <Button onClick={() => window.location.assign("/")}>
              Find your first piece
            </Button>
          }
        />
      )}
    </>
  );
}
function OrderCard({ order }: { order: Order }) {
  return (
    <article className="order-card">
      <div>
        <strong>Order {order.orderId.slice(0, 8).toUpperCase()}</strong>
        <small>
          {new Date(order.createdAt).toLocaleDateString(undefined, {
            dateStyle: "long",
          })}{" "}
          · {order.items.length} items ·{" "}
          {formatMoney(order.totalAmount, order.currency)}
        </small>
      </div>
      <div className="order-actions">
        <StatusPill status={order.status} />
        <a
          className="text-link"
          href={`/orders/${encodeURIComponent(order.orderId)}`}
        >
          Details ↗
        </a>
      </div>
    </article>
  );
}

```

---

## File: `frontend/customer-app/src/ProductPage.tsx`

```typescript
import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  EmptyState,
  ErrorState,
  Field,
  Loading,
  ProductVisual,
  SectionHeading,
  SelectField,
  apiClient,
  formatMoney,
  notify,
  useSession,
  type Review,
} from "@ecommerce/shared";
import { messageOf } from "./helpers";
export function ProductPage() {
  const { productId = "" } = useParams();
  const { user } = useSession();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const productQuery = useQuery({
    queryKey: ["product", productId],
    queryFn: () => apiClient.product(productId),
  });
  const reviewsQuery = useQuery({
    queryKey: ["reviews", productId],
    queryFn: () => apiClient.reviews(productId),
  });
  const cartMutation = useMutation({
    mutationFn: () => {
      if (!user) {
        navigate("/login", { state: { from: `/products/${productId}` } });
        throw new Error("Sign in to add products to your bag.");
      }
      return apiClient.addCartItem(user.id, productId);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["cart"] });
      notify("Added to your bag");
    },
    onError: (error) => notify(messageOf(error), "error"),
  });
  const favoriteMutation = useMutation({
    mutationFn: async () => {
      if (!user) {
        navigate("/login", { state: { from: `/products/${productId}` } });
        throw new Error("Sign in to save products.");
      }
      const items = await apiClient.wishlist();
      return items.items.some((item) => item.product_id === productId)
        ? apiClient.removeWishlist(productId)
        : apiClient.addWishlist(productId);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wishlist"] });
      notify("Wishlist updated");
    },
    onError: (error) => notify(messageOf(error), "error"),
  });
  const product = productQuery.data;
  if (productQuery.isPending) return <Loading label="Opening the details" />;
  if (productQuery.isError || !product)
    return (
      <ErrorState
        error={productQuery.error ?? new Error("Product not found")}
        retry={() => void productQuery.refetch()}
      />
    );
  return (
    <>
      <div className="product-detail">
        <div className="detail-visual">
          <ProductVisual product={product} />
        </div>
        <div className="detail-copy">
          <p className="eyebrow">
            {product.brand} / {product.category}
          </p>
          <h1>{product.name}</h1>
          <p className="rating-stars">
            {product.rating
              ? `★ ${product.rating.toFixed(1)}`
              : "✳ New arrival"}{" "}
            <span className="small">
              {product.reviewCount ? `(${product.reviewCount} reviews)` : ""}
            </span>
          </p>
          <div className="detail-price">{formatMoney(product.price)}</div>
          <p className="detail-description product-description">
            {product.description ||
              "A considered addition to your everyday. Made with care, chosen to last."}
          </p>
          {!!product.attributes && (
            <div className="detail-attributes">
              {Object.entries(product.attributes).map(([key, value]) => (
                <div className="detail-attribute" key={key}>
                  <strong>{key}</strong>
                  {value}
                </div>
              ))}
            </div>
          )}
          <div className="product-copy">
            <Button
              onClick={() => cartMutation.mutate()}
              disabled={cartMutation.isPending}
            >
              Add to bag · {formatMoney(product.price)}
            </Button>
            <Button
              variant="secondary"
              onClick={() => favoriteMutation.mutate()}
            >
              ♡ Save to wishlist
            </Button>
            <span className="product-stock-note">
              Complimentary delivery on orders over $100
            </span>
          </div>
        </div>
      </div>
      <section>
        <SectionHeading
          title="Notes from customers"
          detail={
            reviewsQuery.data
              ? `${reviewsQuery.data.review_count} verified reviews · ${reviewsQuery.data.average_rating?.toFixed(1) ?? "—"} average`
              : "Reviews from people who brought this piece home."
          }
        />
        {reviewsQuery.isPending ? (
          <Loading label="Loading reviews" />
        ) : reviewsQuery.isError ? (
          <ErrorState
            error={reviewsQuery.error}
            retry={() => void reviewsQuery.refetch()}
          />
        ) : reviewsQuery.data.content.length ? (
          <div className="review-list">
            {reviewsQuery.data.content.map((review) => (
              <ReviewCard review={review} key={review.review_id} />
            ))}
          </div>
        ) : (
          <EmptyState
            title="Be the first to leave a note"
            body="A verified purchase is needed before a review can be shared."
          />
        )}
        {user?.role === "CUSTOMER" && (
          <ReviewForm
            productId={productId}
            onCreated={() =>
              void queryClient.invalidateQueries({
                queryKey: ["reviews", productId],
              })
            }
          />
        )}
      </section>
    </>
  );
}
export function ReviewCard({ review }: { review: Review }) {
  return (
    <article className="review-card">
      <div className="rating-stars">
        {"★".repeat(review.rating)}
        {"☆".repeat(5 - review.rating)}
      </div>
      <h3>{review.title}</h3>
      <p>{review.body}</p>
      <p className="verified-label">
        {review.verified_purchase ? "Verified purchase" : "Customer review"}
      </p>
    </article>
  );
}
export function ReviewForm({
  productId,
  onCreated,
}: {
  productId: string;
  onCreated: () => void;
}) {
  const [orderId, setOrderId] = useState("");
  const [rating, setRating] = useState("5");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const mutation = useMutation({
    mutationFn: () =>
      apiClient.createReview(productId, {
        order_id: orderId,
        rating: Number(rating),
        title,
        body,
      }),
    onSuccess: () => {
      notify("Thanks for sharing your experience");
      setOrderId("");
      setTitle("");
      setBody("");
      onCreated();
    },
    onError: (error) => notify(messageOf(error), "error"),
  });
  return (
    <div className="panel review-compose">
      <div className="panel-title">
        <div>
          <h3>Leave a review</h3>
          <p className="small">Only confirmed purchases can be reviewed.</p>
        </div>
      </div>
      <form
        className="form-stack"
        onSubmit={(event) => {
          event.preventDefault();
          mutation.mutate();
        }}
      >
        <div className="field-grid">
          <Field
            label="Order ID"
            value={orderId}
            onChange={(event) => setOrderId(event.target.value)}
            placeholder="Paste your order ID"
            required
          />
          <SelectField
            label="Rating"
            value={rating}
            onChange={(event) => setRating(event.target.value)}
          >
            {[5, 4, 3, 2, 1].map((value) => (
              <option value={value} key={value}>
                {value} star{value > 1 ? "s" : ""}
              </option>
            ))}
          </SelectField>
          <Field
            className="field-full"
            label="Title"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            maxLength={120}
            required
          />
          <label className="field field-full">
            <span>Your note</span>
            <textarea
              value={body}
              onChange={(event) => setBody(event.target.value)}
              maxLength={3000}
              required
            />
          </label>
        </div>
        <div>
          <Button disabled={mutation.isPending}>
            {mutation.isPending ? "Sharing…" : "Share review"}
          </Button>
        </div>
      </form>
    </div>
  );
}

```

---

## File: `frontend/customer-app/src/StoreLayout.tsx`

```typescript
import { useState, type FormEvent, type ReactNode } from "react";
import { Navigate, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  Footer,
  Loading,
  ToastRegion,
  apiClient,
  useSession,
} from "@ecommerce/shared";
export function StoreLayout() {
  const { user } = useSession();
  const cart = useQuery({
    queryKey: ["cart", user?.id],
    queryFn: () => apiClient.cart(user!.id),
    enabled: !!user,
  });
  const count =
    cart.data?.items.reduce((total, item) => total + item.quantity, 0) ?? 0;
  const [term, setTerm] = useState("");
  const navigate = useNavigate();
  const submitSearch = (event: FormEvent) => {
    event.preventDefault();
    navigate(`/?q=${encodeURIComponent(term.trim())}`);
  };
  return (
    <div className="app-shell">
      <header className="site-header">
        <a className="brand-lockup" href="/">
          <span className="brand-mark">m</span>
          <span>
            maison<small>Objects for everyday</small>
          </span>
        </a>
        <nav className="header-nav">
          <a href="/">Discover</a>
          <a href="/wishlist">Wishlist</a>
          <a href="/orders">My orders</a>
        </nav>
        <form
          className="search-input store-search"
          role="search"
          onSubmit={submitSearch}
        >
          <input
            aria-label="Search products"
            placeholder="Find something lovely…"
            value={term}
            onChange={(event) => setTerm(event.target.value)}
          />
          <button aria-label="Search">⌕</button>
        </form>
        <div className="header-actions">
          <a
            className="header-icon"
            href="/cart"
            aria-label={`Cart, ${count} items`}
          >
            Bag <span className="cart-count">{count}</span>
          </a>
          <a className="account-link" href="/account">
            Account <span>↗</span>
          </a>
        </div>
      </header>
      <nav className="mobile-nav" aria-label="Store navigation">
        <a href="/">Discover</a>
        <a href="/wishlist">Wishlist</a>
        <a href="/orders">My orders</a>
      </nav>
      <main className="app-main">
        <Outlet />
      </main>
      <Footer />
      <ToastRegion />
    </div>
  );
}
export function RequireCustomer({ children }: { children: ReactNode }) {
  const { user, ready } = useSession();
  const location = useLocation();
  if (!ready) return <Loading label="Checking your account" />;
  if (!user)
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <>{children}</>;
}

```

---

## File: `frontend/customer-app/src/stripe.ts`

```typescript
import { loadStripe } from "@ecommerce/shared";
const stripeKey = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY as
  string | undefined;
export const stripePromise = stripeKey ? loadStripe(stripeKey) : null;

```

---

## File: `frontend/customer-app/src/test-setup.ts`

```typescript
import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";
afterEach(cleanup);

```

---

## File: `frontend/customer-app/src/WishlistPage.tsx`

```typescript
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  EmptyState,
  ErrorState,
  Loading,
  PageTitle,
  ProductCard,
  apiClient,
  notify,
  useSession,
} from "@ecommerce/shared";
import { messageOf } from "./helpers";
export function WishlistPage() {
  const { user } = useSession();
  const queryClient = useQueryClient();
  const wishlistQuery = useQuery({
    queryKey: ["wishlist"],
    queryFn: () => apiClient.wishlist(0, 100),
  });
  const productQuery = useQuery({
    queryKey: [
      "wishlist-products",
      wishlistQuery.data?.items.map((item) => item.product_id),
    ],
    queryFn: async () =>
      Promise.all(
        (wishlistQuery.data?.items ?? []).map((item) =>
          apiClient.product(item.product_id),
        ),
      ),
    enabled: !!wishlistQuery.data?.items.length,
  });
  const removeMutation = useMutation({
    mutationFn: apiClient.removeWishlist,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["wishlist"] });
      notify("Removed from your wishlist");
    },
    onError: (error) => notify(messageOf(error), "error"),
  });
  const cartMutation = useMutation({
    mutationFn: (productId: string) =>
      apiClient.addCartItem(user!.id, productId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["cart"] });
      notify("Added to your bag");
    },
    onError: (error) => notify(messageOf(error), "error"),
  });
  if (wishlistQuery.isPending) return <Loading />;
  if (wishlistQuery.isError)
    return (
      <ErrorState
        error={wishlistQuery.error}
        retry={() => void wishlistQuery.refetch()}
      />
    );
  if (!wishlistQuery.data.items.length)
    return (
      <>
        <PageTitle eyebrow="Saved for later" title="Your wishlist" />
        <EmptyState
          icon="♡"
          title="Keep a little list"
          body="Save pieces you love and come back when the time is right."
          action={
            <Button onClick={() => window.location.assign("/")}>
              Explore the collection
            </Button>
          }
        />
      </>
    );
  if (productQuery.isPending)
    return <Loading label="Gathering your saved pieces" />;
  if (productQuery.isError)
    return (
      <ErrorState
        error={productQuery.error}
        retry={() => void productQuery.refetch()}
      />
    );
  const products = productQuery.data ?? [];
  return (
    <>
      <PageTitle
        eyebrow="Saved for later"
        title="Your wishlist"
        description={`${products.length} pieces you have your eye on.`}
      />
      <div className="product-grid">
        {products.map((product) => (
          <div key={product.id}>
            <ProductCard
              product={product}
              favorite
              onFavorite={() => removeMutation.mutate(product.id)}
              onAdd={() => cartMutation.mutate(product.id)}
            />
          </div>
        ))}
      </div>
    </>
  );
}

```

---

## File: `frontend/customer-app/tsconfig.json`

```json
{ "extends": "../tsconfig.json", "include": ["src", "vite.config.ts"] }

```

---

## File: `frontend/customer-app/vite.config.ts`

```typescript
import { defineConfig } from "vitest/config";

export default defineConfig({
  server: { port: 3000 },
  test: { environment: "jsdom", setupFiles: "./src/test-setup.ts", css: true },
});

```

---

## File: `frontend/package.json`

```json
{
  "name": "ecommerce-frontend",
  "private": true,
  "workspaces": [
    "customer-app",
    "admin-dashboard",
    "shared-components"
  ],
  "scripts": {
    "dev:customer": "npm --workspace @ecommerce/customer-app run dev",
    "dev:admin": "npm --workspace @ecommerce/admin-dashboard run dev",
    "build": "npm run build --workspaces --if-present",
    "test": "npm run test --workspaces --if-present",
    "typecheck": "npm run typecheck --workspaces --if-present",
    "format": "prettier --write \"**/*.{ts,tsx,css,json,md,html}\""
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.6.3",
    "@testing-library/react": "^16.1.0",
    "@testing-library/user-event": "^14.5.2",
    "@types/node": "^22.10.2",
    "@types/react": "^18.3.12",
    "@types/react-dom": "^18.3.1",
    "jsdom": "^25.0.1",
    "prettier": "^3.9.9",
    "typescript": "~5.6.3",
    "vite": "^5.4.11",
    "vitest": "^2.1.5"
  },
  "dependencies": {
    "react": "18.3.1",
    "react-dom": "18.3.1"
  }
}

```

---

## File: `frontend/package-lock.json`

```json
{
  "name": "ecommerce-frontend",
  "lockfileVersion": 3,
  "requires": true,
  "packages": {
    "": {
      "name": "ecommerce-frontend",
      "workspaces": [
        "customer-app",
        "admin-dashboard",
        "shared-components"
      ],
      "dependencies": {
        "react": "18.3.1",
        "react-dom": "18.3.1"
      },
      "devDependencies": {
        "@testing-library/jest-dom": "^6.6.3",
        "@testing-library/react": "^16.1.0",
        "@testing-library/user-event": "^14.5.2",
        "@types/node": "^22.10.2",
        "@types/react": "^18.3.12",
        "@types/react-dom": "^18.3.1",
        "jsdom": "^25.0.1",
        "prettier": "^3.9.9",
        "typescript": "~5.6.3",
        "vite": "^5.4.11",
        "vitest": "^2.1.5"
      }
    },
    "admin-dashboard": {
      "name": "@ecommerce/admin-dashboard",
      "version": "1.0.0",
      "dependencies": {
        "@ecommerce/shared": "*"
      },
      "devDependencies": {
        "@testing-library/react": "^16.1.0",
        "@testing-library/user-event": "^14.5.2",
        "@types/react": "^18.3.12",
        "@types/react-dom": "^18.3.1",
        "jsdom": "^25.0.1",
        "typescript": "~5.6.3",
        "vite": "^5.4.11",
        "vitest": "^2.1.5"
      }
    },
    "customer-app": {
      "name": "@ecommerce/customer-app",
      "version": "1.0.0",
      "dependencies": {
        "@ecommerce/shared": "*"
      },
      "devDependencies": {
        "@testing-library/react": "^16.1.0",
        "@testing-library/user-event": "^14.5.2",
        "@types/react": "^18.3.12",
        "@types/react-dom": "^18.3.1",
        "jsdom": "^25.0.1",
        "typescript": "~5.6.3",
        "vite": "^5.4.11",
        "vitest": "^2.1.5"
      }
    },
    "node_modules/@adobe/css-tools": {
      "version": "4.5.0",
      "resolved": "https://registry.npmjs.org/@adobe/css-tools/-/css-tools-4.5.0.tgz",
      "integrity": "sha512-6OzddxPio9UiWTCemp4N8cYLV2ZN1ncRnV1cVGtve7dhPOtRkleRyx32GQCYSwDYgaHU3USMm84tNsvKzRCa1Q==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/@asamuzakjp/css-color": {
      "version": "3.2.0",
      "resolved": "https://registry.npmjs.org/@asamuzakjp/css-color/-/css-color-3.2.0.tgz",
      "integrity": "sha512-K1A6z8tS3XsmCMM86xoWdn7Fkdn9m6RSVtocUrJYIwZnFVkng/PvkEoWtOWmP+Scc6saYWHWZYbndEEXxl24jw==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "@csstools/css-calc": "^2.1.3",
        "@csstools/css-color-parser": "^3.0.9",
        "@csstools/css-parser-algorithms": "^3.0.4",
        "@csstools/css-tokenizer": "^3.0.3",
        "lru-cache": "^10.4.3"
      }
    },
    "node_modules/@babel/code-frame": {
      "version": "7.29.7",
      "resolved": "https://registry.npmjs.org/@babel/code-frame/-/code-frame-7.29.7.tgz",
      "integrity": "sha512-Aup7aUOfpbAUg2ROOJN6Iw5f9DMBlzu0mIkm/malLQFN/YQgO48wCj0Kxa3sEHJvPVFg7siR+qRInwXd2qhQKw==",
      "dev": true,
      "license": "MIT",
      "peer": true,
      "dependencies": {
        "@babel/helper-validator-identifier": "^7.29.7",
        "js-tokens": "^4.0.0",
        "picocolors": "^1.1.1"
      },
      "engines": {
        "node": ">=6.9.0"
      }
    },
    "node_modules/@babel/helper-validator-identifier": {
      "version": "7.29.7",
      "resolved": "https://registry.npmjs.org/@babel/helper-validator-identifier/-/helper-validator-identifier-7.29.7.tgz",
      "integrity": "sha512-qehxGkRj55h/ff8EMaJ+cYhyaKlHIxqYDn682wQD7RNp9UujOQsHog2uS0r2vzr4pW+sXf90NeeayjcNaX3fFg==",
      "dev": true,
      "license": "MIT",
      "peer": true,
      "engines": {
        "node": ">=6.9.0"
      }
    },
    "node_modules/@babel/runtime": {
      "version": "7.29.7",
      "resolved": "https://registry.npmjs.org/@babel/runtime/-/runtime-7.29.7.tgz",
      "integrity": "sha512-Nq8OhGWiZIZGV6hLHoyAKLLcJihP/xFeBMGJoUrxTX2psI8dCifzLhZISFb+VWS3wFMRDmCGw5R+dOySCqPLhw==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">=6.9.0"
      }
    },
    "node_modules/@csstools/color-helpers": {
      "version": "5.1.0",
      "resolved": "https://registry.npmjs.org/@csstools/color-helpers/-/color-helpers-5.1.0.tgz",
      "integrity": "sha512-S11EXWJyy0Mz5SYvRmY8nJYTFFd1LCNV+7cXyAgQtOOuzb4EsgfqDufL+9esx72/eLhsRdGZwaldu/h+E4t4BA==",
      "dev": true,
      "funding": [
        {
          "type": "github",
          "url": "https://github.com/sponsors/csstools"
        },
        {
          "type": "opencollective",
          "url": "https://opencollective.com/csstools"
        }
      ],
      "license": "MIT-0",
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@csstools/css-calc": {
      "version": "2.1.4",
      "resolved": "https://registry.npmjs.org/@csstools/css-calc/-/css-calc-2.1.4.tgz",
      "integrity": "sha512-3N8oaj+0juUw/1H3YwmDDJXCgTB1gKU6Hc/bB502u9zR0q2vd786XJH9QfrKIEgFlZmhZiq6epXl4rHqhzsIgQ==",
      "dev": true,
      "funding": [
        {
          "type": "github",
          "url": "https://github.com/sponsors/csstools"
        },
        {
          "type": "opencollective",
          "url": "https://opencollective.com/csstools"
        }
      ],
      "license": "MIT",
      "engines": {
        "node": ">=18"
      },
      "peerDependencies": {
        "@csstools/css-parser-algorithms": "^3.0.5",
        "@csstools/css-tokenizer": "^3.0.4"
      }
    },
    "node_modules/@csstools/css-color-parser": {
      "version": "3.1.0",
      "resolved": "https://registry.npmjs.org/@csstools/css-color-parser/-/css-color-parser-3.1.0.tgz",
      "integrity": "sha512-nbtKwh3a6xNVIp/VRuXV64yTKnb1IjTAEEh3irzS+HkKjAOYLTGNb9pmVNntZ8iVBHcWDA2Dof0QtPgFI1BaTA==",
      "dev": true,
      "funding": [
        {
          "type": "github",
          "url": "https://github.com/sponsors/csstools"
        },
        {
          "type": "opencollective",
          "url": "https://opencollective.com/csstools"
        }
      ],
      "license": "MIT",
      "dependencies": {
        "@csstools/color-helpers": "^5.1.0",
        "@csstools/css-calc": "^2.1.4"
      },
      "engines": {
        "node": ">=18"
      },
      "peerDependencies": {
        "@csstools/css-parser-algorithms": "^3.0.5",
        "@csstools/css-tokenizer": "^3.0.4"
      }
    },
    "node_modules/@csstools/css-parser-algorithms": {
      "version": "3.0.5",
      "resolved": "https://registry.npmjs.org/@csstools/css-parser-algorithms/-/css-parser-algorithms-3.0.5.tgz",
      "integrity": "sha512-DaDeUkXZKjdGhgYaHNJTV9pV7Y9B3b644jCLs9Upc3VeNGg6LWARAT6O+Q+/COo+2gg/bM5rhpMAtf70WqfBdQ==",
      "dev": true,
      "funding": [
        {
          "type": "github",
          "url": "https://github.com/sponsors/csstools"
        },
        {
          "type": "opencollective",
          "url": "https://opencollective.com/csstools"
        }
      ],
      "license": "MIT",
      "engines": {
        "node": ">=18"
      },
      "peerDependencies": {
        "@csstools/css-tokenizer": "^3.0.4"
      }
    },
    "node_modules/@csstools/css-tokenizer": {
      "version": "3.0.4",
      "resolved": "https://registry.npmjs.org/@csstools/css-tokenizer/-/css-tokenizer-3.0.4.tgz",
      "integrity": "sha512-Vd/9EVDiu6PPJt9yAh6roZP6El1xHrdvIVGjyBsHR0RYwNHgL7FJPyIIW4fANJNG6FtyZfvlRPpFI4ZM/lubvw==",
      "dev": true,
      "funding": [
        {
          "type": "github",
          "url": "https://github.com/sponsors/csstools"
        },
        {
          "type": "opencollective",
          "url": "https://opencollective.com/csstools"
        }
      ],
      "license": "MIT",
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@ecommerce/admin-dashboard": {
      "resolved": "admin-dashboard",
      "link": true
    },
    "node_modules/@ecommerce/customer-app": {
      "resolved": "customer-app",
      "link": true
    },
    "node_modules/@ecommerce/shared": {
      "resolved": "shared-components",
      "link": true
    },
    "node_modules/@esbuild/aix-ppc64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/aix-ppc64/-/aix-ppc64-0.21.5.tgz",
      "integrity": "sha512-1SDgH6ZSPTlggy1yI6+Dbkiz8xzpHJEVAlF/AM1tHPLsf5STom9rwtjE4hKAF20FfXXNTFqEYXyJNWh1GiZedQ==",
      "cpu": [
        "ppc64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "aix"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/android-arm": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/android-arm/-/android-arm-0.21.5.tgz",
      "integrity": "sha512-vCPvzSjpPHEi1siZdlvAlsPxXl7WbOVUBBAowWug4rJHb68Ox8KualB+1ocNvT5fjv6wpkX6o/iEpbDrf68zcg==",
      "cpu": [
        "arm"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "android"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/android-arm64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/android-arm64/-/android-arm64-0.21.5.tgz",
      "integrity": "sha512-c0uX9VAUBQ7dTDCjq+wdyGLowMdtR/GoC2U5IYk/7D1H1JYC0qseD7+11iMP2mRLN9RcCMRcjC4YMclCzGwS/A==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "android"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/android-x64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/android-x64/-/android-x64-0.21.5.tgz",
      "integrity": "sha512-D7aPRUUNHRBwHxzxRvp856rjUHRFW1SdQATKXH2hqA0kAZb1hKmi02OpYRacl0TxIGz/ZmXWlbZgjwWYaCakTA==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "android"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/darwin-arm64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/darwin-arm64/-/darwin-arm64-0.21.5.tgz",
      "integrity": "sha512-DwqXqZyuk5AiWWf3UfLiRDJ5EDd49zg6O9wclZ7kUMv2WRFr4HKjXp/5t8JZ11QbQfUS6/cRCKGwYhtNAY88kQ==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "darwin"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/darwin-x64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/darwin-x64/-/darwin-x64-0.21.5.tgz",
      "integrity": "sha512-se/JjF8NlmKVG4kNIuyWMV/22ZaerB+qaSi5MdrXtd6R08kvs2qCN4C09miupktDitvh8jRFflwGFBQcxZRjbw==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "darwin"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/freebsd-arm64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/freebsd-arm64/-/freebsd-arm64-0.21.5.tgz",
      "integrity": "sha512-5JcRxxRDUJLX8JXp/wcBCy3pENnCgBR9bN6JsY4OmhfUtIHe3ZW0mawA7+RDAcMLrMIZaf03NlQiX9DGyB8h4g==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "freebsd"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/freebsd-x64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/freebsd-x64/-/freebsd-x64-0.21.5.tgz",
      "integrity": "sha512-J95kNBj1zkbMXtHVH29bBriQygMXqoVQOQYA+ISs0/2l3T9/kj42ow2mpqerRBxDJnmkUDCaQT/dfNXWX/ZZCQ==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "freebsd"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/linux-arm": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/linux-arm/-/linux-arm-0.21.5.tgz",
      "integrity": "sha512-bPb5AHZtbeNGjCKVZ9UGqGwo8EUu4cLq68E95A53KlxAPRmUyYv2D6F0uUI65XisGOL1hBP5mTronbgo+0bFcA==",
      "cpu": [
        "arm"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/linux-arm64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/linux-arm64/-/linux-arm64-0.21.5.tgz",
      "integrity": "sha512-ibKvmyYzKsBeX8d8I7MH/TMfWDXBF3db4qM6sy+7re0YXya+K1cem3on9XgdT2EQGMu4hQyZhan7TeQ8XkGp4Q==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/linux-ia32": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/linux-ia32/-/linux-ia32-0.21.5.tgz",
      "integrity": "sha512-YvjXDqLRqPDl2dvRODYmmhz4rPeVKYvppfGYKSNGdyZkA01046pLWyRKKI3ax8fbJoK5QbxblURkwK/MWY18Tg==",
      "cpu": [
        "ia32"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/linux-loong64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/linux-loong64/-/linux-loong64-0.21.5.tgz",
      "integrity": "sha512-uHf1BmMG8qEvzdrzAqg2SIG/02+4/DHB6a9Kbya0XDvwDEKCoC8ZRWI5JJvNdUjtciBGFQ5PuBlpEOXQj+JQSg==",
      "cpu": [
        "loong64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/linux-mips64el": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/linux-mips64el/-/linux-mips64el-0.21.5.tgz",
      "integrity": "sha512-IajOmO+KJK23bj52dFSNCMsz1QP1DqM6cwLUv3W1QwyxkyIWecfafnI555fvSGqEKwjMXVLokcV5ygHW5b3Jbg==",
      "cpu": [
        "mips64el"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/linux-ppc64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/linux-ppc64/-/linux-ppc64-0.21.5.tgz",
      "integrity": "sha512-1hHV/Z4OEfMwpLO8rp7CvlhBDnjsC3CttJXIhBi+5Aj5r+MBvy4egg7wCbe//hSsT+RvDAG7s81tAvpL2XAE4w==",
      "cpu": [
        "ppc64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/linux-riscv64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/linux-riscv64/-/linux-riscv64-0.21.5.tgz",
      "integrity": "sha512-2HdXDMd9GMgTGrPWnJzP2ALSokE/0O5HhTUvWIbD3YdjME8JwvSCnNGBnTThKGEB91OZhzrJ4qIIxk/SBmyDDA==",
      "cpu": [
        "riscv64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/linux-s390x": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/linux-s390x/-/linux-s390x-0.21.5.tgz",
      "integrity": "sha512-zus5sxzqBJD3eXxwvjN1yQkRepANgxE9lgOW2qLnmr8ikMTphkjgXu1HR01K4FJg8h1kEEDAqDcZQtbrRnB41A==",
      "cpu": [
        "s390x"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/linux-x64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/linux-x64/-/linux-x64-0.21.5.tgz",
      "integrity": "sha512-1rYdTpyv03iycF1+BhzrzQJCdOuAOtaqHTWJZCWvijKD2N5Xu0TtVC8/+1faWqcP9iBCWOmjmhoH94dH82BxPQ==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/netbsd-x64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/netbsd-x64/-/netbsd-x64-0.21.5.tgz",
      "integrity": "sha512-Woi2MXzXjMULccIwMnLciyZH4nCIMpWQAs049KEeMvOcNADVxo0UBIQPfSmxB3CWKedngg7sWZdLvLczpe0tLg==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "netbsd"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/openbsd-x64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/openbsd-x64/-/openbsd-x64-0.21.5.tgz",
      "integrity": "sha512-HLNNw99xsvx12lFBUwoT8EVCsSvRNDVxNpjZ7bPn947b8gJPzeHWyNVhFsaerc0n3TsbOINvRP2byTZ5LKezow==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "openbsd"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/sunos-x64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/sunos-x64/-/sunos-x64-0.21.5.tgz",
      "integrity": "sha512-6+gjmFpfy0BHU5Tpptkuh8+uw3mnrvgs+dSPQXQOv3ekbordwnzTVEb4qnIvQcYXq6gzkyTnoZ9dZG+D4garKg==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "sunos"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/win32-arm64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/win32-arm64/-/win32-arm64-0.21.5.tgz",
      "integrity": "sha512-Z0gOTd75VvXqyq7nsl93zwahcTROgqvuAcYDUr+vOv8uHhNSKROyU961kgtCD1e95IqPKSQKH7tBTslnS3tA8A==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "win32"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/win32-ia32": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/win32-ia32/-/win32-ia32-0.21.5.tgz",
      "integrity": "sha512-SWXFF1CL2RVNMaVs+BBClwtfZSvDgtL//G/smwAc5oVK/UPu2Gu9tIaRgFmYFFKrmg3SyAjSrElf0TiJ1v8fYA==",
      "cpu": [
        "ia32"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "win32"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@esbuild/win32-x64": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/@esbuild/win32-x64/-/win32-x64-0.21.5.tgz",
      "integrity": "sha512-tQd/1efJuzPC6rCFwEvLtci/xNFcTZknmXs98FYDfGE4wP9ClFV98nyKrzJKVPMhdDnjzLhdUyMX4PsQAPjwIw==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "win32"
      ],
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/@jridgewell/sourcemap-codec": {
      "version": "1.6.0",
      "resolved": "https://registry.npmjs.org/@jridgewell/sourcemap-codec/-/sourcemap-codec-1.6.0.tgz",
      "integrity": "sha512-T7jf+5zgsZHwNJ4lvQ7/aezbyk0nNX+zJVWpmHA7VYsEx7a7qr5Rg5IbtJFqkgze5Y2sruq1RUY8Q837Od7iFw==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/@napi-rs/lzma-linux-x64-gnu": {
      "version": "1.5.1",
      "resolved": "https://registry.npmjs.org/@napi-rs/lzma-linux-x64-gnu/-/lzma-linux-x64-gnu-1.5.1.tgz",
      "integrity": "sha512-oTXEIha4SsuXdTA4Iyskj0kpdx2yVXdhd75c2v3xGrHFfVMsbhTPZU/nMPL4sWKo4pBHm3aucLaqGlF696dTyQ==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": "^22.20 || ^24.12 || >=25"
      }
    },
    "node_modules/@remix-run/router": {
      "version": "1.23.4",
      "resolved": "https://registry.npmjs.org/@remix-run/router/-/router-1.23.4.tgz",
      "integrity": "sha512-q7j5geK7xs3UJSdm9/iytUNclBnLmYx1EnSeCFXHPeutdqgIMeFeHtUZgS3EhlKxdBEAu8OwtJCwmLrEzpSs7Q==",
      "license": "MIT",
      "engines": {
        "node": ">=14.0.0"
      }
    },
    "node_modules/@rollup/rollup-android-arm-eabi": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-android-arm-eabi/-/rollup-android-arm-eabi-4.63.5.tgz",
      "integrity": "sha512-J25QJU+B78T4FhhBsNpLJyVWOi31mwtpcMwywHmOKH65Q9IWGA81gPj+dnwlhU8wktVriYE+tFAaQgrnJRzAZg==",
      "cpu": [
        "arm"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "android"
      ]
    },
    "node_modules/@rollup/rollup-android-arm64": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-android-arm64/-/rollup-android-arm64-4.63.5.tgz",
      "integrity": "sha512-LDopB3zuZM5Ux9TT2luNEBJW/tYbGU2g1d+VpKk6I+gSKDb+/7sYE6M225gRQt4RbMX6MSwMsVR/phdjVUgRLg==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "android"
      ]
    },
    "node_modules/@rollup/rollup-darwin-arm64": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-darwin-arm64/-/rollup-darwin-arm64-4.63.5.tgz",
      "integrity": "sha512-wlJEERGfeuHeBavCL2qVnNacOK43NDoZM4sjkeRPymd04OAE9T1zBqDJgmZ+CIsPTYKwdzpUC8vmOw84dwY4Tg==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "darwin"
      ]
    },
    "node_modules/@rollup/rollup-darwin-x64": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-darwin-x64/-/rollup-darwin-x64-4.63.5.tgz",
      "integrity": "sha512-4nJJGg5jbo2wwPP4JP+LfEBA3bvP8rU9CLuhp7jWvq9sxEyhjQFTFdrqi+/dHEin/pd8jpT0vcehIpnZtmEdcQ==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "darwin"
      ]
    },
    "node_modules/@rollup/rollup-freebsd-arm64": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-freebsd-arm64/-/rollup-freebsd-arm64-4.63.5.tgz",
      "integrity": "sha512-DrZbyCDF1hneuO6jRbvZ2D7+PIBM6yIwYnJpg2vIk58T+wuFpiaGZrfUr59lDWw45bg+IrpTGLPiNi/Fk4w3Cg==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "freebsd"
      ]
    },
    "node_modules/@rollup/rollup-freebsd-x64": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-freebsd-x64/-/rollup-freebsd-x64-4.63.5.tgz",
      "integrity": "sha512-gqfUVMJMB3mehqywxp6hTBFfgtMQykZY19+cfiaYP0toIJLb/1DZRJHVkQQGP13W4TAwfZDWeg1qBcheTRioXQ==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "freebsd"
      ]
    },
    "node_modules/@rollup/rollup-linux-arm-gnueabihf": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-linux-arm-gnueabihf/-/rollup-linux-arm-gnueabihf-4.63.5.tgz",
      "integrity": "sha512-CFmhpvAwzSaWMlN3VN7UtmoTihlZNzoP0juQib5TQRnYUyDV8dXeWOp29sobWAT6gXl/hQgAClLlEiYozQG3OQ==",
      "cpu": [
        "arm"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-arm-musleabihf": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-linux-arm-musleabihf/-/rollup-linux-arm-musleabihf-4.63.5.tgz",
      "integrity": "sha512-Uc9H8eXCOayV6JLTH5bXKMId6qbhNHa818/BgYjm4jrlq3vZquC9cqyvHBw17xy5Mnj5f+I3gFK5JcEf3hSqrw==",
      "cpu": [
        "arm"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-arm64-gnu": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-linux-arm64-gnu/-/rollup-linux-arm64-gnu-4.63.5.tgz",
      "integrity": "sha512-VcPr/szv/1BFw112Kt//fxulXt/JPqzzidU84iW68L2DdjnOO8QFUv2zTSYBEPHD6movBD4z+bbr5y60GYM7Jw==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-arm64-musl": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-linux-arm64-musl/-/rollup-linux-arm64-musl-4.63.5.tgz",
      "integrity": "sha512-BnxtJ5/91BrIHYIkGrmjz/lbMhqEHt1dPFqIxIFR+jPn0xVc/oUSCtIT089zfp5ufwGDlYz2UC+Fe1SRBpYFbQ==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-loong64-gnu": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-linux-loong64-gnu/-/rollup-linux-loong64-gnu-4.63.5.tgz",
      "integrity": "sha512-LrYcHZwF+fAMNKHYTOQ5osWM4AZF7YF6D+XtsjDyEvljtt11twc+zHVXBLNEjxVSUnKYsOhvVz4Z213eW02COQ==",
      "cpu": [
        "loong64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-loong64-musl": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-linux-loong64-musl/-/rollup-linux-loong64-musl-4.63.5.tgz",
      "integrity": "sha512-nj7QKQePAAUpCpJHtg0pR0W/b92A9NO17JS3BAQmHDn/yhmkir2p8llrKY9TOhleKIaSzy1JhxS3T9FVld6coA==",
      "cpu": [
        "loong64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-ppc64-gnu": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-linux-ppc64-gnu/-/rollup-linux-ppc64-gnu-4.63.5.tgz",
      "integrity": "sha512-5ylkX6dWMeBKge9nTU+Rxfb+ZfaCIJ9lRqIFaK0eAMcWp7OJbYnLveLgXmm0VrvuLKb8qIK+mHyH0qu88RM+iA==",
      "cpu": [
        "ppc64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-ppc64-musl": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-linux-ppc64-musl/-/rollup-linux-ppc64-musl-4.63.5.tgz",
      "integrity": "sha512-oHK4ZHYFDKjZviK34I+NwgfbGxgI7ztrNxj2hPTSSNFgeq1a/lEd7dHV2fdGAuTH4Iym3RHJg+vAbWaWG4B7Zg==",
      "cpu": [
        "ppc64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-riscv64-gnu": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-linux-riscv64-gnu/-/rollup-linux-riscv64-gnu-4.63.5.tgz",
      "integrity": "sha512-UcetmHZ6XOXuUByiKZyQmb55ZPr0LABr3Ec/HB9wKZn6CEAFWZkE+hsJErJ9hbPBC7nI0dKuELx7CoV6IM7TMg==",
      "cpu": [
        "riscv64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-riscv64-musl": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-linux-riscv64-musl/-/rollup-linux-riscv64-musl-4.63.5.tgz",
      "integrity": "sha512-C5CmDPQBtvjVo8cgQsBs+w6WB0JLkiixhgi6hVLV11hERWdn/p0XcPU2OUcZzac9BPOFq7SbaHFa8r3SWEysCQ==",
      "cpu": [
        "riscv64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-s390x-gnu": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-linux-s390x-gnu/-/rollup-linux-s390x-gnu-4.63.5.tgz",
      "integrity": "sha512-lHVQHJFKsuuxLMi3MQO9XVL8Tje3JR82CzB+QDKC5NWBcsIWuwsn9uIM5e3lBhI+fF1/s63qnyYqsg65+8rV/w==",
      "cpu": [
        "s390x"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-x64-gnu": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-linux-x64-gnu/-/rollup-linux-x64-gnu-4.63.5.tgz",
      "integrity": "sha512-3W9bTFcQNJn71cSJVM9RKIiZOy8DO/XLDii8Uv/Pm6WKqDRj7JV3ZfuXIEfyuy5LXpIzAbB/1M4Ukp9GKNa7nA==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-x64-musl": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-linux-x64-musl/-/rollup-linux-x64-musl-4.63.5.tgz",
      "integrity": "sha512-VDC7rRJlee/scpki96GZ27Omf6yU87s1YXwVTpjE5841faVlDYYT565rgfmoR1U0sqL7z5ivQSDjcsF6VRXyBA==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-openbsd-x64": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-openbsd-x64/-/rollup-openbsd-x64-4.63.5.tgz",
      "integrity": "sha512-z86Ok2p4pTdv5xqCKZsTooO7yBEiaJR/HzU3Wx8RmWsPoLppnMKROhJusQob8B3IE1ghC343kUW9rC2r+Wf3ig==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "openbsd"
      ]
    },
    "node_modules/@rollup/rollup-openharmony-arm64": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-openharmony-arm64/-/rollup-openharmony-arm64-4.63.5.tgz",
      "integrity": "sha512-IzQmj+xXwQFGhMAMKMQVXkMwMZN3TqkJgAE0nSsqvVwWWciP4AIPMmWRqOQ2GfX7TUDZr+xqGFcBS36CRPGw0g==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "openharmony"
      ]
    },
    "node_modules/@rollup/rollup-win32-arm64-msvc": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-win32-arm64-msvc/-/rollup-win32-arm64-msvc-4.63.5.tgz",
      "integrity": "sha512-F6qpTaPc9bwBH85kjy0/BLmLSW1uv7AoOXCoRIkg2arlgCYlWYcAbiMkvZuAcaWk9TpCRG//okznLAqLGshkMw==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "win32"
      ]
    },
    "node_modules/@rollup/rollup-win32-ia32-msvc": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-win32-ia32-msvc/-/rollup-win32-ia32-msvc-4.63.5.tgz",
      "integrity": "sha512-igoDsTFhhwECBeGbUuLeIk7t8Y1apa+cs6mDWpx2EZ0ch7oEQgzHbFUXN9euoHekCAQzXdXApAGkV6jznS7tWw==",
      "cpu": [
        "ia32"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "win32"
      ]
    },
    "node_modules/@rollup/rollup-win32-x64-gnu": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-win32-x64-gnu/-/rollup-win32-x64-gnu-4.63.5.tgz",
      "integrity": "sha512-U3teMeMbXFmaM5D+OTJpsOXd+wV/qftIeYF9kBKL4v73641qyJmoXFtA28DQLsnmlyayEsTe72xpLHrArq6vHw==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "win32"
      ]
    },
    "node_modules/@rollup/rollup-win32-x64-msvc": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/@rollup/rollup-win32-x64-msvc/-/rollup-win32-x64-msvc-4.63.5.tgz",
      "integrity": "sha512-ypfC34F3RKXvCXBglGqGMsUSMKlgwd1HX9AOAlx9RoZZ6GaI42YHVeKpzg3JG+wpBUJYTG+NNZhqbDWL8tBZkw==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "win32"
      ]
    },
    "node_modules/@stripe/stripe-js": {
      "version": "4.10.0",
      "resolved": "https://registry.npmjs.org/@stripe/stripe-js/-/stripe-js-4.10.0.tgz",
      "integrity": "sha512-KrMOL+sH69htCIXCaZ4JluJ35bchuCCznyPyrbN8JXSGQfwBI1SuIEMZNwvy8L8ykj29t6sa5BAAiL7fNoLZ8A==",
      "license": "MIT",
      "engines": {
        "node": ">=12.16"
      }
    },
    "node_modules/@tanstack/query-core": {
      "version": "5.104.0",
      "resolved": "https://registry.npmjs.org/@tanstack/query-core/-/query-core-5.104.0.tgz",
      "integrity": "sha512-JrC2r/JQlXt7khBSdUpsgxNvybzOg+aITa+ARRMlP2AFo93Y8vIqz077rp+e61mJetSZ0T6AJbwW1JHer1vrPQ==",
      "license": "MIT",
      "funding": {
        "type": "github",
        "url": "https://github.com/sponsors/tannerlinsley"
      }
    },
    "node_modules/@tanstack/react-query": {
      "version": "5.104.0",
      "resolved": "https://registry.npmjs.org/@tanstack/react-query/-/react-query-5.104.0.tgz",
      "integrity": "sha512-e1TZmDCQnWIfiDVryIHeA6Idj+Lfx1hORLOhXS/l5wcgvtVD4yYqbTDl378sGQKIi2cSgb0TEMC9cKK8GGoJEw==",
      "license": "MIT",
      "dependencies": {
        "@tanstack/query-core": "5.104.0"
      },
      "funding": {
        "type": "github",
        "url": "https://github.com/sponsors/tannerlinsley"
      },
      "peerDependencies": {
        "react": "^18 || ^19"
      }
    },
    "node_modules/@testing-library/dom": {
      "version": "10.4.2",
      "resolved": "https://registry.npmjs.org/@testing-library/dom/-/dom-10.4.2.tgz",
      "integrity": "sha512-yzr2S9HyAIdhz2/6qHgbs665Q7PKVcDF05vsOlHPxG1mo36gKVesdYVeDLnXgfjJ03CrKRk08knc6+E/9m8v2Q==",
      "dev": true,
      "license": "MIT",
      "peer": true,
      "dependencies": {
        "@babel/code-frame": "^7.10.4",
        "@babel/runtime": "^7.12.5",
        "@types/aria-query": "^5.0.1",
        "aria-query": "5.3.0",
        "dom-accessibility-api": "^0.5.9",
        "lz-string": "^1.5.0",
        "picocolors": "1.1.1",
        "pretty-format": "^27.0.2"
      },
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@testing-library/jest-dom": {
      "version": "6.9.1",
      "resolved": "https://registry.npmjs.org/@testing-library/jest-dom/-/jest-dom-6.9.1.tgz",
      "integrity": "sha512-zIcONa+hVtVSSep9UT3jZ5rizo2BsxgyDYU7WFD5eICBE7no3881HGeb/QkGfsJs6JTkY1aQhT7rIPC7e+0nnA==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "@adobe/css-tools": "^4.4.0",
        "aria-query": "^5.0.0",
        "css.escape": "^1.5.1",
        "dom-accessibility-api": "^0.6.3",
        "picocolors": "^1.1.1",
        "redent": "^3.0.0"
      },
      "engines": {
        "node": ">=14",
        "npm": ">=6",
        "yarn": ">=1"
      }
    },
    "node_modules/@testing-library/jest-dom/node_modules/dom-accessibility-api": {
      "version": "0.6.3",
      "resolved": "https://registry.npmjs.org/dom-accessibility-api/-/dom-accessibility-api-0.6.3.tgz",
      "integrity": "sha512-7ZgogeTnjuHbo+ct10G9Ffp0mif17idi0IyWNVA/wcwcm7NPOD/WEHVP3n7n3MhXqxoIYm8d6MuZohYWIZ4T3w==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/@testing-library/react": {
      "version": "16.3.3",
      "resolved": "https://registry.npmjs.org/@testing-library/react/-/react-16.3.3.tgz",
      "integrity": "sha512-Uo193NgQbPMz6lrrhtRQQFcMC6Re/ELLFbbuVL30WDlZxlpZf9/lMHTAVxPRLw1q1iu9OJmR1c2BLiENRstdBg==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "@babel/runtime": "^7.12.5"
      },
      "engines": {
        "node": ">=18"
      },
      "peerDependencies": {
        "@testing-library/dom": "^10.0.0",
        "@types/react": "^18.0.0 || ^19.0.0",
        "@types/react-dom": "^18.0.0 || ^19.0.0",
        "react": "^18.0.0 || ^19.0.0",
        "react-dom": "^18.0.0 || ^19.0.0"
      },
      "peerDependenciesMeta": {
        "@types/react": {
          "optional": true
        },
        "@types/react-dom": {
          "optional": true
        }
      }
    },
    "node_modules/@testing-library/user-event": {
      "version": "14.6.7",
      "resolved": "https://registry.npmjs.org/@testing-library/user-event/-/user-event-14.6.7.tgz",
      "integrity": "sha512-MPCpX8bxe8zS+JmmTwLp8jd0dy1rAm60Te/SL8JrQM3qvQJcBOs1d7IefJMyZzqM3EWBrDn/LWDt1BCGu4ASfg==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">=12",
        "npm": ">=6"
      },
      "peerDependencies": {
        "@testing-library/dom": ">=7.21.4"
      }
    },
    "node_modules/@types/aria-query": {
      "version": "5.0.4",
      "resolved": "https://registry.npmjs.org/@types/aria-query/-/aria-query-5.0.4.tgz",
      "integrity": "sha512-rfT93uj5s0PRL7EzccGMs3brplhcrghnDoV26NqKhCAS1hVo+WdNsPvE/yb6ilfr5hi2MEk6d5EWJTKdxg8jVw==",
      "dev": true,
      "license": "MIT",
      "peer": true
    },
    "node_modules/@types/estree": {
      "version": "1.0.9",
      "resolved": "https://registry.npmjs.org/@types/estree/-/estree-1.0.9.tgz",
      "integrity": "sha512-GhdPgy1el4/ImP05X05Uw4cw2/M93BCUmnEvWZNStlCzEKME4Fkk+YpoA5OiHNQmoS7Cafb8Xa3Pya8m1Qrzeg==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/@types/node": {
      "version": "22.20.4",
      "resolved": "https://registry.npmjs.org/@types/node/-/node-22.20.4.tgz",
      "integrity": "sha512-zJRE40jpHtKqE/C4fgHrAKQLJuSpzEnP9ff9Y7YtoR3Wd2pwqzlekDeEuUQXjRd+QCYnVnNwuJYmhdk9XV8gvA==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "undici-types": "~6.21.0"
      }
    },
    "node_modules/@types/prop-types": {
      "version": "15.7.15",
      "resolved": "https://registry.npmjs.org/@types/prop-types/-/prop-types-15.7.15.tgz",
      "integrity": "sha512-F6bEyamV9jKGAFBEmlQnesRPGOQqS2+Uwi0Em15xenOxHaf2hv6L8YCVn3rPdPJOiJfPiCnLIRyvwVaqMY3MIw==",
      "devOptional": true,
      "license": "MIT"
    },
    "node_modules/@types/react": {
      "version": "18.3.31",
      "resolved": "https://registry.npmjs.org/@types/react/-/react-18.3.31.tgz",
      "integrity": "sha512-vfEqpXTvwT91yhmwdfouStN2hSKwTvyRs8qpLfADyrq/kxDw0hZM7Wk9Ug1FELj8hIby+S/+kQCSRFF32nv2Qw==",
      "devOptional": true,
      "license": "MIT",
      "dependencies": {
        "@types/prop-types": "*",
        "csstype": "^3.2.2"
      }
    },
    "node_modules/@types/react-dom": {
      "version": "18.3.7",
      "resolved": "https://registry.npmjs.org/@types/react-dom/-/react-dom-18.3.7.tgz",
      "integrity": "sha512-MEe3UeoENYVFXzoXEWsvcpg6ZvlrFNlOQ7EOsvhI3CfAXwzPfO8Qwuxd40nepsYKqyyVQnTdEfv68q91yLcKrQ==",
      "dev": true,
      "license": "MIT",
      "peerDependencies": {
        "@types/react": "^18.0.0"
      }
    },
    "node_modules/@vitest/expect": {
      "version": "2.1.9",
      "resolved": "https://registry.npmjs.org/@vitest/expect/-/expect-2.1.9.tgz",
      "integrity": "sha512-UJCIkTBenHeKT1TTlKMJWy1laZewsRIzYighyYiJKZreqtdxSos/S1t+ktRMQWu2CKqaarrkeszJx1cgC5tGZw==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "@vitest/spy": "2.1.9",
        "@vitest/utils": "2.1.9",
        "chai": "^5.1.2",
        "tinyrainbow": "^1.2.0"
      },
      "funding": {
        "url": "https://opencollective.com/vitest"
      }
    },
    "node_modules/@vitest/mocker": {
      "version": "2.1.9",
      "resolved": "https://registry.npmjs.org/@vitest/mocker/-/mocker-2.1.9.tgz",
      "integrity": "sha512-tVL6uJgoUdi6icpxmdrn5YNo3g3Dxv+IHJBr0GXHaEdTcw3F+cPKnsXFhli6nO+f/6SDKPHEK1UN+k+TQv0Ehg==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "@vitest/spy": "2.1.9",
        "estree-walker": "^3.0.3",
        "magic-string": "^0.30.12"
      },
      "funding": {
        "url": "https://opencollective.com/vitest"
      },
      "peerDependencies": {
        "msw": "^2.4.9",
        "vite": "^5.0.0"
      },
      "peerDependenciesMeta": {
        "msw": {
          "optional": true
        },
        "vite": {
          "optional": true
        }
      }
    },
    "node_modules/@vitest/pretty-format": {
      "version": "2.1.9",
      "resolved": "https://registry.npmjs.org/@vitest/pretty-format/-/pretty-format-2.1.9.tgz",
      "integrity": "sha512-KhRIdGV2U9HOUzxfiHmY8IFHTdqtOhIzCpd8WRdJiE7D/HUcZVD0EgQCVjm+Q9gkUXWgBvMmTtZgIG48wq7sOQ==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "tinyrainbow": "^1.2.0"
      },
      "funding": {
        "url": "https://opencollective.com/vitest"
      }
    },
    "node_modules/@vitest/runner": {
      "version": "2.1.9",
      "resolved": "https://registry.npmjs.org/@vitest/runner/-/runner-2.1.9.tgz",
      "integrity": "sha512-ZXSSqTFIrzduD63btIfEyOmNcBmQvgOVsPNPe0jYtESiXkhd8u2erDLnMxmGrDCwHCCHE7hxwRDCT3pt0esT4g==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "@vitest/utils": "2.1.9",
        "pathe": "^1.1.2"
      },
      "funding": {
        "url": "https://opencollective.com/vitest"
      }
    },
    "node_modules/@vitest/snapshot": {
      "version": "2.1.9",
      "resolved": "https://registry.npmjs.org/@vitest/snapshot/-/snapshot-2.1.9.tgz",
      "integrity": "sha512-oBO82rEjsxLNJincVhLhaxxZdEtV0EFHMK5Kmx5sJ6H9L183dHECjiefOAdnqpIgT5eZwT04PoggUnW88vOBNQ==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "@vitest/pretty-format": "2.1.9",
        "magic-string": "^0.30.12",
        "pathe": "^1.1.2"
      },
      "funding": {
        "url": "https://opencollective.com/vitest"
      }
    },
    "node_modules/@vitest/spy": {
      "version": "2.1.9",
      "resolved": "https://registry.npmjs.org/@vitest/spy/-/spy-2.1.9.tgz",
      "integrity": "sha512-E1B35FwzXXTs9FHNK6bDszs7mtydNi5MIfUWpceJ8Xbfb1gBMscAnwLbEu+B44ed6W3XjL9/ehLPHR1fkf1KLQ==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "tinyspy": "^3.0.2"
      },
      "funding": {
        "url": "https://opencollective.com/vitest"
      }
    },
    "node_modules/@vitest/utils": {
      "version": "2.1.9",
      "resolved": "https://registry.npmjs.org/@vitest/utils/-/utils-2.1.9.tgz",
      "integrity": "sha512-v0psaMSkNJ3A2NMrUEHFRzJtDPFn+/VWZ5WxImB21T9fjucJRmS7xCS3ppEnARb9y11OAzaD+P2Ps+b+BGX5iQ==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "@vitest/pretty-format": "2.1.9",
        "loupe": "^3.1.2",
        "tinyrainbow": "^1.2.0"
      },
      "funding": {
        "url": "https://opencollective.com/vitest"
      }
    },
    "node_modules/agent-base": {
      "version": "7.1.4",
      "resolved": "https://registry.npmjs.org/agent-base/-/agent-base-7.1.4.tgz",
      "integrity": "sha512-MnA+YT8fwfJPgBx3m60MNqakm30XOkyIoH1y6huTQvC0PwZG7ki8NacLBcrPbNoo8vEZy7Jpuk7+jMO+CUovTQ==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">= 14"
      }
    },
    "node_modules/ansi-regex": {
      "version": "5.0.1",
      "resolved": "https://registry.npmjs.org/ansi-regex/-/ansi-regex-5.0.1.tgz",
      "integrity": "sha512-quJQXlTSUGL2LH9SUXo8VwsY4soanhgo6LNSm84E1LBcE8s3O0wpdiRzyR9z/ZZJMlMWv37qOOb9pdJlMUEKFQ==",
      "dev": true,
      "license": "MIT",
      "peer": true,
      "engines": {
        "node": ">=8"
      }
    },
    "node_modules/ansi-styles": {
      "version": "5.2.0",
      "resolved": "https://registry.npmjs.org/ansi-styles/-/ansi-styles-5.2.0.tgz",
      "integrity": "sha512-Cxwpt2SfTzTtXcfOlzGEee8O+c+MmUgGrNiBcXnuWxuFJHe6a5Hz7qwhwe5OgaSYI0IJvkLqWX1ASG+cJOkEiA==",
      "dev": true,
      "license": "MIT",
      "peer": true,
      "engines": {
        "node": ">=10"
      },
      "funding": {
        "url": "https://github.com/chalk/ansi-styles?sponsor=1"
      }
    },
    "node_modules/aria-query": {
      "version": "5.3.0",
      "resolved": "https://registry.npmjs.org/aria-query/-/aria-query-5.3.0.tgz",
      "integrity": "sha512-b0P0sZPKtyu8HkeRAfCq0IfURZK+SuwMjY1UXGBU27wpAiTwQAIlq56IbIO+ytk/JjS1fMR14ee5WBBfKi5J6A==",
      "dev": true,
      "license": "Apache-2.0",
      "dependencies": {
        "dequal": "^2.0.3"
      }
    },
    "node_modules/assertion-error": {
      "version": "2.0.1",
      "resolved": "https://registry.npmjs.org/assertion-error/-/assertion-error-2.0.1.tgz",
      "integrity": "sha512-Izi8RQcffqCeNVgFigKli1ssklIbpHnCYc6AknXGYoB6grJqyeby7jv12JUQgmTAnIDnbck1uxksT4dzN3PWBA==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/asynckit": {
      "version": "0.4.0",
      "resolved": "https://registry.npmjs.org/asynckit/-/asynckit-0.4.0.tgz",
      "integrity": "sha512-Oei9OH4tRh0YqU3GxhX79dM/mwVgvbZJaSNaRk+bshkj0S5cfHcgYakreBjrHwatXKbz+IoIdYLxrKim2MjW0Q==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/cac": {
      "version": "6.7.14",
      "resolved": "https://registry.npmjs.org/cac/-/cac-6.7.14.tgz",
      "integrity": "sha512-b6Ilus+c3RrdDk+JhLKUAQfzzgLEPy6wcXqS7f/xe1EETvsDP6GORG7SFuOs6cID5YkqchW/LXZbX5bc8j7ZcQ==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">=8"
      }
    },
    "node_modules/call-bind-apply-helpers": {
      "version": "1.0.2",
      "resolved": "https://registry.npmjs.org/call-bind-apply-helpers/-/call-bind-apply-helpers-1.0.2.tgz",
      "integrity": "sha512-Sp1ablJ0ivDkSzjcaJdxEunN5/XvksFJ2sMBFfq6x0ryhQV/2b/KwFe21cMpmHtPOSij8K99/wSfoEuTObmuMQ==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "es-errors": "^1.3.0",
        "function-bind": "^1.1.2"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/chai": {
      "version": "5.3.3",
      "resolved": "https://registry.npmjs.org/chai/-/chai-5.3.3.tgz",
      "integrity": "sha512-4zNhdJD/iOjSH0A05ea+Ke6MU5mmpQcbQsSOkgdaUMJ9zTlDTD/GYlwohmIE2u0gaxHYiVHEn1Fw9mZ/ktJWgw==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "assertion-error": "^2.0.1",
        "check-error": "^2.1.1",
        "deep-eql": "^5.0.1",
        "loupe": "^3.1.0",
        "pathval": "^2.0.0"
      },
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/check-error": {
      "version": "2.1.3",
      "resolved": "https://registry.npmjs.org/check-error/-/check-error-2.1.3.tgz",
      "integrity": "sha512-PAJdDJusoxnwm1VwW07VWwUN1sl7smmC3OKggvndJFadxxDRyFJBX/ggnu/KE4kQAB7a3Dp8f/YXC1FlUprWmA==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">= 16"
      }
    },
    "node_modules/combined-stream": {
      "version": "1.0.8",
      "resolved": "https://registry.npmjs.org/combined-stream/-/combined-stream-1.0.8.tgz",
      "integrity": "sha512-FQN4MRfuJeHf7cBbBMJFXhKSDq+2kAArBlmRBvcvFE5BB1HZKXtSFASDhdlz9zOYwxh8lDdnvmMOe/+5cdoEdg==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "delayed-stream": "~1.0.0"
      },
      "engines": {
        "node": ">= 0.8"
      }
    },
    "node_modules/css.escape": {
      "version": "1.5.1",
      "resolved": "https://registry.npmjs.org/css.escape/-/css.escape-1.5.1.tgz",
      "integrity": "sha512-YUifsXXuknHlUsmlgyY0PKzgPOr7/FjCePfHNt0jxm83wHZi44VDMQ7/fGNkjY3/jV1MC+1CmZbaHzugyeRtpg==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/cssstyle": {
      "version": "4.6.0",
      "resolved": "https://registry.npmjs.org/cssstyle/-/cssstyle-4.6.0.tgz",
      "integrity": "sha512-2z+rWdzbbSZv6/rhtvzvqeZQHrBaqgogqt85sqFNbabZOuFbCVFb8kPeEtZjiKkbrm395irpNKiYeFeLiQnFPg==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "@asamuzakjp/css-color": "^3.2.0",
        "rrweb-cssom": "^0.8.0"
      },
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/cssstyle/node_modules/rrweb-cssom": {
      "version": "0.8.0",
      "resolved": "https://registry.npmjs.org/rrweb-cssom/-/rrweb-cssom-0.8.0.tgz",
      "integrity": "sha512-guoltQEx+9aMf2gDZ0s62EcV8lsXR+0w8915TC3ITdn2YueuNjdAYh/levpU9nFaoChh9RUS5ZdQMrKfVEN9tw==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/csstype": {
      "version": "3.2.3",
      "resolved": "https://registry.npmjs.org/csstype/-/csstype-3.2.3.tgz",
      "integrity": "sha512-z1HGKcYy2xA8AGQfwrn0PAy+PB7X/GSj3UVJW9qKyn43xWa+gl5nXmU4qqLMRzWVLFC8KusUX8T/0kCiOYpAIQ==",
      "devOptional": true,
      "license": "MIT"
    },
    "node_modules/data-urls": {
      "version": "5.0.0",
      "resolved": "https://registry.npmjs.org/data-urls/-/data-urls-5.0.0.tgz",
      "integrity": "sha512-ZYP5VBHshaDAiVZxjbRVcFJpc+4xGgT0bK3vzy1HLN8jTO975HEbuYzZJcHoQEY5K1a0z8YayJkyVETa08eNTg==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "whatwg-mimetype": "^4.0.0",
        "whatwg-url": "^14.0.0"
      },
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/debug": {
      "version": "4.4.3",
      "resolved": "https://registry.npmjs.org/debug/-/debug-4.4.3.tgz",
      "integrity": "sha512-RGwwWnwQvkVfavKVt22FGLw+xYSdzARwm0ru6DhTVA3umU5hZc28V3kO4stgYryrTlLpuvgI9GiijltAjNbcqA==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "ms": "^2.1.3"
      },
      "engines": {
        "node": ">=6.0"
      },
      "peerDependenciesMeta": {
        "supports-color": {
          "optional": true
        }
      }
    },
    "node_modules/decimal.js": {
      "version": "10.6.0",
      "resolved": "https://registry.npmjs.org/decimal.js/-/decimal.js-10.6.0.tgz",
      "integrity": "sha512-YpgQiITW3JXGntzdUmyUR1V812Hn8T1YVXhCu+wO3OpS4eU9l4YdD3qjyiKdV6mvV29zapkMeD390UVEf2lkUg==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/deep-eql": {
      "version": "5.0.2",
      "resolved": "https://registry.npmjs.org/deep-eql/-/deep-eql-5.0.2.tgz",
      "integrity": "sha512-h5k/5U50IJJFpzfL6nO9jaaumfjO/f2NjK/oYB2Djzm4p9L+3T9qWpZqZ2hAbLPuuYq9wrU08WQyBTL5GbPk5Q==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">=6"
      }
    },
    "node_modules/delayed-stream": {
      "version": "1.0.0",
      "resolved": "https://registry.npmjs.org/delayed-stream/-/delayed-stream-1.0.0.tgz",
      "integrity": "sha512-ZySD7Nf91aLB0RxL4KGrKHBXl7Eds1DAmEdcoVawXnLD7SDhpNgtuII2aAkg7a7QS41jxPSZ17p4VdGnMHk3MQ==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">=0.4.0"
      }
    },
    "node_modules/dequal": {
      "version": "2.0.3",
      "resolved": "https://registry.npmjs.org/dequal/-/dequal-2.0.3.tgz",
      "integrity": "sha512-0je+qPKHEMohvfRTCEo3CrPG6cAzAYgmzKyxRiYSSDkS6eGJdyVJm7WaYA5ECaAD9wLB2T4EEeymA5aFVcYXCA==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">=6"
      }
    },
    "node_modules/dom-accessibility-api": {
      "version": "0.5.16",
      "resolved": "https://registry.npmjs.org/dom-accessibility-api/-/dom-accessibility-api-0.5.16.tgz",
      "integrity": "sha512-X7BJ2yElsnOJ30pZF4uIIDfBEVgF4XEBxL9Bxhy6dnrm5hkzqmsWHGTiHqRiITNhMyFLyAiWndIJP7Z1NTteDg==",
      "dev": true,
      "license": "MIT",
      "peer": true
    },
    "node_modules/dunder-proto": {
      "version": "1.0.1",
      "resolved": "https://registry.npmjs.org/dunder-proto/-/dunder-proto-1.0.1.tgz",
      "integrity": "sha512-KIN/nDJBQRcXw0MLVhZE9iQHmG68qAVIBg9CqmUYjmQIhgij9U5MFvrqkUL5FbtyyzZuOeOt0zdeRe4UY7ct+A==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "call-bind-apply-helpers": "^1.0.1",
        "es-errors": "^1.3.0",
        "gopd": "^1.2.0"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/entities": {
      "version": "6.0.1",
      "resolved": "https://registry.npmjs.org/entities/-/entities-6.0.1.tgz",
      "integrity": "sha512-aN97NXWF6AWBTahfVOIrB/NShkzi5H7F9r1s9mD3cDj4Ko5f2qhhVoYMibXF7GlLveb/D2ioWay8lxI97Ven3g==",
      "dev": true,
      "license": "BSD-2-Clause",
      "engines": {
        "node": ">=0.12"
      },
      "funding": {
        "url": "https://github.com/fb55/entities?sponsor=1"
      }
    },
    "node_modules/es-define-property": {
      "version": "1.0.1",
      "resolved": "https://registry.npmjs.org/es-define-property/-/es-define-property-1.0.1.tgz",
      "integrity": "sha512-e3nRfgfUZ4rNGL232gUgX06QNyyez04KdjFrF+LTRoOXmrOgFKDg4BCdsjW8EnT69eqdYGmRpJwiPVYNrCaW3g==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/es-errors": {
      "version": "1.3.0",
      "resolved": "https://registry.npmjs.org/es-errors/-/es-errors-1.3.0.tgz",
      "integrity": "sha512-Zf5H2Kxt2xjTvbJvP2ZWLEICxA6j+hAmMzIlypy4xcBg1vKVnx89Wy0GbS+kf5cwCVFFzdCFh2XSCFNULS6csw==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/es-module-lexer": {
      "version": "1.7.0",
      "resolved": "https://registry.npmjs.org/es-module-lexer/-/es-module-lexer-1.7.0.tgz",
      "integrity": "sha512-jEQoCwk8hyb2AZziIOLhDqpm5+2ww5uIE6lkO/6jcOCusfk6LhMHpXXfBLXTZ7Ydyt0j4VoUQv6uGNYbdW+kBA==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/es-object-atoms": {
      "version": "1.1.2",
      "resolved": "https://registry.npmjs.org/es-object-atoms/-/es-object-atoms-1.1.2.tgz",
      "integrity": "sha512-HWcBoN6NileqtSydK2FqHbS/LoDd2pqrnQHLyJzBj4kOp/ky2MWMN694xOfkK8/SnUsW2DH7EfyVlydKCsm1Zw==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "es-errors": "^1.3.0"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/es-set-tostringtag": {
      "version": "2.1.0",
      "resolved": "https://registry.npmjs.org/es-set-tostringtag/-/es-set-tostringtag-2.1.0.tgz",
      "integrity": "sha512-j6vWzfrGVfyXxge+O0x5sh6cvxAog0a/4Rdd2K36zCMV5eJ+/+tOAngRO8cODMNWbVRdVlmGZQL2YS3yR8bIUA==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "es-errors": "^1.3.0",
        "get-intrinsic": "^1.2.6",
        "has-tostringtag": "^1.0.2",
        "hasown": "^2.0.2"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/esbuild": {
      "version": "0.21.5",
      "resolved": "https://registry.npmjs.org/esbuild/-/esbuild-0.21.5.tgz",
      "integrity": "sha512-mg3OPMV4hXywwpoDxu3Qda5xCKQi+vCTZq8S9J/EpkhB2HzKXq4SNFZE3+NK93JYxc8VMSep+lOUSC/RVKaBqw==",
      "dev": true,
      "hasInstallScript": true,
      "license": "MIT",
      "bin": {
        "esbuild": "bin/esbuild"
      },
      "engines": {
        "node": ">=12"
      },
      "optionalDependencies": {
        "@esbuild/aix-ppc64": "0.21.5",
        "@esbuild/android-arm": "0.21.5",
        "@esbuild/android-arm64": "0.21.5",
        "@esbuild/android-x64": "0.21.5",
        "@esbuild/darwin-arm64": "0.21.5",
        "@esbuild/darwin-x64": "0.21.5",
        "@esbuild/freebsd-arm64": "0.21.5",
        "@esbuild/freebsd-x64": "0.21.5",
        "@esbuild/linux-arm": "0.21.5",
        "@esbuild/linux-arm64": "0.21.5",
        "@esbuild/linux-ia32": "0.21.5",
        "@esbuild/linux-loong64": "0.21.5",
        "@esbuild/linux-mips64el": "0.21.5",
        "@esbuild/linux-ppc64": "0.21.5",
        "@esbuild/linux-riscv64": "0.21.5",
        "@esbuild/linux-s390x": "0.21.5",
        "@esbuild/linux-x64": "0.21.5",
        "@esbuild/netbsd-x64": "0.21.5",
        "@esbuild/openbsd-x64": "0.21.5",
        "@esbuild/sunos-x64": "0.21.5",
        "@esbuild/win32-arm64": "0.21.5",
        "@esbuild/win32-ia32": "0.21.5",
        "@esbuild/win32-x64": "0.21.5"
      }
    },
    "node_modules/estree-walker": {
      "version": "3.0.3",
      "resolved": "https://registry.npmjs.org/estree-walker/-/estree-walker-3.0.3.tgz",
      "integrity": "sha512-7RUKfXgSMMkzt6ZuXmqapOurLGPPfgj6l9uRZ7lRGolvk0y2yocc35LdcxKC5PQZdn2DMqioAQ2NoWcrTKmm6g==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "@types/estree": "^1.0.0"
      }
    },
    "node_modules/expect-type": {
      "version": "1.4.0",
      "resolved": "https://registry.npmjs.org/expect-type/-/expect-type-1.4.0.tgz",
      "integrity": "sha512-KfYbmpRm0VbLjEvVa9yGwCi9GI34xvi7A/HXYWQO65CSD2u3MczUJSuwXKFIxlGsgBQizV9q5J9NHj4VG0n+pA==",
      "dev": true,
      "license": "Apache-2.0",
      "engines": {
        "node": ">=12.0.0"
      }
    },
    "node_modules/form-data": {
      "version": "4.0.6",
      "resolved": "https://registry.npmjs.org/form-data/-/form-data-4.0.6.tgz",
      "integrity": "sha512-vKatAh4SlVfgbv+YtmhiRjhEMJsYpsG1Y2rMQtR+SVSbytsSD1YGzDIcrAJmdFec88u/+VoGmxnl+80gL1tRCQ==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "asynckit": "^0.4.0",
        "combined-stream": "^1.0.8",
        "es-set-tostringtag": "^2.1.0",
        "hasown": "^2.0.4",
        "mime-types": "^2.1.35"
      },
      "engines": {
        "node": ">= 6"
      }
    },
    "node_modules/fsevents": {
      "version": "2.3.3",
      "resolved": "https://registry.npmjs.org/fsevents/-/fsevents-2.3.3.tgz",
      "integrity": "sha512-5xoDfX+fL7faATnagmWPpbFtwh/R77WmMMqqHGS65C3vvB0YHrgF+B1YmZ3441tMj5n63k0212XNoJwzlhffQw==",
      "dev": true,
      "hasInstallScript": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "darwin"
      ],
      "engines": {
        "node": "^8.16.0 || ^10.6.0 || >=11.0.0"
      }
    },
    "node_modules/function-bind": {
      "version": "1.1.2",
      "resolved": "https://registry.npmjs.org/function-bind/-/function-bind-1.1.2.tgz",
      "integrity": "sha512-7XHNxH7qX9xG5mIwxkhumTox/MIRNcOgDrxWsMt2pAr23WHp6MrRlN7FBSFpCpr+oVO0F744iUgR82nJMfG2SA==",
      "dev": true,
      "license": "MIT",
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/get-intrinsic": {
      "version": "1.3.0",
      "resolved": "https://registry.npmjs.org/get-intrinsic/-/get-intrinsic-1.3.0.tgz",
      "integrity": "sha512-9fSjSaos/fRIVIp+xSJlE6lfwhES7LNtKaCBIamHsjr2na1BiABJPo0mOjjz8GJDURarmCPGqaiVg5mfjb98CQ==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "call-bind-apply-helpers": "^1.0.2",
        "es-define-property": "^1.0.1",
        "es-errors": "^1.3.0",
        "es-object-atoms": "^1.1.1",
        "function-bind": "^1.1.2",
        "get-proto": "^1.0.1",
        "gopd": "^1.2.0",
        "has-symbols": "^1.1.0",
        "hasown": "^2.0.2",
        "math-intrinsics": "^1.1.0"
      },
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/get-proto": {
      "version": "1.0.1",
      "resolved": "https://registry.npmjs.org/get-proto/-/get-proto-1.0.1.tgz",
      "integrity": "sha512-sTSfBjoXBp89JvIKIefqw7U2CCebsc74kiY6awiGogKtoSGbgjYE/G/+l9sF3MWFPNc9IcoOC4ODfKHfxFmp0g==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "dunder-proto": "^1.0.1",
        "es-object-atoms": "^1.0.0"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/gopd": {
      "version": "1.2.0",
      "resolved": "https://registry.npmjs.org/gopd/-/gopd-1.2.0.tgz",
      "integrity": "sha512-ZUKRh6/kUFoAiTAtTYPZJ3hw9wNxx+BIBOijnlG9PnrJsCcSjs1wyyD6vJpaYtgnzDrKYRSqf3OO6Rfa93xsRg==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/has-symbols": {
      "version": "1.1.0",
      "resolved": "https://registry.npmjs.org/has-symbols/-/has-symbols-1.1.0.tgz",
      "integrity": "sha512-1cDNdwJ2Jaohmb3sg4OmKaMBwuC48sYni5HUw2DvsC8LjGTLK9h+eb1X6RyuOHe4hT0ULCW68iomhjUoKUqlPQ==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/has-tostringtag": {
      "version": "1.0.2",
      "resolved": "https://registry.npmjs.org/has-tostringtag/-/has-tostringtag-1.0.2.tgz",
      "integrity": "sha512-NqADB8VjPFLM2V0VvHUewwwsw0ZWBaIdgo+ieHtK3hasLz4qeCRjYcqfB6AQrBggRKppKF8L52/VqdVsO47Dlw==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "has-symbols": "^1.0.3"
      },
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/hasown": {
      "version": "2.0.4",
      "resolved": "https://registry.npmjs.org/hasown/-/hasown-2.0.4.tgz",
      "integrity": "sha512-T2UbfbBEF32wiepXIsMlTW9+dDYC6wMh/t/vYA4tuOMKqWz/n3vr1NFSxQiyP+zk2mXsoMA/i/7qV6LKut1t1A==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "function-bind": "^1.1.2"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/html-encoding-sniffer": {
      "version": "4.0.0",
      "resolved": "https://registry.npmjs.org/html-encoding-sniffer/-/html-encoding-sniffer-4.0.0.tgz",
      "integrity": "sha512-Y22oTqIU4uuPgEemfz7NDJz6OeKf12Lsu+QC+s3BVpda64lTiMYCyGwg5ki4vFxkMwQdeZDl2adZoqUgdFuTgQ==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "whatwg-encoding": "^3.1.1"
      },
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/http-proxy-agent": {
      "version": "7.0.2",
      "resolved": "https://registry.npmjs.org/http-proxy-agent/-/http-proxy-agent-7.0.2.tgz",
      "integrity": "sha512-T1gkAiYYDWYx3V5Bmyu7HcfcvL7mUrTWiM6yOfa3PIphViJ/gFPbvidQ+veqSOHci/PxBcDabeUNCzpOODJZig==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "agent-base": "^7.1.0",
        "debug": "^4.3.4"
      },
      "engines": {
        "node": ">= 14"
      }
    },
    "node_modules/https-proxy-agent": {
      "version": "7.0.6",
      "resolved": "https://registry.npmjs.org/https-proxy-agent/-/https-proxy-agent-7.0.6.tgz",
      "integrity": "sha512-vK9P5/iUfdl95AI+JVyUuIcVtd4ofvtrOr3HNtM2yxC9bnMbEdp3x01OhQNnjb8IJYi38VlTE3mBXwcfvywuSw==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "agent-base": "^7.1.2",
        "debug": "4"
      },
      "engines": {
        "node": ">= 14"
      }
    },
    "node_modules/iconv-lite": {
      "version": "0.6.3",
      "resolved": "https://registry.npmjs.org/iconv-lite/-/iconv-lite-0.6.3.tgz",
      "integrity": "sha512-4fCk79wshMdzMp2rH06qWrJE4iolqLhCUH+OiuIgU++RB0+94NlDL81atO7GX55uUKueo0txHNtvEyI6D7WdMw==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "safer-buffer": ">= 2.1.2 < 3.0.0"
      },
      "engines": {
        "node": ">=0.10.0"
      }
    },
    "node_modules/indent-string": {
      "version": "4.0.0",
      "resolved": "https://registry.npmjs.org/indent-string/-/indent-string-4.0.0.tgz",
      "integrity": "sha512-EdDDZu4A2OyIK7Lr/2zG+w5jmbuk1DVBnEwREQvBzspBJkCEbRa8GxU1lghYcaGJCnRWibjDXlq779X1/y5xwg==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">=8"
      }
    },
    "node_modules/is-potential-custom-element-name": {
      "version": "1.0.1",
      "resolved": "https://registry.npmjs.org/is-potential-custom-element-name/-/is-potential-custom-element-name-1.0.1.tgz",
      "integrity": "sha512-bCYeRA2rVibKZd+s2625gGnGF/t7DSqDs4dP7CrLA1m7jKWz6pps0LpYLJN8Q64HtmPKJ1hrN3nzPNKFEKOUiQ==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/js-tokens": {
      "version": "4.0.0",
      "resolved": "https://registry.npmjs.org/js-tokens/-/js-tokens-4.0.0.tgz",
      "integrity": "sha512-RdJUflcE3cUzKiMqQgsCu06FPu9UdIJO0beYbPhHN4k6apgJtifcoCtT9bcxOpYBtpD2kCM6Sbzg4CausW/PKQ==",
      "license": "MIT"
    },
    "node_modules/jsdom": {
      "version": "25.0.1",
      "resolved": "https://registry.npmjs.org/jsdom/-/jsdom-25.0.1.tgz",
      "integrity": "sha512-8i7LzZj7BF8uplX+ZyOlIz86V6TAsSs+np6m1kpW9u0JWi4z/1t+FzcK1aek+ybTnAC4KhBL4uXCNT0wcUIeCw==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "cssstyle": "^4.1.0",
        "data-urls": "^5.0.0",
        "decimal.js": "^10.4.3",
        "form-data": "^4.0.0",
        "html-encoding-sniffer": "^4.0.0",
        "http-proxy-agent": "^7.0.2",
        "https-proxy-agent": "^7.0.5",
        "is-potential-custom-element-name": "^1.0.1",
        "nwsapi": "^2.2.12",
        "parse5": "^7.1.2",
        "rrweb-cssom": "^0.7.1",
        "saxes": "^6.0.0",
        "symbol-tree": "^3.2.4",
        "tough-cookie": "^5.0.0",
        "w3c-xmlserializer": "^5.0.0",
        "webidl-conversions": "^7.0.0",
        "whatwg-encoding": "^3.1.1",
        "whatwg-mimetype": "^4.0.0",
        "whatwg-url": "^14.0.0",
        "ws": "^8.18.0",
        "xml-name-validator": "^5.0.0"
      },
      "engines": {
        "node": ">=18"
      },
      "peerDependencies": {
        "canvas": "^2.11.2"
      },
      "peerDependenciesMeta": {
        "canvas": {
          "optional": true
        }
      }
    },
    "node_modules/loose-envify": {
      "version": "1.4.0",
      "resolved": "https://registry.npmjs.org/loose-envify/-/loose-envify-1.4.0.tgz",
      "integrity": "sha512-lyuxPGr/Wfhrlem2CL/UcnUc1zcqKAImBDzukY7Y5F/yQiNdko6+fRLevlw1HgMySw7f611UIY408EtxRSoK3Q==",
      "license": "MIT",
      "dependencies": {
        "js-tokens": "^3.0.0 || ^4.0.0"
      },
      "bin": {
        "loose-envify": "cli.js"
      }
    },
    "node_modules/loupe": {
      "version": "3.2.1",
      "resolved": "https://registry.npmjs.org/loupe/-/loupe-3.2.1.tgz",
      "integrity": "sha512-CdzqowRJCeLU72bHvWqwRBBlLcMEtIvGrlvef74kMnV2AolS9Y8xUv1I0U/MNAWMhBlKIoyuEgoJ0t/bbwHbLQ==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/lru-cache": {
      "version": "10.4.3",
      "resolved": "https://registry.npmjs.org/lru-cache/-/lru-cache-10.4.3.tgz",
      "integrity": "sha512-JNAzZcXrCt42VGLuYz0zfAzDfAvJWW6AfYlDBQyDV5DClI2m5sAmK+OIO7s59XfsRsWHp02jAJrRadPRGTt6SQ==",
      "dev": true,
      "license": "ISC"
    },
    "node_modules/lz-string": {
      "version": "1.5.0",
      "resolved": "https://registry.npmjs.org/lz-string/-/lz-string-1.5.0.tgz",
      "integrity": "sha512-h5bgJWpxJNswbU7qCrV0tIKQCaS3blPDrqKWx+QxzuzL1zGUzij9XCWLrSLsJPu5t+eWA/ycetzYAO5IOMcWAQ==",
      "dev": true,
      "license": "MIT",
      "peer": true,
      "bin": {
        "lz-string": "bin/bin.js"
      }
    },
    "node_modules/magic-string": {
      "version": "0.30.21",
      "resolved": "https://registry.npmjs.org/magic-string/-/magic-string-0.30.21.tgz",
      "integrity": "sha512-vd2F4YUyEXKGcLHoq+TEyCjxueSeHnFxyyjNp80yg0XV4vUhnDer/lvvlqM/arB5bXQN5K2/3oinyCRyx8T2CQ==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "@jridgewell/sourcemap-codec": "^1.5.5"
      }
    },
    "node_modules/math-intrinsics": {
      "version": "1.1.0",
      "resolved": "https://registry.npmjs.org/math-intrinsics/-/math-intrinsics-1.1.0.tgz",
      "integrity": "sha512-/IXtbwEk5HTPyEwyKX6hGkYXxM9nbj64B+ilVJnC/R6B0pH5G4V3b0pVbL7DBj4tkhBAppbQUlf6F6Xl9LHu1g==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/mime-db": {
      "version": "1.52.0",
      "resolved": "https://registry.npmjs.org/mime-db/-/mime-db-1.52.0.tgz",
      "integrity": "sha512-sPU4uV7dYlvtWJxwwxHD0PuihVNiE7TyAbQ5SWxDCB9mUYvOgroQOwYQQOKPJ8CIbE+1ETVlOoK1UC2nU3gYvg==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/mime-types": {
      "version": "2.1.35",
      "resolved": "https://registry.npmjs.org/mime-types/-/mime-types-2.1.35.tgz",
      "integrity": "sha512-ZDY+bPm5zTTF+YpCrAU9nK0UgICYPT0QtT1NZWFv4s++TNkcgVaT0g6+4R2uI4MjQjzysHB1zxuWL50hzaeXiw==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "mime-db": "1.52.0"
      },
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/min-indent": {
      "version": "1.0.1",
      "resolved": "https://registry.npmjs.org/min-indent/-/min-indent-1.0.1.tgz",
      "integrity": "sha512-I9jwMn07Sy/IwOj3zVkVik2JTvgpaykDZEigL6Rx6N9LbMywwUSMtxET+7lVoDLLd3O3IXwJwvuuns8UB/HeAg==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">=4"
      }
    },
    "node_modules/ms": {
      "version": "2.1.3",
      "resolved": "https://registry.npmjs.org/ms/-/ms-2.1.3.tgz",
      "integrity": "sha512-6FlzubTLZG3J2a/NVCAleEhjzq5oxgHyaCU9yYXvcLsvoVaHJq/s5xXI6/XXP6tz7R9xAOtHnSO/tXtF3WRTlA==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/nanoid": {
      "version": "3.3.19",
      "resolved": "https://registry.npmjs.org/nanoid/-/nanoid-3.3.19.tgz",
      "integrity": "sha512-Y2tUNy4ouw6tq5oDSKeQYGOyhkUBhNOcGV/02KC+6kd9eDGqdZd++mjMiIDilrBYvjEnCYvVtsuHCuP+okSfug==",
      "dev": true,
      "funding": [
        {
          "type": "github",
          "url": "https://github.com/sponsors/ai"
        }
      ],
      "license": "MIT",
      "bin": {
        "nanoid": "bin/nanoid.cjs"
      },
      "engines": {
        "node": "^10 || ^12 || ^13.7 || ^14 || >=15.0.1"
      }
    },
    "node_modules/nwsapi": {
      "version": "2.2.28",
      "resolved": "https://registry.npmjs.org/nwsapi/-/nwsapi-2.2.28.tgz",
      "integrity": "sha512-IlVB7OS7qrOsVYlpnFIkETjMwT9jwvmocJmmM+GZU/PAB3uGi9Ezd7vcWhWBUnSc0ya4ppmQITOyP1ez9gg8cg==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/object-assign": {
      "version": "4.1.1",
      "resolved": "https://registry.npmjs.org/object-assign/-/object-assign-4.1.1.tgz",
      "integrity": "sha512-rJgTQnkUnH1sFw8yT6VSU3zD3sWmu6sZhIseY8VX+GRu3P6F7Fu+JNDoXfklElbLJSnc3FUQHVe4cU5hj+BcUg==",
      "license": "MIT",
      "engines": {
        "node": ">=0.10.0"
      }
    },
    "node_modules/parse5": {
      "version": "7.3.0",
      "resolved": "https://registry.npmjs.org/parse5/-/parse5-7.3.0.tgz",
      "integrity": "sha512-IInvU7fabl34qmi9gY8XOVxhYyMyuH2xUNpb2q8/Y+7552KlejkRvqvD19nMoUW/uQGGbqNpA6Tufu5FL5BZgw==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "entities": "^6.0.0"
      },
      "funding": {
        "url": "https://github.com/inikulin/parse5?sponsor=1"
      }
    },
    "node_modules/pathe": {
      "version": "1.1.2",
      "resolved": "https://registry.npmjs.org/pathe/-/pathe-1.1.2.tgz",
      "integrity": "sha512-whLdWMYL2TwI08hn8/ZqAbrVemu0LNaNNJZX73O6qaIdCTfXutsLhMkjdENX0qhsQ9uIimo4/aQOmXkoon2nDQ==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/pathval": {
      "version": "2.0.1",
      "resolved": "https://registry.npmjs.org/pathval/-/pathval-2.0.1.tgz",
      "integrity": "sha512-//nshmD55c46FuFw26xV/xFAaB5HF9Xdap7HJBBnrKdAd6/GxDBaNA1870O79+9ueg61cZLSVc+OaFlfmObYVQ==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">= 14.16"
      }
    },
    "node_modules/picocolors": {
      "version": "1.1.1",
      "resolved": "https://registry.npmjs.org/picocolors/-/picocolors-1.1.1.tgz",
      "integrity": "sha512-xceH2snhtb5M9liqDsmEw56le376mTZkEX/jEb/RxNFyegNul7eNslCXP9FDj/Lcu0X8KEyMceP2ntpaHrDEVA==",
      "dev": true,
      "license": "ISC"
    },
    "node_modules/postcss": {
      "version": "8.5.28",
      "resolved": "https://registry.npmjs.org/postcss/-/postcss-8.5.28.tgz",
      "integrity": "sha512-RRuzqDtt5Y9h3quz5hWhK+TPnsmVs6WwSU6LkJMeY4HstUEDuYTG8UJSdawMRzmzAtV+KEoG8N3Qg2qLy5vM/A==",
      "dev": true,
      "funding": [
        {
          "type": "opencollective",
          "url": "https://opencollective.com/postcss/"
        },
        {
          "type": "tidelift",
          "url": "https://tidelift.com/funding/github/npm/postcss"
        },
        {
          "type": "github",
          "url": "https://github.com/sponsors/ai"
        }
      ],
      "license": "MIT",
      "dependencies": {
        "nanoid": "^3.3.18",
        "picocolors": "^1.1.1",
        "source-map-js": "^1.2.1"
      },
      "engines": {
        "node": "^10 || ^12 || >=14"
      }
    },
    "node_modules/prettier": {
      "version": "3.9.9",
      "resolved": "https://registry.npmjs.org/prettier/-/prettier-3.9.9.tgz",
      "integrity": "sha512-Z/CJHIkdujO/OtN7nXUii0Rf3VT5SRuhjBA82Xvu2XhBUgX3nhP67T0LHceBdQLex7OOFGTox+Q5Yg8Jk2Qivg==",
      "dev": true,
      "bin": {
        "prettier": "bin/prettier.cjs"
      },
      "engines": {
        "node": ">=14"
      },
      "funding": {
        "url": "https://github.com/prettier/prettier?sponsor=1"
      }
    },
    "node_modules/pretty-format": {
      "version": "27.5.1",
      "resolved": "https://registry.npmjs.org/pretty-format/-/pretty-format-27.5.1.tgz",
      "integrity": "sha512-Qb1gy5OrP5+zDf2Bvnzdl3jsTf1qXVMazbvCoKhtKqVs4/YK4ozX4gKQJJVyNe+cajNPn0KoC0MC3FUmaHWEmQ==",
      "dev": true,
      "license": "MIT",
      "peer": true,
      "dependencies": {
        "ansi-regex": "^5.0.1",
        "ansi-styles": "^5.0.0",
        "react-is": "^17.0.1"
      },
      "engines": {
        "node": "^10.13.0 || ^12.13.0 || ^14.15.0 || >=15.0.0"
      }
    },
    "node_modules/prop-types": {
      "version": "15.8.1",
      "resolved": "https://registry.npmjs.org/prop-types/-/prop-types-15.8.1.tgz",
      "integrity": "sha512-oj87CgZICdulUohogVAR7AjlC0327U4el4L6eAvOqCeudMDVU0NThNaV+b9Df4dXgSP1gXMTnPdhfe/2qDH5cg==",
      "license": "MIT",
      "dependencies": {
        "loose-envify": "^1.4.0",
        "object-assign": "^4.1.1",
        "react-is": "^16.13.1"
      }
    },
    "node_modules/prop-types/node_modules/react-is": {
      "version": "16.13.1",
      "resolved": "https://registry.npmjs.org/react-is/-/react-is-16.13.1.tgz",
      "integrity": "sha512-24e6ynE2H+OKt4kqsOvNd8kBpV65zoxbA4BVsEOB3ARVWQki/DHzaUoC5KuON/BiccDaCCTZBuOcfZs70kR8bQ==",
      "license": "MIT"
    },
    "node_modules/punycode": {
      "version": "2.3.1",
      "resolved": "https://registry.npmjs.org/punycode/-/punycode-2.3.1.tgz",
      "integrity": "sha512-vYt7UD1U9Wg6138shLtLOvdAu+8DsC/ilFtEVHcH+wydcSpNE20AfSOduf6MkRFahL5FY7X1oU7nKVZFtfq8Fg==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">=6"
      }
    },
    "node_modules/react": {
      "version": "18.3.1",
      "resolved": "https://registry.npmjs.org/react/-/react-18.3.1.tgz",
      "integrity": "sha512-wS+hAgJShR0KhEvPJArfuPVN1+Hz1t0Y6n5jLrGQbkb4urgPE/0Rve+1kMB1v/oWgHgm4WIcV+i7F2pTVj+2iQ==",
      "dependencies": {
        "loose-envify": "^1.1.0"
      },
      "engines": {
        "node": ">=0.10.0"
      }
    },
    "node_modules/react-dom": {
      "version": "18.3.1",
      "resolved": "https://registry.npmjs.org/react-dom/-/react-dom-18.3.1.tgz",
      "integrity": "sha512-5m4nQKp+rZRb09LNH59GM4BxTh9251/ylbKIbpe7TpGxfJ+9kv6BLkLBXIjjspbgbnIBNqlI23tRnTWT0snUIw==",
      "dependencies": {
        "loose-envify": "^1.1.0",
        "scheduler": "^0.23.2"
      },
      "peerDependencies": {
        "react": "^18.3.1"
      }
    },
    "node_modules/react-is": {
      "version": "17.0.2",
      "resolved": "https://registry.npmjs.org/react-is/-/react-is-17.0.2.tgz",
      "integrity": "sha512-w2GsyukL62IJnlaff/nRegPQR94C/XXamvMWmSHRJ4y7Ts/4ocGRmTHvOs8PSE6pB3dWOrD/nueuU5sduBsQ4w==",
      "dev": true,
      "license": "MIT",
      "peer": true
    },
    "node_modules/react-router": {
      "version": "6.30.6",
      "resolved": "https://registry.npmjs.org/react-router/-/react-router-6.30.6.tgz",
      "integrity": "sha512-5HfK7k5im7LTOB0EqCQmfvy4C13G92Ssj1VTmouTK3AJvyjKTnFuCV0vcMAD/JS+JC4DvDIBRrlAeJIFjh5VWg==",
      "license": "MIT",
      "dependencies": {
        "@remix-run/router": "1.23.4"
      },
      "engines": {
        "node": ">=14.0.0"
      },
      "peerDependencies": {
        "react": ">=16.8"
      }
    },
    "node_modules/react-router-dom": {
      "version": "6.30.6",
      "resolved": "https://registry.npmjs.org/react-router-dom/-/react-router-dom-6.30.6.tgz",
      "integrity": "sha512-0RHKZz7wwffvkU+2MFVT2NnjK44ssLEV+m0CAJaS2Ksmorrwj7WxH00jO0SOCW26/tINUnJHToXblDs33I38YQ==",
      "license": "MIT",
      "dependencies": {
        "@remix-run/router": "1.23.4",
        "react-router": "6.30.6"
      },
      "engines": {
        "node": ">=14.0.0"
      },
      "peerDependencies": {
        "react": ">=16.8",
        "react-dom": ">=16.8"
      }
    },
    "node_modules/redent": {
      "version": "3.0.0",
      "resolved": "https://registry.npmjs.org/redent/-/redent-3.0.0.tgz",
      "integrity": "sha512-6tDA8g98We0zd0GvVeMT9arEOnTw9qM03L9cJXaCjrip1OO764RDBLBfrB4cwzNGDj5OA5ioymC9GkizgWJDUg==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "indent-string": "^4.0.0",
        "strip-indent": "^3.0.0"
      },
      "engines": {
        "node": ">=8"
      }
    },
    "node_modules/rollup": {
      "version": "4.63.5",
      "resolved": "https://registry.npmjs.org/rollup/-/rollup-4.63.5.tgz",
      "integrity": "sha512-KRWwmNLlPw5M7HcdYfm15oBv9n9LPtjzpzCIxS/phwqvPyxHSoKX6Y2YU3pxSPfy0CLquVgsx/j/hBi6OvH1Nw==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "@types/estree": "1.0.9"
      },
      "bin": {
        "rollup": "dist/bin/rollup"
      },
      "engines": {
        "node": ">=18.0.0",
        "npm": ">=8.0.0"
      },
      "optionalDependencies": {
        "@napi-rs/lzma-linux-x64-gnu": "1.5.1",
        "@rollup/rollup-android-arm-eabi": "4.63.5",
        "@rollup/rollup-android-arm64": "4.63.5",
        "@rollup/rollup-darwin-arm64": "4.63.5",
        "@rollup/rollup-darwin-x64": "4.63.5",
        "@rollup/rollup-freebsd-arm64": "4.63.5",
        "@rollup/rollup-freebsd-x64": "4.63.5",
        "@rollup/rollup-linux-arm-gnueabihf": "4.63.5",
        "@rollup/rollup-linux-arm-musleabihf": "4.63.5",
        "@rollup/rollup-linux-arm64-gnu": "4.63.5",
        "@rollup/rollup-linux-arm64-musl": "4.63.5",
        "@rollup/rollup-linux-loong64-gnu": "4.63.5",
        "@rollup/rollup-linux-loong64-musl": "4.63.5",
        "@rollup/rollup-linux-ppc64-gnu": "4.63.5",
        "@rollup/rollup-linux-ppc64-musl": "4.63.5",
        "@rollup/rollup-linux-riscv64-gnu": "4.63.5",
        "@rollup/rollup-linux-riscv64-musl": "4.63.5",
        "@rollup/rollup-linux-s390x-gnu": "4.63.5",
        "@rollup/rollup-linux-x64-gnu": "4.63.5",
        "@rollup/rollup-linux-x64-musl": "4.63.5",
        "@rollup/rollup-openbsd-x64": "4.63.5",
        "@rollup/rollup-openharmony-arm64": "4.63.5",
        "@rollup/rollup-win32-arm64-msvc": "4.63.5",
        "@rollup/rollup-win32-ia32-msvc": "4.63.5",
        "@rollup/rollup-win32-x64-gnu": "4.63.5",
        "@rollup/rollup-win32-x64-msvc": "4.63.5",
        "fsevents": "~2.3.2"
      }
    },
    "node_modules/rrweb-cssom": {
      "version": "0.7.1",
      "resolved": "https://registry.npmjs.org/rrweb-cssom/-/rrweb-cssom-0.7.1.tgz",
      "integrity": "sha512-TrEMa7JGdVm0UThDJSx7ddw5nVm3UJS9o9CCIZ72B1vSyEZoziDqBYP3XIoi/12lKrJR8rE3jeFHMok2F/Mnsg==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/safer-buffer": {
      "version": "2.1.2",
      "resolved": "https://registry.npmjs.org/safer-buffer/-/safer-buffer-2.1.2.tgz",
      "integrity": "sha512-YZo3K82SD7Riyi0E1EQPojLz7kpepnSQI9IyPbHHg1XXXevb5dJI7tpyN2ADxGcQbHG7vcyRHk0cbwqcQriUtg==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/saxes": {
      "version": "6.0.0",
      "resolved": "https://registry.npmjs.org/saxes/-/saxes-6.0.0.tgz",
      "integrity": "sha512-xAg7SOnEhrm5zI3puOOKyy1OMcMlIJZYNJY7xLBwSze0UjhPLnWfj2GF2EpT0jmzaJKIWKHLsaSSajf35bcYnA==",
      "dev": true,
      "license": "ISC",
      "dependencies": {
        "xmlchars": "^2.2.0"
      },
      "engines": {
        "node": ">=v12.22.7"
      }
    },
    "node_modules/scheduler": {
      "version": "0.23.2",
      "resolved": "https://registry.npmjs.org/scheduler/-/scheduler-0.23.2.tgz",
      "integrity": "sha512-UOShsPwz7NrMUqhR6t0hWjFduvOzbtv7toDH1/hIrfRNIDBnnBWd0CwJTGvTpngVlmwGCdP9/Zl/tVrDqcuYzQ==",
      "dependencies": {
        "loose-envify": "^1.1.0"
      }
    },
    "node_modules/siginfo": {
      "version": "2.0.0",
      "resolved": "https://registry.npmjs.org/siginfo/-/siginfo-2.0.0.tgz",
      "integrity": "sha512-ybx0WO1/8bSBLEWXZvEd7gMW3Sn3JFlW3TvX1nREbDLRNQNaeNN8WK0meBwPdAaOI7TtRRRJn/Es1zhrrCHu7g==",
      "dev": true,
      "license": "ISC"
    },
    "node_modules/source-map-js": {
      "version": "1.2.1",
      "resolved": "https://registry.npmjs.org/source-map-js/-/source-map-js-1.2.1.tgz",
      "integrity": "sha512-UXWMKhLOwVKb728IUtQPXxfYU+usdybtUrK/8uGE8CQMvrhOpwvzDBwj0QhSL7MQc7vIsISBG8VQ8+IDQxpfQA==",
      "dev": true,
      "license": "BSD-3-Clause",
      "engines": {
        "node": ">=0.10.0"
      }
    },
    "node_modules/stackback": {
      "version": "0.0.2",
      "resolved": "https://registry.npmjs.org/stackback/-/stackback-0.0.2.tgz",
      "integrity": "sha512-1XMJE5fQo1jGH6Y/7ebnwPOBEkIEnT4QF32d5R1+VXdXveM0IBMJt8zfaxX1P3QhVwrYe+576+jkANtSS2mBbw==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/std-env": {
      "version": "3.10.0",
      "resolved": "https://registry.npmjs.org/std-env/-/std-env-3.10.0.tgz",
      "integrity": "sha512-5GS12FdOZNliM5mAOxFRg7Ir0pWz8MdpYm6AY6VPkGpbA7ZzmbzNcBJQ0GPvvyWgcY7QAhCgf9Uy89I03faLkg==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/strip-indent": {
      "version": "3.0.0",
      "resolved": "https://registry.npmjs.org/strip-indent/-/strip-indent-3.0.0.tgz",
      "integrity": "sha512-laJTa3Jb+VQpaC6DseHhF7dXVqHTfJPCRDaEbid/drOhgitgYku/letMUqOXFoWV0zIIUbjpdH2t+tYj4bQMRQ==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "min-indent": "^1.0.0"
      },
      "engines": {
        "node": ">=8"
      }
    },
    "node_modules/symbol-tree": {
      "version": "3.2.4",
      "resolved": "https://registry.npmjs.org/symbol-tree/-/symbol-tree-3.2.4.tgz",
      "integrity": "sha512-9QNk5KwDF+Bvz+PyObkmSYjI5ksVUYtjW7AU22r2NKcfLJcXp96hkDWU3+XndOsUb+AQ9QhfzfCT2O+CNWT5Tw==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/tinybench": {
      "version": "2.9.0",
      "resolved": "https://registry.npmjs.org/tinybench/-/tinybench-2.9.0.tgz",
      "integrity": "sha512-0+DUvqWMValLmha6lr4kD8iAMK1HzV0/aKnCtWb9v9641TnP/MFb7Pc2bxoxQjTXAErryXVgUOfv2YqNllqGeg==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/tinyexec": {
      "version": "0.3.2",
      "resolved": "https://registry.npmjs.org/tinyexec/-/tinyexec-0.3.2.tgz",
      "integrity": "sha512-KQQR9yN7R5+OSwaK0XQoj22pwHoTlgYqmUscPYoknOoWCWfj/5/ABTMRi69FrKU5ffPVh5QcFikpWJI/P1ocHA==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/tinypool": {
      "version": "1.1.1",
      "resolved": "https://registry.npmjs.org/tinypool/-/tinypool-1.1.1.tgz",
      "integrity": "sha512-Zba82s87IFq9A9XmjiX5uZA/ARWDrB03OHlq+Vw1fSdt0I+4/Kutwy8BP4Y/y/aORMo61FQ0vIb5j44vSo5Pkg==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": "^18.0.0 || >=20.0.0"
      }
    },
    "node_modules/tinyrainbow": {
      "version": "1.2.0",
      "resolved": "https://registry.npmjs.org/tinyrainbow/-/tinyrainbow-1.2.0.tgz",
      "integrity": "sha512-weEDEq7Z5eTHPDh4xjX789+fHfF+P8boiFB+0vbWzpbnbsEr/GRaohi/uMKxg8RZMXnl1ItAi/IUHWMsjDV7kQ==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">=14.0.0"
      }
    },
    "node_modules/tinyspy": {
      "version": "3.0.2",
      "resolved": "https://registry.npmjs.org/tinyspy/-/tinyspy-3.0.2.tgz",
      "integrity": "sha512-n1cw8k1k0x4pgA2+9XrOkFydTerNcJ1zWCO5Nn9scWHTD+5tp8dghT2x1uduQePZTZgd3Tupf+x9BxJjeJi77Q==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">=14.0.0"
      }
    },
    "node_modules/tldts": {
      "version": "6.1.86",
      "resolved": "https://registry.npmjs.org/tldts/-/tldts-6.1.86.tgz",
      "integrity": "sha512-WMi/OQ2axVTf/ykqCQgXiIct+mSQDFdH2fkwhPwgEwvJ1kSzZRiinb0zF2Xb8u4+OqPChmyI6MEu4EezNJz+FQ==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "tldts-core": "^6.1.86"
      },
      "bin": {
        "tldts": "bin/cli.js"
      }
    },
    "node_modules/tldts-core": {
      "version": "6.1.86",
      "resolved": "https://registry.npmjs.org/tldts-core/-/tldts-core-6.1.86.tgz",
      "integrity": "sha512-Je6p7pkk+KMzMv2XXKmAE3McmolOQFdxkKw0R8EYNr7sELW46JqnNeTX8ybPiQgvg1ymCoF8LXs5fzFaZvJPTA==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/tough-cookie": {
      "version": "5.1.2",
      "resolved": "https://registry.npmjs.org/tough-cookie/-/tough-cookie-5.1.2.tgz",
      "integrity": "sha512-FVDYdxtnj0G6Qm/DhNPSb8Ju59ULcup3tuJxkFb5K8Bv2pUXILbf0xZWU8PX8Ov19OXljbUyveOFwRMwkXzO+A==",
      "dev": true,
      "license": "BSD-3-Clause",
      "dependencies": {
        "tldts": "^6.1.32"
      },
      "engines": {
        "node": ">=16"
      }
    },
    "node_modules/tr46": {
      "version": "5.1.1",
      "resolved": "https://registry.npmjs.org/tr46/-/tr46-5.1.1.tgz",
      "integrity": "sha512-hdF5ZgjTqgAntKkklYw0R03MG2x/bSzTtkxmIRw/sTNV8YXsCJ1tfLAX23lhxhHJlEf3CRCOCGGWw3vI3GaSPw==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "punycode": "^2.3.1"
      },
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/typescript": {
      "version": "5.6.3",
      "resolved": "https://registry.npmjs.org/typescript/-/typescript-5.6.3.tgz",
      "integrity": "sha512-hjcS1mhfuyi4WW8IWtjP7brDrG2cuDZukyrYrSauoXGNgx0S7zceP07adYkJycEr56BOUTNPzbInooiN3fn1qw==",
      "dev": true,
      "license": "Apache-2.0",
      "bin": {
        "tsc": "bin/tsc",
        "tsserver": "bin/tsserver"
      },
      "engines": {
        "node": ">=14.17"
      }
    },
    "node_modules/undici-types": {
      "version": "6.21.0",
      "resolved": "https://registry.npmjs.org/undici-types/-/undici-types-6.21.0.tgz",
      "integrity": "sha512-iwDZqg0QAGrg9Rav5H4n0M64c3mkR59cJ6wQp+7C4nI0gsmExaedaYLNO44eT4AtBBwjbTiGPMlt2Md0T9H9JQ==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/vite": {
      "version": "5.4.21",
      "resolved": "https://registry.npmjs.org/vite/-/vite-5.4.21.tgz",
      "integrity": "sha512-o5a9xKjbtuhY6Bi5S3+HvbRERmouabWbyUcpXXUA1u+GNUKoROi9byOJ8M0nHbHYHkYICiMlqxkg1KkYmm25Sw==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "esbuild": "^0.21.3",
        "postcss": "^8.4.43",
        "rollup": "^4.20.0"
      },
      "bin": {
        "vite": "bin/vite.js"
      },
      "engines": {
        "node": "^18.0.0 || >=20.0.0"
      },
      "funding": {
        "url": "https://github.com/vitejs/vite?sponsor=1"
      },
      "optionalDependencies": {
        "fsevents": "~2.3.3"
      },
      "peerDependencies": {
        "@types/node": "^18.0.0 || >=20.0.0",
        "less": "*",
        "lightningcss": "^1.21.0",
        "sass": "*",
        "sass-embedded": "*",
        "stylus": "*",
        "sugarss": "*",
        "terser": "^5.4.0"
      },
      "peerDependenciesMeta": {
        "@types/node": {
          "optional": true
        },
        "less": {
          "optional": true
        },
        "lightningcss": {
          "optional": true
        },
        "sass": {
          "optional": true
        },
        "sass-embedded": {
          "optional": true
        },
        "stylus": {
          "optional": true
        },
        "sugarss": {
          "optional": true
        },
        "terser": {
          "optional": true
        }
      }
    },
    "node_modules/vite-node": {
      "version": "2.1.9",
      "resolved": "https://registry.npmjs.org/vite-node/-/vite-node-2.1.9.tgz",
      "integrity": "sha512-AM9aQ/IPrW/6ENLQg3AGY4K1N2TGZdR5e4gu/MmmR2xR3Ll1+dib+nook92g4TV3PXVyeyxdWwtaCAiUL0hMxA==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "cac": "^6.7.14",
        "debug": "^4.3.7",
        "es-module-lexer": "^1.5.4",
        "pathe": "^1.1.2",
        "vite": "^5.0.0"
      },
      "bin": {
        "vite-node": "vite-node.mjs"
      },
      "engines": {
        "node": "^18.0.0 || >=20.0.0"
      },
      "funding": {
        "url": "https://opencollective.com/vitest"
      }
    },
    "node_modules/vitest": {
      "version": "2.1.9",
      "resolved": "https://registry.npmjs.org/vitest/-/vitest-2.1.9.tgz",
      "integrity": "sha512-MSmPM9REYqDGBI8439mA4mWhV5sKmDlBKWIYbA3lRb2PTHACE0mgKwA8yQ2xq9vxDTuk4iPrECBAEW2aoFXY0Q==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "@vitest/expect": "2.1.9",
        "@vitest/mocker": "2.1.9",
        "@vitest/pretty-format": "^2.1.9",
        "@vitest/runner": "2.1.9",
        "@vitest/snapshot": "2.1.9",
        "@vitest/spy": "2.1.9",
        "@vitest/utils": "2.1.9",
        "chai": "^5.1.2",
        "debug": "^4.3.7",
        "expect-type": "^1.1.0",
        "magic-string": "^0.30.12",
        "pathe": "^1.1.2",
        "std-env": "^3.8.0",
        "tinybench": "^2.9.0",
        "tinyexec": "^0.3.1",
        "tinypool": "^1.0.1",
        "tinyrainbow": "^1.2.0",
        "vite": "^5.0.0",
        "vite-node": "2.1.9",
        "why-is-node-running": "^2.3.0"
      },
      "bin": {
        "vitest": "vitest.mjs"
      },
      "engines": {
        "node": "^18.0.0 || >=20.0.0"
      },
      "funding": {
        "url": "https://opencollective.com/vitest"
      },
      "peerDependencies": {
        "@edge-runtime/vm": "*",
        "@types/node": "^18.0.0 || >=20.0.0",
        "@vitest/browser": "2.1.9",
        "@vitest/ui": "2.1.9",
        "happy-dom": "*",
        "jsdom": "*"
      },
      "peerDependenciesMeta": {
        "@edge-runtime/vm": {
          "optional": true
        },
        "@types/node": {
          "optional": true
        },
        "@vitest/browser": {
          "optional": true
        },
        "@vitest/ui": {
          "optional": true
        },
        "happy-dom": {
          "optional": true
        },
        "jsdom": {
          "optional": true
        }
      }
    },
    "node_modules/w3c-xmlserializer": {
      "version": "5.0.0",
      "resolved": "https://registry.npmjs.org/w3c-xmlserializer/-/w3c-xmlserializer-5.0.0.tgz",
      "integrity": "sha512-o8qghlI8NZHU1lLPrpi2+Uq7abh4GGPpYANlalzWxyWteJOCsr/P+oPBA49TOLu5FTZO4d3F9MnWJfiMo4BkmA==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "xml-name-validator": "^5.0.0"
      },
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/webidl-conversions": {
      "version": "7.0.0",
      "resolved": "https://registry.npmjs.org/webidl-conversions/-/webidl-conversions-7.0.0.tgz",
      "integrity": "sha512-VwddBukDzu71offAQR975unBIGqfKZpM+8ZX6ySk8nYhVoo5CYaZyzt3YBvYtRtO+aoGlqxPg/B87NGVZ/fu6g==",
      "dev": true,
      "license": "BSD-2-Clause",
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/whatwg-encoding": {
      "version": "3.1.1",
      "resolved": "https://registry.npmjs.org/whatwg-encoding/-/whatwg-encoding-3.1.1.tgz",
      "integrity": "sha512-6qN4hJdMwfYBtE3YBTTHhoeuUrDBPZmbQaxWAqSALV/MeEnR5z1xd8UKud2RAkFoPkmB+hli1TZSnyi84xz1vQ==",
      "deprecated": "Use @exodus/bytes instead for a more spec-conformant and faster implementation",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "iconv-lite": "0.6.3"
      },
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/whatwg-mimetype": {
      "version": "4.0.0",
      "resolved": "https://registry.npmjs.org/whatwg-mimetype/-/whatwg-mimetype-4.0.0.tgz",
      "integrity": "sha512-QaKxh0eNIi2mE9p2vEdzfagOKHCcj1pJ56EEHGQOVxp8r9/iszLUUV7v89x9O1p/T+NlTM5W7jW6+cz4Fq1YVg==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/whatwg-url": {
      "version": "14.2.0",
      "resolved": "https://registry.npmjs.org/whatwg-url/-/whatwg-url-14.2.0.tgz",
      "integrity": "sha512-De72GdQZzNTUBBChsXueQUnPKDkg/5A5zp7pFDuQAj5UFoENpiACU0wlCvzpAGnTkj++ihpKwKyYewn/XNUbKw==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "tr46": "^5.1.0",
        "webidl-conversions": "^7.0.0"
      },
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/why-is-node-running": {
      "version": "2.3.0",
      "resolved": "https://registry.npmjs.org/why-is-node-running/-/why-is-node-running-2.3.0.tgz",
      "integrity": "sha512-hUrmaWBdVDcxvYqnyh09zunKzROWjbZTiNy8dBEjkS7ehEDQibXJ7XvlmtbwuTclUiIyN+CyXQD4Vmko8fNm8w==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "siginfo": "^2.0.0",
        "stackback": "0.0.2"
      },
      "bin": {
        "why-is-node-running": "cli.js"
      },
      "engines": {
        "node": ">=8"
      }
    },
    "node_modules/ws": {
      "version": "8.22.0",
      "resolved": "https://registry.npmjs.org/ws/-/ws-8.22.0.tgz",
      "integrity": "sha512-Ydggc987+RO0AnWtZ/7Wq9FtNvcrL1b/RO0ud9mWjUPgDrsAAwQSF51sm2hm1XofbU/4jkpGEsLFsZZxU+1DOg==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": ">=10.0.0"
      },
      "peerDependencies": {
        "bufferutil": "^4.0.1",
        "utf-8-validate": ">=5.0.2"
      },
      "peerDependenciesMeta": {
        "bufferutil": {
          "optional": true
        },
        "utf-8-validate": {
          "optional": true
        }
      }
    },
    "node_modules/xml-name-validator": {
      "version": "5.0.0",
      "resolved": "https://registry.npmjs.org/xml-name-validator/-/xml-name-validator-5.0.0.tgz",
      "integrity": "sha512-EvGK8EJ3DhaHfbRlETOWAS5pO9MZITeauHKJyb8wyajUfQUenkIg2MvLDTZ4T/TgIcm3HU0TFBgWWboAZ30UHg==",
      "dev": true,
      "license": "Apache-2.0",
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/xmlchars": {
      "version": "2.2.0",
      "resolved": "https://registry.npmjs.org/xmlchars/-/xmlchars-2.2.0.tgz",
      "integrity": "sha512-JZnDKK8B0RCDw84FNdDAIpZK+JuJw+s7Lz8nksI7SIuU3UXJJslUthsi+uWBUYOwPFwW7W7PRLRfUKpxjtjFCw==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/zustand": {
      "version": "5.0.15",
      "resolved": "https://registry.npmjs.org/zustand/-/zustand-5.0.15.tgz",
      "integrity": "sha512-MpSEjRiBkA9crSYeOUH32rJC7SVqAbm0Fqcqge/bUi2PPoLcBWKOsG+C8mevmpr8TwXHBVkChbbJiyvkE+i/3A==",
      "license": "MIT",
      "engines": {
        "node": ">=12.20.0"
      },
      "peerDependencies": {
        "@types/react": ">=18.0.0",
        "immer": ">=9.0.6",
        "react": ">=18.0.0",
        "use-sync-external-store": ">=1.2.0"
      },
      "peerDependenciesMeta": {
        "@types/react": {
          "optional": true
        },
        "immer": {
          "optional": true
        },
        "react": {
          "optional": true
        },
        "use-sync-external-store": {
          "optional": true
        }
      }
    },
    "shared-components": {
      "name": "@ecommerce/shared",
      "version": "1.0.0",
      "dependencies": {
        "@stripe/react-stripe-js": "^2.9.0",
        "@stripe/stripe-js": "^4.5.0",
        "@tanstack/react-query": "^5.62.8",
        "react": "18.3.1",
        "react-dom": "18.3.1",
        "react-router-dom": "^6.28.0",
        "zustand": "^5.0.2"
      }
    },
    "shared-components/node_modules/@stripe/react-stripe-js": {
      "version": "2.9.0",
      "resolved": "https://registry.npmjs.org/@stripe/react-stripe-js/-/react-stripe-js-2.9.0.tgz",
      "integrity": "sha512-+/j2g6qKAKuWSurhgRMfdlIdKM+nVVJCy/wl0US2Ccodlqx0WqfIIBhUkeONkCG+V/b+bZzcj4QVa3E/rXtT4Q==",
      "license": "MIT",
      "dependencies": {
        "prop-types": "^15.7.2"
      },
      "peerDependencies": {
        "@stripe/stripe-js": "^1.44.1 || ^2.0.0 || ^3.0.0 || ^4.0.0",
        "react": "^16.8.0 || ^17.0.0 || ^18.0.0",
        "react-dom": "^16.8.0 || ^17.0.0 || ^18.0.0"
      }
    }
  }
}

```

---

## File: `frontend/README.md`

```markdown
# Frontend

Maison Market has two Vite + React + TypeScript applications backed by the API gateway:

- `customer-app` on port `3000`: authentication, catalog/search, product reviews, cart, wishlist, Stripe card checkout, and order tracking.
- `admin-dashboard` on port `3001`: catalog management, stock updates, order lookup, and operational overview.
- `shared-components`: API client, token refresh/session handling, shared UI, types, notifications, and styles.

## Local setup

Install Node.js 18 or newer and npm. From this directory:

```bash
npm install
cp customer-app/.env.example customer-app/.env.local
cp admin-dashboard/.env.example admin-dashboard/.env.local
```

Set `VITE_STRIPE_PUBLISHABLE_KEY` in `customer-app/.env.local` to a Stripe test-mode publishable key. Never put a Stripe secret key in a browser environment variable. `VITE_API_BASE_URL` defaults to `http://localhost:8080`, the local API gateway.

Start the applications in separate terminals:

```bash
npm run dev:customer
npm run dev:admin
```

The admin app requires an account with the `ADMIN` role. The customer registration endpoint only creates `CUSTOMER` accounts.

## Checks

```bash
npm test
npm run typecheck
npm run build
```

The customer checkout uses Stripe Elements to create a payment method, submits an order with the backend's `Idempotency-Key` contract, then watches the asynchronous order/payment saga. It handles a short-lived payment-status 404 while the Kafka consumer creates the payment record, and can complete a Stripe customer-action step with the returned PaymentIntent client secret.

```

---

## File: `frontend/shared-components/package.json`

```json
{
  "name": "@ecommerce/shared",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "exports": {
    ".": "./src/index.ts",
    "./styles.css": "./src/styles.css"
  },
  "dependencies": {
    "@stripe/react-stripe-js": "^2.9.0",
    "@stripe/stripe-js": "^4.5.0",
    "@tanstack/react-query": "^5.62.8",
    "react": "18.3.1",
    "react-dom": "18.3.1",
    "react-router-dom": "^6.28.0",
    "zustand": "^5.0.2"
  }
}

```

---

## File: `frontend/shared-components/src/api.ts`

```typescript
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

```

---

## File: `frontend/shared-components/src/index.ts`

```typescript
export * from "./api";
export * from "./session";
export * from "./notifications";
export * from "./types";
export * from "./ui";
export {
  CardElement,
  Elements,
  useElements,
  useStripe,
} from "@stripe/react-stripe-js";
export { loadStripe } from "@stripe/stripe-js";

```

---

## File: `frontend/shared-components/src/notifications.tsx`

```typescript
import { useEffect } from "react";
import { create } from "zustand";

interface Notice {
  id: number;
  message: string;
  tone: "success" | "error" | "info";
}
interface NoticeState {
  notices: Notice[];
  push(message: string, tone?: Notice["tone"]): void;
  dismiss(id: number): void;
}

export const useNoticeStore = create<NoticeState>((set) => ({
  notices: [],
  push: (message, tone = "success") => {
    const id = Date.now() + Math.random();
    set((state) => ({ notices: [...state.notices, { id, message, tone }] }));
    window.setTimeout(
      () =>
        set((state) => ({
          notices: state.notices.filter((item) => item.id !== id),
        })),
      3600,
    );
  },
  dismiss: (id) =>
    set((state) => ({
      notices: state.notices.filter((item) => item.id !== id),
    })),
}));

export const notify = (message: string, tone: Notice["tone"] = "success") =>
  useNoticeStore.getState().push(message, tone);

export function ToastRegion() {
  const notices = useNoticeStore((state) => state.notices);
  const dismiss = useNoticeStore((state) => state.dismiss);
  useEffect(() => {
    if (!notices.length) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape")
        notices.forEach((notice) => dismiss(notice.id));
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [notices, dismiss]);
  return (
    <div className="toast-region" aria-live="polite">
      {notices.map((notice) => (
        <div
          key={notice.id}
          className={`toast toast-${notice.tone}`}
          role={notice.tone === "error" ? "alert" : "status"}
        >
          <span>
            {notice.tone === "success"
              ? "✓"
              : notice.tone === "error"
                ? "!"
                : "i"}
          </span>
          {notice.message}
          <button
            aria-label="Dismiss notification"
            onClick={() => dismiss(notice.id)}
          >
            ×
          </button>
        </div>
      ))}
    </div>
  );
}

```

---

## File: `frontend/shared-components/src/session.tsx`

```typescript
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useQueryClient } from "@tanstack/react-query";
import { apiClient, clearSession, readSession } from "./api";
import type { User } from "./types";

interface SessionContextValue {
  user: User | null;
  ready: boolean;
  signIn(email: string, password: string): Promise<User>;
  signUp(email: string, password: string): Promise<User>;
  signOut(): Promise<void>;
}

const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);
  const queryClient = useQueryClient();

  useEffect(() => {
    let active = true;
    const expire = () => {
      setUser(null);
      void queryClient.clear();
    };
    window.addEventListener("ecommerce:auth-expired", expire);
    if (!readSession()) setReady(true);
    else
      apiClient
        .me()
        .then((next) => {
          if (active) setUser(next);
        })
        .catch(() => {
          if (active) {
            clearSession();
            setUser(null);
          }
        })
        .finally(() => {
          if (active) setReady(true);
        });
    return () => {
      active = false;
      window.removeEventListener("ecommerce:auth-expired", expire);
    };
  }, [queryClient]);

  const signIn = useCallback(
    async (email: string, password: string) => {
      const next = await apiClient.login(email, password);
      setUser(next);
      await queryClient.invalidateQueries();
      return next;
    },
    [queryClient],
  );
  const signUp = useCallback(
    async (email: string, password: string) => {
      const next = await apiClient.register(email, password);
      setUser(next);
      await queryClient.invalidateQueries();
      return next;
    },
    [queryClient],
  );
  const signOut = useCallback(async () => {
    try {
      await apiClient.logout();
    } finally {
      setUser(null);
      await queryClient.clear();
    }
  }, [queryClient]);

  const value = useMemo(
    () => ({ user, ready, signIn, signUp, signOut }),
    [user, ready, signIn, signUp, signOut],
  );
  return (
    <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
  );
}

export function useSession() {
  const context = useContext(SessionContext);
  if (!context)
    throw new Error("useSession must be used inside SessionProvider");
  return context;
}

```

---

## File: `frontend/shared-components/src/types.ts`

```typescript
export type Role = "CUSTOMER" | "ADMIN";

export interface User {
  id: string;
  email: string;
  role: Role;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface Product {
  id: string;
  name: string;
  description?: string | null;
  category: string;
  brand: string;
  price: number;
  attributes?: Record<string, string> | null;
  createdAt?: string;
  updatedAt?: string;
  rating?: number | null;
  reviewCount?: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CartItem {
  product_id: string;
  name: string;
  price: number;
  quantity: number;
}

export interface Cart {
  user_id: string;
  items: CartItem[];
}

export interface WishlistItem {
  product_id: string;
  added_at: string;
}

export interface OrderItem {
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Order {
  orderId: string;
  userId: string;
  status: string;
  totalAmount: number;
  currency: string;
  items: OrderItem[];
  createdAt: string;
  updatedAt: string;
}

export interface Payment {
  orderId: string;
  status: string;
  amount: number;
  currency: string;
  clientSecret?: string | null;
  failureReason?: string | null;
  createdAt: string;
}

export interface Review {
  review_id: string;
  product_id: string;
  rating: number;
  title: string;
  body: string;
  verified_purchase: boolean;
  created_at: string;
  updated_at: string;
}

export interface ReviewPage extends Page<Review> {
  average_rating: number | null;
  review_count: number;
}

export interface InventoryItem {
  productId: string;
  quantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  lastUpdatedAt: string;
}

export interface SearchResult extends Page<Product> {
  facets?: {
    categories?: { value: string; count: number }[];
    brands?: { value: string; count: number }[];
  };
}

```

---

## File: `frontend/shared-components/src/ui.tsx`

```typescript
import { Link, NavLink } from "react-router-dom";
import type { ButtonHTMLAttributes, ReactNode } from "react";
import type { Product } from "./types";

export function Button({
  className = "",
  variant = "primary",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "quiet" | "danger";
}) {
  return (
    <button className={`button button-${variant} ${className}`} {...props} />
  );
}

export function PageTitle({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="page-title">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>{title}</h1>
        {description && <p className="muted lead">{description}</p>}
      </div>
      {action}
    </div>
  );
}

export function Loading({ label = "Loading" }: { label?: string }) {
  return (
    <div className="loading" role="status">
      <span className="spinner" />
      {label}
    </div>
  );
}

export function EmptyState({
  icon = "✳",
  title,
  body,
  action,
  className = "",
}: {
  icon?: string;
  title: string;
  body: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={`empty-state ${className}`}>
      <span className="empty-icon">{icon}</span>
      <h2>{title}</h2>
      <p>{body}</p>
      {action}
    </div>
  );
}

export function ErrorState({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  const message =
    error instanceof Error
      ? error.message
      : "Something went wrong while loading this page.";
  return (
    <div className="error-state">
      <strong>We hit a snag.</strong>
      <p>{message}</p>
      {retry && (
        <Button variant="secondary" onClick={retry}>
          Try again
        </Button>
      )}
    </div>
  );
}

export function StatusPill({ status }: { status: string }) {
  const normalized = status.toLowerCase().replaceAll("_", "-");
  const tone = ["confirmed", "succeeded", "paid", "active"].includes(normalized)
    ? "success"
    : ["cancelled", "failed", "declined"].includes(normalized)
      ? "danger"
      : [
            "pending-inventory",
            "pending-payment",
            "processing",
            "requires-action",
          ].includes(normalized)
        ? "pending"
        : "neutral";
  return (
    <span className={`status-pill status-${tone}`}>
      <i />
      {status.replaceAll("_", " ").toLowerCase()}
    </span>
  );
}

export function ProductVisual({
  product,
  compact = false,
}: {
  product: Product;
  compact?: boolean;
}) {
  const hue = hashHue(product.id || product.category);
  return (
    <div
      className={`product-visual ${compact ? "product-visual-compact" : ""}`}
      style={{ "--product-hue": hue } as React.CSSProperties}
      aria-hidden="true"
    >
      <div className="visual-orbit orbit-one" />
      <div className="visual-orbit orbit-two" />
      <span className="visual-category">{product.category}</span>
      <span className="visual-mark">
        {product.brand.slice(0, 1).toUpperCase()}
      </span>
    </div>
  );
}

export function ProductCard({
  product,
  onAdd,
  onFavorite,
  favorite = false,
}: {
  product: Product;
  onAdd?: () => void;
  onFavorite?: () => void;
  favorite?: boolean;
}) {
  return (
    <article className="product-card">
      <div className="product-card-art">
        <Link
          to={`/products/${encodeURIComponent(product.id)}`}
          aria-label={`View ${product.name}`}
        >
          <ProductVisual product={product} />
        </Link>
        {onFavorite && (
          <button
            className={`favorite-button ${favorite ? "is-favorite" : ""}`}
            aria-label={favorite ? "Remove from wishlist" : "Add to wishlist"}
            onClick={onFavorite}
          >
            {favorite ? "♥" : "♡"}
          </button>
        )}
      </div>
      <div className="product-card-info">
        <div className="product-meta">
          <span>{product.brand}</span>
          {product.rating != null && <span>★ {product.rating.toFixed(1)}</span>}
        </div>
        <Link
          className="product-name"
          to={`/products/${encodeURIComponent(product.id)}`}
        >
          {product.name}
        </Link>
        <div className="product-card-bottom">
          <strong>{formatMoney(product.price)}</strong>
          {onAdd && (
            <Button
              variant="quiet"
              aria-label={`Add ${product.name} to cart`}
              onClick={onAdd}
            >
              ＋
            </Button>
          )}
        </div>
      </div>
    </article>
  );
}

export function SiteHeader({
  cartCount = 0,
  admin = false,
}: {
  cartCount?: number;
  admin?: boolean;
}) {
  return (
    <header className="site-header">
      <Link to={admin ? "/" : "/"} className="brand-lockup">
        <span className="brand-mark">m</span>
        <span>
          {admin ? "Maison / studio" : "maison"}
          <small>{admin ? "Commerce console" : "Objects for everyday"}</small>
        </span>
      </Link>
      {admin ? (
        <nav className="header-nav admin-nav">
          <NavLink to="/">Overview</NavLink>
          <NavLink to="/products">Products</NavLink>
          <NavLink to="/inventory">Inventory</NavLink>
          <NavLink to="/orders">Orders</NavLink>
        </nav>
      ) : (
        <nav className="header-nav">
          <NavLink to="/">Discover</NavLink>
          <NavLink to="/wishlist">Wishlist</NavLink>
          <NavLink to="/orders">My orders</NavLink>
        </nav>
      )}
      {!admin && (
        <div className="header-actions">
          <Link
            className="header-icon"
            to="/cart"
            aria-label={`Cart, ${cartCount} items`}
          >
            Bag <span className="cart-count">{cartCount}</span>
          </Link>
          <Link className="account-link" to="/account">
            Account <span>↗</span>
          </Link>
        </div>
      )}
    </header>
  );
}

export function Footer() {
  return (
    <footer className="site-footer">
      <Link to="/" className="footer-brand">
        maison.
      </Link>
      <span>Thoughtful things, chosen well.</span>
      <span>© {new Date().getFullYear()} Maison Market</span>
    </footer>
  );
}

export function formatMoney(value: number, currency = "USD") {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(Number(value) || 0);
}

export function hashHue(value: string) {
  let hash = 0;
  for (const char of value) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return `${hash % 360}deg`;
}

export function Field({
  label,
  hint,
  className = "",
  ...props
}: React.InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  hint?: string;
}) {
  return (
    <label className={`field ${className}`}>
      <span>{label}</span>
      <input {...props} />
      {hint && <small>{hint}</small>}
    </label>
  );
}

export function SelectField({
  label,
  children,
  ...props
}: React.SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
  children: ReactNode;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <select {...props}>{children}</select>
    </label>
  );
}

export function SectionHeading({
  title,
  detail,
  href,
  linkLabel = "View all",
}: {
  title: string;
  detail?: string;
  href?: string;
  linkLabel?: string;
}) {
  return (
    <div className="section-heading">
      <div>
        <h2>{title}</h2>
        {detail && <p>{detail}</p>}
      </div>
      {href && (
        <Link to={href} className="text-link">
          {linkLabel} <span>↗</span>
        </Link>
      )}
    </div>
  );
}

```

---

## File: `frontend/tsconfig.json`

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "Bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "types": ["vite/client", "vitest/globals", "@testing-library/jest-dom"]
  }
}

```
