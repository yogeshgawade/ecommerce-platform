import { afterEach, describe, expect, it, vi } from "vitest";
import { api, clearSession, saveSession } from "@ecommerce/shared";
afterEach(() => {
  clearSession();
  vi.restoreAllMocks();
});
describe("shared API client", () => {
  it("returns decoded successful responses and sends JSON content type", async () => {
    const fetchMock = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(
        new Response(JSON.stringify({ ok: true }), { status: 200 }),
      );
    await expect(
      api<{
        ok: boolean;
      }>("/health", { authenticated: false }),
    ).resolves.toEqual({ ok: true });
    expect(fetchMock.mock.calls[0][1]?.headers).toBeInstanceOf(Headers);
  });
  it("refreshes an expired access token once and retries the original request", async () => {
    saveSession({
      accessToken: "expired",
      refreshToken: "refresh-1",
      tokenType: "Bearer",
      expiresIn: 900,
    });
    const fetchMock = vi.spyOn(globalThis, "fetch");
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ message: "Expired" }), { status: 401 }),
    );
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          accessToken: "fresh",
          refreshToken: "refresh-2",
          tokenType: "Bearer",
          expiresIn: 900,
        }),
        { status: 200 },
      ),
    );
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ id: "user-1" }), { status: 200 }),
    );
    await expect(
      api<{
        id: string;
      }>("/private"),
    ).resolves.toEqual({
      id: "user-1",
    });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(
      new Headers(fetchMock.mock.calls[2][1]?.headers).get("Authorization"),
    ).toBe("Bearer fresh");
  });
  it("preserves backend validation details on failed requests", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ detail: "Quantity must be positive" }), {
        status: 422,
      }),
    );
    await expect(
      api("/api/cart", { authenticated: false }),
    ).rejects.toMatchObject({
      status: 422,
      message: "Quantity must be positive",
    });
  });
});
