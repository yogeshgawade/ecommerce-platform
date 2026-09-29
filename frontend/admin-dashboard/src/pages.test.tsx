import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ProductForm } from "./ProductForm";
import { calculateRevenue } from "./helpers";
import type { Order } from "@ecommerce/shared";
describe("ProductForm", () => {
  it("submits a backend-shaped product with parsed attributes", () => {
    const onSave = vi.fn();
    render(<ProductForm onSave={onSave} onCancel={vi.fn()} />);
    fireEvent.change(screen.getByLabelText("Product name"), {
      target: { value: "Arc lamp" },
    });
    fireEvent.change(screen.getByLabelText("Brand"), {
      target: { value: "Forma" },
    });
    fireEvent.change(screen.getByLabelText("Category"), {
      target: { value: "Lighting" },
    });
    fireEvent.change(screen.getByLabelText("Price (USD)"), {
      target: { value: "89.50" },
    });
    fireEvent.change(screen.getByLabelText(/Attributes/), {
      target: { value: "Material: Brass\nFinish: Satin" },
    });
    fireEvent.submit(
      screen.getByRole("button", { name: "Create product" }).closest("form")!,
    );
    expect(onSave).toHaveBeenCalledWith({
      name: "Arc lamp",
      description: "",
      brand: "Forma",
      category: "Lighting",
      price: 89.5,
      attributes: { Material: "Brass", Finish: "Satin" },
    });
  });
  it("does not submit a non-positive price", () => {
    const onSave = vi.fn();
    render(<ProductForm onSave={onSave} onCancel={vi.fn()} />);
    fireEvent.change(screen.getByLabelText("Product name"), {
      target: { value: "Arc lamp" },
    });
    fireEvent.change(screen.getByLabelText("Brand"), {
      target: { value: "Forma" },
    });
    fireEvent.change(screen.getByLabelText("Category"), {
      target: { value: "Lighting" },
    });
    fireEvent.change(screen.getByLabelText("Price (USD)"), {
      target: { value: "0" },
    });
    fireEvent.submit(
      screen.getByRole("button", { name: "Create product" }).closest("form")!,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Enter a price greater than zero",
    );
    expect(onSave).not.toHaveBeenCalled();
  });
});
describe("calculateRevenue", () => {
  it("counts confirmed order totals and excludes pending or cancelled orders", () => {
    const base = {
      orderId: "id",
      userId: "user",
      totalAmount: 20,
      currency: "USD",
      items: [],
      createdAt: "2026-01-01",
      updatedAt: "2026-01-01",
    };
    const orders = [
      { ...base, status: "CONFIRMED", totalAmount: 25 },
      { ...base, status: "PENDING_PAYMENT", totalAmount: 10 },
      { ...base, status: "CANCELLED", totalAmount: 50 },
    ] as Order[];
    expect(calculateRevenue(orders)).toBe(25);
  });
});
