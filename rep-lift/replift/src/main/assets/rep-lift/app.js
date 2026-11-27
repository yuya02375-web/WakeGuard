"use strict";

const T = globalThis.Training;
const R = globalThis.RunTracker;
const nativeApp = globalThis.RepLiftNative;
const $ = (selector) => document.querySelector(selector);
const root = $("#content");
const dialog = $("#modal");
const storageKey = "rep-lift-data-v1";
const iconPaths = {
  today: '<rect x="3.5" y="3.5" width="7" height="7" rx="1.4"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.4"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.4"/><rect x="13.5" y="13.5" width="7" height="7" rx="1.4"/>',
  train: '<path d="M6 7v10M3 9v6m15-8v10m3-8v6M6 12h12"/>',
  run: '<circle cx="15" cy="4.5" r="2"/><path d="m6 10 4-2 4 2 2 3h4m-6-3-3 6 4 2 1 3m-5-5-4 4H3"/>',
  history: '<path d="M4 20h16M6 16v-4m6 4V8m6 8V4"/>',
  settings: '<circle cx="12" cy="8" r="3"/><path d="M5 20v-2a4 4 0 0 1 4-4h6a4 4 0 0 1 4 4v2"/>',
  calendar: '<rect x="4" y="5" width="16" height="16" rx="2"/><path d="M8 3v4m8-4v4M4 10h16m-12 4h2m4 0h2m-8 3h2"/>',
  bolt: '<path d="m13 2-8 12h6l-1 8 9-12h-6z"/>',
  clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7v5l3 2"/>',
  layers: '<path d="m12 4 9 5-9 5-9-5 9-5Zm-9 9 9 5 9-5M3 17l9 5 9-5"/>',
  play: '<path d="m9 5 11 7-11 7z" fill="currentColor" stroke-width="0"/>',
  trophy: '<path d="M8 4h8v6a4 4 0 0 1-8 0V4Zm0 1H4v3a4 4 0 0 0 4 4m8-7h4v3a4 4 0 0 1-4 4m-4 2v5m-4 1h8"/>',
  add: '<path d="M12 5v14M5 12h14"/>',
  minus: '<path d="M5 12h14"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
  close: '<path d="m6 6 12 12M18 6 6 18"/>',
  pin: '<path d="M19 10c0 5-7 10-7 10S5 15 5 10a7 7 0 1 1 14 0Z"/><circle cx="12" cy="10" r="2.4"/>',
  edit: '<path d="m4 16-1 5 5-1L20 8l-4-4L4 16Zm10-10 4 4"/>',
  shield: '<path d="M12 3 20 6v5c0 5-4 9-8 10-4-1-8-5-8-10V6l8-3Z"/><path d="m8.5 12 2.3 2.3 4.7-5"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5m0-8h.01"/>',
  refresh: '<path d="M20 8a8 8 0 1 0-.5 9M20 4v5h-5"/>',
  download: '<path d="M12 3v12m-4-4 4 4 4-4M4 17v4h16v-4"/>',
  search: '<circle cx="10.5" cy="10.5" r="5.5"/><path d="m15 15 5 5"/>',
};
const icon = (name) => `<svg class="icon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${iconPaths[name] ?? iconPaths.train}</svg>`;
const escape = (value) => String(value ?? "").replace(/[&<>"']/g, (char) => (
  { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]
));
const formatDate = (value, options = { month: "long", day: "numeric", weekday: "short" }) =>
  new Intl.DateTimeFormat("ja-JP", options).format(new Date(value));
const newId = () => globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;
const unit = (exercise) => exercise.unit === "sec" ? "秒" : "回";
const localDay = (value) => {
  const d = new Date(value);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
};
const number = (value, maximumFractionDigits = 1) => new Intl.NumberFormat("ja-JP", { maximumFractionDigits }).format(value);
const nav = [
  { id: "today", label: "今日", icon: "today" },
  { id: "train", label: "筋トレ", icon: "train" },
  { id: "run", label: "ラン", icon: "run" },
  { id: "history", label: "記録", icon: "history" },
  { id: "settings", label: "設定", icon: "settings" },
];
const equipmentNames = { body: "器具なし", dumbbell: "ダンベル", gym: "ジム" };
const levelNames = { beginner: "初心者", regular: "経験あり", advanced: "上級者" };
const goalNames = { habit: "習慣をつくる", strength: "筋力をつける", endurance: "持久力を上げる" };
const readinessNames = { light: "軽め", normal: "いつも通り", strong: "調子がいい" };

function readData() {
  const defaults = { profile: { ...T.defaults }, configured: false, workouts: [], runs: [], draft: null };
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey) ?? "null");
    if (!saved || !Array.isArray(saved.workouts) || !Array.isArray(saved.runs)) return defaults;
    return { ...defaults, ...saved, profile: { ...defaults.profile, ...saved.profile } };
  } catch {
    return defaults;
  }
}

let data = readData();
let readiness = "normal";
let historyFilter = "all";
let historyTab = "activity";
let libraryQuery = "";
let view = "today";
let toastTimer;
let result = null;
let runState;
let gpsMode = true;
let sessionTick = 0;
let activeMilliseconds = 0;
let keepRunScreen = false;
const tracker = new R({ onChange: (value) => {
  runState = value;
  if (view === "run") updateRun();
  const active = value.phase === "running" && value.source === "gps";
  if (active !== keepRunScreen) {
    keepRunScreen = active;
    nativeApp?.keepRunScreenOn(active);
  }
} });
runState = tracker.snapshot();
globalThis.repLiftActivityDays = () => [...new Set([...data.workouts, ...data.runs]
  .filter(({ date }) => Number.isFinite(new Date(date).getTime())).map(({ date }) => localDay(date)))].sort();

function saveData(next = data) {
  try {
    localStorage.setItem(storageKey, JSON.stringify(next));
    data = next;
    return true;
  } catch {
    notify("この端末に保存できません。空き容量を確認してください。");
    return false;
  }
}

function notify(message) {
  const toast = $(".toast");
  toast.textContent = message;
  toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { toast.hidden = true; }, 4200);
}

const titleBlock = (heading, summary, action = "") =>
  `<div class="page-intro"><div><h1>${heading}</h1><p>${summary}</p></div>${action}</div>`;
const button = (label, action, { kind = "secondary", glyph = "", extra = "" } = {}) =>
  `<button type="button" class="button ${kind}" data-action="${action}" ${extra}>${glyph ? icon(glyph) : ""}<span>${label}</span></button>`;
