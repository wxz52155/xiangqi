/**
 * TV 专用浮层：二维码（代替复制/分享/外链）与确认框（代替 window.confirm）。
 */
import { create } from 'zustand';
import { IS_TV } from './env';

export interface QrRequest {
  title: string;
  /** 二维码内容 */
  text: string;
  /** 弹窗里的说明文字 */
  description?: string;
  /** 外部链接：显示“在浏览器中打开”按钮 */
  url?: string;
}

interface ConfirmRequest {
  message: string;
  confirmLabel: string;
  danger: boolean;
  resolve: (ok: boolean) => void;
}

interface TvOverlayState {
  qr: QrRequest | null;
  confirm: ConfirmRequest | null;
  showQr: (request: QrRequest) => void;
  closeQr: () => void;
  askConfirm: (request: Omit<ConfirmRequest, 'resolve'>) => Promise<boolean>;
  settleConfirm: (ok: boolean) => void;
}

export const useTvOverlayStore = create<TvOverlayState>((set, get) => ({
  qr: null,
  confirm: null,
  showQr: (request) => set({ qr: request }),
  closeQr: () => set({ qr: null }),
  askConfirm: (request) =>
    new Promise<boolean>((resolve) => {
      get().confirm?.resolve(false);
      set({ confirm: { ...request, resolve } });
    }),
  settleConfirm: (ok) => {
    const current = get().confirm;
    set({ confirm: null });
    current?.resolve(ok);
  },
}));

/** 确认操作：TV 上用遥控器友好的弹窗，其它环境保持原来的 window.confirm。 */
export async function confirmAction(
  message: string,
  options: { confirmLabel?: string; danger?: boolean } = {},
): Promise<boolean> {
  if (!IS_TV) return window.confirm(message);
  return useTvOverlayStore.getState().askConfirm({
    message,
    confirmLabel: options.confirmLabel ?? '确定',
    danger: options.danger ?? true,
  });
}

export function showQr(request: QrRequest): void {
  useTvOverlayStore.getState().showQr(request);
}
