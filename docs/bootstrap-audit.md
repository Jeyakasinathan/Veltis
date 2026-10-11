# Veltis cold-start audit, patch, and benchmark report

Branch: `Fixes` (uncommitted work; see §9). Machine: Windows 11, Java 26.0.2.1 (real
JVM, not the `javapath` wrapper), 20 processors, default max heap 12200MB, Mojang
reachable over the default route. Server: Veltis `26.3`. Distributable:
`build/distributions/veltis.jar` (493,795 bytes).

All timings are wall-clock from the launcher's own monotonic stage instrumentation
and the Done/minecraft-start boundary captured by `bench.ps1`. "Runtime ready" is
the `Veltis runtime ready in …` INFO line printed by `VeltisRuntime.prepare()`;
"Done" is Minecraft's `Done (Xs)!`.

## 1. Measured cause of the ~30 second cold start

The cold path was **not** a defective pipeline. The measured floor cost nothing to
do with patching:

| Stage (cold, fresh home, before changes) | Time |
|---|---|
| Mojang metadata resolve (identity) | 599 ms |
| Mojang downloads, sequential | **19.698 s** |
| — 62.3 MB server jar (single CDN transfer, ~4 MB/s on this connection) | ~15 s |
| — 114 libraries (sequential round trips) | ~4.7 s |
| Bytecode patch stages (load/transform/materialize/verify) | ~298 ms |
| Launcher-side total → `Veltis runtime ready` | ~20.7 s |
| Minecraft JVM pre-boot (class init + `ServicesEnvironment`/`discoveryUrl` probe) | ~6.9 s |
| Fresh level prep (world generation) | ~2.7 s |
| **Done (cold)** | **34.96 s** |

Cold also relaunched twice (stage-0 bootstrap fetch on first run + JVM-flag relaunch).

Warm steady state was already fine and stayed fine: **8.83–9.39 s to Done** — below
the 10 s target — launcher side only ~1.7 s, fully offline.

### Is the download parallelizable? (measured: no)

Before writing a chunked downloader, the path to Mojang's CDN was probed directly:
a single-stream fetch of the same 62.3 MB artifact vs 8 parallel ranged requests.

| Transfer | Time | Rate |
|---|---|---|
| single stream | 5.72 s | 10.4 MB/s |
| 8 parallel ranged streams | 6.26 s | 9.5 MB/s aggregate |

The route is **aggregate-capped**, not per-connection capped: eight concurrent
streams split the same total bandwidth. A chunked downloader would add retry,
Range/206 and assembly logic for **zero** gain and is therefore not implemented —
"0 bottleneck" at that stage is the ~10 MB/s path itself. (The wide variance
between benchmark runs, 15–16 s at 14:08/14:24 vs ~5.7 s later the same day, is
the CDN route, not the pipeline.)

## 2. Changes

Exactly the launcher-side preparation of a **cold miss** was optimized; the warm
path was left untouched (it already validated in ~150 ms). All changes share one
implementation with the Gradle pipeline task (`prepareVeltisRuntime`), so the build
server and `java -jar server.jar` got the same behavior.

### Files changed

