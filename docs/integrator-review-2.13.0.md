# WakeGuard 2.13.0 integrator review

## Scope and limits

Reviewed the current room snapshot: `MainActivity`, `HomeMetrics`, `MascotArtView`, `MascotCatalog`, `MascotPickerActivity`, `Ui`, `VisualDesigns`, `I18n`, `AppThemes`, `DesignSets`, settings entry points, manifest, Gradle version, and color resources. The room copy has no Git metadata, so an authoritative diff against the untouched baseline is unavailable. Findings describe the current source; no build or device check was run.

## Confirmed in the current source

- Pattern previews and live surfaces share `VisualDesigns.drawPattern`. `VisualDesignPickerActivity` calls it directly; `Ui` delegates both the page overlay and the home hero gradient to it. No separate `Ui.drawPattern` implementation remains.
- The home empty-state copy has Japanese, English, and Korean text in `MainActivity.localized`. The empty state sits in the hero and hides the populated hero content. `HomeMetrics` sets minimum heights of 136 dp for the populated hero and 104 dp for the empty hero; actual measured sizes still need a device check.
- The supplied transparent dragon, fox, owl, and robot PNGs are mapped by `MascotArtView` and used in the home and mascot picker. The home character is 96 dp, the empty-state character is 72 dp, and bitmap decoding now uses `inSampleSize = 2`. The `none` selection intentionally keeps its star mark.
- Current appearance preference keys are `app_ui_profile_id`, `app_visual_design_id`, `app_theme_id`, `app_theme_mode`, and `app_mascot_id`. Theme mode remains `system`, `light`, or `dark`. `DesignSets` contains these 12 preset IDs: `classic-paper`, `sky-dragon`, `deep-dragon`, `forest-owl`, `comic-dragon`, `arcade-robot`, `moon-owl`, `sakura-fox`, `circuit-dragon`, `sun-fox`, `storm-robot`, and `tidal-owl`. The keys and IDs are internally consistent in this snapshot; without a baseline, preservation cannot be independently proven.
- MainActivity still uses the existing alarm profile, scheduler, and snooze APIs in its row handlers. This is source inspection only; baseline comparison and runtime alarm verification are unavailable here.
- `app/build.gradle.kts` now targets `versionCode = 204` and `versionName = "2.13.0"`.

## Color review

- The widget background and streak highlights use cool blue colors, including `#FFA9D8FF`. The current shared brand, dialog, and splash color tokens are cool neutrals and blues.
- The warm `solar` and `wax` palettes remain intentional selectable themes. Legacy `ic_ignido_v296_orbit`, `ic_ignido_v296_sunrise`, and `ic_ignido_v296_wakesignal` vectors still contain orange or gold paths, but a repository search found no references to those drawables.
- There is no `values-night*` resource directory. The default `ignido_midnight` splash/window color is the light paper `#F3F6FA`, so system-dark startup colors still need a review or night-qualified overrides in line with the art direction.

## Remaining checks

1. Run `assembleDebug` after the remaining resource and source changes, and record the result.
2. On a 360 x 800 dp device or emulator, inspect empty and populated home states, measured hero height, compact alarm rows, all four characters, text scaling, and ja/en/ko copy. Check the blue palette in light and dark modes, including the system-dark startup screen.
3. Exercise alarm creation, enable/disable, snooze, and rescheduling to verify behavior at runtime. The room copy does not provide a baseline diff, so source review alone cannot prove alarm logic or old settings IDs were unchanged.

No build, device check, push, or release was performed by this reviewer.