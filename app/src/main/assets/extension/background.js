const nativePort = browser.runtime.connectNative("openpane");

nativePort.onMessage.addListener((message) => {
  browser.tabs.query({}).then((tabs) => tabs.forEach((tab) => {
    if (tab.id !== undefined) browser.tabs.sendMessage(tab.id, message);
  }));
});

browser.runtime.onMessage.addListener((message) => {
  if (message && message.type === "openpane.rules") nativePort.postMessage(message);
});

nativePort.postMessage({ type: "openpane.ready" });
