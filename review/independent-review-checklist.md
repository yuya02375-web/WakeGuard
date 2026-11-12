# Mascot integration review — as built

Status: implementation review for 2.16.0, 2026-11-12. This file replaces the pre-implementation room checklist; the room's detailed coordinates and verdicts are historical discussion, not current requirements.

## Contract

- Public mascot IDs are unchanged: `dragon`, `fox`, `owl`, `robot`; persisted `none` still suppresses drawing.
- Existing constructors, factories, getters/setters, color methods, and both static drawing overloads remain available.
- `drawCharacter(Context, ...)` uses the supplied context density. The canvas-only overload uses system density.
- Canvas save/restore is balanced. Static geometry is initialized once; each rendering thread has its own painter.

## Art pass

- Large illustrations use a low-contrast paper plate; 56 dp catalog previews omit the common frame and enlarge the art so the selected design card supplies the background.
- Repeated paper-colored rims were removed from character parts. Pigment blocks and species-specific ink contours now define the forms, reducing the shared sticker look.
- The dragon uses a long neck, radial frill, rounded snout, and repaired tail-tip path instead of generic wings and horn spikes. The fox has a folded far ear and low tail sweep. The owl keeps unequal features and a perched pose. The robot uses brass/steel/copper roles, an unequal bell pair, twelve full-size dial ticks, and near-6:45 hands.
- Dot radii use a device-pixel minimum; this avoids the 1.8-unit floor that nearly erased the dragon's pupil in 56 dp previews.
- Body pigments do not follow the selected UI palette. Palette color stays on each figure's small badge/accessory.
- There is no raster artwork, gradient, glow, or floating ground shadow. Small previews omit fine marks.

## Bugs corrected

- Dragon tail tip commands wrote into the tail path instead of the separate tip path.
- The specimen inner-rule commands wrote into the outer plate path.
- Replaced the far-fox-ear silhouette and robot dial orientation/size to match the reviewed design.

## Remaining validation

- `git diff --check` passed, and final local `assembleDebug` completed successfully in `../.workbench_logs/WakeGuard/build_assembleDebug_20261112-133045.log` from the workspace root.
- The GitHub stable artifact signature and checksum are verified separately after the committed workflow run; this source review does not claim that check.
- No Android device/emulator is connected; 56 dp and dark-hero screenshots are not verified.

## Team review

Independent API, visual-quality, integration, and release-workflow reviews informed this revision. The review findings were checked against the source before integration. The earlier room could provide direction and review material, but its code-writing seat had no available model; its draft was not treated as an implemented change.