const linkButton = (label, page, glyph = "") => `<a class="button secondary" href="#${page}">${glyph ? icon(glyph) : ""}<span>${label}</span></a>`;
const chip = (label, glyph = "") => `<span class="chip">${glyph ? icon(glyph) : ""}${label}</span>`;
const section = (title, detail = "", action = "") =>
  `<div class="section-head"><div><h2>${title}</h2>${detail ? `<p>${detail}</p>` : ""}</div>${action}</div>`;
const empty = (glyph, title, description) =>
  `<div class="empty">${icon(glyph)}<strong>${title}</strong><p>${description}</p></div>`;
const quick = (label, detail, glyph, action, extra = "") =>
  `<button class="quick-action" type="button" data-action="${action}" ${extra}><span class="tile-icon quick-action-icon">${icon(glyph)}</span><span><strong>${label}</strong><small>${detail}</small></span></button>`;
const select = (name, label, options, current) =>
  `<label class="field"><span>${label}</span><select name="${name}">${Object.entries(options).map(([value, text]) =>
    `<option value="${value}" ${String(current) === value ? "selected" : ""}>${text}</option>`).join("")}</select></label>`;
const compactSeconds = (seconds) => seconds >= 60 ? `${Math.floor(seconds / 60)}分${seconds % 60 ? `${seconds % 60}秒` : ""}` : `${seconds}秒`;
const activeDraft = () => data.draft && data.draft.items.every((item) => T.catalog[item.id]) ? data.draft : null;

function shell() {
  const activeId = view === "session" ? "train" : view === "result" ? "history" : view;
  const links = nav.map(({ id, label, icon: glyph }) =>
    `<a class="nav-item ${id === activeId ? "active" : ""}" href="#${id}" ${id === activeId ? 'aria-current="page"' : ""}>${icon(glyph)}<span>${label}</span></a>`).join("");
  $(".side-nav").innerHTML = links;
  $(".bottom-nav").innerHTML = links;
  $(".sidebar-foot").innerHTML = `<span class="sidebar-foot-icon">${icon("shield")}</span><span><strong>ずっと無料</strong><small>記録はこの端末に保存</small></span>`;
  $(".topbar-date").innerHTML = `<a class="mobile-brand brand-lockup" href="#today" aria-label="IGNIDO REP LIFT ホーム"><span class="brand-mark" aria-hidden="true"><img src="icon-192.png" alt="" width="34" height="34"></span><span class="brand-copy"><strong class="brand-name">IGNIDO</strong><small class="brand-product">REP LIFT</small></span></a><span class="date-label">${icon("calendar")}${formatDate(Date.now())}</span>`;
  $(".topbar-actions").innerHTML = `<button class="icon-button profile-button" type="button" data-action="profile" aria-label="トレーニング設定">${icon("settings")}</button>`;
}

function weeklyCard() {
  const week = T.week(data.workouts, data.runs);
  const target = data.profile.days;
  const days = ["月", "火", "水", "木", "金", "土", "日"];
  const today = localDay(Date.now());
  return `<section class="week-card card">
    ${section("今週の目標", "", `<span class="section-icon">${icon("trophy")}</span>`)}
    <div class="week-core"><div class="week-ring" style="--progress:${Math.min(100, 100 * week.count / target)}%"><span><strong>${week.count}</strong><small> / ${target}日</small></span></div>
    <p>${week.count >= target ? "今週の目標を達成" : `あと <strong>${target - week.count}日</strong> で達成`}<small>${week.minutes}分の運動${week.distance > 0 ? ` · ${number(week.distance, 2)} km` : ""}</small></p></div>
    <div class="week-days" aria-label="今週の運動日">${week.sessions.map((day, index) =>
      `<div class="week-day ${day.count ? "done" : ""} ${day.date === today ? "current" : ""}" aria-label="${days[index]}曜日 ${day.count ? "運動あり" : "運動なし"}"><span>${days[index]}</span><b aria-hidden="true">${day.count ? icon("check") : "·"}</b></div>`).join("")}</div>
  </section>`;
}

function workoutList(items) {
  return items.map((item, index) => {
    const ex = T.catalog[item.id];
    return `<button type="button" class="exercise-row" data-action="detail" data-id="${ex.id}">
      <span class="exercise-index">${String(index + 1).padStart(2, "0")}</span><span class="exercise-name"><strong>${ex.name}</strong><small>${ex.group} · ${equipmentNames[ex.equipment]}</small></span>
      <span class="exercise-target">${item.target}${unit(ex)} × ${item.sets}${item.weight > 0 ? `<small>${number(item.weight)} kg</small>` : ""}</span>
    </button>`;
  }).join("");
}

function todayView() {
  const plan = T.plan(data.profile, readiness, data.workouts);
  const draft = activeDraft();
  const start = draft
    ? button("トレーニングを再開", "resume-session", { kind: "primary", glyph: "play" })
    : button("筋トレを始める", "start-plan", { kind: "primary", glyph: "play" });
  return `${titleBlock("今日のトレーニング", "器具と今日の調子に合わせたメニュー。",
    button(`調子：${readinessNames[readiness]}`, "readiness", { glyph: "bolt" }))}
    <div class="dashboard-grid">
      <article class="hero">
        <div class="hero-content"><p class="eyebrow"><span class="indicator"></span> 今日のおすすめ</p>
        <h2>${plan.title}</h2><p>${plan.description}</p>
        <div class="chips">${chip(`約${plan.minutes}分`, "clock")}${chip(`${plan.items.length}種目`, "layers")}${chip(equipmentNames[data.profile.equipment], "train")}</div>
        <div class="hero-actions">${start}${linkButton("ランを計測", "run", "run")}</div></div>
      </article>
      <section class="quick-card card">${section("1種目から記録")}
        <div class="quick-grid quick-actions">
          ${quick("腕立て伏せ", "上半身", "train", "single", 'data-id="pushup"')}
          ${quick("スクワット", "下半身", "bolt", "single", 'data-id="squat"')}
          ${quick("腹筋", "体幹", "layers", "single", 'data-id="crunch"')}
        </div>
      </section>
      ${weeklyCard()}
      <section class="workout-card card">${section("今日のメニュー", `セット間の休憩 ${compactSeconds(plan.rest)}`, button("メニュー調整", "profile", { kind: "ghost", glyph: "edit" }))}<div class="exercise-list">${workoutList(plan.items)}</div></section>
      ${globalThis.RepLiftLink?.card("today") ?? ""}
    </div>`;
}

