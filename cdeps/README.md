# cdeps/

Prebuilt native (JNI) shared libraries get dropped here before packaging.
`src/assembly/circlepack.xml` sweeps `**/*.dll`, `**/*.so`, `**/*.dylib`, and
`**/*.jnilib` from this directory into the built jar's root, preserving
subdirectory structure. `JNI.NativeLib.ensureLoaded(...)` looks these up at
runtime as jar resources under `/<platform>/<libFileName>`.

Expected layout for the GOPack JNI bridge (`org.kensmath.gopack.GOPackNative`):

    cdeps/
      win64/
        gopack_jni.dll
      macos/
        libgopack_jni.dylib

Both come from the GOPack-cpp repository's CI build
(`.github/workflows/build.yml`), as the `gopack-cpp-windows-x64` and
`gopack-cpp-macos-universal` workflow artifacts (or from a local
`cmake --build build --config Release` there, in `build/jni/cpp/`). The
macOS build is a single universal (arm64+x86_64) binary, so no
per-architecture split is needed on that side.

This directory is empty until those two files are placed here by hand (or by
a future CI step). Until then, any code that calls
`GOPackNative`/`NativeLib.ensureLoaded("gopack_jni")` will throw a
`JNIException` explaining exactly that, rather than failing silently.
