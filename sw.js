/* Nafas service worker: offline cache + reminder checks */
const CACHE = 'nafas-v13';
const CONFIG = 'nafas-config';
const CORE = ['./', './index.html', './manifest.webmanifest', './icon-192.png', './icon-512.png', './apple-touch-icon.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(CORE)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE && k !== CONFIG).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});
/* Cache first: after the first visit Nafas makes no network requests, fonts included. */
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  const own = url.origin === self.location.origin;
  if (!own && !/fonts\.(googleapis|gstatic)\.com$/.test(url.hostname)) return;
  e.respondWith(
    caches.match(req, { ignoreSearch: own }).then(hit => hit || fetch(req).then(res => {
      if (res && (res.ok || res.type === 'opaque')) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
      return res;
    }).catch(() => (req.mode === 'navigate' ? caches.match('./index.html') : Response.error())))
  );
});

async function loadCfg() {
  const c = await caches.open(CONFIG); const r = await c.match('./__reminder');
  return r ? r.json() : null;
}
async function saveCfg(cfg) {
  const c = await caches.open(CONFIG);
  await c.put('./__reminder', new Response(JSON.stringify(cfg), { headers: { 'content-type': 'application/json' } }));
}
self.addEventListener('message', e => {
  const d = e.data;
  if (d && d.type === 'reminder') e.waitUntil(loadCfg().then(old => saveCfg({ ...d.cfg, lastShown: old ? old.lastShown : null })));
});
async function checkReminder() {
  const cfg = await loadCfg();
  if (!cfg || !cfg.on) return;
  const now = new Date(), dow = (now.getDay() + 6) % 7 + 1;
  if (!(cfg.days || []).includes(dow)) return;
  const [h, m] = String(cfg.time || '20:30').split(':').map(Number);
  const at = new Date(now); at.setHours(h, m, 0, 0);
  const key = now.toDateString();
  if (now >= at && now - at < 3 * 3600e3 && cfg.lastShown !== key) {
    cfg.lastShown = key; await saveCfg(cfg);
    await self.registration.showNotification('Nafas', { body: cfg.text || "Time for a short breath, if you'd like.", tag: 'nafas-reminder', icon: 'icon-192.png', badge: 'icon-192.png' });
  }
}
self.addEventListener('periodicsync', e => { if (e.tag === 'nafas-reminder') e.waitUntil(checkReminder()); });
self.addEventListener('notificationclick', e => {
  e.notification.close();
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(ws => {
    for (const w of ws) if ('focus' in w) return w.focus();
    return self.clients.openWindow('./');
  }));
});