function libraryItems() {
  const query = libraryQuery.trim().toLowerCase();
  const exercises = T.available(data.profile).map((exercise) => typeof exercise === "string" ? T.catalog[exercise] : exercise);
  const list = exercises.filter((exercise) => [exercise.name, exercise.group, equipmentNames[exercise.equipment]].some((item) => item.toLowerCase().includes(query)));
  if (!list.length) return empty("search", "見つかりませんでした", "種目名や鍛えたい部位を変えて検索してください。");
  return list.map((ex) => `<article class="library-item"><span class="tile-icon">${icon(ex.unit === "sec" ? "clock" : "train")}</span><div><h3>${ex.name}</h3><small>${ex.group} · ${equipmentNames[ex.equipment]}</small></div><div class="action-row">${button("やり方", "detail", { kind: "ghost", extra: `data-id="${ex.id}"` })}${button("記録", "single", { extra: `data-id="${ex.id}"` })}</div></article>`).join("");
}

function trainView() {
  const plan = T.plan(data.profile, readiness, data.workouts);
  return `${titleBlock("筋トレ", "提案メニューで始めるか、好きな種目だけを記録。", button("器具を変更", "profile", { glyph: "train" }))}
  <div class="subgrid">
    <section class="card">${section(plan.title, `約${plan.minutes}分 · ${readinessNames[plan.readiness]}`, button("調子を変更", "readiness", { kind: "ghost" }))}
    <div class="action-row train-start">${activeDraft() ? button("セッションを再開", "resume-session", { kind: "primary", glyph: "play" }) : button("このメニューで始める", "start-plan", { kind: "primary", glyph: "play" })}</div>
    <div class="exercise-list">${workoutList(plan.items)}</div></section>
    <section class="card">${section("種目を選ぶ", equipmentNames[data.profile.equipment])}<label class="field"><span>種目・部位を検索</span><input id="library-search" type="search" value="${escape(libraryQuery)}" placeholder="例：脚、ランジ" autocomplete="off"></label><div class="library-grid" id="library-items">${libraryItems()}</div></section>
  </div>`;
}

function beginSession(plan, single = false) {
  if (activeDraft()) return openDialog("進行中のトレーニング", `<p>進行中のセットを続けるか、新しいセッションに切り替えられます。</p>
    <div class="dialog-actions">${button("続ける", "resume-session", { kind: "primary" })}${button("新しく始める", "replace-session")}</div>`, () => startSession(plan, single));
  startSession(plan, single);
}

function startSession(plan, single) {
  const draft = {
    title: plan.title, items: plan.items, rest: plan.rest, readiness: plan.readiness, single,
    started: Date.now(), index: 0, sets: [], count: 0, weight: plan.items[0]?.weight || 0,
    elapsedStart: null, restUntil: null, reviewing: false, activeMilliseconds: 0,
  };
  if (!saveData({ ...data, draft })) return;
  activeMilliseconds = 0;
  sessionTick = 0;
  closeDialog();
  go("session");
}

function singleSession(id) {
  const ex = T.catalog[id];
  if (!ex) return;
  const suggested = T.plan(data.profile, readiness, data.workouts).items.find((item) => item.id === id);
  beginSession({ title: ex.name, readiness, rest: 45, items: [{ id, sets: 1, target: suggested?.target ?? ex.base ?? 8, weight: suggested?.weight ?? 0 }] }, true);
}

const currentItem = (draft) => draft.items[draft.index];
const currentGroup = (draft) => draft.sets[draft.index] ?? [];
const counterValue = (draft) => draft.count + (draft.elapsedStart ? Math.max(0, Math.floor((Date.now() - draft.elapsedStart) / 1000)) : 0);
const plannedSets = (draft) => draft.items.reduce((sum, item) => sum + item.sets, 0);
const loggedSets = (draft) => draft.sets.reduce((sum, group) => sum + (group?.length ?? 0), 0);

function updateDraft(fields, render = true) {
  const draft = activeDraft();
  if (!draft || !saveData({ ...data, draft: { ...draft, ...fields, activeMilliseconds: (draft.activeMilliseconds ?? 0) + activeMilliseconds } })) return false;
  activeMilliseconds = 0;
  if (render) paint();
  return true;
}

function sessionView() {
  const draft = activeDraft();
  if (!draft) return trainView();
  if (draft.reviewing) return reviewView(draft);
  const item = currentItem(draft);
  const ex = T.catalog[item.id];
  const activeSets = currentGroup(draft);
  const logged = loggedSets(draft);
  const attempts = Math.max(plannedSets(draft), logged + 1);
  const previous = T.bests(data.workouts, []).exercises[ex.id];
  const previousValue = bestValue(ex, previous, draft.weight);
  const resting = Boolean(draft.restUntil && draft.restUntil > Date.now());
  return `${titleBlock(draft.title, `種目 ${draft.index + 1} / ${draft.items.length} · ${logged} セット記録済み`, button("終了・保存", "review", { glyph: "check" }))}
    <div class="subgrid session-grid">
      <section class="card session-stage">
        ${section(ex.name, `セット ${activeSets.length + 1} · 目安 ${item.target}${unit(ex)}`, button("やり方", "detail", { kind: "ghost", glyph: "info", extra: `data-id="${ex.id}"` }))}
        <progress class="session-progress" aria-label="セットの進捗" value="${logged}" max="${attempts}"></progress>
        ${resting ? `<div class="rest-box"><span>${icon("clock")}セット間の休憩</span><strong id="rest-left">${R.formatTime(Math.ceil((draft.restUntil - Date.now()) / 1000))}</strong>${button("次のセットへ", "skip-rest")}</div>` : `
          <div class="counter"><output class="counter-value" id="rep-count">${counterValue(draft)}</output><span>${unit(ex)}</span>
          <div class="counter-actions">
            ${ex.unit === "sec" ? button(draft.elapsedStart ? "タイマーを停止" : "タイマー開始", "set-timer", { kind: "primary", glyph: "clock" }) : `${button("−1", "adjust-count", { extra: 'data-delta="-1" aria-label="1回減らす"' })}${button("+1 回", "adjust-count", { kind: "primary", extra: 'data-delta="1"' })}`}
          </div></div>
          <label class="field count-field"><span>${ex.unit === "sec" ? "秒数" : "回数"}を直接入力</span><input id="count-input" type="number" inputmode="numeric" min="0" max="9999" step="1" value="${counterValue(draft)}" ${draft.elapsedStart ? "disabled" : ""}></label>
          ${ex.load ? `<label class="field count-field"><span>使用重量（kg）</span><input id="weight-input" type="number" inputmode="decimal" min="0" max="500" step="0.1" value="${escape(draft.weight)}"></label>` : ""}
          <div class="action-row">${button("セットを記録", "log-set", { kind: "primary", glyph: "check" })}${button("種目をスキップ", "skip-exercise", { kind: "ghost" })}</div>
        `}
      </section>
      <section class="card">
        ${section("前回までの自己ベスト", ex.load ? "同じ重量の回数を表示" : ex.name)}
        <div class="best-inline"><strong id="set-best">${previousValue ? `${number(previousValue)}${unit(ex)}` : "—"}</strong><span id="set-delta" class="chip active" hidden></span></div>
        ${section("このセッション")}
        <div class="set-log">${draft.items.map((entry, index) => {
          const exercise = T.catalog[entry.id];
          const sets = draft.sets[index] ?? [];
          return `<div class="record-row"><div><strong>${exercise.name}</strong><small>${sets.length ? sets.map((set) => `${set.reps ?? set.seconds}${unit(exercise)}${set.weight ? ` · ${set.weight}kg` : ""}`).join(" / ") : "記録なし"}</small></div>${sets.length ? `<span class="chip">${sets.length}セット</span>` : ""}</div>`;
        }).join("")}</div>
        <div class="action-row">${logged ? button("直前のセットを取り消す", "undo-set", { kind: "ghost" }) : ""}${button("セッションを破棄", "discard-session", { kind: "ghost" })}</div>
      </section>
    </div>`;
}

