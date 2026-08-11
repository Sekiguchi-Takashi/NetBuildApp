#!/data/data/com.termux/files/usr/bin/bash
cd "$(dirname "$0")/.." || exit 1
python3 - << 'PY'
import re

SRC = 'app/src/main/java/com/appathy/netbuild/'


def literals(js):
    return ''.join(p.replace('\\n', '\n').replace('\\"', '"')
                   for p in re.findall(r'"((?:[^"\\]|\\.)*)"', js))


def blocks(src, marker):
    out = []
    i = 0
    while True:
        i = src.find(marker, i)
        if i == -1:
            return out
        j = i + len(marker)
        depth, instr, esc = 1, False, False
        while depth > 0:
            c = src[j]
            if instr:
                if esc:
                    esc = False
                elif c == '\\':
                    esc = True
                elif c == '"':
                    instr = False
            else:
                if c == '"':
                    instr = True
                elif c == '(':
                    depth += 1
                elif c == ')':
                    depth -= 1
            j += 1
        out.append(src[i + len(marker):j - 1])
        i = j


manual = open(SRC + 'Manual.java').read()
lines = ["# NetBuildApp マニュアル", "",
         "アプリ内の「マニュアルを読む」と同じ内容です。",
         "本文は Manual.java が原本で、このファイルは tools/build_docs.sh で生成されます。", ""]
for b in blocks(manual, 'new Manual('):
    head, body = b.split(',', 1)
    lines += ["## " + literals(head), "", literals(body).strip(), ""]
open('docs/MANUAL.md', 'w').write('\n'.join(lines))

guide = open(SRC + 'DeviceGuide.java').read()
out = ["# 機器の説明", "",
       "アプリ内の「機器の説明を読む」と同じ内容です。",
       "原本は DeviceGuide.java の entries()。1件追加すればアプリと本ファイルの両方に反映されます。", ""]
for b in blocks(guide, 'new DeviceGuide('):
    parts = []
    depth, instr, esc, buf = 0, False, False, ''
    for c in b:
        if instr:
            buf += c
            if esc:
                esc = False
            elif c == '\\':
                esc = True
            elif c == '"':
                instr = False
            continue
        if c == '"':
            instr = True
            buf += c
        elif c == ',' and depth == 0:
            parts.append(buf)
            buf = ''
        else:
            if c == '(':
                depth += 1
            elif c == ')':
                depth -= 1
            buf += c
    parts.append(buf)
    key, name, one, role, place, pit, status = [p.strip() for p in parts]
    label = '未実装' if 'PLANNED' in status else '実装済み'
    out += ["## " + literals(name) + "（" + label + "）", "",
            literals(one), "",
            "**何をするもの** — " + literals(role), "",
            "**どこに置くか** — " + literals(place), "",
            "**よくある間違い** — " + literals(pit), ""]
open('docs/DEVICES.md', 'w').write('\n'.join(out))
print('docs/MANUAL.md と docs/DEVICES.md を生成しました')
PY
