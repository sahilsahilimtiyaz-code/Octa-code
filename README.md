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
- Unit tests: phase matrix, repository contract, detectors, redaction, net logic, full pipeline — **86+ passing**

### M3b Mission engine + UI kit + screens
- `MissionEngine` — observable (`StateFlow`), cancellable, Room-persisted; resumes via `listRecoverable()`
- Phase handlers **1–6** (Understand → Detect → Team → Provider → Plan → Checkpoint)
- Checkpoint gated by autonomy: ASK/BALANCED wait for Approve/Reject; HIGH auto-approves
- Provider selection always records **real** registry status (MissingKey/Unavailable pass through)
- UI kit: `PhaseRail`, `ThinkingOrb`, `StreamTerminal`, `DiffCard`, `StatusBanner`, `GlassPanel`
- Screens: **MissionLaunch** (goal/path/provider) + **MissionDetail** (rail, stream, checkpoint, pause/cancel/resume)
- Home hub: New Mission, recoverable resume, recent list; bottom bar hides on `mission/*` routes
- Koin: `engineModule` → `MissionEngine` singleton

### M3c Implement / diff / rollback (phases 7–16)
- Full handler map **1–16** — no honesty stop at phase 7
- **Implement**: Ready-provider gate → streamed `OCTA_EDIT` full-file replacements → path safety → app-private snapshot → Room diffs (before/after SHA-256 + redacted patch)
- **Review diff**: HIGH auto-accepts; other autonomies pause for Approve/Reject; reject fails mission + auto-rollback
- **Format / Analyze impact / Test / Build / Install**: real `ProcessRunner` when tools exist; otherwise `SKIPPED` with reason (never fake success). BUILD/INSTALL honor autonomy approval gates
- **Verify**: file hashes must match recorded `afterHash` (or created-file existence)
- **Rollback**: healthy path skips with “snapshot kept”; any failure after implement restores snapshot and emits `ROLLBACK_PERFORMED`
- **Complete**: closes mission with accepted-change summary
- Koin: `FileSnapshotStore` (filesDir/mission_snapshots) + `JvmProcessRunner`
- MissionDetail: live diff observe, Accept/Reject wired, phase-aware approval copy, rollback banner
- Unit tests: full pipeline happy path, review gate, unsafe paths, no-edit fail, auto-rollback on test fail

### M3d Mission engine UI wiring + premium polish
- `MissionDetailScreen` binds `MissionEngine.state` + Room flows (mission, phase runs, events, diffs)
- Live `PhaseRail`, `StreamTerminal`, `DiffCard` accept/reject, phase-aware approval copy, pause/cancel/resume, rollback banner
- `MissionLaunchScreen`: goal/path/provider/autonomy glass panels, real registry status probe + Refresh, honest path gate
- Home hub: running progress card, recoverable missions, recent list with status accents
- UI kit polish: reduced-motion safe `PhaseRail`/`ThinkingOrb`/`StatusBanner`, stream copy + empty hint, patch preview truncation
- Theme: full glass-morphism token set on DeepSpaceBlack / NeonBlue / ElectricPurple / NeonGreen / NeonRed
- `OctaRoot` premium shell: starfield, motion policy locals, glass bottom bar (hidden on splash + mission routes)
- **94 unit tests passing** (`:app:testDebugUnitTest`)

### M4 Chat & Agent UI
- `ChatEngine` — StateFlow conversation; Ready-gate before send; cancellable stream (Stop)
- Streams **live** from M2 `AiProvider.chatStream` (OpenAI / Custom) — no canned replies
- Provider picker + real `CapabilityRegistry` status line (Ready / Unavailable + reason)
- Chat bubbles: user vs agent, streaming label, honest Failed / Stopped captions
- ThinkingOrb while waiting for first token; StatusBanner for ready-gate errors
- Composer disabled until provider is Ready; in-memory session (documented: lost on process death)
- Koin: `chatModule` → `ChatEngine` singleton; Claude/Gemini remain honestly Unavailable
- Shared `defaultModelFor(ProviderId)` used by chat + mission implement phase
- Unit tests: ready-gate, delta accumulation, stream error, stop/partial, empty draft, provider switch — **94 passing**

### M4.1 Agent chrome (reference match)
- Gold/champagne accent tokens (`Gold`, `GoldBright`, `GoldDeep`, `GoldText`) alongside brand neon
- `AgentChrome` kit: glass top bar + hex logo, status pill, hero planet, accent info cards, composer pills, gold send/stop
- Chat empty state: “Build Better / Together” hero + Agent unavailable / Mission activity cards
- Live conversation reuses `ChatBubble` + real `ChatEngine` (unchanged)
- Nav: menu/profile → Settings, project pill/cards → Projects
- **94 unit tests still passing** (`:app:testDebugUnitTest`)

### M4.2 Chat UI pixel pass
- Bottom bar reduced to reference set: **Workspace / Code / Terminal** (gold pill on active icon + label underline); Settings via header menu
- Status pill: gold folder outline, split project | agent halves, green readiness dot
- Hero: larger cut-off planet + “Build Better / Together” typography matched to mock
- Cards: 64dp icon wells, 19sp titles, full accent borders (gold / cyan)
- Composer: gold→cyan border, Model 1.55:1 Project pills, BasicTextField + glowing gold Send
- Spacing/horizontal gutters aligned to mock (20dp page margin)
- **94 unit tests still passing** (`:app:testDebugUnitTest`)

## Setup
1. Copy this folder into your projects directory.
2. Open as existing Gradle project (AGP 8.5.2 + Kotlin 1.9.24).
3. Run `app` on device (minSdk 26).

## Roadmap
- M3a Room schema / repository / tests — **done**
- M3b Mission engine + UI kit + Launch/Detail — **done**
- M3c Implement/diff/rollback handlers (phases 7–16) — **done**
- M3d Mission engine UI wiring + premium polish — **done**
- M4 Chat & Agent UI (streams from M2 adapters) — **done**
- M5 Projects (index, editor, git, build, PTY terminal)
- M6 Settings & credits polish

## Honesty principle
No fake integrations, agents, tool calls, or success messages. Unavailable = "Unavailable" + reason.
