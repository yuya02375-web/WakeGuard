(function () {
  "use strict";

  const forms = [
    { art: "egg", name: "竜の卵" },
    { art: "ember", name: "火種" },
    { art: "flame", name: "小さな炎" },
    { art: "hatchling", name: "幼炎竜" },
    { art: "adult", name: "天炎竜" },
    { art: "stellar", name: "恒星炎竜" },
    { art: "infinite", name: "無限炎竜" },
  ];
  const unavailable = {
    missing: "対応版の WakeGuard が見つかりません。",
    incompatible: "WakeGuard を対応版に更新すると連携できます。",
    unavailable: "WakeGuard に接続できませんでした。WakeGuard を開いてから確認してください。",
  };
  const linkIcon = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10 13a4 4 0 0 0 6 .5l3.5-3.5a4 4 0 0 0-5.7-5.7l-2 2m2.2 4.7a4 4 0 0 0-6-.5L4.5 14a4 4 0 0 0 5.7 5.7l2-2"/></svg>';
  let snapshot = { enabled: false, available: false, status: "pending" };
  let pending = "";
  let pendingTimer;
  let message = "";
  const expanded = { today: false, settings: false };

  const asCount = (value, fallback = 0) => Number.isSafeInteger(value) && value >= 0 ? value : fallback;
  const escapeText = (value) => String(value).replace(/[&<>"']/g, (char) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));
  const action = (name, label, kind = "secondary") =>
    `<button class="button ${kind}" data-wakeguard-action="${name}" type="button" ${pending ? "disabled" : ""}>${label}</button>`;

  function portrait(form) {
    const base = `wakeguard-art/ignido_stage_${form.art}`;
    return `<picture class="link-portrait">
      <source media="(prefers-reduced-motion: reduce)" srcset="${base}_still.webp" type="image/webp">
      <img src="${base}_motion.webp" alt="成長段階のドラゴン" width="64" height="64">
    </picture>`;
  }

  function isoDate(value) {
    if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
    const date = new Date(`${value}T00:00:00Z`);
    return Number.isFinite(date.valueOf()) && date.toISOString().slice(0, 10) === value ? value : null;
  }

  function dateRange(days, first, last) {
    if (!days) return "記録なし";
    const start = isoDate(first);
    const end = isoDate(last);
    if (!start || !end || start > end) return "期間データなし";
    const year = String(new Date().getFullYear());
    const showYear = !start.startsWith(`${year}-`) || !end.startsWith(`${year}-`);
    const time = (iso) => {
      const [y, m, d] = iso.split("-").map(Number);
      return `<time datetime="${iso}">${showYear ? `${y}年` : ""}${m}月${d}日</time>`;
    };
    return start === end ? time(start) : `${time(start)}〜${time(end)}`;
  }

  function streakMetric(label, days, first, last, marker) {
    return `<div class="link-metric"><dt>${label}</dt>
      <dd><span class="link-count"><strong ${marker}>${days}</strong>日</span><small class="link-range">${dateRange(days, first, last)}</small></dd>
    </div>`;
  }

  function exerciseStreak() {
    const now = new Date();
    const dayMs = 86_400_000;
    const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()) / dayMs;
    const uniqueDays = new Set();
    for (const raw of globalThis.repLiftActivityDays?.() ?? []) {
      const date = isoDate(raw);
      if (!date) continue;
      const day = Date.parse(date) / dayMs;
      if (day <= today) uniqueDays.add(day);
    }
    const days = [...uniqueDays].sort((a, b) => a - b);
    let previous = -Infinity;
    let current = 0;
    let best = 0;
    for (const day of days) {
      current = previous + 1 === day ? current + 1 : 1;
      best = Math.max(best, current);
      previous = day;
    }
    return { count: days.length, current: previous >= today - 1 ? current : 0, best };
  }

  function localActivity({ count, current, best }) {
    return `<section class="link-personal">
      <h3>REP LIFTの運動ストリーク</h3>
      <dl class="link-metrics">
        <div class="link-metric"><dt>連続運動</dt><dd><span class="link-count"><strong data-replift-current>${current}</strong>日</span></dd></div>
        <div class="link-metric"><dt>最長連続</dt><dd><span class="link-count"><strong data-replift-best>${best}</strong>日</span></dd></div>
      </dl>
      <p class="link-note">${count ? `この端末で運動した日 <strong data-replift-local-days>${count}</strong>日` : "保存した筋トレ・ランはまだありません。"}</p>
    </section>`;
  }

  function linkedContent(settings) {
    const form = forms[asCount(snapshot.stage)] ?? forms[0];
    const level = Math.max(1, asCount(snapshot.level, 1));
    const streak = asCount(snapshot.current);
    const best = asCount(snapshot.best);
    const remaining = asCount(snapshot.remaining);
    const formName = typeof snapshot.form === "string" && snapshot.form.trim() ? escapeText(snapshot.form) : form.name;
    const personal = exerciseStreak();
    return `${localActivity(personal)}
        <section class="link-companion">
          ${portrait(form)}
          <div class="link-companion-copy"><h3>ドラゴンの成長</h3><p>${formName} <span>Lv. ${level}</span></p>
            ${settings && remaining ? `<p class="link-next">次の姿は Lv. ${level + remaining}</p>` : ""}
            <button type="button" class="button ghost link-art-trigger" data-action="view-ignido" aria-haspopup="dialog">ブランドアートを見る</button></div>
        </section>
        <details class="link-shared" data-link-accordion ${expanded[settings ? "settings" : "today"] ? "open" : ""}>
          <summary>WakeGuardとの共有ストリーク</summary>
          <div class="link-shared-content">
          <dl class="link-metrics">
            ${streakMetric("共有の現在", streak, snapshot.currentStart, snapshot.currentEnd, "data-wakeguard-current")}
            ${streakMetric("共有の最長", best, snapshot.bestStart, snapshot.bestEnd, "data-wakeguard-best")}
          </dl>
          <p class="link-note">WakeGuardの起床成功日と、連携で共有された運動日を集計します。同じ日は1日と数えます。</p>
          </div>
        </details>
      ${settings ? `<div class="link-controls">${action("refresh", "更新")}${action("disconnect", "連携を解除", "ghost")}</div>` : ""}`;
  }

  function disconnectedContent(bridge) {
    if (!bridge) return '<p class="link-note">WakeGuard との連携は Android アプリで利用できます。</p>';
    if (snapshot.status === "pending") return '<p class="link-note">WakeGuard の接続を確認しています。</p>';
    if (snapshot.available && !snapshot.enabled) {
      return `<p class="link-note">起床と運動を、ひとつのストリークにできます。連携時に、この端末の運動日を WakeGuard に共有します。</p><div class="link-controls">${action("connect", "WakeGuard と連携")}</div>`;
    }
    const status = unavailable[snapshot.status] ?? "WakeGuard に接続できませんでした。";
    return `<p class="link-note">${status}</p><div class="link-controls">${action("refresh", "もう一度確認")}${snapshot.enabled ? action("disconnect", "連携を解除", "ghost") : ""}</div>`;
  }

  function card(placement = "settings") {
    const settings = placement === "settings";
    const linked = snapshot.enabled === true && snapshot.status === "connected";
    const bridge = globalThis.RepLiftNative;
    return `<section class="card link-card link-${settings ? "settings" : "today"}${linked ? " link-connected" : ""}" data-replift-wakeguard="${settings ? "settings" : "today"}" aria-busy="${Boolean(pending)}">
      <header class="link-heading"><span class="link-icon">${linkIcon}</span><h2>WakeGuard 連携</h2>${linked ? '<span class="link-badge">連携中</span>' : ""}</header>
      ${linked ? linkedContent(settings) : disconnectedContent(bridge)}
      <p class="link-feedback" role="status" ${message || pending ? "" : "hidden"}>${message || "処理中です。"}</p>
    </section>`;
  }

  function paint() {
    for (const item of document.querySelectorAll("[data-replift-wakeguard]")) {
      const focusedAction = item.contains(document.activeElement) ? document.activeElement.dataset.wakeguardAction : "";
      const focusedSummary = document.activeElement === item.querySelector("summary");
      const template = document.createElement("template");
      template.innerHTML = card(item.dataset.repliftWakeguard);
      const next = template.content.firstElementChild;
      item.replaceWith(next);
      if (focusedAction && !pending) next.querySelector(`[data-wakeguard-action="${focusedAction}"]`)?.focus({ preventScroll: true });
      if (focusedSummary) next.querySelector("summary")?.focus({ preventScroll: true });
    }
  }

  function applyState(value) {
    if (!value || typeof value !== "object") return;
    clearTimeout(pendingTimer);
    pending = "";
    message = "";
    snapshot = value;
    paint();
  }

  function reportUnreachable() {
    pending = "";
    message = "WakeGuard の応答を確認できませんでした。";
    if (snapshot.status === "pending") snapshot = { ...snapshot, status: "unavailable" };
    paint();
  }

  document.addEventListener("toggle", (event) => {
    const details = event.target;
    if (!details.matches?.("[data-link-accordion]") || !details.isConnected) return;
    const card = details.closest("[data-replift-wakeguard]");
    if (card) expanded[card.dataset.repliftWakeguard] = details.open;
  }, true);

  document.addEventListener("click", (event) => {
    const target = event.target.closest?.("[data-wakeguard-action]");
    const bridge = globalThis.RepLiftNative;
    if (!target || !bridge || pending) return;
    const name = target.dataset.wakeguardAction;
    if (!["connect", "disconnect", "refresh"].includes(name)) return;
    message = "";
    pending = name;
    paint();
    try {
      pendingTimer = setTimeout(reportUnreachable, 6000);
      if (name === "refresh") bridge.refreshWakeGuard();
      else {
        if (name === "disconnect") expanded.today = expanded.settings = false;
        const days = name === "connect" ? globalThis.repLiftActivityDays?.() ?? [] : [];
        bridge.setWakeGuardLinked(name === "connect", JSON.stringify(days));
      }
    } catch {
      clearTimeout(pendingTimer);
      reportUnreachable();
    }
  });

  globalThis.RepLiftLink = Object.freeze({ card });
  globalThis.__repliftWakeGuardState = applyState;
}());
