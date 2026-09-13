"""Rebuild pixel assets using local Minecraft textures. Requires Pillow and NumPy."""
import argparse
import io
import random
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parent.parent


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("minecraft_jar", type=Path)
    args = parser.parse_args()
    with zipfile.ZipFile(args.minecraft_jar) as jar:
        def texture(name):
            image = Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/block/{name}.png"))).convert("RGBA")
            # Vanilla animated block textures stack square frames vertically.
            return image.crop((0, 0, image.width, image.width)) if image.height > image.width else image

        # Preserve the original four quadrants and their existing 45% darkening.
        old = Image.open(ROOT / "original-icon.png").convert("RGBA")
        background = old.copy()
        for name, x, y, tint in [
            ("grass_block_top", 0, 0, (144, 188, 88)),
            ("oak_log_top", 256, 0, (255, 255, 255)),
            ("copper_block", 0, 256, (255, 255, 255)),
            ("amethyst_block", 256, 256, (255, 255, 255)),
        ]:
            pixels = np.array(texture(name), dtype=float)
            pixels[:, :, :3] *= np.array(tint) / 255 * .55
            tile = Image.fromarray(pixels.astype("uint8")).resize((512, 512), Image.Resampling.NEAREST)
            for yy in range(y, y + 256, 32):
                for xx in range(x, x + 256, 32):
                    # Recover each original pixel's exact color wherever it was not hidden by text.
                    colors = old.crop((xx, yy, xx + 32, yy + 32)).getcolors(1024)
                    visible = [(count, color) for count, color in colors if color[:3] != (255, 255, 255)]
                    color = max(visible)[1] if visible else tile.getpixel((xx, yy))
                    background.paste(color, (xx, yy, xx + 32, yy + 32))
        background.save(ROOT / "icon-background.png")
        text = Image.open(ROOT / "logo-text.png").convert("RGBA").resize((512, 512), Image.Resampling.NEAREST)
        icon = Image.alpha_composite(background, text)
        icon.save(PROJECT / "src/main/resources/assets/rocks_infinite_craft/icon.png")
        icon.save(ROOT / "icon.png")

        blocks = ["oak_log", "copper_block", "amethyst_block", "moss_block", "bookshelf",
                  "prismarine", "bricks", "deepslate_tiles", "hay_block_side", "cherry_planks",
                  "lapis_block", "melon_side", "end_stone", "red_mushroom_block", "dark_oak_planks", "quartz_block_side"]
        random.Random(42).shuffle(blocks)
        banner = Image.new("RGBA", (1600, 400))
        for i, name in enumerate(blocks):
            tile = texture(name).resize((200, 200), Image.Resampling.NEAREST)
            banner.alpha_composite(tile, ((i % 8) * 200, (i // 8) * 200))
        banner = Image.alpha_composite(banner, Image.new("RGBA", banner.size, (0, 0, 0, 115)))
        banner.save(ROOT / "banner-background.png")
        title = Image.open(ROOT / "banner-text.png").convert("RGBA")
        if title.size != banner.size:
            raise ValueError("Banner text must use a 1600 × 400 canvas")
        banner = Image.alpha_composite(banner, title)
        banner.save(ROOT / "banner.png")

        social_blocks = blocks + [
            "diamond_block", "gold_block", "emerald_block", "redstone_block",
            "obsidian", "crying_obsidian", "glowstone", "sea_lantern",
            "pink_terracotta", "blue_ice", "purpur_block", "magma",
            "warped_planks", "crimson_planks", "honeycomb_block", "bamboo_mosaic",
        ]
        random.Random(43).shuffle(social_blocks)
        social = Image.new("RGBA", (1280, 640))
        for i, name in enumerate(social_blocks):
            tile = texture(name).resize((160, 160), Image.Resampling.NEAREST)
            social.alpha_composite(tile, ((i % 8) * 160, (i // 8) * 160))
        social = Image.alpha_composite(social, Image.new("RGBA", social.size, (0, 0, 0, 115)))
        social.save(ROOT / "social-background.png")
        social_text = Image.open(ROOT / "social-text.png").convert("RGBA")
        if social_text.size != social.size:
            raise ValueError("Social preview text must use a 1280 × 640 canvas")
        Image.alpha_composite(social, social_text).save(ROOT / "social-preview.png")


if __name__ == "__main__":
    main()
