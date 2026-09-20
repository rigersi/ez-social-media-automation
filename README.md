# EZ GOOD FINDS — Content Factory (module 1 of 3)

Autonomous editorial pipeline for the EZ Good Finds channels (X, YouTube Shorts, TikTok,
Instagram Reels, Facebook, Threads — all already connected in Postiz).

**2 posts/day (10:00 & 18:00 Europe/Tirane), zero daily manual work:**

```
 PLAN ─▶ RENDER ─▶ BRAND STAMP ─▶ STATIC VIDEO ─▶ EDITORIAL QA ─▶ SCHEDULE (Postiz)
  │        │            │              │                │                 │
  │        │            │              │                └─ rejects plain ads / rule breakers
  │        │            │              └─ 8-12s silent Ken-Burns MP4, 1080x1920 H.264
  │        │            └─ official logo composited (never AI-drawn), tinted to art colour
  │        └─ deterministic base layout -> Qwen-Image-Edit (ComfyUI, local & free)
  └─ LLM (local Ollama, free) writes hook/caption/hashtags within your brand rules
        ▲
        │  FEEDBACK (you by text/screenshot, or the future QA module — same door)
        └── POST /feedback · feedback/inbox/ folder · CLI
```

Modules 2 (Quality Assurance) and 3 (Affiliate) plug into this one later through the
documented feedback contract — see [`docs/QA_AND_AFFILIATE.md`](docs/QA_AND_AFFILIATE.md).

---

## Is Java the right call? (your question)

Yes. The heavy AI never lives inside your code: ComfyUI (graphics) and Ollama (text) are
**local services with HTTP APIs** — language-agnostic. What the factory itself does is
orchestration: HTTP calls, JSON, image compositing, ffmpeg subprocesses, a scheduler daemon,
a small HTTP server for feedback. Java does all of that natively, runs forever as a service,
and — the way this project is written — needs **zero external libraries**: no Maven repo,
no n8n, no node_modules. `javac` + your JDK is the whole build. Python would only win if you
were training models; you are not.

Recommended free AI stack on your PC:

| Job | Tool | Cost |
|---|---|---|
| Posters | ComfyUI: `qwen_image_edit_fp8_e4m3fn` + `qwen_2.5_vl_7b_fp8_scaled` + `qwen_image_vae` + Lightning-4steps LoRA (your existing setup) | free, local |
| Captions/hashtags/hooks | Ollama `qwen2.5:7b` (or `llama3.1:8b`) | free, local |
| Reading your feedback screenshots | Ollama vision model, e.g. `minicpm-v:8b` (optional) | free, local |
| Publishing | Postiz (already connected) | your existing |

If no LLM is reachable the factory still publishes: a deterministic template planner writes
a fully valid post from the topic cards, so **a slot is never missed**. Same for ComfyUI:
if it is down, the deterministic renderer's layout ships as-is.

---

## Requirements

- JDK 11 or newer (`javac -version`)
- ffmpeg on PATH (or set `video.ffmpegPath`)
- Postiz with your 6 channels connected + an API key (Settings → API)
- Optional: Ollama, ComfyUI with your Qwen-Image-Edit workflow

## Quick start

```bash
# Linux / macOS
cd ez-content-factory
cp config/config.example.json config/config.json   # then edit it
./build.sh
./factory.sh run-once            # produce ONE post for the next slot (dry-run by default)
./factory.sh status              # slots, recent posts, active directives
./factory.sh daemon              # the always-on factory

# Windows
build.bat
factory.bat run-once
factory.bat daemon
```

`run-once` prints the whole post package and writes:

- `output/art/<id>.png` — final stamped poster (+ `<id>-layout.png` = pre-AI base)
- `output/video/<id>.mp4` — the static video that goes to all six channels
- `output/publish/<id>.json` — the exact Postiz payload (dry-run mode)
- `output/plans/<id>.json` — full plan (caption, hashtags, yt tags, sources…) for QA/you

