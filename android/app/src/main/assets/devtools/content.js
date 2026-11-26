(() => {
  if (window.top !== window) return;
  const MAX_LOGS = 500;
  const MAX_HTML = 2 * 1024 * 1024;
  const pageWin = window.wrappedJSObject;
  const port = browser.runtime.connect({ name: "devtools-page" });

  function text(value) {
    try {
      if (typeof value === "string") return value;
      if (value === undefined) return "undefined";
      if (typeof value === "function") return String(value);
      if (value && value.message !== undefined && value.stack !== undefined) return value.name + ": " + value.message;
      const seen = new Set();
      return JSON.stringify(value, (k, v) => {
        if (typeof v === "object" && v !== null) {
          if (seen.has(v)) return "[Circular]";
          seen.add(v);
        }
        return typeof v === "function" ? String(v) : v;
      }, 2) || String(value);
    } catch (e) {
      try { return String(value); } catch (e2) { return "[unprintable]"; }
    }
  }

  function log(level, args) {
    try {
      port.postMessage({ type: "log", entry: { level, text: Array.prototype.map.call(args, text).join(" ").slice(0, 4000), time: Date.now() } });
    } catch (e) {}
  }

  try {
    for (const level of ["log", "info", "warn", "error", "debug"]) {
      const original = pageWin.console[level];
      exportFunction(function () {
        log(level, arguments);
        return original.apply(pageWin.console, arguments);
      }, pageWin.console, { defineAs: level });
    }
  } catch (e) {}

  window.addEventListener("error", (e) => log("error", [e.message + " (" + e.filename + ":" + e.lineno + ")"]));
  window.addEventListener("unhandledrejection", (e) => log("error", ["Unhandled rejection: " + text(e.reason)]));

  function hello() {
    port.postMessage({ type: "hello", href: location.href, visible: document.visibilityState === "visible" });
  }
  hello();
  document.addEventListener("visibilitychange", () => port.postMessage({ type: "visibility", visible: document.visibilityState === "visible" }));
  window.addEventListener("pageshow", hello);

  function nodeAt(path) {
    let node = document.documentElement;
    for (const index of path || []) {
      node = node && node.children[index];
      if (!node) throw new Error("node-not-found");
    }
    return node;
  }

  function label(el) {
    let s = el.tagName.toLowerCase();
    if (el.id) s += "#" + el.id;
    if (el.classList && el.classList.length) s += "." + Array.prototype.slice.call(el.classList, 0, 3).join(".");
    return s;
  }

  function tree() {
    const nodes = [];
    const walk = (el, path, depth) => {
      if (nodes.length >= 3000 || depth > 24) return;
      nodes.push({ path, depth, label: label(el), children: el.children.length, text: el.children.length === 0 ? (el.textContent || "").trim().slice(0, 60) : "" });
      for (let i = 0; i < el.children.length; i++) walk(el.children[i], path.concat(i), depth + 1);
    };
    walk(document.documentElement, [], 0);
    return nodes;
  }

  function replaceDocument(html) {
    const doc = new DOMParser().parseFromString(html, "text/html");
    for (const attr of Array.from(document.documentElement.attributes)) document.documentElement.removeAttribute(attr.name);
    for (const attr of Array.from(doc.documentElement.attributes)) document.documentElement.setAttribute(attr.name, attr.value);
    document.head.innerHTML = doc.head.innerHTML;
    document.body.innerHTML = doc.body.innerHTML;
  }

  function storageFor(kind) {
    return kind === "session" ? window.sessionStorage : window.localStorage;
  }

  const ops = {
    "dom.tree": () => ({ nodes: tree() }),
    "dom.getOuter": (a) => ({ html: nodeAt(a.path).outerHTML.slice(0, MAX_HTML) }),
    "dom.setOuter": (a) => { nodeAt(a.path).outerHTML = String(a.html); return {}; },
    "dom.remove": (a) => { nodeAt(a.path).remove(); return {}; },
    "dom.highlight": (a) => {
      const el = nodeAt(a.path);
      el.scrollIntoView({ block: "center", behavior: "smooth" });
      const previous = el.style.outline;
      el.style.outline = "3px solid #e91e63";
      setTimeout(() => { el.style.outline = previous; }, 1500);
      return {};
    },
    "html.get": () => ({ html: ("<!DOCTYPE html>\n" + document.documentElement.outerHTML).slice(0, MAX_HTML) }),
    "html.set": (a) => { replaceDocument(String(a.html)); return {}; },
    "js.eval": (a) => {
      try {
        const value = pageWin.eval(String(a.code));
        return { result: text(value) };
      } catch (e) {
        return { error: text(e) };
      }
    },
    "storage.get": (a) => {
      const s = storageFor(a.kind);
      const entries = [];
      for (let i = 0; i < s.length; i++) entries.push({ key: s.key(i), value: s.getItem(s.key(i)) });
      return { entries };
    },
    "storage.set": (a) => { storageFor(a.kind).setItem(String(a.key), String(a.value)); return {}; },
    "storage.remove": (a) => { storageFor(a.kind).removeItem(String(a.key)); return {}; },
    "storage.clear": (a) => { storageFor(a.kind).clear(); return {}; },
    "sources.list": () => {
      const items = [{ kind: "document", url: location.href, index: -1 }];
      Array.prototype.forEach.call(document.scripts, (s, i) => items.push({ kind: "script", url: s.src || "", index: i, size: s.src ? 0 : (s.textContent || "").length }));
      Array.prototype.forEach.call(document.querySelectorAll("style, link[rel~=stylesheet]"), (s, i) => items.push({ kind: "style", url: s.tagName === "LINK" ? s.href : "", index: i, size: s.tagName === "LINK" ? 0 : (s.textContent || "").length }));
      return { items };
    },
    "sources.inline": (a) => {
      const list = a.kind === "script" ? Array.from(document.scripts) : Array.from(document.querySelectorAll("style, link[rel~=stylesheet]"));
      const el = list[a.index];
      if (!el) throw new Error("source-not-found");
      return { text: (el.textContent || "").slice(0, MAX_HTML) };
    }
  };

  port.onMessage.addListener((message) => {
    if (!message || message.type !== "cmd") return;
    const op = ops[message.op];
    if (!op) {
      port.postMessage({ type: "reply", id: message.id, ok: false, error: "unknown-op" });
      return;
    }
    try {
      port.postMessage({ type: "reply", id: message.id, ok: true, result: op(message.args || {}) });
    } catch (e) {
      port.postMessage({ type: "reply", id: message.id, ok: false, error: String(e && e.message ? e.message : e) });
    }
  });
})();
