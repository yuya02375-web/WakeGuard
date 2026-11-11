# WakeGuard 2.13.0 art direction: Moonlit Paper

Owner: art_director. Status: proposal for agreement. Scope: presentation only. No alarm logic, no saved IDs. Reference device: 360 x 800 dp.

## 1. Direction

Quiet, printed, premium. Paper and ink surfaces, one luminous accent, and one companion per set that reads as a collectible character standing on a stage. The characters' crescent and star marks echo the moonlit motif family. Motifs are printed texture: faint, drawn by one renderer, and never behind text. Blue palettes stay blue on every surface, including widgets, dialogs and the splash. Hierarchy comes from type scale and whitespace, not from more boxes. Characters are the emotional anchor, and they never sit on top of a control.

## 2. Fixed constraints

- No change to alarm scheduling, AlarmService, AlarmActivity or mission logic.
- No change to saved keys or IDs: app_ui_profile_id, app_visual_design_id, app_theme_id, app_theme_mode, app_mascot_id, and the 12 DesignSets preset IDs with their component mapping.
- The four parts (UI, design, color, character) stay independently selectable. Full-set apply and per-part apply both stay.
- Palette refresh: every appearance-aware screen keeps its key check in onResume (MainActivity appearanceKey, pickers paletteKey) and recreates when the key changes.
- Every new or changed string gets ja, en and ko entries through I18n.put.

## 3. Verified findings this fixes

1. Two motif renderers. VisualDesigns.drawPattern (pickers) and the private Ui.drawPattern (live screens) disagree on cell size (live 17 to 22 dp against 12 dp in pickers for rules, dots, diagonal, grid), on shape (radial, contour, film, stitch, botanical, circuit, halftone), and mosaic falls back to a grid on live screens.
2. Strength mismatch. Live motif alpha is 12/255 (about 5 percent). Picker samples use 48/255 (about 19 percent).
3. Motifs are drawn on the page and on every list card, which adds noise behind text.
4. Set cards show a 56 dp mascot and four color swatches. They never show a screen.
5. Characters are vector placeholders: 46 dp on home, 56 dp on set cards, 64 dp in the character picker.
6. Navigation: three back-button styles (text chevron at 30 sp and 34 sp, none on the set and character pickers); title sizes 25, 27 and 29 sp; two commit models (footer apply for design and color, tap-to-apply for UI, character and sets); active badges use three different words (適用中, 使用中, 選択中). ThemePickerActivity is titled デザイン, the same title as the pattern picker.
7. Home: header about 85 dp; hero about 215 dp at 360 dp width, which also repeats the empty-state message; tile minimum height 154 dp.
8. Warm and yellow accents outside the palette tables. The blue rows in AppThemes are already cool. The warm sources are static resources:
   - res/drawable/widget_bg.xml: ember gradient #FFC857 to #FF7A00 to #FF3A14, warm brown stroke #553A2A1F, used by at least six world-clock widget layouts.
   - res/values/styles.xml: colorAccent #C2362B and window background #FBF5EE (warm cream).
   - res/values-v31/ignido_splash.xml: splash background #FBF5EE.
   - res/values/wakeguard_v101.xml: WakeGuardAlertDialog background #FFFCF8 and colorAccent #C2362B, so dialog buttons are red in every theme.
   - To verify: res/drawable/ic_ignido_v296_orbit.xml fill #FFD28D, and hex literals in notification_timer_running*.xml, the widget layouts, AlarmService.java and SystemSettingsActivity.java.

## 4. Color

### 4.1 Token roles

| Token | Role | Rule |
|---|---|---|
| BG, BG_2 | page gradient | cool paper in the blue family |
| SURFACE | cards and sheets | never tinted by a motif |
| SURFACE_2 | wells, chips, thumbnails | |
| BORDER | 1 dp hairline | no heavier outlines |
| ACCENT | motif ink, idle icons, secondary emphasis | motif alpha 14 percent |
| ACCENT_2 | active state only | primary button fill, selected stroke, active tab, enabled alarm time, overline |
| HERO_START, HERO_END | hero gradient only | |
| NAV_HIGHLIGHT | active nav pill only | |
| TEXT, MUTED | text | MUTED for all secondary text |

### 4.2 Keeping blue visibly blue

