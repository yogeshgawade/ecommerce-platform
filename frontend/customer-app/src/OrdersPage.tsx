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
