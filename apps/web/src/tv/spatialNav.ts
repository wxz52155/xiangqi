/**
 * 遥控器方向键导航（Spatial Navigation）。
 *
 * - 方向键在可聚焦元素之间按几何位置移动焦点；组件自己处理了方向键（例如象棋棋盘内的
 *   roving focus）时会调用 preventDefault，这里就不再接管。
 * - 焦点越过内容时自动滚动；方向上没有可聚焦元素时直接滚动页面，长文本也能读完。
 * - 弹窗打开时只在弹窗里移动；被禁用的元素（AI 思考时的棋盘）重新可用后自动找回焦点。
 * - 文本框：OK 键唤起软键盘；左右键在光标到达两端时才离开输入框。
 * - 返回键：关弹窗 → 退出输入框 → 后退一页 → 回首页 → 交给原生层（再按一次退出）。
 */
import { nativeBridge } from './env';

type Direction = 'up' | 'down' | 'left' | 'right';

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button',
  'input:not([type="hidden"])',
  'select',
  'textarea',
  'summary',
  '[tabindex]',
  '[contenteditable="true"]',
].join(',');

const TEXT_INPUT_TYPES = new Set(['text', 'search', 'email', 'url', 'tel', 'password', 'number', '']);

let lastFocused: HTMLElement | null = null;
let lastRect: DOMRect | null = null;
let keyboardOpenedFor: HTMLElement | null = null;
let lostFocusSince = 0;
let installed = false;

export function isTextField(element: Element | null): element is HTMLInputElement | HTMLTextAreaElement {
  if (!element) return false;
  if (element instanceof HTMLTextAreaElement) return !element.readOnly && !element.disabled;
  if (element instanceof HTMLInputElement) {
    return TEXT_INPUT_TYPES.has((element.getAttribute('type') || '').toLowerCase()) && !element.readOnly && !element.disabled;
  }
  return element instanceof HTMLElement && element.isContentEditable;
}

function isVisible(element: HTMLElement): boolean {
  if (!element.isConnected) return false;
  const rects = element.getClientRects();
  if (rects.length === 0) return false;
  const rect = element.getBoundingClientRect();
  if (rect.width < 2 || rect.height < 2) return false;
  const style = getComputedStyle(element);
  if (style.visibility === 'hidden' || style.display === 'none' || Number(style.opacity) === 0) return false;
  return true;
}

export function isFocusable(element: Element | null): element is HTMLElement {
  if (!(element instanceof HTMLElement)) return false;
  if (!element.matches(FOCUSABLE_SELECTOR)) return false;
  if (element.matches(':disabled')) return false;
  const tabindex = element.getAttribute('tabindex');
  if (tabindex !== null && Number(tabindex) < 0) return false;
  if (element.closest('[inert], .skip-link, [data-tv-skip]')) return false;
  if (element instanceof HTMLAnchorElement && !element.getAttribute('href')) return false;
  return isVisible(element);
}

function topDialog(): HTMLDialogElement | null {
  const dialogs = Array.from(document.querySelectorAll<HTMLDialogElement>('dialog[open]'));
  return dialogs.length ? dialogs[dialogs.length - 1] ?? null : null;
}

function scopeRoot(): ParentNode {
  return topDialog() ?? document;
}

export function focusableElements(root: ParentNode = scopeRoot()): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(isFocusable);
}

function center(rect: DOMRect): { x: number; y: number } {
  return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
}

/** 左右移动时，候选元素与当前元素在竖直方向上最多错开这么远（CSS px），避免一下跳到页头导航。 */
const SIDEWAYS_GAP_LIMIT = 180;

