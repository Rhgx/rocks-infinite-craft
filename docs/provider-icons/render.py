"""Generate reference renders from SVG sources without overwriting approved assets. Requires numpy, resvg-py, Pillow."""
from pathlib import Path
import io
import numpy as np
import resvg_py
from PIL import Image, ImageDraw

SOURCE = Path(__file__).resolve().parent
DEST = SOURCE.parents[1] / "build/provider-icons"
DEST.mkdir(parents=True, exist_ok=True)
SAMPLE, THRESHOLD = 32, 0.35
ICONS = {
    "ollama": ("ollama", "#E6E6E6"),
    "codex": ("codex", "#E6E6E6"),
    "openai": ("openai", "#E6E6E6"),
    "anthropic": ("anthropic", "#D4A27F"),
    "gemini": ("gemini-color", None),
    "openrouter": ("openrouter", "#E6E6E6"),
    "compatible": ("compatible", "#83C9CC"),
    "disabled": ("disabled", "#AAAAAA"),
}
for name, (source, color) in ICONS.items():
    size = 24 if name == "ollama" else 16
    svg = (SOURCE / (source + ".svg")).read_text(encoding="utf-8")
    if color:
        svg = svg.replace("currentColor", color).replace('fill="white"', 'fill="' + color + '"').replace('stroke="white"', 'stroke="' + color + '"')
        svg = svg.replace("<svg ", '<svg color="' + color + '" ')
        # Simple Icons relies on the default black fill.
        if 'fill=' not in svg:
            svg = svg.replace("<svg ", '<svg fill="' + color + '" ')
    data = resvg_py.svg_to_bytes(svg_string=svg, width=size*SAMPLE, height=size*SAMPLE)
    rgba = np.array(Image.open(io.BytesIO(bytes(data))).convert("RGBA"), dtype=np.float32)
    alpha = rgba[:, :, 3] / 255
    cov = alpha.reshape(size, SAMPLE, size, SAMPLE).mean(axis=(1, 3))
    rgb = (rgba[:, :, :3] * alpha[:, :, None]).reshape(size, SAMPLE, size, SAMPLE, 3).mean(axis=(1, 3))
    rgb /= np.maximum(cov[:, :, None], 0.0001)
    out = np.zeros((size, size, 4), dtype=np.uint8)
    for y in range(size):
        for x in range(size):
            if cov[y, x] < THRESHOLD:
                continue
            above = cov[y-1, x] if y else 0
            left = cov[y, x-1] if x else 0
            below = cov[y+1, x] if y+1 < size else 0
            right = cov[y, x+1] if x+1 < size else 0
            shade = 1.1 if min(above, left) < THRESHOLD else .8 if min(below, right) < THRESHOLD else 1
            out[y, x, :3] = np.clip(rgb[y, x] * shade, 0, 255)
            out[y, x, 3] = 255
    Image.fromarray(out).save(DEST / (name + ".png"))
    (DEST / (name + ".png.mcmeta")).write_text('{"texture":{"blur":false,"clamp":true}}\n', encoding="utf-8")

preview = Image.new("RGB", (640, 180), "#252525")
draw = ImageDraw.Draw(preview)
for i, name in enumerate(ICONS):
    icon = Image.open(DEST / (name + ".png")).resize((64,64), Image.Resampling.NEAREST)
    x, y = (i % 4) * 160 + 12, (i // 4) * 90 + 4
    preview.paste(icon, (x,y), icon)
    draw.text((x+72,y+25), name, fill="white")
preview.save(DEST / "preview.png")
