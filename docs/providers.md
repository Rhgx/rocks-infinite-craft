# Provider setup

Open **Mods > Rocks' Infinite Craft > Configure > Generation**. Choose a provider and model, test the connection, then enable generation. Saved recipes work while generation is disabled.

## Ollama

Run Ollama on the Minecraft host and pull a model. Select **Ollama**, then choose an installed model from the dropdown or enter its exact ID under **Custom**. The default address is `http://localhost:11434`.

## Codex CLI

Install Codex CLI and run `codex login` as the OS user running Minecraft. Choose **Codex CLI** and select a model from its dropdown, or use **Custom**. An empty model uses the CLI default. Reasoning defaults to **Low**; Fast mode is optional.

The mod finds the executable on PATH. If needed, set `CODEX_CLI_PATH` to the native executable, not a `.cmd` or `.ps1` wrapper, and restart Minecraft. Each request uses a fresh ephemeral process with a read-only sandbox. Connection tests consume usage too.

## Hosted and compatible APIs

Choose the provider, enter its **Model ID**, and supply an API key or environment variable. Model IDs are not substituted automatically. OpenRouter IDs usually include a provider prefix.

| Provider | Default API base URL | Key environment variable |
| --- | --- | --- |
| OpenAI | `https://api.openai.com/v1` | `OPENAI_API_KEY` |
| Anthropic | `https://api.anthropic.com/v1` | `ANTHROPIC_API_KEY` |
| Gemini | `https://generativelanguage.googleapis.com/v1beta` | `GEMINI_API_KEY` |
| OpenRouter | `https://openrouter.ai/api/v1` | `OPENROUTER_API_KEY` |
| Compatible API | Set your chat-completions server's URL | Set if needed |

Base URLs include the version prefix, but not `/responses` or `/chat/completions`. Remote endpoints require HTTPS; loopback HTTP works for local servers. Redirects are rejected.

## Configuration file

Dedicated servers use `config/infinitecraft.json`. For example, replace its nested `provider` object with:

```json
{
  "provider": "ollama",
  "baseUrl": "http://localhost:11434",
  "model": "your-installed-model",
  "apiKeyEnv": "",
  "apiKey": "",
  "timeoutSeconds": 60,
  "reasoning": "low",
  "fastMode": false
}
```

Set top-level `generationEnabled` to `true`, then run `/fusion reload` or restart. When changing providers in the file, also update or clear the URL and key fields. Local settings do not configure a remote server; `localhost` means the Minecraft host.

Entered keys are stored in plaintext in this file. Leave `apiKey` empty to use `apiKeyEnv` instead, and set the environment variable before starting Minecraft. Do not share your config file.

Requests include item IDs, names, tags, relevant item data, and generation preferences. Custom item names may therefore reach your provider. The mod does not send chat history or world save files. Hosted providers may charge for requests.

## Troubleshooting

- **No models listed:** check that Ollama is running or Codex is logged in, then reopen settings. Custom IDs remain available.
- **Authentication fails:** check the selected provider's key or CLI login.
- **Model rejected:** use the exact ID available to your account or local installation.
- **Timeout:** try a faster model or increase the timeout under Advanced.
- **Repeated invalid output:** try another model, then [forget the blocked pair](engine.md) to retry it.
