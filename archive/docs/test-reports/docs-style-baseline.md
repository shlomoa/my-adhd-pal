# Aurora restyle baseline (red), issue #164

Evidence that TC-D-031..038 fail before the restyle. Red commit: 93adb71 (first commit of the PR `claude/docs-style-tests`)
(tests and catalogue only; images and sources unchanged).

Command: `scripts/test/test-design-style.sh`. Exit 1.

```
FAIL TC-D-031 W4-choice.png is the approved reference file (sha256)
FAIL TC-D-031 W4 is 1254x1254 RGBA
FAIL TC-D-031 W4 is transparent in the corners and opaque in the center
FAIL TC-D-032 every screen image and the overview have the expected pixel size
FAIL TC-D-033 watch images W1-W9 are transparent outside the circle
FAIL TC-D-034 every image was rendered from the current board and stylesheet (render manifest)
FAIL TC-D-035 stylesheet text contrast pairs meet 4.5:1
FAIL TC-D-036 alert levels 1 and 2 differ by text and icon
FAIL TC-D-037 section texts say aurora, keep 'proposed design' and the undecided tile action, no old color wording
FAIL TC-D-038 the legacy palette is gone from ui.css and the boards
```

`scripts/test/test-design-style.sh` joins CI with the restyle step, when its head is green.
