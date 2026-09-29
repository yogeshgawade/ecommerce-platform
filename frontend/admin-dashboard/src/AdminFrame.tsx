import { useState, type FormEvent } from "react";
import { Navigate, NavLink, Outlet, useNavigate } from "react-router-dom";
import {
  Button,
  Field,
  Footer,
  Loading,
  ToastRegion,
  useSession,
} from "@ecommerce/shared";
export function AdminFrame() {
  const { user, ready, signOut } = useSession();
  const navigate = useNavigate();
  if (!ready) return <Loading label="Checking administrator access" />;
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== "ADMIN")
    return (
      <div className="app-shell">
        <main className="app-main">
          <div className="panel role-denied">
            <p className="eyebrow">Access restricted</p>
            <h1>This space is for the studio team.</h1>
            <p className="muted">
              Your account does not have administrator access. Switch accounts
              or return to the storefront.
            </p>
            <div className="form-actions">
              <Button
                variant="secondary"
                onClick={() => void signOut().then(() => navigate("/login"))}
              >
                Switch account
              </Button>
              <Button
                onClick={() => window.location.assign("http://localhost:3000")}
              >
                Go to storefront
              </Button>
            </div>
          </div>
        </main>
        <ToastRegion />
      </div>
    );
  return (
    <div className="app-shell">
      <header className="site-header admin-topbar">
        <a className="brand-lockup admin-logo" href="/">
          <span className="brand-mark">m</span>
          <span>
            Maison / studio<small>Commerce console</small>
          </span>
        </a>
        <div className="admin-user">
          <span>{user.email}</span>
          <span className="admin-avatar">
            {user.email.slice(0, 1).toUpperCase()}
          </span>
          <Button
            variant="quiet"
            className="button-small"
            onClick={() => void signOut()}
          >
            Sign out
          </Button>
        </div>
      </header>
      <main className="app-main">
        <div className="admin-layout">
          <nav className="admin-sidebar" aria-label="Admin navigation">
            <NavLink end to="/">
              Overview
            </NavLink>
            <NavLink to="/products">Products</NavLink>
            <NavLink to="/inventory">Inventory</NavLink>
            <NavLink to="/orders">Orders</NavLink>
          </nav>
          <section className="admin-content">
            <Outlet />
          </section>
        </div>
      </main>
      <Footer />
      <ToastRegion />
    </div>
  );
}
export function AdminLogin() {
  const { user, ready, signIn } = useSession();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  if (ready && user?.role === "ADMIN") return <Navigate to="/" replace />;
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const account = await signIn(email, password);
      if (account.role !== "ADMIN")
        throw new Error("This account does not have administrator access.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not sign in.");
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="app-shell">
      <main className="app-main">
        <div className="panel admin-prompt">
          <p className="eyebrow">Maison / studio</p>
          <h1>Welcome to the back office.</h1>
          <p className="muted">
            Sign in with an administrator account to manage the catalog,
            inventory, and orders.
          </p>
          <form className="form-stack" onSubmit={submit}>
            <Field
              label="Email address"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
            <Field
              label="Password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
            {error && (
              <div role="alert" className="error-banner">
                {error}
              </div>
            )}
            <Button disabled={busy}>
              {busy ? "Signing in…" : "Sign in to studio"}
            </Button>
          </form>
          <a className="text-link" href="http://localhost:3000">
            Back to storefront ↗
          </a>
        </div>
      </main>
      <ToastRegion />
    </div>
  );
}
