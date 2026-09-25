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

### M4.3 Compact cards + pinned composer
- Cards compacted to reference height (56dp icon well, 18sp title, 14sp/19lh body maxLines 4)
- Status pill, Model/Project pills, gold send button tightened to match mock proportions
- Composer pinned outside the scrollable column — always visible (attach / files / mic / send row can no longer clip)
- Hero + planet reduced (200dp / 280dp) so empty state fits with cards and composer on one screen
- Attach/file/mic use plain boxes (full-opacity icons; disabled IconButton wash-out removed)
- **94 unit tests still passing** (`:app:testDebugUnitTest`)

### R1 Runtime provisioning (the download layer)
- `tools/gen_runtime_manifest.py` — reproducible generator that reads the live Termux apt
  index and freezes each package's URL, size and SHA-256 into the APK. Re-run to refresh;
  `--check` fails if the committed asset is stale.
- Bundled `assets/runtime-manifest.json` — **78 pinned arm64 artifacts (264MB)** in six
  independent groups: base userland 14.8MB · PRoot 0.1MB · Node 24 23.9MB · Python 3.14 10.0MB
  · dev tools 23.2MB · **Rust 219MB**. No group is opt-in: all six are part of the default
  install, and a group install downloads only what it still needs (closures overlap and are
  deduped against the ledger, so shared packages are never fetched or charged twice).
- Manifest ships **inside the APK** — a compromised mirror cannot widen what the app accepts.
- `ArtifactDownloader` — streams to disk, `Range`-resumes partials, computes SHA-256 over the
  bytes actually written (including a resumed prefix). A mismatch **deletes the file** and
  reports expected vs received; a complete-but-wrong file restarts instead of resuming;
  429/5xx retry through the existing `RetryPolicy`, other statuses fail at once; cancellation
  is never reported as a failure.
- Dedicated download client in `KtorHttpFactory` — the shared client's 90s request timeout
  covers the response *body*, which would kill a 125MB download mid-flight.
- `RuntimeLedger` — atomic (tmp + rename) JSON record of what was proven. An unreadable
  ledger starts **empty** and says so: cached artifacts are then re-verified offline by
  hashing them, so damage costs a re-check, never a re-download and never a free pass.
- `RuntimeProvisioner` — `installAll()` runs every group in order (the default install,
  Rust included); `install(id)` runs one. The plan is re-resolved per group so a package
  shared by two closures is never fetched twice. Publishes busy state before the coroutine
  starts, stops at the first failure with the real reason, keeps bytes on Stop so the next
  run resumes.
- **Runtime screen** (Settings → Runtime): a **"Install full runtime (264MB)"** primary
  action that quotes the honest remaining bytes first, plus per-group Install / Stop /
  Re-verify with real byte progress, honest "n/m verified · X to download", ABI gate that
  refuses to download anything on a non-arm64 device, delete-all.
- Still honest about the milestone: artifacts are **fetched and verified** here; unpacking
  them into a working shell and wiring them into `which()` is R2. No tool is reported
  available until it actually is.
- Unit tests: manifest pin validation, downloader (fresh / resume / mismatch / truncated /
  reuse-without-network / retry / 404), ledger (round-trip / corruption / atomicity),
  provisioner end-to-end (install / idempotent / stop-on-failure / mismatch / offline
  re-verify / full install with cross-group dedup / queue stop at first failure).

### R2 Unpacking (the files land on disk)
- `core/runtime/extract/` — hand-written `ar` and `tar` readers plus one new dependency,
  `org.tukaani:xz` (pure Java, 169KB), because Termux ships **`data.tar.xz`** in every
  package sampled, including the 125MB `rust`. LZMA2 has no JDK or Android equivalent, and
  the path handling was never going to be delegated anyway.
- Two things about these packages were **measured against the live mirrors before the code
  was written**, not assumed:
  - entries are *not* package-relative. Every one embeds the whole Termux prefix —
    `./data/data/com.termux/files/usr/bin/bash` — so naive extraction would bury the
    runtime under `prefix/data/data/com.termux/...`. A scan of the 49-package `base` group
    (4,274 entries) found every path either under that prefix or part of the chain of
    directories leading to it — **nothing outside it**.
  - that scan found **1,016 symlinks** (52 using `..`, all of which stay inside), **2
    absolute** ones — `bzip2`'s `bin/bzcmp -> /data/data/com.termux/files/usr/bin/bzdiff` —
    no hardlinks, no devices, no traversal paths, and a worst-case expansion of **10.5x**
    (which is what the 16x/128MB decompression-bomb cap is sized against).
