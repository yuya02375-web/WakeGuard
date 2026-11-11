package jp.wakeguard.alarm;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Independent, non-palette visual treatments for the app. */
public final class VisualDesigns {
    private static final String PREF = "wake_guard";
    private static final String KEY = "app_visual_design_id";
    private static final String DEFAULT_ID = "paper-craft";

    private VisualDesigns() {}

    private static Design d(String id, String category, String[] names, String[] descriptions,
                            String pattern, String fontFamily, String cardTreatment,
                            float letterSpacing, float cornerScale) {
        return new Design(id, category, names, descriptions, pattern, fontFamily,
                cardTreatment, letterSpacing, cornerScale);
    }

    private static String[] s(String ja, String en, String ko) { return new String[]{ja, en, ko}; }

    private static final List<Design> DESIGNS = Collections.unmodifiableList(Arrays.asList(new Design[]{
        d("paper-craft", "paper", s("紙細工", "Paper Craft", "종이 공예"), s("折り目と素朴な罫線が広がる、手仕事の紙の質感。", "Folded-paper geometry and quiet rules give the interface a handmade feel.", "접힌 종이의 선과 차분한 줄무늬로 손으로 만든 느낌을 더합니다."), "rules", "serif", "double-border", 0.025f, 0.88f),
        d("origami-atlas", "paper", s("折り紙アトラス", "Origami Atlas", "종이접기 지도"), s("斜めの折り筋と細い文字で、折り紙の図解帳のように。", "Diagonal folds and fine lettering echo an illustrated origami atlas.", "대각선 접힘과 가는 글꼴로 종이접기 도감을 표현합니다."), "diagonal", "sans", "wire", 0.035f, 0.42f),
        d("field-notes", "paper", s("野帳ノート", "Field Notes", "현장 노트"), s("小さな点と余白を生かした、観察記録のノート。", "Dot-grid stationery and generous margins make a calm field notebook.", "점 격자와 넉넉한 여백을 살린 관찰 기록 노트입니다."), "dots", "mono", "outline", 0.012f, 0.52f),
        d("ink-etching", "paper", s("銅版エッチング", "Ink Etching", "동판 에칭"), s("版画の細線と引き締まった活字で、深い陰影を演出。", "Engraved-looking contour lines and compact type create print-shop depth.", "판화 같은 윤곽선과 단정한 활자로 깊은 느낌을 냅니다."), "contour", "serif", "double-border", 0.018f, 0.34f),
        d("linen-stitch", "paper", s("リネンステッチ", "Linen Stitch", "리넨 스티치"), s("布目のような交差模様とステッチ線で、柔らかな手触りに。", "Crossed threads and stitched rules bring a soft textile character.", "실이 교차하는 무늬와 바느질선을 넣은 부드러운 직물 느낌입니다."), "stitch", "serif", "dashed", 0.028f, 0.72f),
        d("herbarium", "nature", s("植物標本室", "Herbarium", "식물 표본실"), s("植物のシルエットとスケッチ線が、静かな標本帳をつくります。", "Botanical silhouettes and sketch marks turn the screen into a quiet herbarium.", "식물 실루엣과 스케치 선이 고요한 식물 표본실을 만듭니다."), "botanical", "serif", "outline", 0.022f, 0.58f),
        d("petal-orbit", "nature", s("花びらの軌道", "Petal Orbit", "꽃잎 궤도"), s("花びらのような点模様と大きな余白で、軽やかに。", "Petal-like marks float across open space for a light, airy mood.", "꽃잎 같은 무늬가 넓은 여백에 흩어져 가벼운 분위기를 냅니다."), "botanical", "sans", "raised", 0.045f, 1.08f),
        d("moonlit-stars", "nature", s("月夜の星図", "Moonlit Star Map", "달빛 별자리"), s("夜空の点描と結び目のある線で、星図を思わせます。", "A field of stars linked by fine paths recalls a night-sky chart.", "별점과 가는 연결선으로 밤하늘의 별자리를 표현합니다."), "mosaic", "mono", "wire", 0.03f, 0.48f),
        d("aurora-contour", "nature", s("オーロラ地形", "Aurora Contours", "오로라 등고선"), s("流れる等高線とゆったりした文字が、静かな景色を描きます。", "Flowing contour paths and relaxed type suggest an illuminated landscape.", "흐르는 등고선과 여유 있는 글꼴로 빛나는 풍경을 그립니다."), "contour", "sans", "flat", 0.04f, 1.18f),
        d("ocean-current", "nature", s("海流図", "Ocean Currents", "해류 지도"), s("波の曲線と水平のリズムで、海の流れを感じるデザイン。", "Wave lines and horizontal rhythm bring the movement of ocean currents.", "파도 곡선과 수평 리듬으로 바다의 흐름을 담았습니다."), "waves", "sans", "outline", 0.018f, 0.76f),
        d("topographic-map", "nature", s("地形図", "Topographic Map", "지형도"), s("重なる等高線が、山道の地図のような表情をつくります。", "Nested contour paths lend the interface the character of a hiking map.", "겹치는 등고선이 산길 지도 같은 표정을 만듭니다."), "contour", "mono", "double-border", 0.012f, 0.38f),
        d("tidepool", "nature", s("潮だまり", "Tide Pool", "바위 웅덩이"), s("大小のモザイク粒子で、波打ち際の小さな世界を表現。", "A mosaic of small forms evokes the miniature world of a tide pool.", "크고 작은 모자이크 무늬로 조수 웅덩이의 작은 세계를 표현합니다."), "mosaic", "sans", "raised", 0.055f, 1.24f),
        d("halftone-pop", "retro", s("ハーフトーン・ポップ", "Halftone Pop", "하프톤 팝"), s("網点と太い見出しで、古いコミックの印刷感を再現。", "Halftone dots and bold display lettering recall vintage comic printing.", "망점과 굵은 제목 글꼴로 오래된 만화 인쇄 느낌을 냅니다."), "halftone", "condensed", "double-border", 0.06f, 0.28f),
        d("scanline-vhs", "retro", s("ビデオテープ", "VHS Scanline", "비디오테이프"), s("走査線と詰まった文字が、アナログ映像の雰囲気を添えます。", "Scanlines and condensed type bring the texture of analog video.", "주사선과 좁은 글꼴로 아날로그 영상의 분위기를 더합니다."), "scanline", "mono", "wire", 0.018f, 0.22f),
        d("vapor-grid", "retro", s("ネオン格子", "Vapor Grid", "네온 격자"), s("遠近感のある格子と広い字間で、夜のデジタル風景に。", "A receding grid and airy lettering suggest a retro-futurist horizon.", "멀어지는 격자와 넓은 자간으로 복고풍 미래 풍경을 만듭니다."), "grid", "sans", "raised", 0.075f, 0.9f),
        d("cassette-deck", "retro", s("カセットデッキ", "Cassette Deck", "카세트 데크"), s("録音メーターの線と機械的な文字を組み合わせました。", "Meter marks and mechanical lettering look like a well-loved cassette deck.", "레벨 미터 선과 기계적인 글꼴을 카세트 데크처럼 조합했습니다."), "film", "mono", "dashed", 0.014f, 0.36f),
        d("blueprint", "tech", s("青焼き設計図", "Blueprint", "청사진"), s("方眼と細い寸法線で、設計図のように整然と見せます。", "Measured grid lines and precise type make a tidy engineering blueprint.", "정교한 격자와 글꼴로 설계도처럼 정돈된 화면을 만듭니다."), "grid", "mono", "wire", 0.022f, 0.18f),
        d("circuit-trace", "tech", s("回路トレース", "Circuit Trace", "회로 트레이스"), s("直角に走る配線と端子模様が、回路基板を思わせます。", "Angular traces and connection nodes mimic a printed circuit board.", "각진 배선과 접점 무늬로 회로 기판을 표현합니다."), "circuit", "mono", "double-border", 0.028f, 0.3f),
        d("space-console", "tech", s("宇宙管制室", "Space Console", "우주 관제실"), s("放射状の計器線と端正な字間で、宇宙船の操作盤へ。", "Radial instrument marks and measured typography evoke a spacecraft console.", "방사형 계기 선과 정돈된 글꼴로 우주선 조종석을 표현합니다."), "radial", "mono", "wire", 0.035f, 0.42f),
        d("terminal-green", "tech", s("ターミナル", "Terminal", "터미널"), s("行番号のような水平線と等幅文字で、端末画面の雰囲気に。", "Monospaced type and quiet rules create a focused terminal aesthetic.", "고정폭 글꼴과 차분한 줄무늬로 집중하기 좋은 터미널 느낌을 냅니다."), "rules", "mono", "flat", 0.01f, 0.12f),
        d("pixel-arcade", "tech", s("ピクセルアーケード", "Pixel Arcade", "픽셀 아케이드"), s("小さなタイル模様と角ばった文字で、ゲーム画面のように。", "Blocky mosaic marks and compact lettering give an arcade-screen feel.", "블록형 모자이크와 짧은 글꼴로 아케이드 화면을 표현합니다."), "mosaic", "mono", "double-border", 0.08f, 0.16f),
        d("editorial-ink", "editorial", s("エディトリアル・インク", "Editorial Ink", "에디토리얼 잉크"), s("縦横の罫線とセリフ体で、読みやすい雑誌の誌面に。", "Editorial rules and serif typography make a composed, readable page.", "편집용 선과 세리프 글꼴로 읽기 좋은 잡지 지면을 만듭니다."), "rules", "serif", "flat", 0.012f, 0.24f),
        d("magazine-cover", "editorial", s("雑誌の表紙", "Magazine Cover", "잡지 표지"), s("大胆な見出しと網点のアクセントで、表紙のような存在感。", "Large display lettering and halftone texture make a confident cover layout.", "큰 제목과 망점 질감으로 잡지 표지 같은 존재감을 냅니다."), "halftone", "condensed", "raised", 0.055f, 0.74f),
        d("swiss-poster", "editorial", s("スイス・ポスター", "Swiss Poster", "스위스 포스터"), s("グリッドと整った字間が、秩序あるポスターの印象をつくります。", "A strict grid and deliberate spacing create a clear poster-like composition.", "정확한 격자와 의도적인 자간으로 명료한 포스터 구성을 만듭니다."), "grid", "sans", "flat", 0.045f, 0.08f),
        d("library-classic", "editorial", s("古書室", "Library Classic", "고전 도서관"), s("繊細なセリフ体と控えめな枠で、落ち着いた本の装丁に。", "Refined serif lettering and a restrained frame feel like a classic book.", "정제된 세리프 글꼴과 절제된 테두리로 고전 장정 느낌을 냅니다."), "none", "serif", "double-border", 0.02f, 0.66f),
        d("newspaper-archive", "editorial", s("新聞アーカイブ", "Newspaper Archive", "신문 아카이브"), s("細かな行と引き締まった文字が、新聞の資料欄を思わせます。", "Tight leading and archival rules suggest a well-kept newspaper index.", "촘촘한 행간과 기록용 선으로 신문 자료실을 표현합니다."), "rules", "serif", "outline", 0.005f, 0.1f),
        d("concrete-brutal", "editorial", s("コンクリート・ブルータル", "Concrete Brutal", "콘크리트 브루탈"), s("直線、角、太い文字だけで構成する、力強い無骨さ。", "Hard edges, wire frames, and condensed type make an unapologetic statement.", "직선과 각진 테두리, 굵은 글꼴로 거침없는 인상을 줍니다."), "diagonal", "condensed", "wire", 0.07f, 0.06f),
        d("bubblegum-soft", "playful", s("バブルガム・ソフト", "Bubblegum Soft", "버블검 소프트"), s("丸いカードとゆったりした字間で、やさしい遊び心を。", "Rounded surfaces and open spacing give the interface a friendly bounce.", "둥근 카드와 넓은 자간으로 친근하고 경쾌한 느낌을 줍니다."), "dots", "sans", "raised", 0.06f, 1.36f),
        d("geometric-tiles", "playful", s("幾何タイル", "Geometric Tiles", "기하 타일"), s("小さなタイルの反復で、整然とした遊び心を加えます。", "Repeated mosaic blocks add playful geometry without crowding the screen.", "반복되는 모자이크 타일로 화면을 복잡하게 하지 않고 재미를 더합니다."), "mosaic", "sans", "double-border", 0.032f, 0.48f),
        d("memphis-confetti", "playful", s("メンフィス・コンフェッティ", "Memphis Confetti", "멤피스 콘페티"), s("ばらまいた粒と曲線が、軽快なデザインポスターをつくります。", "Loose mosaic flecks and lively curves make a playful design poster.", "흩어진 점과 곡선으로 경쾌한 디자인 포스터를 만듭니다."), "mosaic", "condensed", "dashed", 0.08f, 0.92f),
        d("terrazzo", "playful", s("テラゾー", "Terrazzo", "테라초"), s("大小のかけらを散らした、石材テラゾーのリズム。", "Scattered mosaic chips bring the rhythm of terrazzo stone.", "크고 작은 조각 무늬로 테라초의 리듬을 담았습니다."), "mosaic", "sans", "outline", 0.025f, 0.7f),
        d("checker-club", "playful", s("チェッカークラブ", "Checker Club", "체커 클럽"), s("チェッカー模様と詰まった文字で、レトロなクラブ感を。", "Alternating tiles and compact lettering give a lively club-poster mood.", "체커 타일과 짧은 글꼴로 활기찬 클럽 포스터 분위기를 냅니다."), "grid", "condensed", "double-border", 0.05f, 0.3f),
        d("transit-barcode", "tech", s("トランジット・コード", "Transit Code", "트랜짓 코드"), s("駅の案内板を思わせる線と等幅数字で、移動のリズムを。", "Route-like lines and monospaced numerals recall a modern transit board.", "노선도 같은 선과 고정폭 숫자로 이동의 리듬을 담았습니다."), "film", "mono", "wire", 0.026f, 0.2f),
        d("tapestry-weave", "paper", s("タペストリー", "Tapestry Weave", "태피스트리"), s("交互に重なる線模様とセリフ体が、織物の奥行きを演出。", "Alternating thread lines and serif lettering give a woven tapestry depth.", "엇갈리는 실 무늬와 세리프 글꼴로 직물의 깊이를 표현합니다."), "stitch", "serif", "dashed", 0.034f, 1.02f),
        d("ceramic-glaze", "nature", s("陶器の釉薬", "Ceramic Glaze", "도자기 유약"), s("有機的な植物模様とふっくらしたカードで、釉薬の柔らかさを。", "Organic botanical marks and rounded surfaces suggest glazed ceramic.", "유기적인 식물 무늬와 둥근 표면으로 유약의 부드러움을 표현합니다."), "botanical", "serif", "raised", 0.042f, 1.28f),
        d("solar-orbit", "tech", s("ソーラーオービット", "Solar Orbit", "솔라 오비트"), s("中心から広がる放射線で、天体模型のような構成に。", "Radial paths and precise labels create the feel of a solar-system model.", "중심에서 뻗는 방사선과 정밀한 글꼴로 천체 모형을 표현합니다."), "radial", "sans", "outline", 0.05f, 0.84f)
    }));

