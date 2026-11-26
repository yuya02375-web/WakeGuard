(() => {
  "use strict";
  const bridge = globalThis.RepLiftNative;
  if (!bridge || typeof bridge.requestLocation !== "function") return;
  const watches = new Map();
  let nextId = 0;
  Object.defineProperty(navigator, "geolocation", {
    configurable: true,
    value: {
      watchPosition(success, error, options) {
        const id = ++nextId;
        const timer = setTimeout(() => error?.({ code: 3 }), options?.timeout ?? 15000);
        watches.set(id, { success, error, timer });
        bridge.requestLocation(id);
        return id;
      },
      clearWatch(id) {
        clearTimeout(watches.get(id)?.timer);
        watches.delete(id);
        bridge.stopLocation(id);
      },
    },
  });
  globalThis.__repliftLocation = (id, latitude, longitude, accuracy, timestamp, restarted) => {
    const watch = watches.get(id);
    if (!watch) return;
    clearTimeout(watch.timer);
    watch.success({
      coords: { latitude: Number(latitude), longitude: Number(longitude), accuracy: Number(accuracy) },
      timestamp: Number(timestamp),
      restarted: Boolean(restarted),
    });
  };
  globalThis.__repliftLocationError = (id, code) => {
    const watch = watches.get(id);
    if (!watch) return;
    clearTimeout(watch.timer);
    watch.error?.({ code: Number(code) });
  };
})();
