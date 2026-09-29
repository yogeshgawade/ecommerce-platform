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
