import type { Order } from "@ecommerce/shared";
export function calculateRevenue(orders: Order[]) {
  return orders
    .filter((order) => order.status === "CONFIRMED")
    .reduce((total, order) => total + Number(order.totalAmount), 0);
}
