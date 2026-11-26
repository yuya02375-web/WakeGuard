(function () {
  "use strict";

  const forms = [
    { file: "ignido_stage_egg.webp", name: "竜の卵", bounds: [12, 18, 49, 64] },
    { file: "ignido_stage_ember.webp", name: "火種", bounds: [16, 28, 44, 63] },
    { file: "ignido_stage_flame.webp", name: "小さな炎", bounds: [15, 19, 49, 64] },
    { file: "ignido_stage_hatchling.webp", name: "幼炎竜", bounds: [18, 26, 46, 55] },
    { file: "ignido_stage_adult.webp", name: "天炎竜", bounds: [0, 4, 49, 64] },
    { file: "ignido_stage_stellar.png", name: "恒星炎竜", bounds: [3, 1, 59, 59] },
    { file: "ignido_stage_infinite.png", name: "無限炎竜", bounds: [1, 1, 63, 63] },
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

  const asCount = (value, fallback = 0) => Number.isSafeInteger(value) && value >= 0 ? value : fallback;
  const escapeText = (value) => String(value).replace(/[&<>"']/g, (char) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));
  const action = (name, label, kind = "secondary") =>
    `<button class="button ${kind}" data-wakeguard-action="${name}" type="button" ${pending ? "disabled" : ""}>${label}</button>`;

  function portrait(form) {
    const [left, top, right, bottom] = form.bounds;
    const scale = 51 / Math.max(right - left, bottom - top);
    const x = (32 - (left + right) / 2) * scale * 100 / 64;
    const y = (32 - (top + bottom) / 2) * scale * 100 / 64;
    return `<span class="link-portrait" aria-hidden="true"><img src="wakeguard-art/${form.file}" alt="" style="--art-scale:${scale.toFixed(4)};--art-x:${x.toFixed(2)}%;--art-y:${y.toFixed(2)}%"></span>`;
  }

  function linkedContent(settings) {
    const form = forms[asCount(snapshot.stage)] ?? forms[0];
    const level = Math.max(1, asCount(snapshot.level, 1));
    const streak = asCount(snapshot.current);
    const best = asCount(snapshot.best);
    const remaining = asCount(snapshot.remaining);
    const formName = typeof snapshot.form === "string" && snapshot.form.trim() ? escapeText(snapshot.form) : form.name;
    return `<div class="link-companion">
        ${portrait(form)}
        <div class="link-companion-copy"><h3>イグニド</h3><p>${formName} <span>Lv. ${level}</span></p>
          ${settings && remaining ? `<p class="link-next">次の姿は Lv. ${level + remaining}</p>` : ""}</div>
        <div class="link-streak"><span>共有ストリーク</span><div><strong data-wakeguard-current>${streak}</strong><span>日</span></div>
          ${settings ? `<small>最高 ${best} 日</small>` : ""}</div>
      </div>${settings ? `<div class="link-controls">${action("refresh", "更新")}${action("disconnect", "連携を解除", "ghost")}</div>` : ""}`;
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
      const template = document.createElement("template");
      template.innerHTML = card(item.dataset.repliftWakeguard);
      const next = template.content.firstElementChild;
      item.replaceWith(next);
      if (focusedAction && !pending) next.querySelector(`[data-wakeguard-action="${focusedAction}"]`)?.focus({ preventScroll: true });
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
