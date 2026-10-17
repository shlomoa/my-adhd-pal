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
