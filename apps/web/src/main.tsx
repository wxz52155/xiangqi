import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { registerSW } from 'virtual:pwa-register';
import App from './App';
import { initializeBaiduAnalytics } from './lib/analytics';
import { IS_TV } from './tv/env';
import { installSpatialNavigation } from './tv/spatialNav';
import './styles/global.css';
import './styles/tv.css';

if (IS_TV) {
  document.documentElement.classList.add('tv');
  installSpatialNavigation();
} else {
  initializeBaiduAnalytics({
    production: import.meta.env.PROD,
    hostname: window.location.hostname,
  });
}

if (import.meta.env.DEV && 'serviceWorker' in navigator) {
  void navigator.serviceWorker.getRegistrations().then((registrations) => {
    for (const registration of registrations) {
      void registration.unregister();
    }
  });
}

if (IS_TV) {
  // TV 版的页面、训练内容和本地引擎都打包在 APK 里，本来就能离线，不需要 Service Worker。
  if ('serviceWorker' in navigator) {
    void navigator.serviceWorker.getRegistrations().then((registrations) => {
      for (const registration of registrations) void registration.unregister();
    }).catch(() => {});
  }
  window.dispatchEvent(new Event('qilian:app-shell-ready'));
} else {
  registerSW({
    immediate: true,
    onOfflineReady() {
      window.dispatchEvent(new Event('qilian:app-shell-ready'));
    },
  });
}

const baseUrl = import.meta.env.BASE_URL;
const basename = baseUrl === '/' ? undefined : baseUrl.replace(/\/$/, '');

const root = document.getElementById('root');
if (!root) throw new Error('root element missing');

createRoot(root).render(
  <StrictMode>
    <BrowserRouter basename={basename}>
      <App />
    </BrowserRouter>
  </StrictMode>,
);
