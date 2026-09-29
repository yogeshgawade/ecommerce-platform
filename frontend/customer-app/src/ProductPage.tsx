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
