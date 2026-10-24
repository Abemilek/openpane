(() => {
  "use strict";

  const state = {
    hideSuggestions: false,
    hidePromoted: true,
    pending: new Map(),
    flushTimer: null,
    nextId: 1
  };

  function hideSuggestions() {
    if (!state.hideSuggestions) return;
    document.querySelectorAll('[data-testid="UserCell"]').forEach((node) => {
      node.setAttribute("data-openpane-hidden", "true");
    });
  }

  function textLooksPromoted(article) {
    const labels = article.querySelectorAll('[aria-label], [data-testid], span, div');
    for (const node of labels) {
      const value = (node.getAttribute("aria-label") || node.textContent || "")
        .trim().toLowerCase();
      if (/^(promoted|promocionado|promocionada|sponsored|patrocinado|publicidad|ad)$/.test(value)) {
        return true;
      }
    }
    return false;
  }

  function summarize(article) {
    const status = article.querySelector('a[href*="/status/"]');
    const match = status && status.href.match(/\/status\/([0-9]+)/);
    const user = article.querySelector('[data-testid="User-Name"]');
    const author = user ? user.textContent.trim().split(/\s+/)[0] : "";
    const id = match ? match[1] : (article.dataset.openpaneId || `dom-${state.nextId++}`);
    article.dataset.openpaneId = id;
    return {
      id,
      author,
      text: article.innerText || "",
      promoted: textLooksPromoted(article)
    };
  }

  function queueArticles() {
    document.querySelectorAll('article[data-testid="tweet"]').forEach((article) => {
      if (article.dataset.openpaneEvaluated === "true") return;
      article.dataset.openpaneEvaluated = "true";
      const post = summarize(article);
      state.pending.set(post.id, post);
      if (post.promoted && state.hidePromoted) {
        article.setAttribute("data-openpane-hidden", "true");
      }
    });
    if (state.pending.size && !state.flushTimer) {
      state.flushTimer = setTimeout(flush, 40);
    }
  }

  function flush() {
    state.flushTimer = null;
    const posts = Array.from(state.pending.values());
    state.pending.clear();
    if (posts.length) browser.runtime.sendMessage({ type: "openpane.evaluate", posts });
  }

  function applyVerdicts(message) {
    if (!message || message.type !== "openpane.verdicts") return;
    const hidden = new Set(message.hiddenIds || []);
    document.querySelectorAll('article[data-testid="tweet"]').forEach((article) => {
      const post = summarize(article);
      if (hidden.has(post.id)) article.setAttribute("data-openpane-hidden", "true");
    });
  }

  const observer = new MutationObserver(() => {
    hideSuggestions();
    queueArticles();
  });
  observer.observe(document.documentElement, { childList: true, subtree: true });
  hideSuggestions();
  queueArticles();

  browser.runtime.onMessage.addListener((message) => {
    if (!message) return;
    if (message.type === "openpane.rules") {
      state.hideSuggestions = Boolean(message.hideSuggestions);
      state.hidePromoted = message.hidePromoted !== false;
      document.querySelectorAll('[data-openpane-hidden="true"]')
        .forEach((node) => node.removeAttribute("data-openpane-hidden"));
      hideSuggestions();
      queueArticles();
    } else {
      applyVerdicts(message);
    }
  });
})();