/** 计算候选元素在指定方向上的得分，越小越优先；不在该方向返回 null。 */
function score(from: DOMRect, to: DOMRect, direction: Direction): number | null {
  const a = center(from);
  const b = center(to);
  let primary: number;
  let gap: number;
  let offset: number;
  const eps = 1;
  switch (direction) {
    case 'right':
      if (b.x <= a.x + eps || to.right <= from.right - eps) return null;
      primary = Math.max(0, to.left - from.right);
      gap = Math.max(0, to.top - from.bottom, from.top - to.bottom);
      if (gap > SIDEWAYS_GAP_LIMIT) return null;
      offset = Math.abs(b.y - a.y);
      break;
    case 'left':
      if (b.x >= a.x - eps || to.left >= from.left + eps) return null;
      primary = Math.max(0, from.left - to.right);
      gap = Math.max(0, to.top - from.bottom, from.top - to.bottom);
      if (gap > SIDEWAYS_GAP_LIMIT) return null;
      offset = Math.abs(b.y - a.y);
      break;
    case 'down':
      if (b.y <= a.y + eps || to.bottom <= from.bottom - eps) return null;
      primary = Math.max(0, to.top - from.bottom);
      gap = Math.max(0, to.left - from.right, from.left - to.right);
      offset = Math.abs(b.x - a.x);
      break;
    case 'up':
    default:
      if (b.y >= a.y - eps || to.top >= from.top + eps) return null;
      primary = Math.max(0, from.top - to.bottom);
      gap = Math.max(0, to.left - from.right, from.left - to.right);
      offset = Math.abs(b.x - a.x);
      break;
  }
  return primary + gap * 3 + offset * 0.35;
}

export function findNext(fromRect: DOMRect, direction: Direction, exclude?: HTMLElement | null): HTMLElement | null {
  let best: HTMLElement | null = null;
  let bestScore = Infinity;
  for (const candidate of focusableElements()) {
    if (candidate === exclude) continue;
    if (exclude && candidate.contains(exclude)) continue;
    const value = score(fromRect, candidate.getBoundingClientRect(), direction);
    if (value !== null && value < bestScore) {
      bestScore = value;
      best = candidate;
    }
  }
  return best;
}

function scrollContainerOf(element: HTMLElement): HTMLElement | null {
  let node = element.parentElement;
  while (node && node !== document.body && node !== document.documentElement) {
    const style = getComputedStyle(node);
    if (/(auto|scroll)/.test(style.overflowY) && node.scrollHeight > node.clientHeight + 4) return node;
    node = node.parentElement;
  }
  return null;
}

function viewportFor(element: HTMLElement | null): { top: number; bottom: number; height: number } {
  const container = element ? scrollContainerOf(element) : null;
  if (container) {
    const rect = container.getBoundingClientRect();
    return { top: rect.top, bottom: rect.bottom, height: rect.height };
  }
  const header = document.querySelector<HTMLElement>('.site-header');
  const headerBottom = header && !topDialog() ? Math.max(0, header.getBoundingClientRect().bottom) : 0;
  return { top: headerBottom, bottom: window.innerHeight, height: window.innerHeight - headerBottom };
}

/** 页面（或弹窗内滚动区）按方向滚一段；返回是否真的滚动了。 */
function scrollBy(direction: Direction, reference: HTMLElement | null): boolean {
  if (direction === 'left' || direction === 'right') return false;
  const dialog = topDialog();
  const container =
    (reference && scrollContainerOf(reference)) ||
    (dialog ? dialog.querySelector<HTMLElement>('.dialog-body') : null);
  const amount = Math.round((container ? container.clientHeight : window.innerHeight) * 0.55) * (direction === 'down' ? 1 : -1);
  if (container && container.scrollHeight > container.clientHeight + 4) {
    const before = container.scrollTop;
    container.scrollBy({ top: amount, behavior: 'smooth' });
    return Math.abs(container.scrollTop - before) > 0 || (direction === 'down'
      ? before + container.clientHeight < container.scrollHeight - 2
      : before > 0);
  }
  const root = document.scrollingElement ?? document.documentElement;
  const before = root.scrollTop;
  const canScroll = direction === 'down'
    ? before + window.innerHeight < root.scrollHeight - 2
    : before > 0;
  if (!canScroll) return false;
  window.scrollBy({ top: amount, behavior: 'smooth' });
  return true;
}