    public static List<Design> all() { return DESIGNS; }

    public static Design get(String id) {
        if (id != null) for (Design design : DESIGNS) if (design.id.equals(id)) return design;
        return DESIGNS.get(0);
    }

    public static String id(Context context) {
        String saved = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, DEFAULT_ID);
        return get(saved).id;
    }

    public static Design current(Context context) { return get(id(context)); }

    public static void select(Context context, String id) {
        Design design = get(id);
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, design.id).apply();
    }

    public static Typeface typeface(Context context, Design design, boolean bold) {
        int style = bold ? Typeface.BOLD : Typeface.NORMAL;
        String family = design == null ? "sans" : design.fontFamily;
        if ("serif".equals(family)) return Typeface.create(Typeface.SERIF, style);
        if ("mono".equals(family)) return Typeface.create(Typeface.MONOSPACE, style);
        if ("condensed".equals(family)) return Typeface.create("sans-serif-condensed", style);
        return Typeface.create(Typeface.SANS_SERIF, style);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /** Uniform motif ink on pages, heroes and samples, in both light and dark mode. */
    public static final int PATTERN_ALPHA = 0x24;

    public static int patternColor(int accent) { return withAlpha(accent, PATTERN_ALPHA); }

    /** Density is pixels per dp in the caller's canvas, including any preview scale. */
    public static void drawPattern(Canvas canvas, String pattern, RectF bounds, int accent, float density) {
        Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        ink.setColor(accent);
        drawPattern(canvas, pattern, bounds, ink, density);
    }

    /**
     * The single motif renderer. Source supplies the palette's ACCENT RGB only;
     * ink alpha and geometry are fixed here so a sample cannot exaggerate a motif.
     * Does not read Ui state, so preset snapshots remain independent of live settings.
     */
    public static void drawPattern(Canvas canvas, String pattern, RectF bounds, Paint source, float density) {
        if (canvas == null || bounds == null || bounds.isEmpty() || source == null
                || pattern == null || "none".equals(pattern)) return;
        float unit = Float.isNaN(density) || Float.isInfinite(density) || density <= 0f ? 1f : density;
        float step = cellDp(pattern) * unit;
        Paint p = new Paint(source);
        p.setAntiAlias(true);
        p.setShader(null);
        p.setPathEffect(null);
        p.setColor(patternColor(source.getColor()));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(.75f * unit);
        p.setStrokeCap(Paint.Cap.BUTT);
        float left = bounds.left, top = bounds.top, right = bounds.right, bottom = bounds.bottom;
        int save = canvas.save();
        canvas.clipRect(bounds);
        if ("rules".equals(pattern)) {
            for (float y = top + step; y < bottom; y += step) canvas.drawLine(left, y, right, y, p);
        } else if ("grid".equals(pattern)) {
            for (float x = left + step; x < right; x += step) canvas.drawLine(x, top, x, bottom, p);
            for (float y = top + step; y < bottom; y += step) canvas.drawLine(left, y, right, y, p);
        } else if ("dots".equals(pattern)) {
            p.setStyle(Paint.Style.FILL);
            for (float y = top + step / 2; y < bottom; y += step)
                for (float x = left + step / 2; x < right; x += step)
                    canvas.drawCircle(x, y, .9f * unit, p);
        } else if ("diagonal".equals(pattern)) {
            for (float x = left - bounds.height(); x < right; x += step)
                canvas.drawLine(x, bottom, x + bounds.height(), top, p);
        } else if ("scanline".equals(pattern)) {
            p.setStrokeWidth(.6f * unit);
            for (float y = top + step / 2; y < bottom; y += step) canvas.drawLine(left, y, right, y, p);
        } else if ("contour".equals(pattern)) {
            for (float inset = 0; inset < Math.min(bounds.width(), bounds.height()) / 2; inset += step) {
                RectF oval = new RectF(left + inset, top + inset * .6f, right - inset, bottom - inset * .6f);
                if (!oval.isEmpty()) canvas.drawOval(oval, p);
            }
            Path path = new Path();
            path.moveTo(left, bounds.centerY());
            path.cubicTo(bounds.centerX() - 12f * unit, top,
                    bounds.centerX() + 12f * unit, bottom, right, bounds.centerY());
            canvas.drawPath(path, p);
        } else if ("radial".equals(pattern)) {
            float cx = bounds.centerX(), cy = bounds.centerY();
            float radius = (float)Math.hypot(bounds.width(), bounds.height());
            for (int i = 0; i < 16; i++) {
                double angle = Math.PI * 2 * i / 16;
                canvas.drawLine(cx, cy, cx + (float)Math.cos(angle) * radius,
                        cy + (float)Math.sin(angle) * radius, p);
            }
            for (int i = 1; i < 4; i++) canvas.drawCircle(cx, cy, step * i, p);
        } else if ("halftone".equals(pattern)) {
            p.setStyle(Paint.Style.FILL);
            int row = 0;
            for (float y = top + step / 2; y < bottom; y += step, row++) {
                int column = 0;
                for (float x = left + step / 2; x < right; x += step, column++)
                    canvas.drawCircle(x, y, ((column + row) % 2 == 0 ? .6f : 1.2f) * unit, p);
            }
        } else if ("circuit".equals(pattern)) {
            for (float y = top + step; y < bottom; y += step * 2) {
                for (float x = left; x < right; x += step * 4) {
                    Path path = new Path();
                    path.moveTo(x, y); path.lineTo(x + step, y);
                    path.lineTo(x + step * 2, y - step / 2);
                    path.lineTo(x + step * 3, y - step / 2);
                    canvas.drawPath(path, p);
                    canvas.drawCircle(x + step * 3, y - step / 2, 1.5f * unit, p);
                }
            }
        } else if ("stitch".equals(pattern)) {
            p.setStrokeWidth(.9f * unit);
            p.setPathEffect(new android.graphics.DashPathEffect(new float[]{3f * unit, 3f * unit}, 0));
            for (float y = top + step; y < bottom; y += step) canvas.drawLine(left, y, right, y, p);
        } else if ("waves".equals(pattern)) {
            p.setStrokeWidth(.9f * unit);
            for (float base = top + step; base < bottom + step; base += step) {
                Path path = new Path(); path.moveTo(left, base);
                for (float x = left; x <= right + 4f * unit; x += 4f * unit)
                    path.lineTo(x, base + (float)Math.sin((x - left) / (8f * unit)) * 2.3f * unit);
                canvas.drawPath(path, p);
            }
        } else if ("film".equals(pattern)) {
            for (float x = left + step / 2; x < right; x += step) {
                canvas.drawLine(x, top, x, bottom, p);
                p.setStyle(Paint.Style.FILL);
                for (float y = top + step / 2; y < bottom; y += step)
                    canvas.drawCircle(x, y, 1.2f * unit, p);
                p.setStyle(Paint.Style.STROKE);
            }
        } else if ("botanical".equals(pattern)) {
            p.setStrokeWidth(.9f * unit);
            for (float x = left + step; x < right; x += step * 2) {
                Path stem = new Path();
                stem.moveTo(x, bottom); stem.quadTo(x - step, bounds.centerY(), x, top);
                canvas.drawPath(stem, p);
                for (float y = top + step; y < bottom; y += step) {
                    RectF leaf = new RectF(x - step * .85f, y - step * .3f, x, y + step * .3f);
                    canvas.drawOval(leaf, p);
                    leaf.set(x, y - step * .3f, x + step * .85f, y + step * .3f);
                    canvas.drawOval(leaf, p);
                }
            }
        } else if ("mosaic".equals(pattern)) {
            p.setStyle(Paint.Style.FILL);
            int row = 0;
            for (float y = top + step / 2; y < bottom; y += step, row++) {
                int column = 0;
                for (float x = left + step / 2; x < right; x += step, column++) {
                    int seed = (column * 13 + row * 7 + column * row * 3) % 11;
                    if (seed > 5) continue;
                    float radius = (1.2f + seed % 3) * unit;
                    if (seed % 2 == 0) canvas.drawCircle(x, y, radius, p);
                    else canvas.drawRect(x - radius, y - radius, x + radius, y + radius, p);
                }
            }
        }
        canvas.restoreToCount(save);
    }

    /** Physical repeat sizes; do not apply the UI density profile to motif geometry. */
    public static float cellDp(String pattern) {
        if ("scanline".equals(pattern)) return 5f;
        if ("halftone".equals(pattern)) return 8f;
        if ("stitch".equals(pattern)) return 12f;
        if ("grid".equals(pattern) || "circuit".equals(pattern)) return 16f;
        if ("contour".equals(pattern) || "radial".equals(pattern)
                || "film".equals(pattern) || "mosaic".equals(pattern)) return 18f;
        if ("botanical".equals(pattern)) return 24f;
        return 14f; // rules, dots, diagonal, waves
    }

    public static Drawable patternDrawable(Context context, Design design, int color) {
        float density = context.getResources().getDisplayMetrics().density;
        return new PatternDrawable(design == null ? "none" : design.pattern, color, density);
    }

    public static final class Design {
        public final String id;
        public final String nameJa, nameEn, nameKo;
        public final String descriptionJa, descriptionEn, descriptionKo;
        public final String pattern, fontFamily, cardTreatment;
        public final float letterSpacing, cornerScale;
        private final String category;

        private Design(String id, String category, String[] names, String[] descriptions,
                       String pattern, String fontFamily, String cardTreatment,
                       float letterSpacing, float cornerScale) {
            this.id = id; this.category = category;
            this.nameJa = names[0]; this.nameEn = names[1]; this.nameKo = names[2];
            this.descriptionJa = descriptions[0]; this.descriptionEn = descriptions[1]; this.descriptionKo = descriptions[2];
            this.pattern = pattern; this.fontFamily = fontFamily; this.cardTreatment = cardTreatment;
            this.letterSpacing = letterSpacing; this.cornerScale = cornerScale;
        }
        public String label(Context context) {
            String lang = I18n.language(context);
            return "en".equals(lang) ? nameEn : "ko".equals(lang) ? nameKo : nameJa;
        }
        public String description(Context context) {
            String lang = I18n.language(context);
            return "en".equals(lang) ? descriptionEn : "ko".equals(lang) ? descriptionKo : descriptionJa;
        }
        public String categoryId() { return category; }
    }

    private static final class PatternDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String pattern;
        private final float density;
        private int alpha = 255;
        PatternDrawable(String pattern, int color, float density) {
            this.pattern = pattern; this.density = density; paint.setColor(patternColor(color));
        }
        @Override public void draw(Canvas canvas) {
            if (alpha == 0) return;
            Rect b = getBounds();
            int save = alpha == 255 ? canvas.save() : canvas.saveLayerAlpha(new RectF(b), alpha);
            drawPattern(canvas, pattern, new RectF(b), paint, density);
            canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int alpha) { this.alpha = Math.max(0, Math.min(255, alpha)); invalidateSelf(); }
        @Override public int getAlpha() { return alpha; }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
