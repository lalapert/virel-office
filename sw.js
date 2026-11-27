/* VIREL Office · 서하 비서실 — Service Worker
   홈 화면에 추가했을 때 앱처럼(standalone) 열리고, 신호가 약해도 마지막 화면을 보여 줍니다. */
const VERSION = "virel-office-v2";
const SCOPE = new URL("./", self.location).pathname;

const CORE = [
  "./",
  "./index.html",
  "./manifest.webmanifest",
  "./assets/icon-192.png",
  "./assets/icon-512.png",
  "./assets/icon-maskable-512.png",
  "./assets/office-map.webp",
  "./assets/office-map.jpg"
];

self.addEventListener("install", event => {
  event.waitUntil((async () => {
    const cache = await caches.open(VERSION);
    await Promise.all(CORE.map(url =>
      cache.add(new Request(url, { cache: "reload" })).catch(() => {})
    ));
    await self.skipWaiting();
  })());
});

self.addEventListener("activate", event => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter(k => k !== VERSION).map(k => caches.delete(k)));
    await self.clients.claim();
  })());
});

self.addEventListener("fetch", event => {
  const req = event.request;
  if (req.method !== "GET") return;

  let url;
  try { url = new URL(req.url); } catch (e) { return; }
  if (url.origin !== self.location.origin) return;
  if (!url.pathname.startsWith(SCOPE)) return;

  // 화면 이동: 네트워크 우선(항상 최신), 실패하면 캐시된 앱 화면
  if (req.mode === "navigate") {
    event.respondWith((async () => {
      try {
        const fresh = await fetch(req);
        if (fresh && fresh.ok) {
          const cache = await caches.open(VERSION);
          cache.put("./index.html", fresh.clone());
        }
        return fresh;
      } catch (e) {
        const cache = await caches.open(VERSION);
        return (await cache.match(req)) ||
               (await cache.match("./index.html")) ||
               (await cache.match("./")) ||
               Response.error();
      }
    })());
    return;
  }

  // 대화 기록 JSON: 네트워크 우선(날짜별 기록이 바로 반영), 실패하면 캐시
  if (url.pathname.indexOf("/data/") !== -1) {
    event.respondWith((async () => {
      try {
        const fresh = await fetch(req);
        if (fresh && fresh.ok) {
          const cache = await caches.open(VERSION);
          cache.put(req, fresh.clone());
        }
        return fresh;
      } catch (e) {
        const cached = await caches.match(req);
        return cached || Response.error();
      }
    })());
    return;
  }

  // 이미지·아이콘 등: 캐시 먼저 보여 주고 뒤에서 갱신
  event.respondWith((async () => {
    const cache = await caches.open(VERSION);
    const cached = await cache.match(req);
    const network = fetch(req).then(res => {
      if (res && res.ok) cache.put(req, res.clone());
      return res;
    }).catch(() => null);
    return cached || (await network) || Response.error();
  })());
});
