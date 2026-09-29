import { useState, type FormEvent, type ReactNode } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import {
  Button,
  Field,
  Footer,
  SiteHeader,
  ToastRegion,
  useSession,
} from "@ecommerce/shared";
import { messageOf } from "./helpers";
export function AuthPage({ mode }: { mode: "login" | "register" }) {
  const { user, ready, signIn, signUp } = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const destination =
    (
      location.state as {
        from?: string;
      } | null
    )?.from || "/account";
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  if (ready && user) return <Navigate to={destination} replace />;
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      if (mode === "login") await signIn(email, password);
      else await signUp(email, password);
      navigate(destination, { replace: true });
    } catch (cause) {
      setError(messageOf(cause));
    } finally {
      setBusy(false);
    }
  };
  const register = mode === "register";
  return (
    <div className="auth-layout">
      <div className="auth-art">
        <div className="auth-art-copy">
          <p className="eyebrow">The Maison list</p>
          <h2>Good taste is better shared.</h2>
          <p>
            Save your edit, follow your orders, and find your way back to the
            things you love.
          </p>
        </div>
      </div>
      <div className="auth-panel">
        <div className="auth-card">
          <p className="eyebrow">
            {register ? "Start your collection" : "Welcome back"}
          </p>
          <h1>{register ? "Make yourself at home." : "Come on in."}</h1>
          <p className="muted">
            {register
              ? "Create an account to keep all your good finds together."
              : "Sign in to find your saved pieces and orders."}
          </p>
          <form onSubmit={submit}>
            <Field
              label="Email address"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
              maxLength={254}
            />
            <Field
              label="Password"
              type="password"
              autoComplete={register ? "new-password" : "current-password"}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              minLength={register ? 8 : 1}
              maxLength={100}
              hint={register ? "At least 8 characters" : undefined}
            />
            {error && (
              <div className="error-banner" role="alert">
                {error}
              </div>
            )}
            <Button className="button-wide" disabled={busy}>
              {busy ? "One moment…" : register ? "Create account" : "Sign in"}
            </Button>
          </form>
          <p className="auth-switch">
            {register ? "Already have an account?" : "New to Maison?"}
            <a href={register ? "/login" : "/register"}>
              {register ? "Sign in" : "Create an account"}
            </a>
          </p>
        </div>
      </div>
    </div>
  );
}
export function AuthFrame({ children }: { children: ReactNode }) {
  return (
    <div className="app-shell">
      <SiteHeader />
      <main className="app-main">{children}</main>
      <Footer />
      <ToastRegion />
    </div>
  );
}
