# NobiTerminal v13 — full source snapshot

This repo is the **complete patched app source tree for NobiTerminal v13**
(the version shipped as the v13 APK on 2026-10-26). It exists so the app's
source can be browsed, understood, and edited by hand in one place.

> This snapshot is for viewing and editing. It is **not** the build route.
> The real build route is the generator repo
> (`tale92266-hash/nobiterminal`): it clones upstream, applies the Nobi
> patches, renames the package, builds the bootstrap and the app in GitHub
> Actions, and produces the APK. Edits made here do **not** flow into
> generator builds automatically — to ship an edit, it must become a patch
> in the generator repo.

## What this snapshot was made from

- **Base:** upstream `termux/termux-app`
  `@8629e632fcb95da272221be327db653fb24befe9` (2026-09-26).
- **Patches:** all 25 Nobi patches from generator branch `v13-cleanup`
  `@efe832d0b68dc7c19a2795a6d6ac77675202956f`
  (built on v12-batch `@6b772019`; the 25 app patches are identical to v12 —
  v13 adds no new app patch, only a generator-script cleanup step, see below), applied with GNU
  `patch -p1` in plain lexical order (the same order the generator uses).
  All 25 applied cleanly: 0 `.rej`, 0 `.orig` files.
- **Shipped v13 APK for cross-reference:** 35,489,360 bytes,
  SHA-256 `e745c08e832f2b78c4cbf326389e2d534b957e62caf6906616947927da0ad62e`,
  generator run `40284326355` (cache MISS build, 72 min 18 s — upstream
  termux-packages had moved, so the bootstrap was rebuilt and re-seeded),
  signer
  SHA-256 fingerprint
  `B6:DA:01:48:0E:EF:D5:FB:F2:CD:37:71:B8:D1:02:1E:C7:91:30:4B:DD:6C:4B:F4:1D:3F:AA:BA:D4:8E:E5:E1`.

### The 25 patches, in apply order

1. `nobiterminal-app-name.patch`
2. `nobiterminal-drawer-default.patch`
3. `nobiterminal-neon-default.patch`
4. `nobiterminal-settings-suite.patch`
5. `nobiterminal-tabs-bar.patch`
6. `nobiterminal-v5-core.patch`
7. `nobiterminal-v5-drawer.patch`
8. `nobiterminal-v5-icon-splash.patch`
9. `nobiterminal-v5-nobicode-icons-a.patch`
10. `nobiterminal-v5-nobicode-icons-b.patch`
11. `nobiterminal-v5-nobicode-res.patch`
12. `nobiterminal-v5-nobicode-src.patch`
13. `nobiterminal-v5-tabs-rename.patch`
14. `nobiterminal-v6-batch.patch`
15. `nobiterminal-v7-batch.patch`
16. `nobiterminal-v7-fix.patch`
17. `nobiterminal-v7-fix2.patch`
18. `nobiterminal-v8-extrakeys.patch`
19. `nobiterminal-v9-drawer-notify.patch`
20. `nobiterminal-w10-editor-permissions.patch`
21. `nobiterminal-w11-followup.patch`
22. `nobiterminal-w12-fix-duplicate-permissions.patch`
23. `nobiterminal-w13-fix-vectors-kotlin.patch`
24. `nobiterminal-w14-autorepo.patch`
25. `nobiterminal-w15-keys-settings-theme.patch`

### What is new in v13 (no new app patch)

v13 contains **exactly the same 25 app patches as v12** — no app code
changed. The only change is a **generator build-time cleanup step**
(`remove_dead_nobi_assets()` in the generator scripts, called after
`patch_apps`, before the Gradle build) that deletes 11 dead files from
the build tree before packaging:

- `app/src/main/res/mipmap-{hdpi,mdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`
  and `ic_launcher_round.png` (10 legacy upstream launcher PNGs, the old
  `>_` icons), and
- `app/src/main/res/drawable/banner.png` (1 legacy upstream banner PNG).

These files had no reference anywhere (manifest icon is the Nobi vector
`nobi_app_icon`), but the patch channel cannot delete binary files, so
they rode along in every APK until v12. The cleanup step deletes them
with a post-check that fails the build if any survives (the v13 job log
shows `Dead Nobi assets removed (legacy launcher PNGs + banner.png).`
before Gradle ran, and the shipped v13 APK was verified to contain
**0** of these 11 PNGs).

> **Important nuance — tree vs APK for these 11 files:** the 11 PNGs are
> **still present in this tree** (they are upstream files in the patched
> source). They are absent only from the **shipped v13 APK**, because
> the cleanup is a generator build-time step, not a tree change. Do NOT
> claim this tree == the v13 APK for these files. The v13 APK is
> 37,224 bytes smaller than v12 (partly zip recompression, so the size
> difference is not exactly the ~28 KB of deleted PNGs).

