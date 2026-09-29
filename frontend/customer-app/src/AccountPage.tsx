import { Navigate, useNavigate } from "react-router-dom";
import { Button, PageTitle, StatusPill, useSession } from "@ecommerce/shared";
export function AccountPage() {
  const { user, signOut } = useSession();
  const navigate = useNavigate();
  if (!user)
    return <Navigate to="/login" replace state={{ from: "/account" }} />;
  return (
    <div className="panel account-card">
      <PageTitle
        eyebrow="Your account"
        title="Welcome back."
        description="Your details are kept safe and your orders are always close by."
      />
      <div className="summary-line">
        <span>Email</span>
        <strong>{user.email}</strong>
      </div>
      <div className="summary-line">
        <span>Account type</span>
        <StatusPill status={user.role} />
      </div>
      <div className="form-actions">
        <Button variant="secondary" onClick={() => navigate("/orders")}>
          View orders
        </Button>
        <Button
          variant="quiet"
          onClick={() => void signOut().then(() => navigate("/"))}
        >
          Sign out
        </Button>
      </div>
    </div>
  );
}
