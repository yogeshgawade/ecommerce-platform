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
