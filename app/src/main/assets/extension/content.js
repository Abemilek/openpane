(() => {
  "use strict";

  const HIDDEN = "data-openpane-hidden";
  const EVALUATED = "data-openpane-evaluated";
  const PROMO_RE = /^(promoted|promocionado|promocionada|sponsored|patrocinado|publicidad|ad)$/;

  const state = {
    hideSuggestions: false,
    hidePromoted: true,
    // Only talk to the native side when there is something to evaluate there
    // (muted words / accounts). The app tells us through the rules message.
    needsEvaluation: false,
    pending: new Map(),
    flushTimer: null,
    scanScheduled: false,
    nextId: 1
  };

  function hide(node) {
    if (node.getAttribute(HIDDEN) !== "true") node.setAttribute(HIDDEN, "true");
  }

  function hideSuggestions() {
    if (!state.hideSuggestions) return;
    document.querySelectorAll('[data-testid="UserCell"]').forEach(hide);
  }

  // Only leaf elements are inspected: reading textContent of big containers for
  // every node of every post was the most expensive part of the old version.
  function looksPromoted(article) {
    const labelled = article.querySelectorAll("[aria-label]");
    for (let i = 0; i < labelled.length; i++) {
      const label = (labelled[i].getAttribute("aria-label") || "").trim().toLowerCase();
      if (PROMO_RE.test(label)) return true;
    }
    const nodes = article.querySelectorAll("span, div");
    for (let i = 0; i < nodes.length; i++) {
      const node = nodes[i];
      if (node.firstElementChild) continue;
      const text = node.textContent;
      if (!text || text.length > 14) continue;
      if (PROMO_RE.test(text.trim().toLowerCase())) return true;
    }
    return false;
  }

  // The id is computed once and stored, so the id sent to the app is always the id
  // used later to apply its verdict.
  function articleId(article) {
    if (article.dataset.openpaneId) return article.dataset.openpaneId;
    const status = article.querySelector('a[href*="/status/"]');
    const match = status && status.href.match(/\/status\/([0-9]+)/);
    const id = match ? match[1] : `dom-${state.nextId++}`;
    article.dataset.openpaneId = id;
    return id;
  }

  function authorOf(article) {
    const user = article.querySelector('[data-testid="User-Name"]');
    return user ? user.textContent.trim().split(/\s+/)[0] : "";
  }

  function scan() {
    state.scanScheduled = false;
    hideSuggestions();

    const articles = document.querySelectorAll(
      'article[data-testid="tweet"]:not([' + EVALUATED + '])'
    );
    for (let i = 0; i < articles.length; i++) {
      const article = articles[i];
      article.setAttribute(EVALUATED, "true");
      const promoted = looksPromoted(article);
      if (promoted && state.hidePromoted) hide(article);
      if (state.needsEvaluation) {
        const id = articleId(article);
        // textContent does not force a layout; innerText did.
        state.pending.set(id, {
          id,
          author: authorOf(article),
          text: article.textContent || "",
          promoted
        });
      }
    }
    if (state.pending.size && !state.flushTimer) {
      state.flushTimer = setTimeout(flush, 40);
    }
  }

  // Coalesce DOM changes into one scan per frame, run before the next paint so a
  // promoted post is hidden before it is ever drawn.
  function schedule() {
    if (state.scanScheduled) return;
    state.scanScheduled = true;
    requestAnimationFrame(scan);
  }

  function flush() {
    state.flushTimer = null;
    const posts = Array.from(state.pending.values());
    state.pending.clear();
    if (posts.length) {
      browser.runtime.sendMessage({ type: "openpane.evaluate", posts }).catch(() => {});
    }
  }

  function applyVerdicts(message) {
    if (!message || message.type !== "openpane.verdicts") return;
    const hidden = new Set(message.hiddenIds || []);
    if (!hidden.size) return;
    document.querySelectorAll('article[data-testid="tweet"][data-openpane-id]').forEach((article) => {
      if (hidden.has(article.dataset.openpaneId)) hide(article);
    });
  }

  new MutationObserver(schedule).observe(document.documentElement, {
    childList: true,
    subtree: true
  });
  schedule();

  browser.runtime.onMessage.addListener((message) => {
    if (!message) return;
    if (message.type === "openpane.rules") {
      state.hideSuggestions = Boolean(message.hideSuggestions);
      state.hidePromoted = message.hidePromoted !== false;
      state.needsEvaluation = Boolean(message.needsEvaluation);
      // Re-apply the new rules to everything already on screen.
      document.querySelectorAll("[" + HIDDEN + '="true"]')
        .forEach((node) => node.removeAttribute(HIDDEN));
      document.querySelectorAll("[" + EVALUATED + "]")
        .forEach((node) => node.removeAttribute(EVALUATED));
      schedule();
    } else {
      applyVerdicts(message);
    }
  });
})();
