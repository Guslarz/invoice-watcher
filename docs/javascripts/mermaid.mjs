// Provides Mermaid with the elk layout engine to the theme, which loads its own Mermaid only when
// none is defined yet, and makes every diagram open full screen on click.
// Module scripts run before DOMContentLoaded, when the theme starts rendering diagrams.
import mermaid from "https://cdn.jsdelivr.net/npm/mermaid@11.12.0/dist/mermaid.esm.min.mjs";
import elk from "https://cdn.jsdelivr.net/npm/@mermaid-js/layout-elk@0.2.0/dist/mermaid-layout-elk.esm.min.mjs";

mermaid.registerLayoutLoaders(elk);
window.mermaid = mermaid;

// The theme renders each diagram into a closed shadow root of a div.mermaid, so the whole div is
// enlarged; clicks inside the diagram are retargeted to it.
document.addEventListener("click", (event) => {
  const diagram = event.target.closest?.("div.mermaid");
  if (diagram) {
    diagram.classList.toggle("mermaid--zoomed");
  }
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    document.querySelectorAll(".mermaid--zoomed").forEach((d) => d.classList.remove("mermaid--zoomed"));
  }
});
