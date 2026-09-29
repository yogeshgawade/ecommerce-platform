import { Navigate, Route, Routes } from "react-router-dom";
import { AdminFrame, AdminLogin } from "./AdminFrame";
import { OverviewPage } from "./OverviewPage";
import { ProductsPage } from "./ProductsPage";
import { InventoryPage } from "./InventoryPage";
import { OrdersPage } from "./OrdersPage";
export function AdminApp() {
  return (
    <Routes>
      <Route path="/login" element={<AdminLogin />} />
      <Route element={<AdminFrame />}>
        <Route index element={<OverviewPage />} />
        <Route path="products" element={<ProductsPage />} />
        <Route path="inventory" element={<InventoryPage />} />
        <Route path="orders" element={<OrdersPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
