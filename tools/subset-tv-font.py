#!/usr/bin/env python3
"""把 Noto Serif CJK SC Bold 裁剪成电视版用到的字（界面文案 + 训练题库），生成 android/qilian/src/main/res/font/qilian_serif.otf。
用法：python3 tools/subset-tv-font.py [NotoSerifCJK-Bold.ttc 路径]
缺字时 Android 会自动回落到系统字体，所以题库更新后不重新裁剪也能正常显示。"""
import glob, os, sys
from fontTools import subset
from fontTools.ttLib import TTCollection

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = sys.argv[1] if len(sys.argv) > 1 else '/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc'
chars = set()
paths = glob.glob(os.path.join(ROOT, 'android/**/*.kt'), recursive=True)
paths += glob.glob(os.path.join(ROOT, 'android/app/src/main/assets/content/*.json'))
for p in paths:
    with open(p, encoding='utf-8') as f:
        chars.update(f.read())
chars.update(chr(c) for c in range(0x20, 0x7f))
chars.update('，。、：；？！“”‘’（）《》·—…〇一二三四五六七八九十车马炮相仕帅兵卒将士象馬車砲')
coll = TTCollection(src)
font = coll.fonts[2]  # SC
opts = subset.Options()
opts.layout_features = ['*']
opts.name_IDs = ['*']
opts.notdef_outline = True
opts.hinting = False
sub = subset.Subsetter(opts)
sub.populate(unicodes=[ord(c) for c in chars if ord(c) >= 0x20])
sub.subset(font)
out = os.path.join(ROOT, 'android/qilian/src/main/res/font/qilian_serif.otf')
font.save(out)
print(f'{len(chars)} chars -> {out} ({os.path.getsize(out)//1024} KB)')
