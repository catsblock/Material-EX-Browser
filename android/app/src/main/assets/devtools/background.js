const APP = "materialexDevtools";
const MAX_REQUESTS = 500;
const MAX_SOURCE_BYTES = 1024 * 1024;

const pages = new Map();
let nextPageId = 1;
const requests = [];
const requestIndex = new Map();

const nativePort = browser.runtime.connectNative(APP);

function sendToApp(message) {
  try {
    nativePort.postMessage(message);
  } catch (e) {}
}

browser.runtime.onConnect.addListener((port) => {
  if (port.sender.id !== browser.runtime.id) return;
  const pageId = nextPageId++;
  const page = { pageId, port, href: "", visible: false, tabId: port.sender.tab ? port.sender.tab.id : -1, seen: Date.now() };
  pages.set(pageId, page);
  port.onMessage.addListener((message) => {
    if (!message || typeof message !== "object") return;
    page.seen = Date.now();
    if (message.type === "hello") {
      page.href = String(message.href || "");
      page.visible = !!message.visible;
    } else if (message.type === "visibility") {
      page.visible = !!message.visible;
    } else if (message.type === "reply" || message.type === "log") {
      sendToApp(Object.assign({}, message, { href: page.href }));
    }
  });
  port.onDisconnect.addListener(() => pages.delete(pageId));
});

function pickPage(href) {
  let best = null;
  for (const page of pages.values()) {
    if (page.href !== href) continue;
    if (!best || (page.visible && !best.visible) || (page.visible === best.visible && page.seen > best.seen)) best = page;
  }
  if (best) return best;
  for (const page of pages.values()) {
    if (!page.visible) continue;
    if (!best || page.seen > best.seen) best = page;
  }
  return best;
}

function record(details) {
  if (details.url.startsWith("moz-extension:")) return null;
  let entry = requestIndex.get(details.requestId);
  if (!entry) {
    entry = { id: details.requestId, method: details.method, url: details.url, type: details.type, status: 0, start: details.timeStamp, end: 0, fromCache: false, tabId: details.tabId };
    requestIndex.set(details.requestId, entry);
    requests.push(entry);
    if (requests.length > MAX_REQUESTS) requestIndex.delete(requests.shift().id);
  }
  return entry;
}

browser.webRequest.onBeforeRequest.addListener((d) => { record(d); }, { urls: ["<all_urls>"] });
browser.webRequest.onCompleted.addListener((d) => {
  const e = record(d);
  if (e) { e.status = d.statusCode; e.end = d.timeStamp; e.fromCache = !!d.fromCache; }
}, { urls: ["<all_urls>"] });
browser.webRequest.onErrorOccurred.addListener((d) => {
  const e = record(d);
  if (e) { e.status = -1; e.end = d.timeStamp; e.error = d.error; }
}, { urls: ["<all_urls>"] });

function cookieView(c) {
  return { name: c.name, value: c.value, domain: c.domain, path: c.path, secure: c.secure, httpOnly: c.httpOnly, sameSite: c.sameSite, session: c.session, expirationDate: c.expirationDate || 0, hostOnly: c.hostOnly };
}

function isWebUrl(url) {
  return typeof url === "string" && /^https?:\/\//i.test(url);
}

const handlers = {
  async "cookies.list"(args, page) {
    if (!isWebUrl(args.url)) throw new Error("invalid url");
    return { cookies: (await browser.cookies.getAll({ url: args.url })).map(cookieView) };
  },
  async "cookies.set"(args) {
    if (!isWebUrl(args.url) || !args.name) throw new Error("invalid cookie");
    const details = { url: args.url, name: String(args.name), value: String(args.value || ""), path: args.path || "/", secure: !!args.secure, httpOnly: !!args.httpOnly };
    if (args.domain) details.domain = String(args.domain);
    if (args.sameSite) details.sameSite = args.sameSite;
    if (args.expirationDate && args.expirationDate > 0) details.expirationDate = Number(args.expirationDate);
    const c = await browser.cookies.set(details);
    return { cookie: c ? cookieView(c) : null };
  },
  async "cookies.remove"(args) {
    if (!isWebUrl(args.url) || !args.name) throw new Error("invalid cookie");
    await browser.cookies.remove({ url: args.url, name: String(args.name) });
    return {};
  },
  async "network.list"(args, page) {
    const origin = page.href ? new URL(page.href).origin : "";
    const list = requests.filter((r) => (page.tabId >= 0 && r.tabId === page.tabId) || (page.tabId < 0 && origin && r.url.startsWith(origin)) || r.tabId === -1 && origin && r.url.startsWith(origin));
    return { requests: list.slice(-300).map((r) => ({ method: r.method, url: r.url, type: r.type, status: r.status, ms: r.end ? Math.round(r.end - r.start) : 0, cache: r.fromCache })) };
  },
  async "network.clear"() {
    requests.length = 0;
    requestIndex.clear();
    return {};
  },
  async "source.fetch"(args) {
    if (!isWebUrl(args.url)) throw new Error("invalid url");
    const response = await fetch(args.url, { credentials: "omit", cache: "force-cache" });
    const buffer = await response.arrayBuffer();
    const bytes = buffer.byteLength > MAX_SOURCE_BYTES ? buffer.slice(0, MAX_SOURCE_BYTES) : buffer;
    return { text: new TextDecoder("utf-8", { fatal: false }).decode(bytes), truncated: buffer.byteLength > MAX_SOURCE_BYTES };
  }
};

nativePort.onMessage.addListener(async (message) => {
  if (!message || message.type !== "cmd") return;
  const page = pickPage(String(message.href || ""));
  const reply = (body) => sendToApp(Object.assign({ type: "reply", id: message.id, href: message.href }, body));
  if (!page) {
    reply({ ok: false, error: "page-not-ready" });
    return;
  }
  const handler = handlers[message.op];
  if (handler) {
    try {
      reply({ ok: true, result: await handler(message.args || {}, page) });
    } catch (e) {
      reply({ ok: false, error: String(e && e.message ? e.message : e) });
    }
    return;
  }
  page.port.postMessage({ type: "cmd", id: message.id, op: message.op, args: message.args || {} });
});
