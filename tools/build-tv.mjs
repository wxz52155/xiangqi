// 构建 Android TV 版网页包，并复制到 android/app/src/main/assets/web/。
//
//   pnpm build:tv
//
// 之后在 android/ 目录执行 ./gradlew assembleRelease 即可得到 APK（CI 里自动完成）。
import { spawnSync } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const WEB = path.join(ROOT, 'apps/web');
const OUT = path.join(WEB, 'dist-tv');
const ASSETS = path.join(ROOT, 'android/app/src/main/assets/web');

const env = {
  ...process.env,
  VITE_TARGET: 'tv',
  VITE_OUT_DIR: 'dist-tv',
  VITE_BASE_PATH: '/',
};

function run(command, args) {
  const result = spawnSync(command, args, { cwd: WEB, env, stdio: 'inherit', shell: process.platform === 'win32' });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

run('node', [path.join(ROOT, 'node_modules/typescript/bin/tsc'), '--noEmit', '-p', 'tsconfig.json']);
run('node', [path.join(ROOT, 'node_modules/vite/bin/vite.js'), 'build']);

// 电视版不需要 Service Worker / PWA 安装清单 / 静态托管重定向规则。
const SKIP = new Set(['sw.js', 'sw.js.map', 'sw.mjs', 'manifest.webmanifest', '_redirects', 'registerSW.js']);

async function copyDir(from, to) {
  await fs.mkdir(to, { recursive: true });
  for (const entry of await fs.readdir(from, { withFileTypes: true })) {
    if (SKIP.has(entry.name) || entry.name.startsWith('workbox-')) continue;
    const source = path.join(from, entry.name);
    const target = path.join(to, entry.name);
    if (entry.isDirectory()) await copyDir(source, target);
    else await fs.copyFile(source, target);
  }
}

await fs.rm(ASSETS, { recursive: true, force: true });
await copyDir(OUT, ASSETS);

let files = 0;
let bytes = 0;
async function measure(dir) {
  for (const entry of await fs.readdir(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) await measure(full);
    else {
      files += 1;
      bytes += (await fs.stat(full)).size;
    }
  }
}
await measure(ASSETS);
console.log(`build-tv: copied ${files} files (${(bytes / 1024 / 1024).toFixed(2)} MB) to ${path.relative(ROOT, ASSETS)}`);
