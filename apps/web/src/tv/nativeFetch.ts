/**
 * 通过 Android 原生层发 HTTP 请求的 fetch 替代品。
 *
 * TV 版页面运行在 https://appassets.androidplatform.net（本地资源域名），
 * 直接 fetch 在线 AI 服务会受到跨域策略限制；这里把请求交给原生层，
 * 再把响应体（含 SSE 流）按块回传给页面，接口形态与 fetch 保持一致。
 */
import { nativeBridge } from './env';

interface PendingRequest {
  resolve: (response: Response) => void;
  reject: (error: unknown) => void;
  controller: ReadableStreamDefaultController<Uint8Array> | null;
  headReceived: boolean;
  queue: Uint8Array[];
  ended: boolean;
  failed: string | null;
}

const pending = new Map<string, PendingRequest>();
let counter = 0;

function decodeBase64(data: string): Uint8Array {
  const binary = atob(data);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

function installCallbacks(): void {
  const target = window as unknown as { __qilianNet?: unknown };
  if (target.__qilianNet) return;
  target.__qilianNet = {
    head(id: string, status: number, headersJson: string) {
      const request = pending.get(id);
      if (!request || request.headReceived) return;
      request.headReceived = true;
      let headers: Record<string, string> = {};
      try {
        headers = JSON.parse(headersJson) as Record<string, string>;
      } catch {
        headers = {};
      }
      const stream = new ReadableStream<Uint8Array>({
        start(controller) {
          request.controller = controller;
          for (const chunk of request.queue) controller.enqueue(chunk);
          request.queue = [];
          if (request.failed) controller.error(new TypeError(request.failed));
          else if (request.ended) controller.close();
        },
        cancel() {
          nativeBridge()?.httpAbort?.(id);
          pending.delete(id);
        },
      });
      const safeStatus = status >= 200 && status <= 599 ? status : 502;
      request.resolve(new Response(stream, { status: safeStatus, headers }));
    },
    chunk(id: string, base64: string) {
      const request = pending.get(id);
      if (!request) return;
      const bytes = decodeBase64(base64);
      if (request.controller) request.controller.enqueue(bytes);
      else request.queue.push(bytes);
    },
    end(id: string) {
      const request = pending.get(id);
      if (!request) return;
      request.ended = true;
      if (request.controller) {
        try {
          request.controller.close();
        } catch {
          // 已关闭
        }
      }
      pending.delete(id);
    },
    fail(id: string, message: string) {
      const request = pending.get(id);
      if (!request) return;
      request.failed = message || 'network error';
      if (!request.headReceived) {
        request.reject(new TypeError(request.failed));
      } else if (request.controller) {
        try {
          request.controller.error(new TypeError(request.failed));
        } catch {
          // 已关闭
        }
      }
      pending.delete(id);
    },
  };
}

function headersToObject(headers: HeadersInit | undefined): Record<string, string> {
  const result: Record<string, string> = {};
  if (!headers) return result;
  new Headers(headers).forEach((value, key) => {
    result[key] = value;
  });
  return result;
}

export function canUseNativeFetch(): boolean {
  const bridge = nativeBridge();
  return Boolean(bridge?.httpRequest);
}

export function nativeFetch(url: string, init: RequestInit = {}): Promise<Response> {
  const bridge = nativeBridge();
  if (!bridge?.httpRequest) return fetch(url, init);
  installCallbacks();
  counter += 1;
  const id = `r${Date.now().toString(36)}${counter}`;
  const signal = init.signal;
  return new Promise<Response>((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Aborted', 'AbortError'));
      return;
    }
    const request: PendingRequest = {
      resolve,
      reject,
      controller: null,
      headReceived: false,
      queue: [],
      ended: false,
      failed: null,
    };
    pending.set(id, request);
    signal?.addEventListener(
      'abort',
      () => {
        if (!pending.has(id)) return;
        bridge.httpAbort?.(id);
        const error = new DOMException('Aborted', 'AbortError');
        if (!request.headReceived) reject(error);
        else {
          try {
            request.controller?.error(error);
          } catch {
            // 已关闭
          }
        }
        pending.delete(id);
      },
      { once: true },
    );
    const body = typeof init.body === 'string' ? init.body : '';
    bridge.httpRequest!(id, (init.method || 'GET').toUpperCase(), url, JSON.stringify(headersToObject(init.headers)), body);
  });
}

/** TV 原生壳里走原生请求，其它环境就是普通 fetch。 */
export function appFetch(url: string, init?: RequestInit): Promise<Response> {
  return canUseNativeFetch() ? nativeFetch(url, init) : fetch(url, init);
}
