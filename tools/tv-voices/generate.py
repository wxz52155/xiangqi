#!/usr/bin/env python3
"""
南坡象棋语音包生成器（在 GitHub Actions 里运行：.github/workflows/tv-voices.yml）。

模型：Kokoro-82M-v1.1-zh（Apache-2.0，生成的音频可自由分发）。
红方男声、黑方女声；“车”读 jū，合成时用同音字“居”代替以避免读成 chē。
输出 out/voice_<id>_<m|f>.ogg 以及 out/report.json（时长、响度、语音识别回读）。
"""
import json
from pathlib import Path

import numpy as np
import soundfile as sf
import torch
from kokoro import KModel, KPipeline

REPO_ID = "hexgrad/Kokoro-82M-v1.1-zh"
SR = 24000
VOICES = {"m": "zm_010", "f": "zf_001"}

# id: (要合成的文字, 说明)
PHRASES = {
    # 局势
    "check": ("将军！", "将军"),
    "check2": ("将！看你往哪儿跑！", "连续将军"),
    "mate": ("绝杀！", "将死"),
    "stalemate": ("困毙！无子可走！", "困毙"),
    # 吃子：按被吃的子
    "eat_rook": ("吃居！", "吃车"),
    "eat_knight": ("吃马！", "吃马"),
    "eat_cannon": ("吃炮！", "吃炮"),
    "eat_bishop": ("吃象！", "吃象/相"),
    "eat_advisor": ("吃士！", "吃士/仕"),
    "eat_pawn": ("吃兵！", "吃兵/卒"),
    # 出子（每种子第一次出动时）
    "out_rook": ("出居！", "出车"),
    "out_knight": ("跳马！", "跳马"),
    "center_cannon": ("当头炮！", "炮走到中路"),
    "out_cannon": ("架炮！", "其它出炮"),
    "out_bishop": ("飞象！", "出象/相"),
    "out_advisor": ("补士！", "出士/仕"),
    "out_pawn": ("进兵！", "第一次走兵/卒"),
    "cross_river": ("过河！", "兵卒过河"),
    "king_move": ("老将出马！", "将帅移动"),
    # 对局流程
    "start": ("开局！请出招。", "开局"),
    "win": ("漂亮！你赢了！", "人机胜"),
    "lose": ("胜败乃兵家常事，再来一局！", "人机负"),
    "draw": ("握手言和。", "和棋"),
    "red_win": ("红方胜！", "双人红胜"),
    "black_win": ("黑方胜！", "双人黑胜"),
    "resign": ("认输了。", "认输"),
    "undo": ("悔棋一步。", "悔棋"),
    "hint": ("看这一步。", "提示"),
    "illegal": ("这步走不了。", "非法走子"),
    # 训练
    "solved": ("好棋！绝杀成功！", "杀法题完成"),
    "wrong": ("再想想。", "做错"),
    "lesson_done": ("这一节完成啦！", "残局/开局完成"),
}


def trim(wav: np.ndarray, threshold: float = 0.004, pad: int = 3600) -> np.ndarray:
    idx = np.where(np.abs(wav) > threshold)[0]
    if len(idx) == 0:
        return wav
    # 开头多留一点：ch / sh / j 等清辅音很轻，裁太狠会把“吃”变成“持”
    return wav[max(0, idx[0] - pad): min(len(wav), idx[-1] + pad // 2)]


def finish(wav: np.ndarray) -> np.ndarray:
    wav = trim(wav.astype(np.float32))
    peak = float(np.max(np.abs(wav))) or 1.0
    wav = wav / peak * 0.89
    fade = min(240, len(wav) // 8)
    if fade > 0:
        wav[:fade] *= np.linspace(0, 1, fade)
        wav[-fade:] *= np.linspace(1, 0, fade)
    return wav


def main() -> None:
    out = Path("out")
    out.mkdir(exist_ok=True)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    model = KModel(repo_id=REPO_ID).to(device).eval()
    en = KPipeline(lang_code="a", repo_id=REPO_ID, model=False)
    pipe = KPipeline(lang_code="z", repo_id=REPO_ID, model=model, en_callable=lambda t: next(en(t)).phonemes)

    report = {}
    for key, (text, note) in PHRASES.items():
        for tag, voice in VOICES.items():
            chunks = [r.audio.numpy() if hasattr(r.audio, "numpy") else np.asarray(r.audio) for r in pipe(text, voice=voice, speed=0.9)]
            wav = finish(np.concatenate(chunks))
            name = f"voice_{key}_{tag}"
            sf.write(out / f"{name}.ogg", wav, SR, format="OGG", subtype="VORBIS")
            sf.write(out / f"{name}.wav", wav, SR)
            report[name] = {"text": text, "note": note, "seconds": round(len(wav) / SR, 2)}
            print(name, text, report[name]["seconds"])

    # 语音识别回读，检查读音
    try:
        from faster_whisper import WhisperModel

        asr = WhisperModel("medium", device="cpu", compute_type="int8")
        for name in report:
            segments, _ = asr.transcribe(
                str(out / f"{name}.wav"), language="zh", beam_size=5,
                initial_prompt="中国象棋对局语音：将军，绝杀，困毙，吃车，吃马，吃炮，吃象，吃士，吃兵，出车，跳马，当头炮，架炮，飞象，补士，进兵，过河。",
            )
            report[name]["heard"] = "".join(s.text for s in segments).strip()
    except Exception as e:  # noqa: BLE001
        print("ASR skipped:", e)

    (out / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