function bestValue(ex, record, weight) {
  return ex.load ? record?.repsByWeight?.[weight] : record?.[ex.unit === "sec" ? "seconds" : "reps"];
}

function updateSetBest(draft = activeDraft()) {
  const best = $("#set-best");
  const delta = $("#set-delta");
  if (!best || !delta || !draft || view !== "session") return;
  const ex = T.catalog[currentItem(draft).id];
  const previous = bestValue(ex, T.bests(data.workouts, []).exercises[ex.id], draft.weight);
  const diff = counterValue(draft) - (previous ?? 0);
  best.textContent = previous ? `${number(previous)}${unit(ex)}` : "—";
  delta.hidden = !previous || diff <= 0;
  if (!delta.hidden) delta.textContent = `+${diff}${unit(ex)}`;
}

function logSet() {
  const draft = activeDraft();
  const input = $("#count-input");
  const value = draft.elapsedStart ? counterValue(draft) : Number(input?.value || draft.count);
  const item = currentItem(draft);
  const ex = T.catalog[item.id];
  const weight = Number($("#weight-input")?.value || 0);
  if (!Number.isSafeInteger(value) || value < 1 || value > 9999) return notify("1以上の回数または秒数を入力してください。");
  if (ex.load && (!Number.isFinite(weight) || weight < 0 || weight > 500)) return notify("使用重量は0〜500kgで入力してください。");
  const sets = [...draft.sets];
  sets[draft.index] = [...currentGroup(draft), { [ex.unit === "sec" ? "seconds" : "reps"]: value, ...(ex.load ? { weight } : {}) }];
  const next = sets[draft.index].length >= item.sets ? draft.index + 1 : draft.index;
  const ended = next >= draft.items.length && !draft.single;
  const index = Math.min(next, draft.items.length - 1);
  updateDraft({
    sets, index, count: 0, elapsedStart: null,
    weight: index === draft.index ? weight : draft.items[index].weight || 0,
    reviewing: ended,
    restUntil: ended || draft.single ? null : Date.now() + draft.rest * 1000,
  });
}

function skipExercise() {
  const draft = activeDraft();
  const next = draft.index + 1;
  if (next >= draft.items.length) return updateDraft({ reviewing: true, elapsedStart: null, restUntil: null });
  updateDraft({ index: next, count: 0, elapsedStart: null, weight: draft.items[next].weight || 0, restUntil: null });
}

function undoSet() {
  const draft = activeDraft();
  const index = draft.sets.findLastIndex((group) => group?.length);
  if (index < 0) return;
  const sets = [...draft.sets];
  sets[index] = [...sets[index]];
  const last = sets[index].pop();
  updateDraft({ sets, index, count: last.reps ?? last.seconds, weight: last.weight ?? 0, reviewing: false, elapsedStart: null, restUntil: null });
}

function reviewView(draft) {
  const count = loggedSets(draft);
  return `${titleBlock("筋トレを保存", `${draft.title} · ${count}セット`)}
    <section class="card review-card">${section("今日のきつさは？", "次のメニューの目安回数に反映します。")}
      ${count ? `<form data-form="save-workout"><fieldset class="effort"><legend>運動のきつさ</legend><div class="segmented">
        <label><input type="radio" name="effort" value="easy"><span>余裕があった</span></label>
        <label><input type="radio" name="effort" value="normal" checked><span>ちょうどいい</span></label>
        <label><input type="radio" name="effort" value="hard"><span>きつかった</span></label>
      </div></fieldset><div class="action-row"><button class="button primary" type="submit">${icon("check")}保存する</button>${button("セットに戻る", "back-session", { kind: "ghost" })}</div></form>`
      : `${empty("train", "記録済みのセットがありません", "1セットから保存できます。")}<div class="action-row">${button("セットに戻る", "back-session", { kind: "primary" })}${button("セッションを破棄", "discard-session", { kind: "ghost" })}</div>`}
    </section>`;
}

function saveWorkout(form) {
  const draft = activeDraft();
  if (!draft || loggedSets(draft) < 1) return;
  const entry = {
    id: newId(), date: new Date().toISOString(), title: draft.title,
    duration: Math.max(1, Math.floor(((draft.activeMilliseconds ?? 0) + activeMilliseconds) / 1000)),
    readiness: draft.readiness, effort: new FormData(form).get("effort"),
    items: draft.items.map((item, index) => ({ id: item.id, sets: draft.sets[index] ?? [] })).filter((item) => item.sets.length),
  };
  const comparison = T.compareWorkout(entry, data.workouts);
  if (!saveData({ ...data, workouts: [...data.workouts, entry], draft: null })) return;
  nativeApp?.recordActivity(localDay(entry.date));
  activeMilliseconds = 0;
  result = { title: entry.title, subtitle: `${entry.items.length}種目 · ${entry.items.reduce((total, item) => total + item.sets.length, 0)}セット`, comparison };
  go("result");
}

const gpsLabels = {
  off: "GPS オフ", searching: "位置情報を取得中", active: "GPS で計測中", weak: "GPS の精度が低い",
  unavailable: "GPS を利用できません", denied: "位置情報の許可がありません", error: "GPS に接続できません",
};

