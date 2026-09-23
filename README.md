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

### M3a Mission persistence (Room)
- 16-phase `MissionPhase` model with autonomy-aware approval rules
- Room schema v1: missions, phase runs, phase events, diffs (FK cascade)
- `MissionRepository` contract + `RoomMissionRepository` (fail-loudly mappers)
- Crash-recovery: `listRecoverable()` returns missions left RUNNING
- Koin `dataModule` wired at app start (no orphans — M3b engine / M3d UI consume next)
- Unit tests: phase matrix, repository contract (in-memory fake), detectors, redaction, net logic — **46 passing**

### M3b Mission engine + UI kit + screens
- `MissionEngine` — observable (`StateFlow`), cancellable, Room-persisted; resumes via `listRecoverable()`
- Phase handlers **1–6 only** (Understand → Detect → Team → Provider → Plan → Checkpoint); no orphan stubs
- Honesty stop at phase 7: mission **PAUSED** with reason *“arrives in M3c”* (never fakes implement)
- Checkpoint gated by autonomy: ASK/BALANCED wait for Approve/Reject; HIGH auto-approves
- Provider selection always records **real** registry status (MissingKey/Unavailable pass through)
- UI kit: `PhaseRail`, `ThinkingOrb`, `StreamTerminal`, `DiffCard`, `StatusBanner`, `GlassPanel`
- Screens: **MissionLaunch** (goal/path/provider) + **MissionDetail** (rail, stream, checkpoint, pause/cancel/resume)
- Home hub: New Mission, recoverable resume, recent list; bottom bar hides on `mission/*` routes
- Koin: `engineModule` → `MissionEngine` singleton

## Open in AndroidIDE / Android Studio
1. Copy this folder into your projects directory.
2. Open as existing Gradle project (AGP 8.5.2 + Kotlin 1.9.24).
3. Run `app` on device (minSdk 26).

## Roadmap
- M3a Room schema / repository / tests — **done**
- M3b Mission engine + UI kit + Launch/Detail — **done**
- M3c Implement/diff/rollback handlers (phases 7–16)
- M3d Deeper mission UX polish
- M4 Chat & Agent UI (streams from M2 adapters)
- M5 Projects (index, editor, git, build, PTY terminal)
- M6 Settings & credits polish

## Honesty principle
No fake integrations, agents, tool calls, or success messages. Unavailable = "Unavailable" + reason.
