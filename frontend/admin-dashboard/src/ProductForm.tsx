import { useState, type FormEvent, type ReactNode } from "react";
import { Button, Field, type Product } from "@ecommerce/shared";
export function ProductForm({
  product,
  onSave,
  onCancel,
  busy = false,
}: {
  product?: Product;
  onSave: (value: ProductInput) => void;
  onCancel: () => void;
  busy?: boolean;
}) {
  const [name, setName] = useState(product?.name ?? "");
  const [description, setDescription] = useState(product?.description ?? "");
  const [category, setCategory] = useState(product?.category ?? "");
  const [brand, setBrand] = useState(product?.brand ?? "");
  const [price, setPrice] = useState(
    product?.price != null ? String(product.price) : "",
  );
  const [attributes, setAttributes] = useState(
    Object.entries(product?.attributes ?? {})
      .map(([key, value]) => `${key}: ${value}`)
      .join("\n"),
  );
  const [error, setError] = useState("");
  const submit = (event: FormEvent) => {
    event.preventDefault();
    setError("");
    const amount = Number(price);
    if (!Number.isFinite(amount) || amount <= 0) {
      setError("Enter a price greater than zero.");
      return;
    }
    const attributeMap = Object.fromEntries(
      attributes
        .split("\n")
        .map((line) => line.split(":"))
        .filter((parts) => parts[0]?.trim() && parts[1]?.trim())
        .map(([key, ...value]) => [key.trim(), value.join(":").trim()]),
    );
    onSave({
      name: name.trim(),
      description: description.trim(),
      category: category.trim(),
      brand: brand.trim(),
      price: amount,
      attributes: attributeMap,
    });
  };
  return (
    <form className="form-stack" onSubmit={submit}>
      <div className="field-grid">
        <Field
          className="field-full"
          label="Product name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          maxLength={200}
          required
        />
        <Field
          label="Brand"
          value={brand}
          onChange={(event) => setBrand(event.target.value)}
          maxLength={100}
          required
        />
        <Field
          label="Category"
          value={category}
          onChange={(event) => setCategory(event.target.value)}
          maxLength={100}
          required
        />
        <Field
          label="Price (USD)"
          type="number"
          min="0.01"
          step="0.01"
          value={price}
          onChange={(event) => setPrice(event.target.value)}
          required
        />
        <label className="field field-full">
          <span>Description</span>
          <textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            maxLength={5000}
          />
        </label>
        <label className="field field-full">
          <span>Attributes</span>
          <textarea
            placeholder="Material: Oak\nFinish: Natural"
            value={attributes}
            onChange={(event) => setAttributes(event.target.value)}
          />
          <small>One attribute per line, in “Name: Value” format.</small>
        </label>
      </div>
      {error && (
        <div className="error-banner" role="alert">
          {error}
        </div>
      )}
      <div className="form-actions">
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button disabled={busy}>
          {busy ? "Saving…" : product ? "Save changes" : "Create product"}
        </Button>
      </div>
    </form>
  );
}
export interface ProductInput {
  name: string;
  description: string;
  category: string;
  brand: string;
  price: number;
  attributes: Record<string, string>;
}
export function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <div
      className="modal-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <div className="modal-header">
          <div>
            <p className="eyebrow">Maison catalog</p>
            <h2>{title}</h2>
          </div>
          <button
            className="modal-close"
            aria-label="Close dialog"
            onClick={onClose}
          >
            ×
          </button>
        </div>
        {children}
      </section>
    </div>
  );
}
