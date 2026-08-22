package JNI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import exceptions.JNIException;

/**
 * Loads native (JNI) shared libraries that are bundled inside CirclePack's
 * own jar under 'cdeps/&lt;platform&gt;/&lt;libFileName&gt;' (see
 * 'src/assembly/circlepack.xml', which sweeps '**&#47;*.dll', '**&#47;*.so',
 * '**&#47;*.dylib', and '**&#47;*.jnilib' from 'cdeps/' into the jar root,
 * preserving subdirectory structure -- see also 'cdeps/README.md').
 *
 * <p>A JNI shared library can't be loaded directly out of a jar -- the JVM's
 * 'System.load'/'System.loadLibrary' need a real file on disk -- so this
 * class extracts the right platform build to a temp file on first use and
 * loads it from there via an absolute path. It deliberately does NOT use
 * 'System.loadLibrary(name)' (which searches 'java.library.path'), since
 * mutating that property after JVM startup doesn't reliably take effect.
 *
 * <p>Call {@link #ensureLoaded(String)} from the static initializer of the
 * Java class that declares the corresponding 'native' methods (see
 * 'JNI.GOPackNative'). Java only runs a class's static
 * initializer the first time that class is actually referenced, and
 * guarantees it runs exactly once and is thread-safe -- so this gives
 * automatic lazy, one-time loading with no extra bookkeeping needed at the
 * call site: as long as CirclePack doesn't touch 'GOPackNative' until a
 * large packing actually needs it, the native library extraction/load cost
 * is paid then, not at startup.
 *
 * <p>Supports Windows, macOS, and Linux (the three platforms GOPack-cpp's
 * CI builds for). The macOS build is a single universal (arm64+x86_64)
 * binary, so no per-architecture branching is needed there. The Linux build
 * is x86_64 only (no arm64), and is deliberately compiled against an old
 * glibc baseline (CentOS 7 / manylinux2014, glibc 2.17 -- see
 * '.github/workflows/build.yml' in the GOPack-cpp repository) rather than
 * whatever glibc the CI runner itself happens to have, so the resulting
 * 'libgopack_jni.so' loads on a wide range of still-current Linux distros
 * instead of only ones at least as new as the build machine.
 *
 * @author kstephe2 (revived/rewritten 8/2026 for the GOPack JNI bridge;
 *         supersedes the pre-2022 version of this class, which extracted to
 *         the working directory rather than a temp directory, had an ad hoc
 *         per-OS naming scheme, and was unused after the project moved to
 *         ProcessBuilder for triangle/qhull)
 */
public class NativeLib {

	// libBaseName -> already loaded, so a repeat call (e.g. if another
	// native class later shares this loader) is a cheap no-op rather than
	// re-extracting and re-loading.
	private static final Set<String> loaded = new HashSet<String>();

	/**
	 * Make sure the native library 'libBaseName' (e.g. "gopack_jni" -- no
	 * platform-specific prefix/suffix) is loaded into this JVM, extracting
	 * it from the jar first if necessary. Safe to call more than once.
	 * @param libBaseName String, e.g. "gopack_jni"
	 * @throws JNIException if the platform isn't supported, the bundled
	 *         resource is missing, or extraction/loading fails
	 */
	public static synchronized void ensureLoaded(String libBaseName) {
		if (loaded.contains(libBaseName))
			return;

		String platformDir = platformDir();
		String libFileName = System.mapLibraryName(libBaseName);
			// e.g. "gopack_jni.dll" on Windows, "libgopack_jni.dylib" on Mac
		String resourcePath = "/" + platformDir + "/" + libFileName;

		InputStream in = NativeLib.class.getResourceAsStream(resourcePath);
		if (in == null) {
			throw new JNIException("Bundled native library not found at '"
					+ resourcePath + "' in the jar. Was '" + libFileName
					+ "' built and placed under cdeps/" + platformDir
					+ "/ before packaging? (see cdeps/README.md)");
		}

		File outFile = new File(System.getProperty("java.io.tmpdir"),
				"circlepack_" + ProcessHandle.current().pid() + "_" + libFileName);
		try {
			try (FileOutputStream out = new FileOutputStream(outFile)) {
				byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) > 0)
					out.write(buf, 0, n);
			}
		} catch (IOException iox) {
			throw new JNIException("Failed extracting native library '"
					+ libFileName + "' to temp directory: " + iox.getMessage());
		} finally {
			try {
				in.close();
			} catch (IOException ignore) {
				// nothing to do
			}
		}
		outFile.deleteOnExit();

		System.load(outFile.getAbsolutePath());
		loaded.add(libBaseName);
	}

	/**
	 * @return the 'cdeps/' subdirectory name to use for the running platform
	 * @throws JNIException if the platform isn't one GOPack-cpp currently
	 *         builds for
	 */
	private static String platformDir() {
		String os = System.getProperty("os.name", "").toLowerCase();
		if (os.contains("win"))
			return "win64";
		if (os.contains("mac") || os.contains("darwin"))
			return "macos";
		if (os.contains("linux")) {
			String arch = System.getProperty("os.arch", "").toLowerCase();
			// GOPack-cpp's Linux CI build is x86_64 only (see
			// .github/workflows/build.yml) -- unlike the macOS universal
			// binary, there's no single file covering both architectures,
			// so an arm64/aarch64 JVM has no bundled library to load yet.
			if (arch.contains("aarch64") || arch.contains("arm"))
				throw new JNIException("No bundled native library for Linux/"
						+ System.getProperty("os.arch")
						+ "; GOPack-cpp's Linux build is currently x86_64 only.");
			return "linux64";
		}
		throw new JNIException("No bundled native library for platform '"
				+ System.getProperty("os.name")
				+ "'; only Windows, macOS, and Linux (x86_64) are currently supported.");
	}
}
