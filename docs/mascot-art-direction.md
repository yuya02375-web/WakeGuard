# Mascot art direction — implemented

Status: implementation in `app/src/main/java/jp/wakeguard/alarm/MascotArtView.java`; this note supersedes the earlier room draft.

## Visual language

Treat each character as a small natural-history illustration: species-specific gesture and anatomy, natural pigments, and a few purposeful ink marks. Geometry is specified as fixed Android `Path` coordinates. There are no generated bitmap characters, gradients, glows, floating shadows, or noisy texture passes. Shapes use pigment and ink; repeated cream rims were removed because they made every figure read as the same sticker family.

At 64 dp and above, a warm paper field-note plate frames the character with a low-contrast hairline. At 56 dp, the plate is omitted and the figure grows to use the preview card's available space. Fine species marks appear only at 64 dp and above; small previews preserve the silhouette, pigment, eyes, limbs, and one palette badge. Dot sizes are clamped in device pixels so small pupils stay distinct from their outer eye disks.

Each character has its own proportions and asymmetry. Keep bodies in recognizable natural pigments. The chosen design-set color belongs on a small accessory, not across the whole animal or machine. Dragon's chest tag also carries a small mountain ridge mark. The robot uses brass for its body, steel for its chest and limbs, and copper for its winding key and hub.

## Character cues

- **Dragon:** long rising neck, broad frill with radial ribs, rounded snout, pale belly, curled tail, small mountain tag. The frill replaces generic bat wings and horn spikes.
- **Fox:** right-facing profile, one upright ear and one folded-back ear, low sweeping brush tail with a pale tip, cream bib, neckerchief.
- **Owl:** perched frontal pose, unequal eyes and tufts, a darker near wing, branch and a small breast pin.
- **Robot:** clock-like head with intentionally unequal bells, smaller face dial, 6:45 hands, brass body, steel chest and legs, copper winding key.

## Rendering contract

- Keep the IDs `dragon`, `fox`, `owl`, and `robot`; `none` remains a valid hidden selection.
- Keep the existing constructors, `create`, `supportedCharacterIds`, setters/getter, and both `drawCharacter` overloads.
- The context-aware static overload uses the supplied context density; the no-context overload uses system density.
- Geometry is shared read-only. Static preview drawing reuses a thread-local painter to avoid both shared-painter races and paint allocation on every frame.
- The art is centered in the existing clipped content box. Large renderings use a 0.9 scale and five-unit inset inside the plate; 56 dp previews use a 0.98 scale and one-unit inset without a plate.

## Issues found while integrating

The dragon tail tip was accidentally added to the body-tail path rather than its own path, and the inner paper rule accidentally mutated the outer plate path. Both path ownership errors are corrected. The fox ear/tail and robot dial deviations from the reviewed direction were also corrected.

## Review limits

The Android project build verifies compilation and packaging. A connected device/emulator is still needed to inspect the 56 dp preview and the 96 dp dark hero on screen; source geometry alone cannot certify final visual balance at runtime.