- Blue family: aurora, abyss, nordic, lagoon, glacier, storm, blueprint, lunar. In light and dark, every surface, border, accent and widget token with saturation above 0.10 has hue between 170 and 240 degrees. Neutral text and hairline tokens may be grey, but never warm. No warm hue (0 to 70, or 330 to 360) appears in these palettes.
- Visibly blue accent (light mode): hue 190 to 235 degrees and HSL saturation 0.45 or more. Current values pass: glacier 2877B9 (207 degrees, 0.63), blueprint 2461A6 (212, 0.64), aurora 007E9E (192, 1.00), abyss 126B81 (192, 0.76).
- lunar 6672A8 is greyish (saturation 0.28). Optional retune, needs approval: light accent 5B6FC4 (229 degrees, 0.46). The dark accent AEBBF9 stays.
- Warm identities (dawn-ink, dune, solar, sakura, ruby, poppy, coffee, wax) keep their warm accents. They are outside the blue rule.
- Chrome that cannot follow the palette (window and splash background, widget frame, default dialog, default colorAccent) uses a cool neutral set: paper #F3F6FA (light), night ink #0D141C (dark), dialog white #F8FAFD, default accent #3E5C70, widget accent gradient #A9D8FF to #4C8DF0, widget frame #F2101A26, hairline #33FFFFFF.

## 5. Type and hierarchy

- Hero time: 52 sp, Light weight, tabular figures, auto-size floor 40 sp.
- Screen title: 26 sp Bold (home and all five appearance screens).
- Overline: 11 sp Bold, letter spacing +0.12 em, ACCENT_2.
- Section header: 13 sp Bold, MUTED.
- Card title: 17 sp Bold in pickers, 16 sp Bold in alarm rows.
- Body 14 sp. Meta 12.5 sp MUTED. Micro 10 sp (thumbnails only).
- The design font (serif, mono, condensed, sans) applies to titles and card titles. The hero time uses Light for sans and condensed, Regular for serif and mono.
- One dominant element per screen. One filled primary button per screen. Secondary information in MUTED.

## 6. Spacing and density

- Scale: 4, 8, 12, 16, 24, 32 dp. Side margin 16 dp everywhere. Card padding 16 dp. Gap between cards 12 dp, between sections 24 dp. The UI density profile scales these, as Ui.dp does today.
- Touch targets at least 48 dp. Back button 48 dp.
- Radii: hero 24, card 20, thumbnail 14, button 16, pills full. All multiplied by the design corner scale, as now.

### 6.1 Home height budget (estimates at 360 x 800 dp)

| Zone | Now | Target |
|---|---|---|
| Header | about 85 dp | 64 dp (title 26 sp) |
| Hero | about 215 dp | 136 dp at most |
| Alarm list heading | 40 dp | 36 dp, carries the enabled-count pill |
| Add button bar | 66 dp | 64 dp |
| Bottom nav | 73 dp | 64 dp (tabs 60 dp) |
| Alarm list area | about 330 dp | about 440 dp |

### 6.2 Hero

- Card: 16 dp outer margin, 18 dp inner sides, 16 dp top, 14 dp bottom. Gradient from HERO_START to HERO_END, with a 1 dp top edge highlight (HERO_TEXT at 12 percent).
- Rows: overline (次の起床), then the time (capped at 60 percent of the card width so it never runs under the character), then a meta line (day, date, remaining), then the horizon track (8 dp, ticks at 00, 06, 12, 18, 24). Remove the separate 00:00 and 24:00 label row. Keep the existing dot logic.
- The enabled count moves into the alarm list heading pill.
- Character stage: 96 dp, anchored to the top end of the hero. It may overhang the top edge by 12 dp. It never overlaps the time, meta line or track, and it is not clickable.

### 6.3 Empty state

- Centered: stage (120 dp character, or a 64 dp plate with a star mark if no character is chosen), title 18 sp Bold, one-line body 13 sp MUTED, 32 dp above the block.
- No second button. The bottom add button stays the only primary action.
- The empty card no longer repeats the hero's next-alarm message.

### 6.4 Alarm rows

- Time 34 sp (from 38 sp). Tile minimum height 132 dp (from 154 dp). Keep all alarm content, handlers and reschedule calls unchanged.

## 7. Motif system: one renderer

- Single source: VisualDesigns.drawPattern. The pattern branch of Ui.drawPattern is removed. Ui keeps only the card-edge treatment, moved to Ui.drawCardTreatment.
- Cell sizes are in dp and identical on live screens and thumbnails.

| Pattern | Cell (dp) | Stroke (dp) |
|---|---|---|
| rules | 14 | 0.75 |
| grid | 16 | 0.75 |
| dots | 14 | dot radius 0.9 |
| diagonal | 14 | 0.75 |
| scanline | 5 | 0.6 |
| contour | 18 | 0.75 |
| radial | 18 | 0.75 |
| halftone | 8 | dot radius 0.6 to 1.2 |
| circuit | 16 | 0.75 |
| stitch | 12 | 0.9, dashed |
| waves | 14 | 0.9 |
| film | 18 | 0.75 |
| botanical | 24 | 0.9 |
| mosaic | 18 | filled tiles |

