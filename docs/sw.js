/* Service worker: rete prima, cache come riserva.
   Ogni apertura con rete scarica l'ultima versione pubblicata; senza rete
   l'app e i banchi già visti restano disponibili. */
const CACHE = "lanquiz-v2";
const CORE = [
  "./", "index.html", "style.css", "app.js", "manifest.webmanifest",
  "banks.json", "icons/icon-192.png", "icons/icon-512.png",
];

self.addEventListener("install", (e) => {
  e.waitUntil(
    caches.open(CACHE)
      .then((c) => c.addAll(CORE))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("message", (e) => {
  if (e.data === "skip-waiting") {
    self.skipWaiting();
  }
});

self.addEventListener("fetch", (e) => {
  if (e.request.method !== "GET") {
    return;
  }
  const url = new URL(e.request.url);
  if (url.origin !== location.origin) {
    return; // le richieste esterne (import da URL) vanno sempre in rete
  }
  e.respondWith(
    fetch(e.request)
      .then((res) => {
        const copy = res.clone();
        caches.open(CACHE).then((c) => c.put(e.request, copy));
        return res;
      })
      .catch(() => caches.match(e.request).then((hit) => hit || caches.match("index.html")))
  );
});
