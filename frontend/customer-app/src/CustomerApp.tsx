import { Navigate, Route, Routes } from "react-router-dom";
import { StoreLayout, RequireCustomer } from "./StoreLayout";
import { HomePage } from "./HomePage";
import { ProductPage } from "./ProductPage";
import { WishlistPage } from "./WishlistPage";
import { CartPage } from "./CartPage";
import { CheckoutPage } from "./CheckoutPage";
import { OrdersPage } from "./OrdersPage";
import { OrderDetailPage } from "./OrderDetailPage";
import { AccountPage } from "./AccountPage";
import { AuthPage, AuthFrame } from "./AuthPage";
export function CustomerApp() {
  return (
    <Routes>
      <Route
        path="/login"
        element={
          <AuthFrame>
            <AuthPage mode="login" />
          </AuthFrame>
        }
      />
      <Route
        path="/register"
        element={
          <AuthFrame>
            <AuthPage mode="register" />
          </AuthFrame>
        }
      />
      <Route element={<StoreLayout />}>
        <Route index element={<HomePage />} />
        <Route path="products/:productId" element={<ProductPage />} />
        <Route
          path="wishlist"
          element={
            <RequireCustomer>
              <WishlistPage />
            </RequireCustomer>
          }
        />
        <Route
          path="cart"
          element={
            <RequireCustomer>
              <CartPage />
            </RequireCustomer>
          }
        />
        <Route
          path="checkout"
          element={
            <RequireCustomer>
              <CheckoutPage />
            </RequireCustomer>
          }
        />
        <Route
          path="orders"
          element={
            <RequireCustomer>
              <OrdersPage />
            </RequireCustomer>
          }
        />
        <Route
          path="orders/:orderId"
          element={
            <RequireCustomer>
              <OrderDetailPage />
            </RequireCustomer>
          }
        />
        <Route path="account" element={<AccountPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