- **Path safety** is judged per entry and never guessed at: `Place` (under the prefix),
  `Ignore` (the prefix's own directory chain, present in every package and not an anomaly),
  `Refuse` (anything outside — dropped and counted, never written). A `..` that climbs past
  the root **fails the whole extraction**; writing a file also re-checks that its parent
  still resolves inside the root, so a symlink planted by an earlier entry cannot be used
  to write to `/sdcard`. Absolute link targets into the original Termux prefix are rewritten
  to relative ones; other absolute links are dropped and counted.
- `ArtifactInstaller` — unpacks into a **staging directory first**, then moves into the
  prefix, so a malformed package can never leave half a tree behind claiming to be
  installed. A failure partway through the move rolls back what it placed. Uninstall
  deletes only paths **no other package claims**, then prunes directories that are empty.
- `RuntimeLedger` — records gain `installedFiles` / `installedAtMillis` / `skippedEntries`,
  so *verified* and *unpacked* are two independent claims and are never conflated.
  Re-verifying a cached artifact preserves the unpacked state; uninstalling keeps the
  verification (the bytes are still proven good) and drops only the unpacked claim.
- `RuntimeIndex` — `commandPath("bash")` answers from recorded `bin/` and `sbin/` paths
  only, so `which("COPYING")` cannot resolve to a licence document, and a dangling symlink
  resolves to `null` rather than to a path that cannot work.
- Runtime screen — per-group **Unpack (offline)** and **Uninstall**, honest
  "n/m verified · k unpacked", and the full-install button switches to
  "Unpack everything (offline)" once every byte is already cached.
- Still honest: the files are genuinely on disk and removable, but they are **not yet
  executable** — Android refuses to run anything the app is allowed to write. That is R3.
- Unit tests: `DebFixtures` builds real `.deb` archives in memory (`ar` + `tar` + xz) so
  the unpacker is exercised against the byte layout a download will actually have — prefix
  stripping, symlink creation and rewriting, refused paths, traversal, bomb cap, corrupt
  header checksum, non-deb input — plus installer install/uninstall/shared-path and index
  lookup suites. **148 tests, all passing** (`:app:testDebugUnitTest`).

### M4.4 Provider diagnostics + neon theme pass

Two reported failures were the app withholding information it already had; a
third was a form that forgot its own contents.

- **Errors carry the server's reason.** `openAiCompatibleStream` threw with only
  the status line. Providers explain themselves in the response body, so
  `HTTP 401` was discarding the sentence that named the wrong key. Errors now
  extract `error.message`, unescape JSON, collapse to one line, and never pass
  HTML off as an explanation.
- **Cross-provider keys caught at validation.** `sk-or-…` passes a
  `startsWith("sk-")` check, so an OpenRouter key cleared OpenAI's validation and
  only failed once the request reached `api.openai.com`. Now named *before* the
  request leaves.
- **`"model": "default"` never reaches the wire.** That string is our own
  placeholder for "unset"; every real server answers 400 to it.
  `resolveCustomModel` returns null and the adapter refuses with an instruction.
- **Settings prefill from storage**; url and model survive saving, and a stored
  key shows its prefix so a wrong paste is visible at a glance.

Visually, gold was retired app-wide — `GoldSendButton` → `NeonSendButton`, the
four `Gold*` tokens deleted with no consumers left — and the chat bubbles were
rebuilt: the agent gets a gradient avatar + glass card, the user a gradient
outline. Composer pill regraded to a neon border.

- Unit tests: error extraction, JSON unescaping, foreign-prefix rules, model
  resolution. **157 tests, all passing**.

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

### Runtime (on-device coding environment)
- **R1** Pinned manifest + downloader + verification ledger + Runtime screen — **done**
- **R2** Unpack `.deb` → `$PREFIX`, `RuntimeIndex`, install/uninstall — **done**
- **R3** Exec bootstrap (jniLibs `lib*.so` + PRoot) — Android 10+ W^X compliance
- **R4** PRoot rootfs + base userland → real shell in Terminal
- **R5** Node/npm → install OpenCode / Claude Code CLI → stream into Chat
- **R6** Python + dev tools + project import (git clone / SAF / zip) — M5
- **R7** Rust on demand + foreground service / Doze exemption for background agents

## Honesty principle
No fake integrations, agents, tool calls, or success messages. Unavailable = "Unavailable" + reason.
