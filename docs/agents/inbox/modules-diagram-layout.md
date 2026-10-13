---
title: Improve the module dependency diagram
kind: idea
area: docs
added: 2026-10-13
---

The Mermaid dependency graph on the generated `reference/modules.md` page (built by
`modules_page` in `scripts/gen-reference.py`) is small and its layout is hard to read: 17 modules
in three subgraphs (`apps`, `images`, `libs`) with a left-to-right flow and many crossing edges.

Investigate and improve:
- Layout: direction (`TB` vs `LR`), grouping libs by area (`mail-producer`, `secret`, ...) in
  nested subgraphs, the `elk` layout engine, hiding transitive edges.
- Size: let the diagram use the full content width, or open it enlarged on click (e.g. a
  zoom/lightbox plugin for Material, or rendering it to SVG at build time and linking it).
- Whether one diagram is enough, or a per-area diagram reads better.

The diagram is generated, so the fix belongs in the generator, not in the page.