| File | Change |
|---|---|
| `patch-engine/src/main/java/org/veltismc/patchengine/MinecraftDownloader.java` | **Parallel library downloads.** `DEFAULT_CONCURRENCY = 8`; constructors accept `maxConcurrent`; `downloadLibraries` uses an `ExecutorCompletionService` (fail-fast on the first failure in declared order, cancels in-flight work, `client.close()` to abort stuck sends). Per-file semantics byte-identical; the single server jar stays sequential-first so bootstrap line ordering and the README'd "Downloading vanilla … server jar" line are preserved. |
| `patch-engine/src/main/java/org/veltismc/patchengine/VeltisRuntime.java` | `prepare()` wraps the miss path in `RebuildLock` with double-checked validation (the lock loser re-validates and skips the rebuild); the miss path was extracted to `buildMissing(...)`. New cache-state INFO lines: `(warm start; cache hit)`, `(cold start; cache miss, rebuilt)`, `(cold start, ready by a concurrent launch; cache hit)`. `rebuildLockFile()` = `.rebuild.lock` beside `veltis-server.jar` (per home, per version, per identity). **`fetchArtifacts` now overlaps the library batch with the server-jar transfer** (one downloader per artifact family so a library failure's `client.close()` cannot kill the server fetch; the server artifact keeps declared-order priority — its failure cancels the libraries, matching the sequential path). The downloader itself stays parallel-free for the server jar: the path to Mojang measured aggregate-capped (see §1). |
| `patch-engine/src/main/java/org/veltismc/patchengine/RebuildLock.java` (new) | OS file lock (`FileChannel.tryLock`) serializing rebuilds; retry loop that treats same-JVM `OverlappingFileLockException` like cross-process contention; lock file deleted after release; honest timeout error. |
| `patch-engine/src/test/java/org/veltismc/patchengine/MinecraftDownloaderTest.java` | +3 tests (concurrent fetch with delay + in-flight peak tracking, fail-fast batch cancel, corrupt-cache recovery); loopback server given an explicit 8-thread executor (its default dispatches one handler at a time, which made the concurrency test vacuous). |
| `patch-engine/src/test/java/org/veltismc/patchengine/RebuildLockTest.java` (new) | 5 tests: acquire/release + cleanup, cross-JVM blocking, same-JVM overlap wait, timeout report, stale lock-file reuse. |
| `patch-engine/src/test/java/org/veltismc/patchengine/PackagedRuntimePrepareTest.java` (new) | Fully **offline** server-install `prepare()` harness (seeded cached `version.json`); 5 tests: cold-build/warm-validate, concurrent launches rebuild exactly once, corrupt artifact rebuilt, `.writing` crash recovery, missing-artifact offline error naming source + expected SHA-1. |
| `bench.ps1` (outside the repo, under the temp bench dir) | Must invoke the real JVM (the Oracle `javapath` wrapper spawns a child and exits, orphaning the relaunch chain and faking world-lock/EXITED_EARLY failures); `taskkill /T /F` tree teardown; `-Java`, `-JvmFlags`; captures Done **and** "Starting minecraft server" boundaries. |

### Deliberately not changed

- No on-disk cache format change → **no migration needed**. The existing identity
  (MC version + Mojang SHA-1 + patch fingerprint SHA-256 + format versions +
  source-set SHA-256) plus staging → verify → `ATOMIC_MOVE` publish with `.writing`
  crash recovery already satisfied identity/atomicity/integrity; the missing piece
  was **process-level rebuild serialization**, now provided by `RebuildLock`.
  SHA-1 is deliberate (it is what Mojang publishes); entry-level integrity stays
  SHA-256.
- No Paperclip code, no runtime dependency, launcher chain, Shulker patching,
  module boundaries, plugin lifecycle/APIs, build metadata, or release-channel
  behavior changed.

## 3. Architecture of the (unchanged) pipeline

1. **Resolve identity** — MC version + cached Mojang metadata (`version.json`,
   re-resolved on every rebuild so Mojang drift is caught).
2. **Inspect cache** — `validateArtifact` answers five questions cheaply: patch set
   present? jar record matches the expected identity? vanilla jar hashes to the
   expected SHA-1? libraries present? guard entries present? Any "no" → miss.
3. **Prepare only on miss** — under `RebuildLock` with double-checked validation.
   Downloads (server jar sequential, libraries parallel) → SHA-1 verify → extract
   classes → generate/apply bytecode patch → materialize runtime JAR → verify →
   atomic publish → discard work tree.
4. **Verify output** — the launcher's guard loads every guarded patched class
   before the server is allowed to boot; the Done line is printed only there, on
   runs that actually rebuilt.
5. **Launch.**

Never patch from an unverified base: `requireArtifactMatches` compares the patch's
recorded server SHA-1 with the resolved metadata and refuses with an actionable
message (expected vs actual SHA-1) if Mojang re-published under the same version id.
A warm start performs **zero network I/O** (it never touches Mojang metadata).

## 4. Paperclip differences (reference only; nothing copied)

Paperclip ships a layered jar and on first run downloads vanilla, attaches the
patch at its own layer, caches the composed jar next to itself, and re-inspects
that patch's last-modified each boot. Veltis instead ships the patch set out-of-tree
(`META-INF/veltis/patches/<version>.zip`), applies it at runtime into a guarded
jar with a persistent identity *record*, keeps the verified vanilla artifact and
libraries in `Vanilla/<version>/` + `libraries/`, and validates in ~150 ms warm
with no Mojang contact. Rebuilds are serialized per installation and published
atomically; Paperclip's boot-time re-inspection has no equivalent here.

## 5. Patch-format rationale

Bytecode patch set (whole-class payloads/deltas) rather than source patches: the
runtime needs no compiler/decompiler, the guard can *load* the patched classes to
prove they link and behave, and `patchFormat`/`widenFormat`/`artifactFormat`/
`classFileRelease` are versioned fields in the record so any future format or JDK
change is detected and re-patching is forced instead of silently reusing stale
output.

## 6. Benchmark results (Cases A–G)

Same machine, same Java, same MC version, same world dir.

| Case | Before | After | Notes |
|---|---|---|---|
| **A cold** (fresh home) | Done **34.96 s**; launcher-side 20.7 s; downloads 19.698 s | Done **27.85 s** (−7.1 s / −20.3%); launcher-side **16.91 s**; artifacts 15.876 s — libraries now overlap the server-jar transfer instead of standing idle behind it | one relaunch now (stage-0 cached) |
| **B warm** (same home) | 9.30 s (runtime ready ~0.15 s) | **9.39 s** — unchanged, as designed; `(warm start; cache hit)` | fully offline; with `-JvmFlags`: 8.96 s vs 8.83 s before |
| **C corrupt artifact** | n/a | Done **10.23 s**; resolve 635 ms (drift check), re-verify **CACHED** 362 ms (no 62 MB re-download), re-patch 344 ms, `(cold start; cache miss, rebuilt)` | recovery works end-to-end |
| **D interrupted download** | n/a | covered by tests: fail-fast batch cancel; corrupt-cache recovery; lock timeout report | manual timing of a mid-transfer kill not meaningful; unit-tested |
| **E offline, valid cache** | 9.30 s | **9.39 s**; warm2 log shows zero Mojang contact (no resolve/fetch lines) | warm is offline by construction; offline cold/rebuild + offline missing-artifact error are unit-tested |
| **F updated patch set** | n/a | identity invalidates on patch fingerprint / server SHA-1 / format change → rebuild; unit-tested | not benchmarked manually (would require re-packaging the jar) |
| **G concurrent launches** | n/a | raceA **17.19 s** `(cold start; cache miss, rebuilt)`; raceB **17.44 s** `(cold start, ready by a concurrent launch; cache hit)`; single rebuild; both ready the same wall second; no stale `.rebuild.lock`/`.writing` left | raceB's later MC boot error is vanilla's world-dir lock (two servers on one directory), not a Veltis failure; cross-process serialization also unit-tested |

## 7. Tests and builds

- `:patch-engine:test` — **236 passed, 0 failed** (222 existing + 14 new: 5 `RebuildLock`, 5 offline server-install `prepare()` harness, 4 downloader incl. the server-and-libraries overlap).
- `./gradlew build` — **BUILD SUCCESSFUL** (all modules; `server` shows only
  pre-existing `sun.misc.Unsafe` warnings).
- The Gradle pipeline task `prepareVeltisRuntime` itself now prints the new line:
  `Veltis runtime ready in 717ms (warm start; cache hit)` — the build server
  shares the same `prepare()` implementation.

## 8. Remaining bottlenecks

1. **Mojang CDN path for the 62.3 MB server jar** — **aggregate-capped**; measured 8 parallel ranged streams at the same total throughput as one (6.26 s vs 5.72 s, ~10 MB/s). A chunked downloader was considered and rejected on that evidence. Cold download time varies 5.7–16 s by the route's state of the day. To move this floor as code you would have to mirror/re-host the artifact — out of scope here.
2. **Minecraft JVM pre-boot ~7 s** (measured: ~5 s to the `Environment` line — class init plus the `ServicesEnvironment`/`discoveryUrl` HTTP probe — then ~2 s to server start). A Shulker source patch that pins `discoveryUrl` instead of probing could reclaim the probe's share (~1–2 s), but it changes a networked runtime behavior and needs its own review; deliberately not bundled into this work.
3. **Fresh level prep ~2.6 s** on the first run — vanilla world generation, per world, unavoidable. A restarting server with an existing world pays ~14 ms here instead.
4. **JVM-flag relaunch ~0.5 s** — start scripts should carry
   `--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow` to
   skip it (documented in the launcher; `bench.ps1 -JvmFlags` proves the flag
   variant).
5. Honest cold floor on this machine ≈ 11–12 s **with zero downloads** (launcher
   ~1.8 s + pre-boot ~7 s + patch ~0.3 s + level prep ~2.6 s). A total-Done
   "<10 s" cold start on a fresh home is therefore **not reachable on this
   machine even at infinite bandwidth**; warm startup — the operational steady
   state every deployed server pays on restart — **is already 8.8–9.4 s and
   under the target**.

## 9. Migration / operations notes

- **No cache format change** → no migration. Future format changes ride the
  versioned identity fields.
- Rebuilds are serialized per home/version via the ephemeral `.rebuild.lock`;
  overlapping launches converge on one rebuild and the losers re-validate.
- A cold rebuild (patch-set update, corrupt artifact, Mojang re-publish) always
  re-resolves Mojang metadata and re-verifies the artifact SHA-1 before patching;
  failure to match names expected vs actual SHA-1 and never fabricates output.
- Warm starts are fully offline; a corrupted runtime JAR is detected on the cheap
  validation and rebuilt **from the cached verified vanilla artifact** (no
  re-download).
- `veltis.jar` still needs the bootstrap `libraries/` beside it (stage-0 fetch).
- Bench/ops note: measure with the real JVM binary, not the `javapath` wrapper;
  tear down the whole process tree between runs.
- **Pending, not part of this work:** the earlier Nightly-fix changes remain
  uncommitted on `Fixes` in both repos (server: `server/.../Worker.java` staged in
  this repo; website: channel model + CI). Review and commit them separately.
  This session's changes are also uncommitted on `Fixes`.