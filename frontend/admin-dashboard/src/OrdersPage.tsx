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
