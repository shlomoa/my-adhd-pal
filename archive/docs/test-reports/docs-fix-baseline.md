# Design images after the generated watch artwork: baseline (red), issue #164

Evidence that the cases changed or added for D4.3 fail on `main` after commit b3664d6 (generated watch images W1-W3, W5-W7, W9).
Red commit: the first commit of the PR `claude/docs-style-fix` (tests and catalogue only; images and sources unchanged).

Command: `scripts/test/test-design-style.sh`. Exit 1.

```
FAIL TC-D-033 watch images W1-W9 are transparent outside the circle        (W2 is not a transparent-cornered circle)
FAIL TC-D-034 images from boards were rendered from the current sources; supplied artwork is listed as supplied (render manifest)
FAIL TC-D-039 no board is left for supplied artwork, and the source README says which images are supplied
FAIL TC-D-040 the overview board shows all 22 screen images
```

After commit e37407d (new generated W8, coordinator addition to D4.3) the expectations were changed again before the fix: W8 is supplied
artwork too (all nine watch images), so only P1-P13 and the overview come from boards. Red commit of this change: the commit "Design
images: W8 is supplied artwork too" (tests and catalogue only):

```
FAIL TC-D-034 images from boards were rendered from the current sources; supplied artwork is listed as supplied (render manifest)
FAIL TC-D-039 no board is left for supplied artwork, and the source README says which images are supplied
```

Cleanup rule (coordinator, standing rule of the user): what a change makes unused is deleted in the same PR. The watch rules of `ui.css`, the
icons of the removed W4 board and the leftovers of the canvas stylesheet were unused; TC-D-041 guards it. Red commit: the commit "Design
images: TC-D-041, no dead style rules" (tests and catalogue only):

```
  unused class in ui.css: .a1 .a2 .accent .d-away .d-none .d-relay .d-upd .danger .err .i-forward .i-pause .i-play .lav .n .p .ring .s .sun .tile .tiles .w .warm .wb .wf .wide .wl .wrow (one line each)
FAIL TC-D-041 every class defined in ui.css is used by a board (no dead style rules)
```
