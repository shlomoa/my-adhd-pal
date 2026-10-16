# Board sources

Sources of the proposed design: the published design canvas
[ADHD Pal screen designs](https://claude.ai/artifact/MREz2fe1cVjhLqmoRtACPV), copied from its `project/` files
(`*.dc.html` boards, `ui.css`, `canvas.json`). Each board is static markup between `<x-dc>` and `</x-dc>`.

The PNG files in [`../images`](../images) are rendered from these boards by `render.mjs`: it wraps each board in a plain
page with `ui.css`, and screenshots it with Chromium at device scale 2 (phone 390x844, Settings 390x1000, watch 340x340,
overview 1200x640). The Atkinson Hyperlegible web font was not reachable when rendering, so the images use the system
sans-serif fallback; text widths can differ slightly from the canvas.

Run: `node render.mjs <dir holding project/ from the canvas> <output dir>` (needs `playwright-core` and a Chromium).
