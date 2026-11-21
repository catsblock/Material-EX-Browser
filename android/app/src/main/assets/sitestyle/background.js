const APP = "materialexSiteStyle";
const contentPorts = new Set();

browser.runtime.onConnect.addListener((port) => {
  if (port.sender.id !== browser.runtime.id) return;
  contentPorts.add(port);
  port.onDisconnect.addListener(() => contentPorts.delete(port));
});

browser.runtime.onMessage.addListener((message, sender) => {
  if (sender.id !== browser.runtime.id || !message || message.type !== "get") return undefined;
  return browser.runtime.sendNativeMessage(APP, { type: "get", host: String(message.host) });
});

const nativePort = browser.runtime.connectNative(APP);
nativePort.onMessage.addListener((message) => {
  if (!message || message.type !== "apply") return;
  for (const port of contentPorts) port.postMessage(message);
});