### Going live checklist

1. **Replace `brand/logo.png`** with the official EZ mark (transparent PNG, glyph in white —
   see `brand/README.md`). The shipped file is a placeholder extracted from an old poster.
2. Point `comfyui.*` at your workflow (next section) and run `run-once` a few times;
   compare `output/art/*-layout.png` (base) with `*.png` (after Qwen-Image-Edit).
3. Set `llm.provider=ollama`, `ollama pull qwen2.5:7b`, run `run-once` again — captions
   become written, not templated.
4. Let `daemon` run in **dry-run** for a week; review `output/publish/*.json` daily.
5. Set `publish.mode = "live"` + your Postiz `apiKey`. Posts now schedule themselves.
6. Install as a service so it survives reboots:

```ini
# /etc/systemd/system/ez-factory.service  (Linux)
[Unit]
Description=EZ Good Finds Content Factory
After=network.target
[Service]
WorkingDirectory=/path/to/ez-content-factory
ExecStart=/usr/bin/java -cp target/classes ez.factory.Main daemon --config config/config.json
Restart=always
[Install]
WantedBy=multi-user.target
```

```bat
:: Windows Task Scheduler (admin cmd) — runs at login, restarts via a wrapper if needed
schtasks /Create /TN "EZFactory" /TR "C:\ez\ez-content-factory\factory.bat daemon" /SC ONLOGON
```

---

## ComfyUI connection (your exact graph, no re-wiring)

1. In ComfyUI open the workflow you already use (Qwen-Image-Edit fp8 + Lightning LoRA).
2. **Workflow → Export → “Save (API Format)”** → save as `comfyui/workflow.json`.
3. Find two node ids in that file (search for your prompt text / your LoadImage):
   - the text node receiving the prompt → `comfyui.promptNodeId` (field usually `"text"`)
   - the `LoadImage` node that should receive the base layout → `comfyui.baseImageNodeId`
     (the factory uploads the base PNG automatically; use `""` if your graph needs none)
4. `comfyui.mode`: `auto` (use ComfyUI when reachable, else fallback — recommended).

The injected prompt always carries the brand hard-rules: top-left logo zone empty,
TikTok/Shorts safe zones (right rail, bottom strip) calm, wording kept verbatim.
See `comfyui/workflow.example.json` for the injection contract.

---

## Brand rules enforced in code (EditorialQa + Validator)

- 6 archetypes rotated, never back-to-back; **a plain product ad fails QA**.
- Every post: hook + story/joke/argument or series continuity.
- Fictional celebrity pairings carry the **CONCEPT VISUAL** tag (else rejected).
- Series posts carry their number (counters in `data/state.json`, seeds in
  `topics/series.json` — Sidemen 1-7 already counted) + a “recommend the next” hook.
- Creator-verified posts need quote + source + ≥1 evidence URL.
- Comparisons need a ≥4-row table and both “best for” verdicts.
- On-image vs off-image split is structural: caption/hashtags/yt-tags are **metadata only**,
  delivered next to the MP4 via the Postiz API, never burned into art.
- Artwork 1080×1920; MP4 silent, 8-12 s, H.264 yuv420p, same file to all six channels.
- Logo: official asset composited top-left, recoloured to the artwork's dominant hue,
  white glyph preserved, dark halo — AI never draws it.
- Anything failing QA goes to `output/quarantine/` with `problems.json`; the slot retries
  on the next run instead of shipping bad material.

## Content inputs you (or QA) can drop in any time

- `topics/custom/*.json` — new topic cards (facts = callouts; the LLM may rephrase, not invent)
- `trends/hashtag-pool.json` — refresh `trending` whenever; re-read on every plan, no restart
- `topics/series.json` — add new series runs

## Feedback — the one door for instructions (you today, QA module tomorrow)

Three equivalent entrances, one memory (`data/directives.json`), auto-retiring after
`runs_left` runs:

