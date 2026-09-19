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

    addMotionToggle();

    button.addEventListener("click", function () {
      var next = root.getAttribute("data-theme") === "light" ? "dark" : "light";
      setTheme(next);
      try { localStorage.setItem("theme", next); } catch (e) { /* not saved: fine */ }
      updateButton();
    });
  });

  // "Pause animation" button in the footer, for the cars on the background bridge.
  // Moving content that runs on its own should have a way to stop it (accessibility guideline WCAG 2.2.2).
  // Pausing adds the "motion-paused" class, and style.css then shows the still bridge instead.
  function savedPaused() {
    try { return localStorage.getItem("motion") === "paused"; } catch (e) { return false; }
  }
  if (savedPaused()) root.classList.add("motion-paused");   // runs immediately, like the theme

  function addMotionToggle() {
    var footer = document.querySelector("footer");
    if (!footer) return;
    var toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "link-button motion-toggle";
    function update() {
      toggle.textContent = root.classList.contains("motion-paused") ? "Play background animation" : "Pause background animation";
    }
    update();
    toggle.addEventListener("click", function () {
      var paused = root.classList.toggle("motion-paused");
      try { localStorage.setItem("motion", paused ? "paused" : "playing"); } catch (e) { /* fine */ }
      update();
    });
    footer.appendChild(toggle);
  }
})();
