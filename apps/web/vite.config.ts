import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

const base = (process.env.VITE_BASE_PATH || '/').replace(/\/?$/, '/');
// VITE_TARGET=tv：Android TV APK 内置的网页包。电视盒子的 WebView 往往较旧，语法降级到 Chrome 80。
const isTv = process.env.VITE_TARGET === 'tv';

export default defineConfig({
  base,
  plugins: [
    react(),
    // TV 版：页面按 1280 宽排版，由 WebView 整体缩放到电视分辨率。
    isTv && {
      name: 'qilian-tv-viewport',
      transformIndexHtml(html: string) {
        return html.replace(
          /<meta\s+name="viewport"[\s\S]*?\/>/,
          '<meta name="viewport" content="width=1280, user-scalable=no" />',
        );
      },
    },
    VitePWA({
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      registerType: 'prompt',
      injectRegister: null,
      manifest: {
        name: '棋练 · 中国象棋',
        short_name: '棋练',
        description: '和在线 AI 人机对弈，也能双人同屏；练习杀法、残局定式与开局。',
        lang: 'zh-CN',
        start_url: base,
        scope: base,
        display: 'standalone',
        orientation: 'any',
        background_color: '#f4ecdd',
        theme_color: '#10211d',
        categories: ['games', 'education'],
        icons: [
          { src: `${base}icons/icon-192.png`, sizes: '192x192', type: 'image/png' },
          { src: `${base}icons/icon-512.png`, sizes: '512x512', type: 'image/png' },
          { src: `${base}icons/icon-512.png`, sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      injectManifest: {
        globPatterns: ['**/*.{js,css,html,svg,png,ico,wasm,webmanifest,woff2,txt,md}'],
        maximumFileSizeToCacheInBytes: 3 * 1024 * 1024,
      },
      devOptions: {
        enabled: process.env.VITE_PWA_DEV === 'true',
        type: 'module',
      },
    }),
  ],
  server: {
    host: '127.0.0.1',
    port: 5173,
  },
  preview: {
    host: '127.0.0.1',
    port: 4173,
  },
  build: {
    outDir: process.env.VITE_OUT_DIR || 'dist',
    target: isTv ? ['chrome80'] : 'es2022',
    cssTarget: isTv ? ['chrome80'] : undefined,
    chunkSizeWarningLimit: 800,
  },
});