function runView() {
  const phase = runState.phase;
  const moving = phase === "running" || phase === "paused";
  return `${titleBlock("ランニング", "距離・時間・ペースを記録。屋内ランも残せます。", button("手入力で記録", "manual-run", { glyph: "edit" }))}
  <div class="subgrid">
    <section class="card run-stage">
      ${section("ランを計測", "屋外でGPSを使うときは、画面を開いたまま走ってください。")}
      <div class="segmented" role="group" aria-label="計測モード">
        ${button("屋外 GPS", "run-mode", { kind: gpsMode ? "primary" : "ghost", glyph: "pin", extra: `data-mode="gps" aria-pressed="${gpsMode}" ${moving ? "disabled" : ""}` })}
        ${button("タイマーのみ", "run-mode", { kind: gpsMode ? "ghost" : "primary", glyph: "clock", extra: `data-mode="timer" aria-pressed="${!gpsMode}" ${moving ? "disabled" : ""}` })}
      </div>
      <div class="run-status" role="status" id="run-status"></div>
      <div class="metric-grid">
        <div class="metric"><span>計測時間</span><strong id="run-time">00:00</strong></div>
        <div class="metric"><span>距離</span><strong id="run-distance">0.00</strong><small>km</small></div>
        <div class="metric"><span>平均ペース</span><strong id="run-pace">—</strong><small>/km</small></div>
      </div>
      <p class="run-split" id="run-split" hidden>最新1kmラップ <strong></strong></p>
      <div class="action-row">
        ${phase === "running" ? button("一時停止", "pause-run", { kind: "primary", glyph: "clock" }) : phase === "paused" ? button("計測を再開", "resume-run", { kind: "primary", glyph: "play" }) : button(phase === "finished" ? "記録を確認する" : "計測を始める", phase === "finished" ? "review-run" : "start-run", { kind: "primary", glyph: "play" })}
        ${moving ? button("終了・保存", "finish-run", { glyph: "check" }) : ""}
        ${phase === "finished" ? button("この計測を破棄", "discard-run", { kind: "ghost" }) : ""}
      </div>
    </section>
    <section class="card">${section("ランの自己ベスト", "ペースは1 km以上のランで比較します。")}
    ${runBests(T.bests([], data.runs).run)}
    <div class="action-row">${linkButton("記録を見る", "history", "history")}</div></section>
  </div>`;
}

function updateRun() {
  if (!$("#run-time")) return;
  $("#run-time").textContent = R.formatTime(runState.seconds);
  $("#run-distance").textContent = runState.distanceKm.toFixed(2);
  $("#run-pace").textContent = runState.paceSeconds ? R.formatPace(runState.paceSeconds) : "—";
  const split = $("#run-split");
  split.hidden = !runState.splitPaceSeconds;
  if (!split.hidden) split.querySelector("strong").textContent = R.formatPace(runState.splitPaceSeconds);
  let status = gpsLabels[runState.gps];
  if (runState.phase === "idle") status = gpsMode ? (runState.canGps ? "開始すると位置情報の許可を求めます。" : "GPSが使えない場合は時間を計測し、距離を入力できます。") : "計測した時間に、あとから距離を入力できます。";
  if (runState.phase === "paused") status = "一時停止中";
  if (runState.phase === "finished") status = "計測終了。保存前に距離を確認できます。";
  if (runState.gps === "active" && runState.phase === "running" && Number.isFinite(runState.accuracyMeters)) status += ` · 精度 約${Math.round(runState.accuracyMeters)}m`;
  if ($("#run-status").textContent !== status) $("#run-status").textContent = status;
}

function runForm(measured = false) {
  const seconds = measured ? Math.max(0, Math.floor(runState.seconds)) : 0;
  const distance = measured ? (Math.round(runState.distanceKm * 1000) / 1000) : "";
  openDialog(measured ? "ランを保存" : "ランを手入力", `<form data-form="save-run">
    <input type="hidden" name="measured" value="${measured ? "1" : "0"}">
    <div class="form-grid">
      <label class="field"><span>時間（分）</span><input name="minutes" type="number" inputmode="numeric" min="0" max="1439" step="1" value="${Math.floor(seconds / 60)}" required></label>
      <label class="field"><span>時間（秒）</span><input name="seconds" type="number" inputmode="numeric" min="0" max="59" step="1" value="${seconds % 60}" required></label>
      <label class="field"><span>距離（km）</span><input name="distance" type="number" inputmode="decimal" min="0" max="500" step="0.001" value="${distance}" placeholder="例：2.5"></label>
    </div>
    <p class="form-note">${measured && runState.distanceKm > 0 ? "GPS距離は、必要に応じて修正できます。" : "距離がわからないときは時間だけでも保存できます。"}</p>
    <div class="dialog-actions"><button class="button primary" type="submit">${icon("check")}保存する</button>${button("閉じる", "close")}</div>
  </form>`);
}

function saveRun(form) {
  const fields = new FormData(form);
  const duration = Number(fields.get("minutes")) * 60 + Number(fields.get("seconds"));
  const distance = Number(fields.get("distance") || 0);
  const validated = R.manual({ seconds: duration, distanceKm: distance });
  if (!validated) { notify("1秒以上の時間と、有効な距離を入力してください。"); return; }
  const measured = fields.get("measured") === "1";
  const gps = measured && runState.source === "gps" && distance > 0 && Math.abs(runState.distanceKm - distance) < 0.00051;
  const entry = { id: newId(), date: validated.date, duration, distance, source: gps ? "gps" : "manual" };
  const comparison = T.compareRun(entry, data.runs);
  if (!saveData({ ...data, runs: [...data.runs, entry] })) return;
  nativeApp?.recordActivity(localDay(entry.date));
  if (measured) tracker.reset();
  result = { title: "ランニング", subtitle: `${R.formatTime(duration)}${distance ? ` · ${number(distance, 3)} km` : ""}`, comparison };
  closeDialog();
  go("result");
}

const metric = (label, value, suffix = "") => `<div class="metric"><span>${label}</span><strong>${value}</strong>${suffix ? `<small>${suffix}</small>` : ""}</div>`;
function runBests(run) {
  if (!run.distance && !run.duration) return empty("trophy", "ランの記録はまだありません", "最初に保存したランが自己ベストになります。");
  return `<div class="metric-grid">
  ${metric("最長距離", run.distance ? number(run.distance, 3) : "—", "km")}
  ${metric("最長時間", run.duration ? R.formatTime(run.duration) : "—")}
  ${metric("最速ペース", run.pace ? R.formatPace(run.pace) : "—", "/km")}</div>`;
}

