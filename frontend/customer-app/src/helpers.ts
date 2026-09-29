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
