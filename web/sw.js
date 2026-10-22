// Rosetta parent alerts: show pushed alerts, and route the Approve / Not yet buttons back to Mission Control.
self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (e) => e.waitUntil(self.clients.claim()));

self.addEventListener("push", (event) => {
  let p = {};
  try { p = event.data ? event.data.json() : {}; } catch (e) { p = { title: "Rosetta", body: event.data && event.data.text() }; }
  const data = p.data || {};
  event.waitUntil(self.registration.showNotification(p.title || "Rosetta", {
    body: p.body || "",
    icon: "icon-192.png",
    badge: "icon-192.png",
    tag: data.questId ? `q-${data.questId}` : p.kind,
    renotify: true,
    data: { kind: p.kind, questId: data.questId || "", code: data.code || "" },
    actions: p.actions || [],
  }));
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const d = event.notification.data || {};
  const code = d.code || "";
  let url = `parent.html${code ? `?code=${code}` : ""}`;
  if ((event.action === "approve" || event.action === "reject") && d.questId) {
    url += `${code ? "&" : "?"}do=${event.action}&q=${encodeURIComponent(d.questId)}`;
  }
  event.waitUntil((async () => {
    const wins = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
    for (const w of wins) {
      if (w.url.includes("parent.html")) { await w.navigate(url); return w.focus(); }
    }
    return self.clients.openWindow(url);
  })());
});
