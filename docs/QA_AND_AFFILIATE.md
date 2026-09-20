# Modules 2 & 3 contract — Quality Assurance and Affiliate (built on the Factory)

The Content Factory was designed so modules 2 and 3 never need to touch its code.
Everything they need is a file or an HTTP call.

## What the factory already exposes (read-side, for QA)

| Artifact | Content |
|---|---|
| `data/state.json` → `posts[]` | rolling log: id, slot, archetype, headline, series, caption, hashtags, video path, status |
| `output/plans/<id>.json` | the full post package as planned (on-image copy, art prompt, sources, tags) |
| `output/publish/<id>.json` | the exact payload handed to Postiz (dry-run mode) |
| `output/quarantine/**` | everything editorial QA rejected, with `problems.json` reasons |
| `GET :8787/status` | next slot, recent posts, active directives (JSON) |
| Postiz `GET /public/v1/analytics/:integration` and `/analytics/post/:postId` | platform numbers (QA fetches these with the same API key) |

## What QA may write (write-side)

1. **Directives** — `POST :8787/feedback` (or drop files into `feedback/inbox/`):
   ```json
   { "source": "qa", "runs_left": 8,
     "text": "Short hooks beat long ones this month; keep headlines under 5 words.",
     "hints": { "archetype": "weird_gadget",
                "ban_topics": ["insta360-go3s"],
                "series": "Gadget Court",
                "hashtag_add": ["#gadgetcourt"] } }
   ```
   Free text goes verbatim into the planner prompt; `hints` are enforced structurally
   (archetype forcing, topic bans, series steering, hashtag injection). Directives retire
   after `runs_left` runs, so adaptations decay automatically if QA stops repeating them.
2. **Trends** — rewrite `trends/hashtag-pool.json` (`trending` list). Re-read on every plan.
3. **Topics** — drop verified cards into `topics/custom/*.json`; ban stale ones via hints.
4. **Screenshots** — send `screenshot_base64`; if a vision model is configured the factory
   summarises it into the directive itself.

Suggested QA loop (phase 2 build): every 24 h fetch Postiz analytics per post → score
archetypes/hooks/hashtags → emit 1-3 directives (keep `runs_left` short, 4-10) → optionally
refresh the hashtag pool. The factory adapts from the very next post.

## Affiliate (phase 3, gated by design)

Brand rule: **no links until a program approves the account**, and always behind a manual
approval gate. Planned shape (nothing to build now):

1. `data/affiliate-approval.json` — you write `{"approved": true, "programs": ["amazon"]}`
   by hand once accepted. The factory refuses affiliate mode without it (hard gate).
2. New archetype `specific_item` (shoppable card: product photo cut-out + price chip +
   "link in caption/DM" CTA) added as a topic-card flag; caption template appends the
   affiliate link **only** when the gate is open.
3. Comment → DM auto-link: on platforms that allow it (Instagram/TikTok via their approved
   DM automation or a tool like ManyChat; X DMs are restricted — caption link instead).
   The factory's job ends at publishing the trigger keyword ("LINK" comment CTA); the DM
   bot is a separate small service reading the same config.
4. QA scores affiliate posts separately (CTR/earnings via program APIs) and can ban a
   product topic through the same directive channel.

Until then the factory stays link-free: growth phase, editorial only — exactly per spec.
