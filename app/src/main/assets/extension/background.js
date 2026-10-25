const nativePort = browser.runtime.connectNative("openpane");

// Messages from the app (rules, verdicts) go to every tab that has the content script.
// Errors are swallowed on purpose: tabs without the content script reject the message.
nativePort.onMessage.addListener((message) => {
  browser.tabs.query({}).then((tabs) => {
    for (const tab of tabs) {
      if (tab.id !== undefined) {
        browser.tabs.sendMessage(tab.id, message).catch(() => {});
      }
    }
  }).catch(() => {});
});

browser.runtime.onMessage.addListener((message) => {
  if (!message) return;
  if (message.type === "openpane.rules" || message.type === "openpane.evaluate") {
    nativePort.postMessage(message);
  }
});

nativePort.postMessage({ type: "openpane.ready" });
