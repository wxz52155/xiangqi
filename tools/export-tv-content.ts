// 把训练内容（一步杀法 / 残棋定式 / 开局）导出成 Android TV 应用使用的精简 JSON。
//   pnpm export:tv-content
// 输出到 android/app/src/main/assets/content/，排序与去重沿用网页版 packages/content 的逻辑。
import fs from 'node:fs';
import path from 'node:path';
import { endgameLessons, openingLessons, tacticExercises } from '../packages/content/src/index';

const outDir = path.resolve(import.meta.dirname ?? __dirname, '../android/app/src/main/assets/content');
fs.mkdirSync(outDir, { recursive: true });

function source(item: { source: { name: string; author: string; license: string; url?: string } }) {
  return { name: item.source.name, author: item.source.author, license: item.source.license };
}

const tactics = tacticExercises.map((item) => ({
  id: item.id,
  title: item.title,
  subtitle: item.subtitle,
  level: item.level,
  tags: item.tags,
  source: source(item),
  fen: item.fen,
  solutions: item.solutions,
  hint: item.hint,
  explanation: item.explanation,
}));

function lesson(item: (typeof endgameLessons)[number]) {
  return {
    id: item.id,
    title: item.title,
    subtitle: item.subtitle,
    level: item.level,
    tags: item.tags,
    source: source(item),
    fen: item.fen,
    userColor: item.userColor,
    objective: item.objective,
    intro: item.intro,
    keyPoints: item.keyPoints,
    lines: item.lines.map((line) => ({ id: line.id, label: line.label, moves: line.moves })),
    completionText: item.completionText,
  };
}

const files: Record<string, unknown[]> = {
  'tactics.json': tactics,
  'endgames.json': endgameLessons.map(lesson),
  'openings.json': openingLessons.map(lesson),
};
for (const [name, data] of Object.entries(files)) {
  fs.writeFileSync(path.join(outDir, name), JSON.stringify(data));
  console.log(`export-tv-content: ${name} ${data.length} items`);
}
