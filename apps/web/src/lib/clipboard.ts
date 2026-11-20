import { IS_TV } from '../tv/env';
import { showQr } from '../tv/overlays';

function fallbackCopy(text: string): boolean {
  const input = document.createElement('textarea');
  input.value = text;
  input.setAttribute('readonly', '');
  input.style.position = 'fixed';
  input.style.opacity = '0';
  document.body.appendChild(input);
  input.select();
  const copied = document.execCommand('copy');
  input.remove();
  return copied;
}

export async function copyText(text: string, label = '棋盘 FEN'): Promise<boolean> {
  if (IS_TV) {
    // 电视上没有顺手的粘贴场景：展示二维码，手机扫码即可拿到同一份内容。
    showQr({
      title: `分享${label}`,
      text,
      description: `用手机扫码获取${label}，发给朋友或在手机版棋练里导入。`,
    });
    try {
      await navigator.clipboard.writeText(text);
    } catch {
      // 电视剪贴板不可用时忽略，二维码已经展示。
    }
    return true;
  }
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return fallbackCopy(text);
  }
}
