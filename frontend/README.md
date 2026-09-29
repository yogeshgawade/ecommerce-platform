# Frontend

Maison Market has two Vite + React + TypeScript applications backed by the API gateway:

- `customer-app` on port `3000`: authentication, catalog/search, product reviews, cart, wishlist, Stripe card checkout, and order tracking.
- `admin-dashboard` on port `3001`: catalog management, stock updates, order lookup, and operational overview.
- `shared-components`: API client, token refresh/session handling, shared UI, types, notifications, and styles.

## Local setup

Install Node.js 18 or newer and npm. From this directory:

```bash
npm install
cp customer-app/.env.example customer-app/.env.local
cp admin-dashboard/.env.example admin-dashboard/.env.local
```

Set `VITE_STRIPE_PUBLISHABLE_KEY` in `customer-app/.env.local` to a Stripe test-mode publishable key. Never put a Stripe secret key in a browser environment variable. `VITE_API_BASE_URL` defaults to `http://localhost:8080`, the local API gateway.

Start the applications in separate terminals:

```bash
npm run dev:customer
npm run dev:admin
```

The admin app requires an account with the `ADMIN` role. The customer registration endpoint only creates `CUSTOMER` accounts.

## Checks

```bash
npm test
npm run typecheck
npm run build
```

The customer checkout uses Stripe Elements to create a payment method, submits an order with the backend's `Idempotency-Key` contract, then watches the asynchronous order/payment saga. It handles a short-lived payment-status 404 while the Kafka consumer creates the payment record, and can complete a Stripe customer-action step with the returned PaymentIntent client secret.
