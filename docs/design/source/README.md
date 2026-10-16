# Board sources

Sources of the proposed design. The boards (`*.dc.html`, static markup between `<x-dc>` and `</x-dc>`), `ui.css` and
`canvas.json` come from the published design canvas
[ADHD Pal screen designs](https://claude.ai/artifact/MREz2fe1cVjhLqmoRtACPV). The look is the aurora style (issue #164,
[style](../README.md#style-aurora)): `ui.css` defines the palette once as variables, and the boards and `ui.css` were
restyled to match the approved reference image of W4.

The PNG files in [`../images`](../images) are rendered from these boards by `render.mjs`, which wraps each board in a plain
page with `ui.css` and screenshots it with Chromium:

- phone boards and the overview at device scale 2 (390x844, Settings 390x1000, overview 1200x640);
- watch boards as the round face only, transparent outside the circle, 1254x1254 like the reference.

The image of screen W4 is not rendered from a board: it is the approved aurora reference image supplied by the user, and the
render manifest marks it as the reference. `render-manifest.json` holds, for every other image, the hash of the board and
`ui.css` it was rendered from, so a changed source cannot go without a new render (checked by
`scripts/test/test-design-style.sh`).

The Atkinson Hyperlegible web font was not reachable when rendering, so the images use the system sans-serif fallback; text
widths can differ slightly from the canvas.

Run, in a directory where `playwright-core` is installed and with a Chromium available (`CHROMIUM_PATH`, default
`/opt/pw-browsers/chromium`): `node <path to>/render.mjs <output dir>`; it also rewrites `render-manifest.json`.