The v12 features below (w15 keys/theme fixes, w14 autorepo, v11
follow-up features) are all still in this tree unchanged.

## Package name: this tree is `com.termux`, not `com.nobiterminal`

That is intentional and matches what the generator patches produce. The
`com.nobiterminal` rename happens **at generator build time, after
patching**, via `TERMUX_APP__PACKAGE_NAME=com.nobiterminal`
(`replace_termux_name` + `migrate_termux_folder_tree` in the generator
scripts): it rewrites `com.termux` → `com.nobiterminal` in text files and
moves the `com/termux/...` Java folders. So:

- If you edit this tree, edit the `com.termux` paths. Do not rename
  things to `com.nobiterminal` here by hand.
- Building this tree directly produces a `com.termux` app. To get a
  `com.nobiterminal` build, build through the generator.

## Signing: local debug builds use the same certificate as the shipped APK

`app/testkey_untrusted.jks` (the upstream debug keystore) is included in
this tree. Its certificate was verified with `keytool`: Owner
`CN=APK Signer, OU=Earth, O=Earth`, SHA-256 fingerprint
`B6:DA:01:48:0E:EF:D5:FB:F2:CD:37:71:B8:D1:02:1E:C7:91:30:4B:DD:6C:4B:F4:1D:3F:AA:BA:D4:8E:E5:E1`
— identical to the shipped v13 APK's signer. A debug build from this tree
therefore signs with the same certificate and installs over the shipped
v13 build. (This is the upstream-published debug keystore, not a private
release key.)

## What is NOT in this snapshot

- **Bootstrap files.** There is no `app/src/main/assets/` here. In CI the
  bootstrap (`bootstrap-aarch64.tar.xz` + `xz` libs) is built from
  `termux/termux-packages`
  `@9ad1480b6e6b4a948ab99074fe1c1ec9c5b23f99` (2026-10-26, the HEAD at
  v13 build time — the v13 build was a cache MISS on this new HEAD and
  re-seeded the bootstrap cache on it, so the next build on this HEAD
  will be a HIT)
  and placed into the app's assets by the generator build. A local build
  from this tree needs bootstrap files supplied separately.
- **Generator CI files.** The generator's workflows, cache system and
  scripts live in the generator repo, not here. The `.github/` folder in
  this tree is upstream Termux's own CI, not NobiTerminal's build.
- **Binary patch hunks.** GNU `patch` cannot apply git binary hunks, so
  any binary file a patch tried to add is absent here — exactly as in
  the generator CI tree (CI uses the same GNU `patch -p1`). The legacy
  `mipmap` PNGs present here are upstream's, not Nobi's — and, per
  the v13 nuance above, they are present here even though the shipped
  v13 APK no longer contains them.

## Building on a phone (ACS): read this first

This snapshot's `main` branch is **not directly buildable in ACS
(AndroidIDE) as-is**. Three modules wire `externalNativeBuild` ndkBuild
in this tree:

| Module | Native lib |
|---|---|
| `app` | `libtermux-bootstrap` (`app/src/main/cpp/Android.mk`) |
| `terminal-emulator` | `libtermux` (`terminal-emulator/src/main/jni/Android.mk`) |
| `termux-shared` | `liblocal-socket` (`termux-shared/src/main/cpp/Android.mk`) |

Phone IDEs cannot run Google NDK's inner `ndk-build` (on v4 this failed
with exit 126 on `termux-shared`). v4 solved it with a separate
`acs-prebuilt` branch: prebuilt `.so` files taken from the shipped APK
plus the `externalNativeBuild` blocks removed, with `main` untouched.
**No v13 `acs-prebuilt` branch was created in this snapshot** — that is a
separate next step if on-phone ACS builds are wanted. Two facts for that
step: the shipped v13 APK contains only `libtermux.so` and
`liblocal-socket.so` (× 4 ABIs each) — it contains **no**
`libtermux-bootstrap.so`, even though the `app` module wires ndkBuild in
this tree, so the `app` module's ndk step needs investigation before any
`acs-prebuilt` treatment. The Gradle wrapper jar
(`gradle/wrapper/gradle-wrapper.jar`) **is** included in this tree, so an
ACS import does not hang on a missing wrapper jar.

## Modules in this tree

`app`, `termux-shared`, `terminal-emulator`, `terminal-view`, plus
`nobicode` (the inbuilt editor, added by the v5 patches and extended by
later batches, including the v11 follow-up features, auto repo setup in
`app/src/main/java/com/termux/app/nobi/NobiRepoSetup.java`, and the v12
keys/theme fixes; v13 adds no app code on top).

## License

Upstream Termux code is GPL-3.0 (see `LICENSE.md`). The Nobi patches on
top are Sam's own changes.
