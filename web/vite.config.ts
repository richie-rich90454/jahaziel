///
/// Copyright 2026 The Jahaziel Authors
/// SPDX-License-Identifier: Apache-2.0
///

import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";
import solid from "vite-plugin-solid";

export default defineConfig({
  base: "/",
  publicDir: "public",
  plugins: [solid(), tailwindcss()],
  build: {
    outDir: "dist",
    emptyOutDir: true,
    assetsDir: "assets",
    sourcemap: false,
    manifest: true,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (/node_modules[/\\](@codemirror[/\\]lang-html|@lezer[/\\]html)[/\\]/.test(id)) {
            return "editor-html";
          }
          if (/node_modules[/\\](@codemirror[/\\]lang-json|@lezer[/\\]json)[/\\]/.test(id)) {
            return "editor-json";
          }
          if (/node_modules[/\\](@codemirror[/\\]lang-python|@lezer[/\\]python)[/\\]/.test(id)) {
            return "editor-python";
          }
          if (/node_modules[/\\](@codemirror[/\\]lang-javascript|@lezer[/\\]javascript)[/\\]/.test(id)) {
            return "editor-javascript";
          }
          if (/node_modules[/\\].*(@codemirror|@lezer|codemirror)/.test(id)) return "editor";
        },
      },
    },
  },
});
