import { useMemo, useState } from "react";
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
  type Product,
} from "@ecommerce/shared";
import { ProductForm, Modal, type ProductInput } from "./ProductForm";
export function ProductsPage() {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [editing, setEditing] = useState<Product | null | "new">(null);
  const productsQuery = useQuery({
    queryKey: ["admin-products"],
    queryFn: () => apiClient.products(0, 100),
  });
  const save = useMutation({
    mutationFn: (input: ProductInput) =>
      editing && editing !== "new"
        ? apiClient.updateProduct(editing.id, input)
        : apiClient.createProduct(input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin-products"] });
      void queryClient.invalidateQueries({ queryKey: ["products"] });
      setEditing(null);
      notify("Catalog updated");
    },
    onError: (error) =>
      notify(
        error instanceof Error ? error.message : "Could not save product",
        "error",
      ),
  });
  const remove = useMutation({
    mutationFn: apiClient.deleteProduct,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin-products"] });
      notify("Product removed");
    },
    onError: (error) =>
      notify(
        error instanceof Error ? error.message : "Could not remove product",
        "error",
      ),
  });
  const filtered = useMemo(
    () =>
      productsQuery.data?.content.filter((product) =>
        `${product.name} ${product.brand} ${product.category}`
          .toLowerCase()
          .includes(search.toLowerCase()),
      ) ?? [],
    [productsQuery.data, search],
  );
  return (
    <>
      <PageTitle
        eyebrow="Catalog / assortment"
        title="Products"
        description="Keep the collection thoughtful, current, and well described."
        action={
          <Button onClick={() => setEditing("new")}>＋ New product</Button>
        }
      />
      <div className="admin-filters">
        <form
          className="search-input"
          onSubmit={(event) => event.preventDefault()}
        >
          <input
            aria-label="Filter products"
            placeholder="Filter by name, brand, or category"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <button aria-label="Filter">⌕</button>
        </form>
        <span className="small">{filtered.length} shown</span>
      </div>
      {productsQuery.isPending ? (
        <Loading label="Opening the catalog" />
      ) : productsQuery.isError ? (
        <ErrorState
          error={productsQuery.error}
          retry={() => void productsQuery.refetch()}
        />
      ) : filtered.length ? (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Piece</th>
                <th>Category</th>
                <th>Price</th>
                <th>Updated</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((product) => (
                <tr key={product.id}>
                  <td>
                    <div className="table-product">
                      <ProductVisual compact product={product} />
                      <span className="table-product-copy">
                        <strong>{product.name}</strong>
                        <small>{product.brand}</small>
                      </span>
                    </div>
                  </td>
                  <td>{product.category}</td>
                  <td>{formatMoney(product.price)}</td>
                  <td>
                    {product.updatedAt
                      ? new Date(product.updatedAt).toLocaleDateString()
                      : "—"}
                  </td>
                  <td>
                    <div className="product-actions">
                      <Button
                        className="button-small"
                        variant="secondary"
                        onClick={() => setEditing(product)}
                      >
                        Edit
                      </Button>
                      <Button
                        className="button-small"
                        variant="quiet"
                        onClick={() => {
                          if (
                            window.confirm(
                              `Remove “${product.name}” from the catalog?`,
                            )
                          )
                            remove.mutate(product.id);
                        }}
                      >
                        Delete
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <EmptyState
          title="No pieces found"
          body={
            search
              ? "Try another search."
              : "Create the first item in your collection."
          }
          action={
            !search && (
              <Button onClick={() => setEditing("new")}>
                Create a product
              </Button>
            )
          }
        />
      )}
      {editing && (
        <Modal
          title={editing === "new" ? "Add a new piece" : "Edit product"}
          onClose={() => setEditing(null)}
        >
          <ProductForm
            product={editing === "new" ? undefined : editing}
            onCancel={() => setEditing(null)}
            busy={save.isPending}
            onSave={(input) => save.mutate(input)}
          />
        </Modal>
      )}
    </>
  );
}
