---
title: Resolve upstream Angular parser WASM peer mismatch
kind: tech-debt
area: build
added: 2026-11-15
status: later
size: S
blocked-by: []
---

Angular CLI/build 22.2.7 depend on oxc-parser 0.156.0. Its optional WASM binding declares
@emnapi/core and @emnapi/runtime 2.0.0-alpha.6, while its @napi-rs/wasm-runtime 1.2.7 dependency
expects ^1.7.1 or specific 2.0.0 beta releases. `pnpm peers check` reports this upstream mismatch.

The Linux native parser builds successfully; the optional WASM fallback is not exercised by that
build. Recheck upstream dependency fixes on the next Angular tooling update and before supporting
a host that relies on the WASM fallback. Prefer an upstream-compatible package update over
forcing a different emnapi ABI through a global override. Remove this note when peer checks pass
and the relevant fallback is verified.