function ensureVisible(element: HTMLElement): void {
  const rect = element.getBoundingClientRect();
  const viewport = viewportFor(element);
  const margin = 24;
  const container = scrollContainerOf(element);
  if (rect.top < viewport.top + margin || rect.bottom > viewport.bottom - margin) {
    const delta =
      rect.height > viewport.height - margin * 2 || rect.top < viewport.top + margin
        ? rect.top - viewport.top - margin
        : rect.bottom - viewport.bottom + margin;
    if (container) container.scrollBy({ top: delta, behavior: 'smooth' });
    else window.scrollBy({ top: delta, behavior: 'smooth' });
  }
  if (rect.left < 0 || rect.right > window.innerWidth) {
    element.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  }
}

export function focusElement(element: HTMLElement): void {
  element.focus({ preventScroll: true });
  ensureVisible(element);
  remember(element);
}

function remember(element: HTMLElement): void {
  lastFocused = element;
  lastRect = element.getBoundingClientRect();
}

function currentElement(): HTMLElement | null {
  const active = document.activeElement;
  if (active instanceof HTMLElement && active !== document.body && active.isConnected) return active;
  return null;
}

function move(direction: Direction): boolean {
  const active = currentElement();
  const dialog = topDialog();
  const insideScope = active && (!dialog || dialog.contains(active));
  const fromRect = insideScope && active ? active.getBoundingClientRect() : lastRect;

  if (!fromRect || (dialog && !insideScope)) {
    const first = initialTarget();
    if (first) {
      focusElement(first);
      return true;
    }
    return scrollBy(direction, null);
  }

  const next = findNext(fromRect, direction, insideScope ? active : null);
  if (next) {
    const nextRect = next.getBoundingClientRect();
    const viewport = viewportFor(next);
    // 下一个焦点离得很远（中间是大段文字）时，先滚动页面，让人能读到中间内容。
    const farAway =
      (direction === 'down' && nextRect.top > viewport.bottom + viewport.height * 0.15) ||
      (direction === 'up' && nextRect.bottom < viewport.top - viewport.height * 0.15);
    if (farAway && scrollBy(direction, next)) return true;
    focusElement(next);
    return true;
  }
  return scrollBy(direction, active);
}

const DIRECTION_KEYS: Record<string, Direction> = {
  ArrowUp: 'up',
  ArrowDown: 'down',
  ArrowLeft: 'left',
  ArrowRight: 'right',
  Up: 'up',
  Down: 'down',
  Left: 'left',
  Right: 'right',
};

function caretAtEdge(element: HTMLInputElement | HTMLTextAreaElement, direction: Direction): boolean {
  try {
    const start = element.selectionStart;
    const end = element.selectionEnd;
    if (start === null || end === null) return true;
    if (direction === 'left') return start === 0 && end === 0;
    if (direction === 'right') return start === element.value.length && end === element.value.length;
  } catch {
    return true;
  }
  return true;
}

function handleKeyDownCapture(event: KeyboardEvent): void {
  autoFocused = null;
  if (event.defaultPrevented || event.altKey || event.ctrlKey || event.metaKey) return;
  const active = currentElement();

  if (event.key === 'Enter' && isTextField(active) && keyboardOpenedFor !== active && !event.isComposing) {
    // 第一次按 OK：唤起软键盘，而不是触发页面里的回车提交。
    const bridge = nativeBridge();
    if (bridge?.showKeyboard) {
      event.preventDefault();
      event.stopPropagation();
      keyboardOpenedFor = active;
      bridge.showKeyboard();
    }
    return;
  }

  if (event.key === 'Enter' && active instanceof HTMLInputElement && (active.type === 'checkbox' || active.type === 'radio')) {
    event.preventDefault();
    active.click();
  }
}

