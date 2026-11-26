(() => {
  "use strict";
  const bridge = globalThis.WakeGuard;
  if (!bridge || typeof bridge.requestLocation !== "function") return;
  const watches = new Map();
  let nextId = 0;
  Object.defineProperty(navigator, "geolocation", {
    configurable: true,
    value: {
      watchPosition(success, error) {
        const id = ++nextId;
        watches.set(id, { success, error });
        bridge.requestLocation(id);
        return id;
      },
      clearWatch(id) {
        watches.delete(id);
        bridge.stopLocation(id);
      },
    },
  });
  globalThis.__wakeguardLocation = (id, latitude, longitude, accuracy, timestamp) => {
    const watch = watches.get(id);
    if (!watch) return;
    watch.success({
      coords: { latitude: Number(latitude), longitude: Number(longitude), accuracy: Number(accuracy) },
      timestamp: Number(timestamp),
    });
  };
  globalThis.__wakeguardLocationError = (id, code, message) => {
    const watch = watches.get(id);
    if (!watch) return;
    watch.error({ code: Number(code), message: String(message) });
  };
})();