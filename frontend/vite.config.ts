import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src"),
    },
  },
  server: {
    host: true,
    port: 5173,
    proxy: {
      "/ws": {
        target: "http://127.0.0.1:8080",
        ws: true,
        changeOrigin: true,
      },
      "/health": { target: "http://127.0.0.1:8080", changeOrigin: true },
      "/api": { target: "http://127.0.0.1:8080", changeOrigin: true },
    },
  },
});