function entryList() {
  const entries = [
    ...(historyFilter !== "runs" ? data.workouts.map((item) => ({ ...item, type: "workout" })) : []),
    ...(historyFilter !== "workouts" ? data.runs.map((item) => ({ ...item, type: "run" })) : []),
  ].sort((a, b) => new Date(b.date) - new Date(a.date));
  if (!entries.length) return empty("history", "記録はまだありません", "筋トレやランを保存すると、ここに並びます。");
  return entries.map((entry) => `<button class="record-row record-button" type="button" data-action="record" data-type="${entry.type}" data-id="${escape(entry.id)}">
    <span class="tile-icon">${icon(entry.type === "run" ? "run" : "train")}</span>
    <span class="record-content"><strong>${entry.type === "run" ? "ランニング" : escape(entry.title || "筋トレ")}</strong><small>${formatDate(entry.date)} · ${R.formatTime(entry.duration)}</small></span>
    <b>${entry.type === "run" ? entry.distance ? `${number(entry.distance, 3)} km` : "時間計測" : `${entry.items.reduce((sum, item) => sum + item.sets.length, 0)}セット`}</b>
  </button>`).join("");
}

function exercisesBests(records) {
  return Object.entries(records).map(([id, record]) => {
    const ex = T.catalog[id];
    if (!ex) return "";
    const value = record.seconds ? `${record.seconds}秒` : record.weight ? `${number(record.weight)} kg · ${record.repsByWeight[record.weight]}回` : `${record.reps}回`;
    return `<button class="record-row record-button" type="button" data-action="detail" data-id="${ex.id}"><span class="tile-icon">${icon("train")}</span><span class="record-content"><strong>${ex.name}</strong><small>${ex.group}</small></span><b>${value}</b></button>`;
  }).join("") || empty("trophy", "筋トレの記録はまだありません", "保存した筋トレから最高のセットを表示します。");
}

function historyView() {
  const records = T.bests(data.workouts, data.runs);
  return `${titleBlock("トレーニング記録", "履歴と自己ベストを確認。")}
  <div class="subgrid history-grid">
    <section class="card">
      ${section(historyTab === "activity" ? "運動の履歴" : "筋トレの自己ベスト")}
      <div class="segmented" role="group" aria-label="記録の表示">
        ${[["activity", "履歴"], ["bests", "自己ベスト"]].map(([value, label]) => button(label, "history-tab", { kind: historyTab === value ? "primary" : "ghost", extra: `data-value="${value}" aria-pressed="${historyTab === value}"` })).join("")}
      </div>
      ${historyTab === "activity" ? `<div class="filter-row" role="group" aria-label="運動の種類">${[["all", "すべて"], ["workouts", "筋トレ"], ["runs", "ラン"]].map(([value, label]) => `<button class="chip ${historyFilter === value ? "active" : ""}" data-action="history-filter" data-value="${value}" aria-pressed="${historyFilter === value}">${label}</button>`).join("")}</div><div>${entryList()}</div>` : `<div>${exercisesBests(records.exercises)}</div>`}
    </section>
    <div class="stack">${weeklyCard()}<section class="card">${section("ランの自己ベスト")}${runBests(records.run)}</section></div>
  </div>`;
}

function comparisonMarkup(comparison) {
  const values = comparison.map((item) => {
    const format = (value) => item.unit === "秒/km" ? `${R.formatPace(value)}/km` : `${number(value, 3)}${item.unit === "kg" || item.unit === "km" ? " " : ""}${item.unit}`;
    const status = item.delta === null ? "初記録" : item.delta > 0 ? (item.unit === "秒/km" ? `${number(item.delta)}秒/km短縮` : `+${format(item.delta)}`) : item.delta === 0 ? "自己ベストと同じ" : "自己ベストをキープ";
    return `<div class="record-row"><span class="record-content"><strong>${escape(item.label)}</strong><small>${item.previous !== null ? `これまで ${format(item.previous)} → 今回 ${format(item.value)}` : `今回 ${format(item.value)}`}</small></span><span class="chip ${item.delta === null || item.delta > 0 ? "active" : ""}">${status}</span></div>`;
  }).join("");
  return values ? `<div class="result-list">${values}</div>` : empty("history", "運動を記録しました", "履歴から今回の内容を確認できます。");
}

function resultView() {
  if (!result) return historyView();
  const renewed = result.comparison.some((item) => item.delta > 0);
  return `${titleBlock(renewed ? "自己ベスト更新！" : "トレーニング完了", `${escape(result.title)} · ${result.subtitle}`)}
    <section class="card result-card">
      ${section("今回の結果", "前回までの最高記録との比較", `<span class="section-icon">${icon(renewed ? "trophy" : "check")}</span>`)}
      ${comparisonMarkup(result.comparison)}
      <div class="action-row">${linkButton("記録を見る", "history", "history")}${linkButton("今日の画面へ", "today")}</div>
    </section>`;
}

function openRecord(type, id) {
  const entries = type === "run" ? data.runs : data.workouts;
  const entry = entries.find((item) => item.id === id);
  if (!entry) return;
  const prior = entries.slice(0, entries.indexOf(entry));
  const time = `<p>${formatDate(entry.date)} · ${R.formatTime(entry.duration)}${type === "run" && entry.source === "gps" ? " · GPS" : ""}</p>`;
  const content = type === "run" ? "" : `<div class="set-log">${entry.items.map(({ id: exId, sets }) => {
    const ex = T.catalog[exId];
    if (!ex) return "";
    return `<div class="record-row"><div><strong>${ex.name}</strong><small>${sets.map((set) => `${set.reps ?? set.seconds}${unit(ex)}${set.weight ? ` × ${number(set.weight)}kg` : ""}`).join(" / ")}</small></div></div>`;
  }).join("")}</div>`;
  openDialog(type === "run" ? "ランニング" : escape(entry.title), time + content + comparisonMarkup(type === "run" ? T.compareRun(entry, prior) : T.compareWorkout(entry, prior)));
}

function profileForm(inDialog = false, startAfter = false) {
  const profile = data.profile;
  return `<form data-form="profile">
    ${startAfter ? '<input type="hidden" name="start_on_save" value="1">' : ""}
    <div class="form-grid">
      ${select("equipment", "使える器具", equipmentNames, profile.equipment)}
      ${select("level", "筋トレ経験", levelNames, profile.level)}
      ${select("goal", "運動の目的", goalNames, profile.goal)}
      ${select("minutes", "1回に使える時間", { 10: "10分", 20: "20分", 30: "30分", 45: "45分" }, profile.minutes)}
      ${select("days", "週の運動日数", { auto: "目的に合わせて自動で決める", 2: "2日", 3: "3日", 4: "4日", 5: "5日" }, profile.manualDays ? profile.days : "auto")}
    </div>
    <p class="form-note">目標はいつでも変更できます。記録と当日の調子に合わせて、次のメニューを調整します。</p>
    <div class="${inDialog ? "dialog-actions" : "action-row"}"><button type="submit" class="button primary">${icon("check")}${startAfter ? "保存して始める" : "設定を保存"}</button>${inDialog ? button("閉じる", "close") : ""}</div>
  </form>`;
}

