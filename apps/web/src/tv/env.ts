/**
 * Android TV 运行环境检测。
 *
 * - APK 内的 WebView 会注入 `window.QilianNative`，UA 里也带有 `QilianTV`。
 * - 用 `VITE_TARGET=tv` 构建的包始终视为 TV 版。
 * - 浏览器调试时可在地址后加 `?tv=1` 强制进入 TV 模式（会记在 sessionStorage 里）。
 */

export interface QilianNativeBridge {
  /** 原生发起 HTTP 请求（绕开 WebView 跨域限制），结果通过 window.__qilianNet 回调。 */
  httpRequest?: (id: string, method: string, url: string, headersJson: string, body: string) => void;
  httpAbort?: (id: string) => void;
  /** 弹出系统软键盘（遥控器选中输入框时）。 */
  showKeyboard?: () => void;
  hideKeyboard?: () => void;
  /** 在系统浏览器里打开外部链接；返回 false 表示设备上没有浏览器。 */
  openExternal?: (url: string) => boolean;
  /** 退出应用。 */
  exitApp?: () => void;
  /** 原生壳版本号。 */
  appVersion?: () => string;
  /** WebView 内核大版本号。 */
  webViewMajor?: () => number;
}

declare global {
  interface Window {
    QilianNative?: QilianNativeBridge;
  }
}

function detectTv(): boolean {
  if (typeof window === 'undefined') return false;
  if (import.meta.env.VITE_TARGET === 'tv') return true;
  if (window.QilianNative) return true;
  if (/QilianTV/i.test(navigator.userAgent)) return true;
  try {
    const params = new URLSearchParams(window.location.search);
    if (params.get('tv') === '1') {
      sessionStorage.setItem('qilian:tv', '1');
      return true;
    }
    if (params.get('tv') === '0') {
      sessionStorage.removeItem('qilian:tv');
      return false;
    }
    return sessionStorage.getItem('qilian:tv') === '1';
  } catch {
    return false;
  }
}

export const IS_TV = detectTv();

export function nativeBridge(): QilianNativeBridge | null {
  return typeof window !== 'undefined' && window.QilianNative ? window.QilianNative : null;
}
