// Light/Dark switch. Dark is the default; the choice is saved in this browser only.
(function () {
  var root = document.documentElement;

  function savedTheme() {
    try { return localStorage.getItem("theme"); } catch (e) { return null; }   // storage can be blocked
  }
  function setTheme(theme) { root.setAttribute("data-theme", theme); }

  setTheme(savedTheme() === "light" ? "light" : "dark");   // runs immediately: no flash of the wrong theme

  document.addEventListener("DOMContentLoaded", function () {
    var button = document.getElementById("theme-toggle");
    if (!button) return;

    function updateButton() {
      var isLight = root.getAttribute("data-theme") === "light";
      button.textContent = isLight ? "Dark mode" : "Light mode";
      button.setAttribute("aria-label", isLight ? "Switch to dark mode" : "Switch to light mode");
    }
    updateButton();

    button.addEventListener("click", function () {
      var next = root.getAttribute("data-theme") === "light" ? "dark" : "light";
      setTheme(next);
      try { localStorage.setItem("theme", next); } catch (e) { /* not saved: fine */ }
      updateButton();
    });
  });
})();
