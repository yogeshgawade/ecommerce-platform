import { loadStripe } from "@ecommerce/shared";
const stripeKey = import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY as
  string | undefined;
export const stripePromise = stripeKey ? loadStripe(stripeKey) : null;
