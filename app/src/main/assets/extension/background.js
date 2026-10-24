const nativePort = browser.runtime.connectNative("openpane");

nativePort.onMessage.addListener((message) => {
  browser.tabs.query({}).then((tabs) => tabs.forEach((tab) => {
    if (tab.id !== undefined) browser.tabs.sendMessage(tab.id, message);
  }));
});

browser.runtime.onMessage.addListener((message) => {
  if (!message) return;
  if (message.type === "openpane.rules" || message.type === "openpane.evaluate") {
    nativePort.postMessage(message);
  }
});

nativePort.postMessage({ type: "openpane.ready" });
