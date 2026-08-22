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
(`.github/workflows/build.yml`), as the `gopack-cpp-windows-x64`,
`gopack-cpp-macos-universal`, and `gopack-cpp-linux-x64` workflow artifacts
(or from a local `cmake --build build --config Release` there, in
`build/jni/cpp/`). The macOS build is a single universal (arm64+x86_64)
binary, so no per-architecture split is needed on that side. The Linux
build is x86_64 only, and is deliberately compiled inside an old-glibc
container (manylinux2014 / CentOS 7, glibc 2.17) rather than on the CI
runner's own (much newer) glibc, so `libgopack_jni.so` loads on a wide
range of still-current Linux distros instead of only ones at least as new
as the build machine -- see the workflow file's comments for the full
rationale. There is currently no Linux/arm64 build.

This directory is empty until these files are placed here by hand (or by a
future CI step). Until then, any code that calls
`GOPackNative`/`NativeLib.ensureLoaded("gopack_jni")` will throw a
`JNIException` explaining exactly that, rather than failing silently.
