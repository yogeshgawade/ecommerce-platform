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
