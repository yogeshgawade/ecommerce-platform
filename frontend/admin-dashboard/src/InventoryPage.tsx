import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  EmptyState,
  ErrorState,
  Field,
  Loading,
  PageTitle,
  ProductVisual,
  apiClient,
  notify,
  type InventoryItem,
  type Product,
} from "@ecommerce/shared";
export function InventoryPage() {
  const queryClient = useQueryClient();
  const inventoryQuery = useQuery({
    queryKey: ["inventory"],
    queryFn: () => apiClient.inventory(0, 100),
  });
  const productsQuery = useQuery({
    queryKey: ["admin-products"],
    queryFn: () => apiClient.products(0, 100),
  });
  const [quantities, setQuantities] = useState<Record<string, string>>({});
  const [search, setSearch] = useState("");
  const update = useMutation({
    mutationFn: ({ id, quantity }: { id: string; quantity: number }) =>
      apiClient.setInventory(id, quantity),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["inventory"] });
      notify("Stock level updated");
    },
    onError: (error) =>
      notify(
        error instanceof Error ? error.message : "Could not update stock",
        "error",
      ),
  });
  if (inventoryQuery.isPending || productsQuery.isPending)
    return <Loading label="Reading stock levels" />;
  const error = inventoryQuery.error ?? productsQuery.error;
  if (error)
    return (
      <ErrorState
        error={error}
        retry={() => {
          void inventoryQuery.refetch();
          void productsQuery.refetch();
        }}
      />
    );
  if (!inventoryQuery.data || !productsQuery.data)
    return <ErrorState error={new Error("Inventory data is incomplete.")} />;
  const productMap = new Map(
    productsQuery.data.content.map((product) => [product.id, product]),
  );
  const inventoryMap = new Map(
    inventoryQuery.data.content.map((item) => [item.productId, item]),
  );
  const allRows = productsQuery.data.content.map(
    (product) =>
      inventoryMap.get(product.id) ??
      ({
        productId: product.id,
        quantity: 0,
        reservedQuantity: 0,
        availableQuantity: 0,
        lastUpdatedAt: "",
      } satisfies InventoryItem),
  );
  const filtered = allRows.filter((item) =>
    `${item.productId} ${productMap.get(item.productId)?.name ?? ""}`
      .toLowerCase()
      .includes(search.toLowerCase()),
  );
  return (
    <>
      <PageTitle
        eyebrow="Catalog / availability"
        title="Inventory"
        description="Adjust total stock. Reserved units are protected from accidental overselling."
      />
      <div className="admin-filters">
        <form
          className="search-input"
          onSubmit={(event) => event.preventDefault()}
        >
          <input
            aria-label="Filter inventory"
            placeholder="Find a product"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
          <button aria-label="Filter">⌕</button>
        </form>
        <span className="small">{filtered.length} tracked products</span>
      </div>
      {filtered.length ? (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>Piece</th>
                <th>Total stock</th>
                <th>Reserved</th>
                <th>Available</th>
                <th>Set total stock</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((item) => (
                <InventoryRow
                  key={item.productId}
                  item={item}
                  product={productMap.get(item.productId)}
                  value={quantities[item.productId] ?? String(item.quantity)}
                  onChange={(value) =>
                    setQuantities((current) => ({
                      ...current,
                      [item.productId]: value,
                    }))
                  }
                  onSave={(quantity) =>
                    update.mutate({ id: item.productId, quantity })
                  }
                  busy={update.isPending}
                />
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <EmptyState
          title="Add products to your catalog first"
          body="Inventory is managed for products in the catalog."
        />
      )}
    </>
  );
}
function InventoryRow({
  item,
  product,
  value,
  onChange,
  onSave,
  busy,
}: {
  item: InventoryItem;
  product?: Product;
  value: string;
  onChange(value: string): void;
  onSave(quantity: number): void;
  busy: boolean;
}) {
  return (
    <tr>
      <td>
        <div className="table-product">
          {product && <ProductVisual compact product={product} />}
          <span className="table-product-copy">
            <strong>{product?.name ?? "Unknown product"}</strong>
            <small>{item.productId}</small>
          </span>
        </div>
      </td>
      <td>{item.quantity}</td>
      <td>{item.reservedQuantity}</td>
      <td>
        <strong
          className={`inventory-quantity ${item.availableQuantity <= 5 ? "inventory-low" : ""}`}
        >
          {item.availableQuantity}
        </strong>
      </td>
      <td>
        <form
          className="inline-form inventory-form"
          onSubmit={(event) => {
            event.preventDefault();
            onSave(Number(value));
          }}
        >
          <Field
            label="Total units"
            aria-label={`Total units for ${product?.name ?? item.productId}`}
            type="number"
            min={item.reservedQuantity}
            step="1"
            value={value}
            onChange={(event) => onChange(event.target.value)}
            required
          />
          <Button
            className="button-small"
            disabled={busy || !value || Number(value) < item.reservedQuantity}
          >
            Update
          </Button>
        </form>
      </td>
    </tr>
  );
}
