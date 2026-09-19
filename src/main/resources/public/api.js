// Shared helpers for the Neighborhoods and Rentals tabs (the main report uses app.js instead).

// The server answers 503 {"status":"loading"} for about a minute after it starts, while it
// downloads the city data. So: show the server's message, wait a few seconds, and ask again.
const RETRY_EVERY_MS = 5000;
const GIVE_UP_AFTER_MS = 3 * 60 * 1000;   // don't retry forever if something is really wrong

async function fetchJsonWithRetry(url, onWaiting) {
  const started = Date.now();
  while (true) {
    let res;
    try {
      res = await fetch(url, { signal: AbortSignal.timeout(25000) });
    } catch {
      throw new Error("Can't reach the RentCheck server. Is it running?");
    }
    const data = await res.json().catch(() => null);   // an error page may not be JSON

    if (res.status === 503 && data && data.status === "loading") {
      if (Date.now() - started > GIVE_UP_AFTER_MS) {
        throw new Error("The city data is taking too long to load. Try refreshing the page.");
      }
      onWaiting(data.message || "Loading city data...");
      await new Promise((resolve) => setTimeout(resolve, RETRY_EVERY_MS));
      continue;
    }
    if (!res.ok || !data) {
      throw new Error((data && (data.message || data.error)) || `Request failed (${res.status}).`);
    }
    return data;
  }
}

// Make an element with text. textContent (never innerHTML), so data can't inject HTML.
function el(tag, className, text) {
  const e = document.createElement(tag);
  if (className) e.className = className;
  if (text !== undefined && text !== null) e.textContent = text;
  return e;
}

// A link to another website that opens in a new tab, announced as such to screen readers.
function externalLink(url, label) {
  const a = el("a", null, label);
  a.href = url;
  a.target = "_blank";
  a.rel = "noopener noreferrer";
  a.appendChild(el("span", "visually-hidden", " (opens in a new tab)"));
  return a;
}
