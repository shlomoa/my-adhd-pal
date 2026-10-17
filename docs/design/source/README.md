# Board sources

Sources of the proposed design. The boards (`*.dc.html`, static markup between `<x-dc>` and `</x-dc>`), `ui.css` and
`canvas.json` come from the published design canvas
[ADHD Pal screen designs](https://claude.ai/artifact/MREz2fe1cVjhLqmoRtACPV). The look is the aurora style (issue #164,
[style](../README.md#style-aurora)): `ui.css` defines the palette once as variables.

## Which images come from boards and which are supplied

| Images | Where they come from |
|---|---|
| P1-P13 and the overview | rendered from the boards by `render.mjs` (phone boards at device scale 2: 390x844, Settings 390x1000; overview 1200x640 shows all 22 screen images) |
| W4 (choice) | **supplied**: the approved aurora reference image from the user; this is the look the other screens follow |
| W1, W2, W3, W5, W6, W7, W8, W9 | **supplied**: generated artwork in the same look (commits b3664d6 and e37407d, 1254x1254, the round face only); there is no board for them, and the old boards were removed so the sources do not claim otherwise |

`render-manifest.json` records this. For every rendered image it holds the hash of the board and `ui.css` it was rendered
from (the overview also of the images it shows), so a changed source cannot go without a new render; the supplied images are
listed under `supplied` and W4 also under `reference`. `scripts/test/test-design-style.sh` checks all of it.

The watch rules of `ui.css` (`.w`, `.wb`, `.tile`) belong to the earlier watch boards and are not used by any board now; the palette
variables are still used by the phone boards.

`clear-outside-circle.py <image.png>` makes a round watch image transparent outside its circle without touching the artwork
inside (generated artwork can leave faint stray pixels in the corners); it was applied to W2.

The Atkinson Hyperlegible web font was not reachable when rendering, so the rendered images use the system sans-serif
fallback; text widths can differ slightly from the canvas.

Run, in a directory where `playwright-core` is installed and with a Chromium available (`CHROMIUM_PATH`, default
`/opt/pw-browsers/chromium`): `node <path to>/render.mjs <output dir> [ids...]`, for example with `overview` to render only
the overview after an image changed. It rewrites `render-manifest.json`.
