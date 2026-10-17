const CACHE = 'finance-workspace-shell-v8';
const SHELL = ['./','./index.html','./assets/css/app.css','./assets/js/app.js','./assets/icons/favicon.svg','./manifest.webmanifest','./privacy-policy.html','./exchange-rates.html','./assets/js/exchange-rates.js'];
const SHELL_URLS = new Set(SHELL.map(path => new URL(path, self.registration.scope).href));
self.addEventListener('install', event => event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(SHELL)).then(() => self.skipWaiting())));
self.addEventListener('activate', event => event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('finance-workspace-shell-') && key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim())));
self.addEventListener('fetch', event => {
    const url = new URL(event.request.url);
    // Cache only the public shell. Financial/API responses must never enter CacheStorage.
    if (event.request.method !== 'GET' || !SHELL_URLS.has(url.href)) return;
    // Prefer the deployed shell so a previous worker cannot pin an old login client.
    event.respondWith(fetch(event.request, {cache: 'no-cache'}).then(response => {
        if (response.ok && response.type === 'basic') {
            const copy = response.clone();
            event.waitUntil(caches.open(CACHE).then(cache => cache.put(event.request, copy)));
        }
        return response;
    }).catch(() => caches.match(event.request)));
});
