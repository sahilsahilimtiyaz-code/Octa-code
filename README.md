# Octa Code — Mobile AI Coding Agent
Package `com.sahil.octacode` · v1.0.0

Native Android AI coding workstation. No legacy, no stubs wired as real.

## Done
### M0 scaffold
- Compose Activity + bottom nav: Home / Agent / Projects / Terminal / Settings
- Dark premium theme (#0A0A12, #00D4FF, #B84CFF, #00EE88, #FF4757)
- Splash + Settings carry creator credits (branding law); zero watermarks elsewhere

### M1 Capability Registry
- `CapabilityRegistry` probes providers — never assumes readiness
- `ProviderStatus`: Ready / MissingKey / Misconfigured / Unavailable + reason
- Project type detection (Android/Node/Python) from real files
- Device probe (SDK, RAM, ABI, Java version)
- Autonomy levels: ASK / BALANCED / GUIDED / HIGH_AUTONOMY (default ASK)

### M2 AI provider adapters (Koin + Ktor)
- `OpenAiAdapter` — real `https://api.openai.com/v1` streaming (SSE)
- `CustomEndpointAdapter` — any OpenAI-compatible base URL (Ollama, vLLM, …)
- Claude + Gemini: declared, honestly **Unavailable — not implemented in M2**
- Credentials in EncryptedSharedPreferences (Android Keystore)
- Secret redaction on all HTTP logs (Bearer/sk-/x-api-key/github_pat_)
- Retry: 429/5xx exponential backoff + jitter (max 4 attempts)
- Capability badges: API / REMOTE / LOCAL / UNAVAILABLE
- Settings screen wires keys, custom endpoint, autonomy picker to real registry

## Open in AndroidIDE / Android Studio
1. Copy this folder into your projects directory.
2. Open as existing Gradle project (AGP 8.5.2 + Kotlin 1.9.24).
3. Run `app` on device (minSdk 26).

## Roadmap
- M3 16-phase Mission Engine (observable, cancellable, persisted)
- M4 Chat & Agent UI (streams from M2 adapters)
- M5 Projects (index, editor, git, build, PTY terminal)
- M6 Settings & credits polish

## Honesty principle
No fake integrations, agents, tool calls, or success messages. Unavailable = "Unavailable" + reason.
