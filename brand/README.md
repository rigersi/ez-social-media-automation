# Brand assets

## logo.png — REPLACE ME

The file currently here is a **placeholder extracted from an existing EZ poster** so the
pipeline can be demoed. Before going live, overwrite `brand/logo.png` with the **official
EZ mark** as a transparent PNG (RGBA).

Rules the brand-stamp step follows automatically (brand spec §1):

- The logo is **never redrawn by AI** — it is composited on top, top-left, by this renderer.
- It is **recoloured to the dominant colour** of each artwork (blue art → blue logo...).
- Pixels that are **near-white in your asset stay white** — that is how the small
  power/house glyph keeps its white colour. So export the glyph in white and the rest of
  the mark in any solid colour (black works great).
- A **dark halo** is drawn behind it for legibility on busy frames.
- Keep the asset at least ~600 px wide so it stays crisp at 1080×1920.
