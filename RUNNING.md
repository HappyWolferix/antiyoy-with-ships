# Running Antiyoy on desktop

The upstream repo ships only `core/src` and `assets` — no root Gradle files, no
launcher, no wrapper. The README tells you to generate a project with the
libGDX `gdx-setup` tool and paste the sources in. That still works, but it needs
the Android SDK and a GUI setup tool.

This directory adds the minimum scaffolding to skip all of that and run the game
directly on the desktop:

| File | Purpose |
| --- | --- |
| `settings.gradle` | Declares the `core` and `desktop` modules |
| `build.gradle` | libGDX dependencies, LWJGL3 desktop backend, run config |
| `desktop/src/.../DesktopLauncher.java` | Entry point (not in upstream) |
| `gradlew`, `gradle/wrapper/` | Gradle 8.7 wrapper |
| `Makefile` | `make run` / `make build` / `make clean` |

Upstream files (`core/build.gradle`, everything under `core/src`) are unmodified.

## Quick start

```sh
make run
```

That's it — the Makefile locates the JDK, so no `JAVA_HOME` export is needed.
Also available: `make build` (compile only) and `make clean`.

The equivalent without make:

```sh
export JAVA_HOME=~/.local/opt/jdk-21.0.12+8
./gradlew :desktop:run
```

## Prerequisites

A JDK 17+ (JDK **21** is what this was verified against) and a working X11 or
XWayland display. Nothing else — Gradle comes from the wrapper, and libGDX is
pulled from Maven Central on first build.

If you have no JDK (a JRE alone is not enough — you need `javac`), install one
into your home directory without root:

```sh
mkdir -p ~/.local/opt && cd ~/.local/opt
curl -L -o jdk21.tar.gz \
  "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
tar xzf jdk21.tar.gz && rm jdk21.tar.gz
```

`make` finds any `~/.local/opt/jdk-21*` automatically. It also respects an
already-exported `JAVA_HOME` if that points at a real JDK, so a system install
takes precedence.

To set it permanently for non-make use, add to `~/.bashrc`:

```sh
export JAVA_HOME=~/.local/opt/jdk-21.0.12+8
export PATH="$JAVA_HOME/bin:$PATH"
```

A distro package (`sudo apt install openjdk-21-jdk`) works equally well if you
have root.

## Notes / gotchas

**libGDX is pinned to 1.9.10 and should stay there.** Version 1.9.11 changed
`InputProcessor.scrolled()` from `(int, int)` to `(float, float)`.
`YioGdxGame` implements the old signature, so any newer libGDX fails to compile
with:

```
YioGdxGame is not abstract and does not override abstract method scrolled(float,float)
```

Bumping libGDX therefore means patching `YioGdxGame.java` (two lines: the method
signature at ~line 796 and its `@Override`). Left alone so upstream source stays
pristine.

**Working directory matters.** Every asset is loaded by bare relative path
(`Gdx.files.internal("splash.png")`), so the process must start from inside
`assets/`. The `run` task sets `workingDir` accordingly — this is the same
"unable to load some assets" trap the README warns about for IDEA.

**`sourceCompatibility` override.** `core/build.gradle` pins Java 1.6, which
javac 21 rejects (8 is the floor). The root `build.gradle` overrides it to 8 in
an `afterEvaluate` block rather than editing the upstream file. The code itself
is Java 6 era, so it compiles at level 8 without changes.

**Harmless startup warning.** LWJGL 3.2.3 doesn't recognise Java 21's JNI
version and prints:

```
[LWJGL] [ThreadLocalUtil] Unsupported JNI version detected, this may result in a crash.
```

The game runs fine regardless. It goes away on newer libGDX/LWJGL, which is
blocked by the `scrolled()` issue above.

**Window size** is set in `DesktopLauncher.java` (540x960, portrait, since this
is a phone game). Change it there.

## Android

Still requires the upstream route: generate a project with `gdx-setup`
(package `yio.tro.antiyoy`, main class `YioGdxGame`, Freetype checked,
Html/Box2D unchecked), then drop in `core/src` and `assets`. You need the
Android SDK for that; none of the scaffolding here covers it.