- Canonical shapes are the VisualDesigns versions, because that is what users choose in the picker.
- Ink: ACCENT at alpha 0x24, in light and dark.
- Where motifs appear: the page background and inside the hero. Not on list cards. Cards show only their card treatment.
- Card treatments (picker shapes are canonical): flat = none; outline = 1 dp BORDER; double-border = outline plus an inner inset of 3 dp; dashed = 1 dp dashed BORDER; wire = corner brackets, 1.5 dp, ACCENT; raised = 2 dp elevation with an ACCENT-tinted ambient shadow.
- Thumbnails use the same composition as the live screen: BG, motif, and one SURFACE card with the treatment.

## 8. Characters

- Assets: keep the four supplied PNGs in drawable-nodpi (mascot_dragon_premium_v1, mascot_fox_premium_v1, mascot_owl_premium_v1, mascot_robot_premium_v1). Never tint, recolor or re-export them.
- Edge check: the premium PNGs have a cyan glow with light fringe pixels. Check every placement on SURFACE and BG. The stage plate is designed to hide the fringe.
- Decode with BitmapFactory at inSampleSize 2 (4 for frames of 64 dp or smaller). Cache by resource id (LruCache, 4 entries). No full-size decode in lists.
- Optical centering: compute each asset's alpha bounding box once. Fit it to 92 percent of the frame height, bottom-aligned at 94 percent, so the four characters carry similar visual weight. The robot is round and the dragon is tall with wings, so raw image bounds would not match.
- Stage: radial plate, ACCENT at 0x33 at the center fading to 0 at the edge, diameter equal to the frame. Contact shadow ellipse at the base (0.56 of frame width, 0.07 of frame height, ACCENT_2 at 0x26). The plate follows the palette. The character does not.
- Sizes: home hero 96 dp (was 46 dp); empty state 120 dp; character picker row 88 dp (was 64 dp); set mini home 40 dp (replaces the 56 dp card mascot).
- Decorative: setClickable(false), setFocusable(false), importantForAccessibility NO. The card text carries the name. Never inside a switch, button or row hit area.
- MascotArtView keeps its class name, Mascot.fromId and SUPPORTED_CHARACTER_IDS. The vector drawing code goes. setMascotColors and clearMascotColors are removed only after a grep shows no callers.

## 9. Appearance studio: one frame, five screens

| Order | Axis | ja | en | ko | Activity |
|---|---|---|---|---|---|
| 1 | Design sets | デザインセット | Design sets | 디자인 세트 | DesignSetPickerActivity |
| 2 | UI style | UIスタイル | UI style | UI 스타일 | UiProfilePickerActivity |
| 3 | Motif and type | 模様・文字 | Motif & type | 무늬·글꼴 | VisualDesignPickerActivity |
| 4 | Color and mode | 色・モード | Color & mode | 색상·모드 | ThemePickerActivity |
| 5 | Character | キャラクター | Character | 캐릭터 | MascotPickerActivity |

- Shared header (new AppearanceChrome helper): a 56 dp bar with a 48 dp back button (ic_v293_chevron_left, description 戻る / Back / 뒤로), a 26 sp Bold title, a one-line 13 sp MUTED subtitle, and the five-segment tab strip below (horizontal scroll, 36 dp chips). The current segment uses an ACCENT_2 fill. Tapping another segment finishes this screen and opens the next with no animation, so the back stack does not grow.
- One commit model for all five: choose (the card shows the selected stroke and label), then the sticky apply bar at the bottom: 72 dp, summary (選択中: name) on the left, primary button on the right. Back without applying discards the choice. Design sets are the exception, with explicit actions (section 10).
- UI style and character change from tap-to-apply to choose-then-apply, matching motif and color. See decision D1.
- Badges: 使用中 = saved, 選択中 = pending. Retire 適用中.
- Settings 外観 section: five rows in tab order. Label 16 sp Bold, current value 13 sp MUTED, chevron 16 dp.

## 10. Design-set cards: truthful mini home

- Card: on the left, a mini home preview 104 x 176 dp. On the right, the name (17 sp Bold), the components line (11 sp MUTED, two lines at most), the description (12 sp MUTED, two lines at most), and four palette dots of 12 dp. Single column; the existing expand behaviour stays (decision D2).
- SetHomePreviewView draws from a snapshot (uiProfile, visualDesign, theme, mascot) built from the preset. It uses the same tokens and the same HomeMetrics constants as MainActivity. It never reads the mutable Ui statics, so each preview shows its own set rather than the current palette.
- Content, top to bottom: page BG with motif; overline bar in ACCENT_2; title bar; hero in the HERO gradient with time, meta line and a 40 dp character at the top end; two alarm rows in the set's layout (list, tiles or timeline) with its card mode and treatment; bottom nav in its nav mode (pill, labels or compact) with NAV_HIGHLIGHT; add button in ACCENT_2.
- Full set: filled ACCENT_2 button, セット全体を適用. Applies all four parts.
- Parts: a section パーツだけ適用 when expanded (button パーツを選ぶ when collapsed). Four rows, each with a small thumbnail of its part and a label. A row shows 現在と同じ when the current selection already matches. Tapping a row applies that part only and shows a status line on the card: 「{part}」を適用しました.
- Active set: 使用中 pill. The current-combination box at the top shows the set name, or カスタム when nothing matches.

