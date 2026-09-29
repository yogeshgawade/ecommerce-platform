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
