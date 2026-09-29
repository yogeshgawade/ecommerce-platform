import { defineConfig } from "vitest/config";

export default defineConfig({
  server: { port: 3000 },
  test: { environment: "jsdom", setupFiles: "./src/test-setup.ts", css: true },
});
