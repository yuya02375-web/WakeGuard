const CACHE = "rep-lift-v1";
const ASSETS = [
  "./", "./index.html", "./style.css", "./training.js", "./run.js", "./app.js",
  "./hero.jpg", "./icon.svg", "./icon-192.png", "./icon-512.png", "./app.webmanifest",
];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(ASSETS)));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(
    keys.filter((key) => key.startsWith("rep-lift-") && key !== CACHE).map((key) => caches.delete(key))
  )).then(() => self.clients.claim()));
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== "GET" || url.origin !== self.location.origin) return;
  if (!ASSETS.some((asset) => new URL(asset, self.registration.scope).pathname === url.pathname)) return;
  event.respondWith(fetch(request).catch(async () => {
    const saved = await caches.match(request, { ignoreSearch: true });
    return saved ?? (request.mode === "navigate" ? caches.match("./index.html") : Response.error());
  }));
});
