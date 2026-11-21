(() => {
  if (window.top !== window) return;
  const STYLE_ID = "__materialex_site_style";

  const apply = (css) => {
    let el = document.getElementById(STYLE_ID);
    if (!css) {
      if (el) el.remove();
      return;
    }
    if (!el) {
      el = document.createElement("style");
      el.id = STYLE_ID;
      (document.head || document.documentElement).appendChild(el);
    }
    el.textContent = css;
  };

  const host = location.hostname.toLowerCase();
  const port = browser.runtime.connect({ name: "site-style" });
  port.onMessage.addListener((message) => {
    if (message && message.type === "apply" && message.host === host) apply(String(message.css || ""));
  });

  browser.runtime
    .sendMessage({ type: "get", host })
    .then((reply) => {
      if (reply && typeof reply.css === "string") apply(reply.css);
    })
    .catch(() => {});
})();
