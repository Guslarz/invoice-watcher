---
title: Module discovery fails on plain files under apps, libs or images
kind: bug
area: build
added: 2026-10-24
---

`settings.gradle.kts:17-24` recurses into every entry returned by `listDirectoryEntries()` that has
no `build.gradle.kts`, including plain files. A file such as `libs/README.md` or `.gitkeep` makes
it call `listDirectoryEntries()` on a file and the build fails with `NotDirectoryException`. None
exists today. Filter with `isDirectory()`.

Also, segments are joined with `-` (`:28`), so `libs/a-b/c` and `libs/a/b-c` would both become
`:libs:a-b-c`; failing on a duplicate would make that explicit.
