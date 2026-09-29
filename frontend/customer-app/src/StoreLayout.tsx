import { useState, type FormEvent, type ReactNode } from "react";
import { Navigate, Outlet, useLocation, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  Footer,
  Loading,
  ToastRegion,
  apiClient,
  useSession,
} from "@ecommerce/shared";
export function StoreLayout() {
  const { user } = useSession();
  const cart = useQuery({
    queryKey: ["cart", user?.id],
    queryFn: () => apiClient.cart(user!.id),
    enabled: !!user,
  });
  const count =
    cart.data?.items.reduce((total, item) => total + item.quantity, 0) ?? 0;
  const [term, setTerm] = useState("");
  const navigate = useNavigate();
  const submitSearch = (event: FormEvent) => {
    event.preventDefault();
    navigate(`/?q=${encodeURIComponent(term.trim())}`);
  };
  return (
    <div className="app-shell">
      <header className="site-header">
        <a className="brand-lockup" href="/">
          <span className="brand-mark">m</span>
          <span>
            maison<small>Objects for everyday</small>
          </span>
        </a>
        <nav className="header-nav">
          <a href="/">Discover</a>
          <a href="/wishlist">Wishlist</a>
          <a href="/orders">My orders</a>
        </nav>
        <form
          className="search-input store-search"
          role="search"
          onSubmit={submitSearch}
        >
          <input
            aria-label="Search products"
            placeholder="Find something lovely…"
            value={term}
            onChange={(event) => setTerm(event.target.value)}
          />
          <button aria-label="Search">⌕</button>
        </form>
        <div className="header-actions">
          <a
            className="header-icon"
            href="/cart"
            aria-label={`Cart, ${count} items`}
          >
            Bag <span className="cart-count">{count}</span>
          </a>
          <a className="account-link" href="/account">
            Account <span>↗</span>
          </a>
        </div>
      </header>
      <nav className="mobile-nav" aria-label="Store navigation">
        <a href="/">Discover</a>
        <a href="/wishlist">Wishlist</a>
        <a href="/orders">My orders</a>
      </nav>
      <main className="app-main">
        <Outlet />
      </main>
      <Footer />
      <ToastRegion />
    </div>
  );
}
export function RequireCustomer({ children }: { children: ReactNode }) {
  const { user, ready } = useSession();
  const location = useLocation();
  if (!ready) return <Loading label="Checking your account" />;
  if (!user)
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <>{children}</>;
}