function handleKeyDown(event: KeyboardEvent): void {
  if (event.altKey || event.ctrlKey || event.metaKey) return;

  if (event.key === 'GoBack' || event.key === 'BrowserBack' || (event.key === 'Escape' && !topDialog())) {
    if (handleBack()) event.preventDefault();
    return;
  }

  const direction = DIRECTION_KEYS[event.key];
  if (!direction || event.defaultPrevented) return;
  const active = currentElement();

  if (isTextField(active)) {
    const multiline = active instanceof HTMLTextAreaElement;
    if ((direction === 'left' || direction === 'right') && !caretAtEdge(active as HTMLInputElement, direction)) return;
    if (multiline && keyboardOpenedFor === active && (direction === 'up' || direction === 'down')) {
      const value = active.value;
      const position = active.selectionStart ?? 0;
      const firstLine = value.lastIndexOf('\n', position - 1) < 0;
      const lastLine = value.indexOf('\n', position) < 0;
      if ((direction === 'up' && !firstLine) || (direction === 'down' && !lastLine)) return;
    }
  }
  if (active instanceof HTMLInputElement && active.type === 'range' && (direction === 'left' || direction === 'right')) {
    return;
  }

  event.preventDefault();
  move(direction);
}

/** 首选焦点：显式标记 → 棋盘中心 → 主要内容第一个按钮 → 导航。 */
export function initialTarget(): HTMLElement | null {
  const dialog = topDialog();
  const root: ParentNode = dialog ?? document;
  const explicit = Array.from(root.querySelectorAll<HTMLElement>('[data-tv-autofocus]')).find(isFocusable);
  if (explicit) return explicit;
  if (dialog) {
    const footerButton = Array.from(dialog.querySelectorAll<HTMLElement>('.dialog-footer button')).filter(isFocusable);
    return footerButton[footerButton.length - 1] ?? focusableElements(dialog)[0] ?? null;
  }

  const frame = document.querySelector<HTMLElement>('main .board-frame');
  if (frame) {
    const cells = focusableElements(frame);
    if (cells.length) {
      const frameCenter = center(frame.getBoundingClientRect());
      let best = cells[0]!;
      let bestDistance = Infinity;
      for (const cell of cells) {
        const point = center(cell.getBoundingClientRect());
        const distance = Math.hypot(point.x - frameCenter.x, point.y - frameCenter.y);
        if (distance < bestDistance) {
          bestDistance = distance;
          best = cell;
        }
      }
      return best;
    }
  }

  const main = document.querySelector<HTMLElement>('#main-content');
  if (main) {
    const inMain = focusableElements(main);
    const viewportBottom = window.innerHeight;
    const visible = inMain.filter((element) => element.getBoundingClientRect().top < viewportBottom);
    const primary = visible.find((element) => element.matches('.btn-primary'));
    if (primary) return primary;
    const secondary = (element: HTMLElement) =>
      element.classList.contains('back-link') || Boolean(element.closest('.content-engine-status, .game-engine-status, .engine-badge'));
    const preferred = visible.find((element) => !secondary(element)) ?? visible[0] ?? inMain[0];
    if (preferred) return preferred;
  }
  return document.querySelector<HTMLElement>('.desktop-nav .nav-link.is-active') ?? focusableElements()[0] ?? null;
}

let autoFocused: HTMLElement | null = null;

/** 路由切换后，如果焦点丢了，就落到新页面的首选位置。 */
export function focusInitialIfLost(): void {
  const active = currentElement();
  if (active && isFocusable(active)) {
    // 自动落下的焦点：页面稍后才可用的主按钮（例如等 AI 就绪）出现时，改落到主按钮上。
    if (active === autoFocused) {
      const better = initialTarget();
      if (better && better !== active) {
        better.focus({ preventScroll: true });
        remember(better);
        autoFocused = better;
      }
    }
    return;
  }
  // 上一个焦点还在页面上、只是暂时被禁用（例如 AI 思考时的棋盘格），等它恢复，不要抢走焦点。
  if (lastFocused && lastFocused.isConnected && !lastFocused.matches(':focus')) {
    const scope = topDialog();
    if (!scope || scope.contains(lastFocused)) return;
  }
  const target = initialTarget();
  if (target) {
    target.focus({ preventScroll: true });
    remember(target);
    autoFocused = target;
  }
}