## 11. Copy (add to I18n.java with put(ja, en, ko))

| Key | ja | en | ko |
|---|---|---|---|
| apply bar summary | 選択中: {0} | Selected: {0} | 선택: {0} |
| apply motif | 模様・文字を適用 | Apply motif & type | 무늬·글꼴 적용 |
| apply color | 色・モードを適用 | Apply color & mode | 색상·모드 적용 |
| apply UI | UIスタイルを適用 | Apply UI style | UI 스타일 적용 |
| apply character | キャラクターを適用 | Apply character | 캐릭터 적용 |
| full set | セット全体を適用 | Apply full set | 세트 전체 적용 |
| parts heading | パーツだけ適用 | Apply one part | 구성 요소만 적용 |
| part UI | UIだけ | UI only | UI만 |
| part motif | 模様・文字だけ | Motif & type only | 무늬·글꼴만 |
| part color | 色・モードだけ | Color & mode only | 색상·모드만 |
| part character | キャラクターだけ | Character only | 캐릭터만 |
| same as now | 現在と同じ | Same as now | 현재와 같음 |
| part applied | 「{0}」を適用しました | Applied: {0} | {0} 적용됨 |
| in use | 使用中 | In use | 사용 중 |
| pending | 選択中 | Selected | 선택됨 |
| empty title | 最初のアラームを追加しましょう | Add your first alarm | 첫 알람을 추가해 보세요 |
| empty body | 時刻と曜日を決めると、ここに一覧で表示されます。 | Set a time and days to see it listed here. | 시간과 요일을 정하면 여기에 목록으로 표시됩니다. |

Renamed: 模様・文字 replaces デザイン on the pattern screen. 色・モード replaces デザイン on the color screen. Settings rows use the names in section 9.

## 12. Ownership proposal (no file has two owners)

- Motif and tokens: VisualDesigns.java, Ui.java (drawCardTreatment), AppThemes.java only if the lunar retune is approved.
- Characters: MascotArtView.java and a small asset helper.
- Home: MainActivity.java, and a new HomeMetrics.java for shared constants.
- Studio: new AppearanceChrome.java; the five picker activities; new SetHomePreviewView.java; DesignSets.java (snapshot helper only, no ID changes).
- Resources and copy: widget_bg.xml and the world-clock widget layouts (verify the rest), styles.xml, values/wakeguard_v101.xml, values-v31/ignido_splash.xml, a new values-night for window and splash colors, I18n.java, the SystemSettingsActivity.java labels, app/build.gradle.kts (versionCode 204, versionName 2.13.0).
- Review: diff against the untouched copy, run the checks in section 13, then assembleDebug in the clean copy. No publish and no push.

## 13. Acceptance checks

- One motif implementation. The pattern branch of Ui.drawPattern is gone.
- No warm hex in surface resources: ignido_ember, #C2362B, #FFC857, #FF7A00, #FF3A14, #FBF5EE, #FFFCF8, #FFD28D (brand icon files excepted, if justified).
- The four premium PNG ids are referenced. No vector mascot drawing path remains.
- Every new or changed string has ja, en and ko entries.
- Saved keys and the 12 preset IDs are unchanged.
- Home hero is at most 136 dp at 360 dp width (measured on a screenshot).
- Character views are not clickable and do not overlap switches or buttons.
- Blue family: hue check for every token (script, or a reviewer table).
- versionCode 204, versionName 2.13.0.
- assembleDebug succeeds.

## 14. Decisions to agree (reply by number)

- D1: One commit model (choose, then apply) for UI style and character too. Recommended. Alternative: keep their tap-to-apply and share only the header and tabs.
- D2: Set cards stay a single column with mini-home thumbnails, keeping the expand flow. Recommended over a two-column grid.
- D3: Motifs only on the page background and inside the hero, never on list cards. Recommended.
- D4: Optional lunar retune to 5B6FC4 in light mode. It changes a palette's look, so it needs approval.
- D5: Keep dawn-ink (warm) as the default for compatibility. A blue default is a product decision for the user.

## 15. Limitations

- Premium PNG edge fringe needs a look on a device, on light surfaces. This agent did not run a device.
- The AppThemes pink token (rose) is computed for every palette but not referenced outside Ui.java (checked by grep), so it has no visible effect today. Keep it out of blue-family surfaces if it is used later.
- Screenshots, layout measurements and the assembleDebug result belong to the reviewer and build steps.