function settingsView() {
  return `${titleBlock("設定", "使う器具や週の目標を変更。")}
  <div class="subgrid">
    <section class="card">${section("トレーニングの条件")}${profileForm()}</section>
    <section class="card">${section("記録の保存先", "この端末内に保存。アプリの機能はすべて無料です。")}
      <div class="action-row">${button("バックアップを保存", "export", { glyph: "download" })}${button("読み込む", "import", { glyph: "refresh" })}<input type="file" id="import-file" accept=".json,application/json" hidden></div>
      <p class="form-note">機種変更時は、保存したバックアップを新しい端末で読み込めます。</p>
    </section>
    ${globalThis.RepLiftLink?.card("settings") ?? ""}
  </div>`;
}

let confirmAction = null;

function openDialog(title, content, confirm = null) {
  confirmAction = confirm;
  dialog.querySelector(".modal-body").innerHTML = `<div class="modal-head"><h2 id="modal-title">${title}</h2><button class="icon-button" type="button" data-action="close" aria-label="閉じる">${icon("close")}</button></div>${content}`;
  if (!dialog.open) dialog.showModal();
}

function closeDialog() {
  if (dialog.open) dialog.close();
  confirmAction = null;
}

globalThis.__repliftCloseDialog = () => {
  if (!dialog.open) return false;
  closeDialog();
  return true;
};

function detail(id) {
  const ex = T.catalog[id];
  if (!ex) return;
  const isCurrent = view === "session" && currentItem(activeDraft() ?? { items: [] })?.id === id;
  openDialog(ex.name, `<div class="chips">${chip(ex.group)}${chip(equipmentNames[ex.equipment])}</div>
    <p>${ex.description}</p>
    <h3>フォームのポイント</h3>
    <ol class="cue-list">${ex.cues.map((cue) => `<li>${cue}</li>`).join("")}</ol>
    <div class="dialog-actions">${isCurrent ? button("セットに戻る", "close", { kind: "primary" }) : button("この種目を記録", "single", { kind: "primary", glyph: "play", extra: `data-id="${id}"` })}${isCurrent ? "" : button("閉じる", "close")}</div>`);
}

function readinessDialog() {
  openDialog("今日の調子", `<p>回数とセット数を調整します。</p><div class="segmented" role="group" aria-label="今日の調子">${Object.entries(readinessNames).map(([value, label]) => button(label, "set-readiness", { kind: readiness === value ? "primary" : "secondary", extra: `data-value="${value}" aria-pressed="${readiness === value}"` })).join("")}</div>`);
}

function startPlan() {
  if (!data.configured) {
    openDialog("最初に、あなたの運動条件を設定", profileForm(true, true));
    return;
  }
  beginSession(T.plan(data.profile, readiness, data.workouts));
}

