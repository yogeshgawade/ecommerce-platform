import { defineConfig } from "vitest/config";

export default defineConfig({
  server: { port: 3001 },
  test: { environment: "jsdom", setupFiles: "./src/test-setup.ts", css: true },
});
