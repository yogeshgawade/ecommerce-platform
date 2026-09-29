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
