import { useEffect, useMemo } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import qrcode from 'qrcode-generator';
import { ExternalLink } from 'lucide-react';
import { Dialog } from '../components/Dialog';
import { nativeBridge } from './env';
import { useTvOverlayStore } from './overlays';
import { focusInitialIfLost, setNavigateHome } from './spatialNav';

function QrCode({ text }: { text: string }) {
  const svg = useMemo(() => {
    const qr = qrcode(0, 'M');
    qr.addData(unescape(encodeURIComponent(text)), 'Byte');
    qr.make();
    return qr.createSvgTag({ cellSize: 6, margin: 2, scalable: true });
  }, [text]);
  return <div className="tv-qr-code" aria-hidden="true" dangerouslySetInnerHTML={{ __html: svg }} />;
}

/** 路由变化后的焦点管理 + 返回首页。 */
function TvRouteFocus() {
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    setNavigateHome(() => navigate('/', { replace: true }));
    // 遥控器菜单键：打开设置
    const openSettings = () => navigate('/settings');
    window.addEventListener('qilian:tv-menu', openSettings);
    return () => {
      setNavigateHome(null);
      window.removeEventListener('qilian:tv-menu', openSettings);
    };
  }, [navigate]);

  useEffect(() => {
    const timers = [60, 260, 700, 1500, 3000].map((delay) => window.setTimeout(focusInitialIfLost, delay));
    return () => timers.forEach((timer) => window.clearTimeout(timer));
  }, [location.pathname, location.search]);

  return null;
}

/** 外部链接：电视上没法顺手开浏览器，改成扫码在手机上看。 */
function useExternalLinkInterceptor() {
  const showQr = useTvOverlayStore((state) => state.showQr);
  useEffect(() => {
    function onClick(event: MouseEvent) {
      const anchor = (event.target as Element | null)?.closest?.('a[href]');
      if (!(anchor instanceof HTMLAnchorElement)) return;
      let url: URL;
      try {
        url = new URL(anchor.href, window.location.href);
      } catch {
        return;
      }
      if (url.origin === window.location.origin) {
        // 同源的静态文件（许可证文本等）在当前页面打开即可。
        return;
      }
      event.preventDefault();
      showQr({
        title: '用手机扫码打开',
        text: url.href,
        url: url.href,
        description: anchor.textContent?.trim() || url.hostname,
      });
    }
    document.addEventListener('click', onClick, true);
    return () => document.removeEventListener('click', onClick, true);
  }, [showQr]);
}

export function TvShell() {
  const qr = useTvOverlayStore((state) => state.qr);
  const closeQr = useTvOverlayStore((state) => state.closeQr);
  const confirm = useTvOverlayStore((state) => state.confirm);
  const settleConfirm = useTvOverlayStore((state) => state.settleConfirm);
  const bridge = nativeBridge();
  useExternalLinkInterceptor();

  return (
    <>
      <TvRouteFocus />

      <Dialog
        open={Boolean(qr)}
        title={qr?.title ?? ''}
        onClose={closeQr}
        className="tv-qr-dialog"
        footer={
          <>
            {qr?.url && bridge?.openExternal && (
              <button
                type="button"
                className="btn btn-ghost"
                onClick={() => {
                  if (qr.url && bridge.openExternal?.(qr.url)) closeQr();
                }}
              >
                <ExternalLink size={16} aria-hidden="true" />
                在电视浏览器中打开
              </button>
            )}
            <button type="button" className="btn btn-dark" data-tv-autofocus onClick={closeQr}>
              知道了
            </button>
          </>
        }
      >
        {qr && (
          <div className="tv-qr-body">
            <QrCode text={qr.text} />
            <div className="tv-qr-text">
              {qr.description && <p className="tv-qr-description">{qr.description}</p>}
              <p className="tv-qr-hint">用手机相机或微信扫一扫即可查看。</p>
              <code className="tv-qr-raw">{qr.text}</code>
            </div>
          </div>
        )}
      </Dialog>

      <Dialog
        open={Boolean(confirm)}
        title="请确认"
        onClose={() => settleConfirm(false)}
        className="tv-confirm-dialog"
        footer={
          <>
            <button type="button" className="btn btn-ghost" data-tv-autofocus onClick={() => settleConfirm(false)}>
              取消
            </button>
            <button
              type="button"
              className={`btn ${confirm?.danger ? 'btn-danger' : 'btn-primary'}`}
              onClick={() => settleConfirm(true)}
            >
              {confirm?.confirmLabel ?? '确定'}
            </button>
          </>
        }
      >
        <p className="tv-confirm-message">{confirm?.message}</p>
      </Dialog>
    </>
  );
}

/** 头部右侧的遥控器按键提示。 */
export function TvKeyLegend() {
  return (
    <div className="tv-key-legend" aria-hidden="true">
      <span><kbd>◀ ▲ ▼ ▶</kbd>移动</span>
      <span><kbd>OK</kbd>确认</span>
      <span><kbd>↩</kbd>返回</span>
    </div>
  );
}