```bash
# 1) CLI
./factory.sh feedback --text "More comparison posts this week, drier humour." --runs 6

# 2) folder drop (great from your phone): note + optional same-name screenshot
#    feedback/inbox/note.txt  +  feedback/inbox/note.png   (picked up within 60 s)

# 3) HTTP API (what the QA module will call)
curl -X POST localhost:8787/feedback -H 'Content-Type: application/json' -d '{
  "source": "qa",                       # or "human"
  "text": "Comparisons outperform: double their share; retire the insta360 topic.",
  "runs_left": 6,
  "hints": { "archetype": "comparison", "ban_topics": ["insta360-go3s"],
             "series": "Gadget Court", "hashtag_add": ["#gadgetcourt"] },
  "screenshot_base64": "<optional png>", "screenshot_ext": "png"
}'
curl localhost:8787/status     # next slot, recent posts, active directives
```

Screenshots: if `llm.visionModel` is set (e.g. `minicpm-v:8b` in Ollama), the factory reads
the screenshot itself and attaches a vision note to the directive; otherwise it is archived
in `feedback/screenshots/` for you. Optional shared secret: `feedback.token`
(header `X-Feedback-Token`).

Directives steer: archetype choice, series choice, banned topics, extra hashtags, and free
text injected verbatim into the planner prompt (and into the LLM retry loop).

---

## Project layout

```
ez-content-factory/
├─ build.sh / build.bat          zero-dependency javac build
├─ factory.sh / factory.bat      runner (daemon|run-once|plan|status|feedback|serve)
├─ config/config.example.json    every knob, documented
├─ brand/logo.png                REPLACE with official mark (see brand/README.md)
├─ comfyui/workflow.example.json injection contract for your API-format export
├─ topics/seed-topics.json       18 verified topic cards across all archetypes
├─ topics/series.json            series counters seed
├─ trends/hashtag-pool.json      hashtag source of truth (live-editable)
├─ docs/QA_AND_AFFILIATE.md      module 2 & 3 contract
└─ src/main/java/ez/factory/
   ├─ Main.java                  CLI + daemon loop
   ├─ Pipeline.java              plan→render→stamp→video→QA→publish→record
   ├─ plan/                      Planner, Validator, TemplatePlanner, TopicPool, TrendPool
   ├─ llm/                       OllamaClient, OpenAiCompatClient (free/local first)
   ├─ art/                       ComfyUiGenerator (Qwen-Image-Edit), FallbackRenderer
   ├─ brand/BrandStamp.java      official-logo composite + dominant-colour tint + halo
   ├─ video/VideoWrap.java       ffmpeg Ken-Burns static video
   ├─ publish/                   PostizPublisher (6 channels), DryRunPublisher
   ├─ qa/EditorialQa.java        pre-publish gate
   ├─ feedback/                  DirectiveStore, FeedbackServer (HTTP), folder inbox
   ├─ schedule/SlotClock.java    10:00/18:00 Europe/Tirane
   ├─ state/StateStore.java      series counters, rotation, post log
   └─ util/                      Json, Http, Exec, IO, Log (no external libraries)
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Ollama unreachable` warning | start Ollama (`ollama serve`) or set `llm.provider=none` |
| `ComfyUI unreachable` warning | start ComfyUI; or `comfyui.mode=fallback` to skip |
| Postiz 401 | wrong/missing `postiz.apiKey`; header is the raw key, no “Bearer” |
| Postiz settings error | one channel DTO rejected a key — the error names it; remove that key in `PostizPublisher.settingsFor` |
| `ffmpeg failed` | install ffmpeg or set `video.ffmpegPath` (absolute path ok) |
| Post quarantined | read `output/quarantine/<id>-a1/problems.json`; fix topic card/directive; next run retries |
| Logo looks wrong | your asset: glyph must be the near-white pixels; everything else gets tinted |
