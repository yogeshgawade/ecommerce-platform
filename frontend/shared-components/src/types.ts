export type Role = "CUSTOMER" | "ADMIN";

export interface User {
  id: string;
  email: string;
  role: Role;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface Product {
  id: string;
  name: string;
  description?: string | null;
  category: string;
  brand: string;
  price: number;
  attributes?: Record<string, string> | null;
  createdAt?: string;
  updatedAt?: string;
  rating?: number | null;
  reviewCount?: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CartItem {
  product_id: string;
  name: string;
  price: number;
  quantity: number;
}

export interface Cart {
  user_id: string;
  items: CartItem[];
}

export interface WishlistItem {
  product_id: string;
  added_at: string;
}

export interface OrderItem {
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Order {
  orderId: string;
  userId: string;
  status: string;
  totalAmount: number;
  currency: string;
  items: OrderItem[];
  createdAt: string;
  updatedAt: string;
}

export interface Payment {
  orderId: string;
  status: string;
  amount: number;
  currency: string;
  clientSecret?: string | null;
  failureReason?: string | null;
  createdAt: string;
}

export interface Review {
  review_id: string;
  product_id: string;
  rating: number;
  title: string;
  body: string;
  verified_purchase: boolean;
  created_at: string;
  updated_at: string;
}

export interface ReviewPage extends Page<Review> {
  average_rating: number | null;
  review_count: number;
}

export interface InventoryItem {
  productId: string;
  quantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  lastUpdatedAt: string;
}

export interface SearchResult extends Page<Product> {
  facets?: {
    categories?: { value: string; count: number }[];
    brands?: { value: string; count: number }[];
  };
}