let navigateHome: (() => void) | null = null;

export function setNavigateHome(handler: (() => void) | null): void {
  navigateHome = handler;
}

/** 遥控器返回键。返回 true 表示页面已处理；false 交给原生层（退出提示）。 */
export function handleBack(): boolean {
  const dialog = topDialog();
  if (dialog) {
    const cancel = new Event('cancel', { cancelable: true });
    const notPrevented = dialog.dispatchEvent(cancel);
    if (notPrevented && dialog.open) dialog.close();
    return true;
  }

  const active = currentElement();
  if (isTextField(active)) {
    nativeBridge()?.hideKeyboard?.();
    keyboardOpenedFor = null;
    active.blur();
    if (lastFocused !== active) remember(active);
    // 焦点留在输入框上（未打开键盘状态），便于继续用方向键离开。
    active.focus({ preventScroll: true });
    return true;
  }

  const state = window.history.state as { idx?: number } | null;
  if (state && typeof state.idx === 'number' && state.idx > 0) {
    window.history.back();
    return true;
  }

  const path = window.location.pathname.replace(/\/+$/, '');
  const base = import.meta.env.BASE_URL.replace(/\/+$/, '');
  if (path !== base && navigateHome) {
    navigateHome();
    return true;
  }
  return false;
}

function recoverFocus(): void {
  if (!document.hasFocus()) return;
  const active = currentElement();
  if (active) {
    lostFocusSince = 0;
    return;
  }
  if (!lostFocusSince) {
    lostFocusSince = performance.now();
    return;
  }
  if (lastFocused && lastFocused.isConnected) {
    if (isFocusable(lastFocused) && (!topDialog() || topDialog()!.contains(lastFocused))) {
      lastFocused.focus({ preventScroll: true });
      lostFocusSince = 0;
    }
    // 元素还在但暂时禁用（例如 AI 思考中），等它恢复。
    return;
  }
  if (performance.now() - lostFocusSince < 350) return;
  lostFocusSince = 0;
  if (lastRect) {
    const cx = lastRect.left + lastRect.width / 2;
    const cy = lastRect.top + lastRect.height / 2;
    let best: HTMLElement | null = null;
    let bestDistance = Infinity;
    for (const element of focusableElements()) {
      const point = center(element.getBoundingClientRect());
      const distance = Math.hypot(point.x - cx, point.y - cy);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = element;
      }
    }
    if (best && bestDistance < 160) {
      best.focus({ preventScroll: true });
      remember(best);
      return;
    }
  }
  focusInitialIfLost();
}

export function installSpatialNavigation(): void {
  if (installed || typeof window === 'undefined') return;
  installed = true;

  document.addEventListener('keydown', handleKeyDownCapture, true);
  document.addEventListener('keydown', handleKeyDown);
  document.addEventListener('focusin', (event) => {
    const target = event.target;
    if (target instanceof HTMLElement && target !== document.body) {
      remember(target);
      if (keyboardOpenedFor && keyboardOpenedFor !== target) keyboardOpenedFor = null;
    }
  });
  document.addEventListener('focusout', (event) => {
    if (event.target === keyboardOpenedFor) keyboardOpenedFor = null;
  });
  // 鼠标/触摸（部分电视盒子接了空鼠）点击后同步记录焦点位置。
  document.addEventListener('pointerdown', (event) => {
    const target = (event.target as Element | null)?.closest?.<HTMLElement>(FOCUSABLE_SELECTOR);
    if (target) remember(target);
  });
  window.setInterval(recoverFocus, 200);

  (window as unknown as { __qilianTvBack?: () => boolean }).__qilianTvBack = handleBack;
}
