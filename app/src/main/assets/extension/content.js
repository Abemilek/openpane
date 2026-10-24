(() => {
  "use strict";
  const state = { hideSuggestions: false };
  function apply() {
    if (!state.hideSuggestions) return;
    document.querySelectorAll('[data-testid="UserCell"]').forEach((node) => {
      node.setAttribute("data-openpane-hidden", "true");
    });
  }
  const observer = new MutationObserver(apply);
  observer.observe(document.documentElement, { childList: true, subtree: true });
  apply();
  browser.runtime.onMessage.addListener((message) => {
    if (!message || message.type !== "openpane.rules") return;
    state.hideSuggestions = Boolean(message.hideSuggestions);
    document.querySelectorAll('[data-openpane-hidden="true"]')
      .forEach((node) => node.removeAttribute("data-openpane-hidden"));
    apply();
  });
})();