function fileExport() {
  const backup = JSON.stringify({ app: "REP LIFT", version: 1, profile: data.profile, configured: data.configured, runs: data.runs, workouts: data.workouts }, null, 2);
  if (nativeApp) {
    nativeApp.saveBackup(backup);
    return;
  }
  const blob = new Blob([backup], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `rep-lift-${localDay(Date.now())}.json`;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function importFile(file) {
  if (!file) return;
  try {
    if (file.size > 20 * 1024 * 1024) throw new Error("size");
    const parsed = JSON.parse(await file.text());
    const validBase = (entry) => entry && typeof entry.id === "string" && Number.isFinite(new Date(entry.date).getTime()) &&
      Number.isFinite(entry.duration) && entry.duration > 0;
    const validSet = (set, ex) => set && Number.isInteger(set[ex.unit === "sec" ? "seconds" : "reps"]) &&
      set[ex.unit === "sec" ? "seconds" : "reps"] > 0 && (set.weight == null || (Number.isFinite(set.weight) && set.weight >= 0));
    const validWorkout = (entry) => validBase(entry) && typeof entry.title === "string" && Array.isArray(entry.items) &&
      entry.items.every((item) => item && Object.hasOwn(T.catalog, item.id) && Array.isArray(item.sets) && item.sets.every((set) => validSet(set, T.catalog[item.id])));
    const validRun = (entry) => validBase(entry) && Number.isFinite(entry.distance) && entry.distance >= 0;
    if (parsed?.app !== "REP LIFT" || parsed.version !== 1 || !Array.isArray(parsed.workouts) ||
      !Array.isArray(parsed.runs) || !parsed.workouts.every(validWorkout) || !parsed.runs.every(validRun)) throw new Error("backup");
    const merge = (current, incoming) => {
      const entries = new Map(current.map((entry) => [entry.id, entry]));
      for (const entry of incoming) if (!entries.has(entry.id)) entries.set(entry.id, entry);
      return [...entries.values()].sort((a, b) => new Date(a.date) - new Date(b.date));
    };
    if (!saveData({
      ...data,
      workouts: merge(data.workouts, parsed.workouts), runs: merge(data.runs, parsed.runs),
      profile: data.configured ? data.profile : { ...T.profile(parsed.profile), manualDays: Boolean(parsed.profile?.manualDays) },
      configured: data.configured || Boolean(parsed.configured),
    })) return;
    const restoredDays = [...new Set([...parsed.workouts, ...parsed.runs].map((entry) => localDay(entry.date)))];
    nativeApp?.restoreActivityDays(JSON.stringify(restoredDays));
    paint();
    notify("バックアップを読み込みました。");
  } catch {
    notify("REP LIFT のバックアップファイルを読み込めませんでした。");
  }
}

globalThis.__repliftImportBackup = async (path) => {
  try {
    const response = await fetch(path);
    if (!response.ok) throw new Error("backup file unavailable");
    await importFile(await response.blob());
  } catch {
    notify("バックアップファイルを読み込めませんでした。");
  }
};

function go(page) {
  if (location.hash !== `#${page}`) location.hash = page;
  else paint();
}

function route() {
  const page = location.hash.slice(1);
  if (page === "content") return;
  if (view === "session" && activeMilliseconds) updateDraft({}, false);
  sessionTick = 0;
  const acceptable = [...nav.map((item) => item.id), "session", "result"];
  view = acceptable.includes(page) ? page : "today";
  if (view === "session" && !activeDraft()) view = "train";
  if (view === "result" && !result) view = "history";
  closeDialog();
  paint();
  root.focus({ preventScroll: true });
  scrollTo(0, 0);
}

function paint() {
  shell();
  const views = { today: todayView, train: trainView, run: runView, session: sessionView, history: historyView, result: resultView, settings: settingsView };
  root.innerHTML = views[view]();
  if (view === "run") updateRun();
  if (view === "session") updateSetBest();
}

function updateSessionDOM() {
  if (view !== "session") return;
  const draft = activeDraft();
  if (!draft || draft.reviewing) return;
  const now = performance.now();
  if (!document.hidden && sessionTick) activeMilliseconds += Math.min(1000, Math.max(0, now - sessionTick));
  sessionTick = document.hidden ? 0 : now;
  if (activeMilliseconds >= 15000) updateDraft({}, false);
  const rest = $("#rest-left");
  if (rest) {
    const seconds = Math.ceil((draft.restUntil - Date.now()) / 1000);
    if (seconds <= 0) {
      if (updateDraft({ restUntil: null })) notify("休憩終了。次のセットを始められます。");
    } else rest.textContent = R.formatTime(seconds);
  }
  if (draft.elapsedStart && $("#rep-count")) {
    const count = String(counterValue(draft));
    if ($("#rep-count").textContent !== count) {
      $("#rep-count").textContent = count;
      updateSetBest(draft);
    }
  }
}

const actions = {
  profile: () => openDialog("トレーニング設定", profileForm(true)),
  readiness: readinessDialog,
  "set-readiness": ({ value }) => { readiness = value; closeDialog(); paint(); },
  "start-plan": startPlan,
  "resume-session": () => { closeDialog(); go("session"); },
  "replace-session": () => confirmAction?.(),
  single: ({ id }) => { closeDialog(); singleSession(id); },
  detail: ({ id }) => detail(id),
  "adjust-count": ({ delta }) => {
    const draft = activeDraft();
    const value = Number($("#count-input")?.value);
    const count = Math.max(0, Math.min(9999, Math.round((Number.isFinite(value) ? value : draft.count) + Number(delta))));
    if (!updateDraft({ count }, false)) return;
    $("#count-input").value = count;
    $("#rep-count").textContent = count;
    updateSetBest();
  },
  "set-timer": () => {
    const draft = activeDraft();
    updateDraft(draft.elapsedStart ? { count: counterValue(draft), elapsedStart: null } :
      { count: Number($("#count-input").value) || 0, elapsedStart: Date.now() });
  },
  "log-set": logSet,
  "skip-exercise": skipExercise,
  "skip-rest": () => updateDraft({ restUntil: null }),
  "undo-set": undoSet,
  review: () => updateDraft({ reviewing: true, restUntil: null, elapsedStart: null, count: counterValue(activeDraft()) }),
  "back-session": () => updateDraft({ reviewing: false }),
  "discard-session": () => openDialog("このセッションを破棄しますか？", `<p>まだ保存していないセットの記録が削除されます。</p><div class="dialog-actions">${button("破棄する", "confirm")}${button("続ける", "close", { kind: "primary" })}</div>`, () => {
    if (saveData({ ...data, draft: null })) { activeMilliseconds = 0; closeDialog(); go("train"); }
  }),
  "run-mode": ({ mode }) => { gpsMode = mode === "gps"; paint(); },
  "start-run": () => { tracker.start({ gps: gpsMode }); paint(); },
  "pause-run": () => { tracker.pause(); paint(); },
  "resume-run": () => { tracker.resume(); paint(); },
  "finish-run": () => { tracker.finish(); paint(); runForm(true); },
  "review-run": () => runForm(true),
  "manual-run": () => runForm(false),
  "discard-run": () => openDialog("この計測を破棄しますか？", `<div class="dialog-actions">${button("破棄する", "confirm")}${button("計測を残す", "close", { kind: "primary" })}</div>`, () => { tracker.reset(); closeDialog(); paint(); }),
  "history-tab": ({ value }) => { historyTab = value; paint(); },
  "history-filter": ({ value }) => { historyFilter = value; paint(); },
  record: ({ type, id }) => openRecord(type, id),
  "view-ignido": () => openDialog("IGNIDO ブランドアート", '<img class="link-artwork" src="wakeguard-art/ignido_ignition_art.jpg" alt="赤橙の炎をまとって飛び出すドラゴン">'),
  close: closeDialog,
  confirm: () => confirmAction?.(),
  export: fileExport,
  import: () => { if (nativeApp) nativeApp.openBackup(); else $("#import-file")?.click(); },
};

document.addEventListener("click", (event) => {
  const control = event.target.closest("[data-action]");
  if (control && !control.disabled && Object.hasOwn(actions, control.dataset.action)) actions[control.dataset.action](control.dataset);
});
document.addEventListener("input", (event) => {
  const input = event.target;
  if (input.id === "library-search") {
    libraryQuery = input.value;
    $("#library-items").innerHTML = libraryItems();
  }
  if (input.id === "count-input") {
    const count = Number(input.value);
    if (Number.isInteger(count) && count >= 0 && count <= 9999) {
      $("#rep-count").textContent = count;
      updateDraft({ count }, false);
      updateSetBest();
    }
  }
  if (input.id === "weight-input" && input.validity.valid) {
    updateDraft({ weight: Number(input.value) }, false);
    updateSetBest();
  }
});
document.addEventListener("change", (event) => {
  if (event.target.id === "import-file") importFile(event.target.files[0]);
});
document.addEventListener("submit", (event) => {
  const form = event.target;
  if (!form.dataset.form) return;
  event.preventDefault();
  if (form.dataset.form === "save-workout") return saveWorkout(form);
  if (form.dataset.form === "save-run") return saveRun(form);
  if (form.dataset.form === "profile") {
    const values = Object.fromEntries(new FormData(form));
    const profile = { ...T.profile(values), manualDays: values.days !== "auto" };
    if (!saveData({ ...data, profile, configured: true })) return;
    closeDialog();
    if (values.start_on_save) return beginSession(T.plan(profile, readiness, data.workouts));
    paint();
    notify("トレーニング設定を保存しました。");
  }
});
dialog.addEventListener("close", () => { if (!dialog.open) confirmAction = null; });
window.addEventListener("hashchange", route);
document.addEventListener("visibilitychange", () => {
  sessionTick = 0;
  if (activeMilliseconds && activeDraft()) updateDraft({}, false);
});
window.addEventListener("pagehide", () => {
  if (activeMilliseconds && activeDraft()) updateDraft({}, false);
});
setInterval(updateSessionDOM, 250);
route();
