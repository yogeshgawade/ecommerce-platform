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
