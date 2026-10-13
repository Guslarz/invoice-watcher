---
title: Silence sun.misc.Unsafe warning from grpc-netty-shaded
kind: tech-debt
area: build
added: 2026-10-13
---

On startup the JVM prints `WARNING: sun.misc.Unsafe::allocateMemory has been called by io.grpc.netty.shaded.io.netty.util.internal.PlatformDependent0$2` (grpc-netty-shaded 1.80.0). Find a dependency upgrade or JVM flag that removes it.
