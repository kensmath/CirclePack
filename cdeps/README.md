# cdeps/

Prebuilt native (JNI) shared libraries get dropped here before packaging.
`src/assembly/circlepack.xml` sweeps `**/*.dll`, `**/*.so`, `**/*.dylib`, and
`**/*.jnilib` from this directory into the built jar's root, preserving
subdirectory structure. `JNI.NativeLib.ensureLoaded(...)` looks these up at
runtime as jar resources under `/<platform>/<libFileName>`.

Expected layout for the GOPack JNI bridge (`JNI.GOPackNative`):

    cdeps/
      win64/
        gopack_jni.dll
      macos/
        libgopack_jni.dylib
      linux64/
        libgopack_jni.so

All three come from the GOPack-cpp repository's CI build
(`.github/workflows/build.yml`). The macOS build is a single universal
(arm64+x86_64) binary, so no per-architecture split is needed on that side.
The Linux build is x86_64 only, and is deliberately compiled inside an
old-glibc container (manylinux2014 / CentOS 7, glibc 2.17) rather than on
the CI runner's own (much newer) glibc, so `libgopack_jni.so` loads on a
wide range of still-current Linux distros instead of only ones at least as
new as the build machine -- see the workflow file's comments for the full
rationale. There is currently no Linux/arm64 build.

**These files are refreshed automatically by CirclePack's own CI.** Its
`.github/workflows/build.yml` runs a "Fetch latest GOPack-cpp native
libraries" step, right before `mvn -B package`, that downloads
GOPack-cpp's rolling `latest` GitHub Release (published by GOPack-cpp's own
CI on every push to its `master` branch) and overwrites whatever is sitting
in `cdeps/` for that build. So every CirclePack CI build packages
GOPack-cpp's current `master`, not whatever happens to be checked into this
repo.

The files checked into `cdeps/` in git are therefore only a **fallback for
local, offline builds** -- if you run `mvn package` on your own machine
without CI's fetch step, you get whatever was last committed here. Keep
them reasonably current by hand (copy from a local
`cmake --build build --config Release` in GOPack-cpp, from
`build/jni/cpp/`, or download GOPack-cpp's `latest` release assets
yourself), or just accept that a local build may lag behind GOPack-cpp's
newest changes until you refresh them.

If this directory is ever empty, any code that calls
`GOPackNative`/`NativeLib.ensureLoaded("gopack_jni")` will throw a
`JNIException` explaining exactly that, rather than failing silently.
