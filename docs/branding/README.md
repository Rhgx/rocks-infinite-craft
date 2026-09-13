# Branding assets

- Icon: 512 × 512 px, using Rocks' supplied transparent text overlay.
- Banner: 1600 × 400 px, eight columns and two rows of block textures.
- Overlay: 45% black. All scaling uses nearest-neighbour sampling.

The banner uses Rocks' supplied `banner-text.png` at its original 1600 × 400 size. `banner-background.png` keeps the texture grid separately for future edits.

To regenerate with local Minecraft assets:

```sh
python docs/branding/render.py /path/to/minecraft-client.jar
```

Requires Pillow and NumPy. Minecraft textures are from the local 26.2 client and belong to Mojang. No generated artwork or downloaded texture pack is used.
