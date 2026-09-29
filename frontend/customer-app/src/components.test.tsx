import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { ProductCard, StatusPill } from "@ecommerce/shared";
const product = {
  id: "lamp-1",
  name: "Arc table lamp",
  brand: "Forma",
  category: "Lighting",
  price: 129.5,
};
describe("ProductCard", () => {
  it("shows product details and calls add when the add control is activated", () => {
    const onAdd = vi.fn();
    render(
      <MemoryRouter>
        <ProductCard product={product} onAdd={onAdd} />
      </MemoryRouter>,
    );
    expect(screen.getByText("Arc table lamp")).toBeInTheDocument();
    expect(screen.getByText("$129.50")).toBeInTheDocument();
    fireEvent.click(
      screen.getByRole("button", { name: "Add Arc table lamp to cart" }),
    );
    expect(onAdd).toHaveBeenCalledOnce();
  });
  it("exposes a wishlist action with its current state", () => {
    const onFavorite = vi.fn();
    render(
      <MemoryRouter>
        <ProductCard product={product} onFavorite={onFavorite} favorite />
      </MemoryRouter>,
    );
    const button = screen.getByRole("button", { name: "Remove from wishlist" });
    fireEvent.click(button);
    expect(onFavorite).toHaveBeenCalledOnce();
  });
});
describe("StatusPill", () => {
  it("formats saga states and marks successful orders accessibly", () => {
    render(<StatusPill status="CONFIRMED" />);
    expect(screen.getByText("confirmed")).toBeInTheDocument();
    expect(screen.getByText("confirmed").closest("span")).toHaveClass(
      "status-success",
    );
  });
});
