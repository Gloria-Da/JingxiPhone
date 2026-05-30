// ============================================================
// 镜隙 (Jingxi) Web版 — Service Worker
// PWA 离线缓存支持
// ============================================================

const CACHE_NAME = 'jingxi-web-v2';
const STATIC_ASSETS = [
    './',
    './index.html',
    './css/style.css',
    './js/main.js',
    './js/db.js',
    './manifest.json',
];

self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME).then(cache => {
            console.log('[SW] Caching static assets');
            return cache.addAll(STATIC_ASSETS);
        }).then(() => self.skipWaiting())
    );
});

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys().then(keys => {
            return Promise.all(
                keys.filter(key => key !== CACHE_NAME)
                    .map(key => {
                        console.log('[SW] Deleting old cache:', key);
                        return caches.delete(key);
                    })
            );
        }).then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', event => {
    if (event.request.method !== 'GET') return;

    const url = new URL(event.request.url);
    // Skip API calls
    if (url.pathname.includes('/v1/') || url.pathname.includes('/api/')) return;

    // Cache-first for same-origin static assets
    event.respondWith(
        caches.match(event.request).then(cached => {
            if (cached) {
                // Stale-while-revalidate
                fetch(event.request).then(response => {
                    if (response.ok) {
                        caches.open(CACHE_NAME).then(cache => cache.put(event.request, response));
                    }
                }).catch(() => {});
                return cached;
            }
            return fetch(event.request).then(response => {
                if (response.ok) {
                    const clone = response.clone();
                    caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
                }
                return response;
            });
        })
    );
});
