# Provider icons

Approved dropdown assets use 24x24 for Ollama and 16x16 for every other option. Transparent PNGs rendered from SVGs using the user-provided coverage-threshold approach. Alpha is either 0 or 255. Texture filtering is explicitly nearest-neighbour. Gemini retains its multicolor gradient; monochrome brands use light colors for dark Minecraft buttons. Anthropic uses a warm neutral UI tint. Compatible API and Disabled are local utility symbols, not brand logos.

SVG sources retrieved 2026-09-12:

- Ollama: https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/ollama.svg
- Anthropic: https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/anthropic.svg
- OpenRouter: https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/openrouter.svg
- OpenAI: https://raw.githubusercontent.com/simple-icons/simple-icons/14.0.0/icons/openai.svg
- Codex: https://raw.githubusercontent.com/lobehub/lobe-icons/master/packages/static-svg/icons/codex.svg
- Gemini: https://raw.githubusercontent.com/lobehub/lobe-icons/master/packages/static-svg/icons/gemini-color.svg

Simple Icons uses CC0; Lobe Icons uses MIT. License texts accompany these sources and are bundled with the PNGs. Brand marks belong to their respective owners.

The approved PNGs live in `src/main/resources/assets/rocks_infinite_craft/textures/gui/providers/`. `preview.png` shows those exact assets. The SVGs here are their editable sources; Ollama and Compatible also received small pixel cleanups.

Run `python docs/provider-icons/render.py` from the project directory to generate reference renders in `build/provider-icons/`. It does not overwrite approved artwork. Requires Pillow, numpy and resvg-py only in the asset-building environment.
