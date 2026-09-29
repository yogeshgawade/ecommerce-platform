import { Link, NavLink } from "react-router-dom";
import type { ButtonHTMLAttributes, ReactNode } from "react";
import type { Product } from "./types";

export function Button({
  className = "",
  variant = "primary",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "quiet" | "danger";
}) {
  return (
    <button className={`button button-${variant} ${className}`} {...props} />
  );
}

export function PageTitle({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow?: string;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="page-title">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>{title}</h1>
        {description && <p className="muted lead">{description}</p>}
      </div>
      {action}
    </div>
  );
}

export function Loading({ label = "Loading" }: { label?: string }) {
  return (
    <div className="loading" role="status">
      <span className="spinner" />
      {label}
    </div>
  );
}

export function EmptyState({
  icon = "✳",
  title,
  body,
  action,
  className = "",
}: {
  icon?: string;
  title: string;
  body: string;
  action?: ReactNode;
  className?: string;
}) {
  return (
    <div className={`empty-state ${className}`}>
      <span className="empty-icon">{icon}</span>
      <h2>{title}</h2>
      <p>{body}</p>
      {action}
    </div>
  );
}

export function ErrorState({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  const message =
    error instanceof Error
      ? error.message
      : "Something went wrong while loading this page.";
  return (
    <div className="error-state">
      <strong>We hit a snag.</strong>
      <p>{message}</p>
      {retry && (
        <Button variant="secondary" onClick={retry}>
          Try again
        </Button>
      )}
    </div>
  );
}

export function StatusPill({ status }: { status: string }) {
  const normalized = status.toLowerCase().replaceAll("_", "-");
  const tone = ["confirmed", "succeeded", "paid", "active"].includes(normalized)
    ? "success"
    : ["cancelled", "failed", "declined"].includes(normalized)
      ? "danger"
      : [
            "pending-inventory",
            "pending-payment",
            "processing",
            "requires-action",
          ].includes(normalized)
        ? "pending"
        : "neutral";
  return (
    <span className={`status-pill status-${tone}`}>
      <i />
      {status.replaceAll("_", " ").toLowerCase()}
    </span>
  );
}

export function ProductVisual({
  product,
  compact = false,
}: {
  product: Product;
  compact?: boolean;
}) {
  const hue = hashHue(product.id || product.category);
  return (
    <div
      className={`product-visual ${compact ? "product-visual-compact" : ""}`}
      style={{ "--product-hue": hue } as React.CSSProperties}
      aria-hidden="true"
    >
      <div className="visual-orbit orbit-one" />
      <div className="visual-orbit orbit-two" />
      <span className="visual-category">{product.category}</span>
      <span className="visual-mark">
        {product.brand.slice(0, 1).toUpperCase()}
      </span>
    </div>
  );
}

export function ProductCard({
  product,
  onAdd,
  onFavorite,
  favorite = false,
}: {
  product: Product;
  onAdd?: () => void;
  onFavorite?: () => void;
  favorite?: boolean;
}) {
  return (
    <article className="product-card">
      <div className="product-card-art">
        <Link
          to={`/products/${encodeURIComponent(product.id)}`}
          aria-label={`View ${product.name}`}
        >
          <ProductVisual product={product} />
        </Link>
        {onFavorite && (
          <button
            className={`favorite-button ${favorite ? "is-favorite" : ""}`}
            aria-label={favorite ? "Remove from wishlist" : "Add to wishlist"}
            onClick={onFavorite}
          >
            {favorite ? "♥" : "♡"}
          </button>
        )}
      </div>
      <div className="product-card-info">
        <div className="product-meta">
          <span>{product.brand}</span>
          {product.rating != null && <span>★ {product.rating.toFixed(1)}</span>}
        </div>
        <Link
          className="product-name"
          to={`/products/${encodeURIComponent(product.id)}`}
        >
          {product.name}
        </Link>
        <div className="product-card-bottom">
          <strong>{formatMoney(product.price)}</strong>
          {onAdd && (
            <Button
              variant="quiet"
              aria-label={`Add ${product.name} to cart`}
              onClick={onAdd}
            >
              ＋
            </Button>
          )}
        </div>
      </div>
    </article>
  );
}

export function SiteHeader({
  cartCount = 0,
  admin = false,
}: {
  cartCount?: number;
  admin?: boolean;
}) {
  return (
    <header className="site-header">
      <Link to={admin ? "/" : "/"} className="brand-lockup">
        <span className="brand-mark">m</span>
        <span>
          {admin ? "Maison / studio" : "maison"}
          <small>{admin ? "Commerce console" : "Objects for everyday"}</small>
        </span>
      </Link>
      {admin ? (
        <nav className="header-nav admin-nav">
          <NavLink to="/">Overview</NavLink>
          <NavLink to="/products">Products</NavLink>
          <NavLink to="/inventory">Inventory</NavLink>
          <NavLink to="/orders">Orders</NavLink>
        </nav>
      ) : (
        <nav className="header-nav">
          <NavLink to="/">Discover</NavLink>
          <NavLink to="/wishlist">Wishlist</NavLink>
          <NavLink to="/orders">My orders</NavLink>
        </nav>
      )}
      {!admin && (
        <div className="header-actions">
          <Link
            className="header-icon"
            to="/cart"
            aria-label={`Cart, ${cartCount} items`}
          >
            Bag <span className="cart-count">{cartCount}</span>
          </Link>
          <Link className="account-link" to="/account">
            Account <span>↗</span>
          </Link>
        </div>
      )}
    </header>
  );
}

export function Footer() {
  return (
    <footer className="site-footer">
      <Link to="/" className="footer-brand">
        maison.
      </Link>
      <span>Thoughtful things, chosen well.</span>
      <span>© {new Date().getFullYear()} Maison Market</span>
    </footer>
  );
}

export function formatMoney(value: number, currency = "USD") {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(Number(value) || 0);
}

export function hashHue(value: string) {
  let hash = 0;
  for (const char of value) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return `${hash % 360}deg`;
}

export function Field({
  label,
  hint,
  className = "",
  ...props
}: React.InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  hint?: string;
}) {
  return (
    <label className={`field ${className}`}>
      <span>{label}</span>
      <input {...props} />
      {hint && <small>{hint}</small>}
    </label>
  );
}

export function SelectField({
  label,
  children,
  ...props
}: React.SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
  children: ReactNode;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <select {...props}>{children}</select>
    </label>
  );
}

export function SectionHeading({
  title,
  detail,
  href,
  linkLabel = "View all",
}: {
  title: string;
  detail?: string;
  href?: string;
  linkLabel?: string;
}) {
  return (
    <div className="section-heading">
      <div>
        <h2>{title}</h2>
        {detail && <p>{detail}</p>}
      </div>
      {href && (
        <Link to={href} className="text-link">
          {linkLabel} <span>↗</span>
        </Link>
      )}
    </div>
  );
}
