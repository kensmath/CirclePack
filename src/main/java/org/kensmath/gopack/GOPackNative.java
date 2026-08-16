package org.kensmath.gopack;

/**
 * JNI bridge to the GOPack C++ core engine (gopack_jni native library).
 *
 * <p>CirclePack bundles the platform-appropriate 'gopack_jni' build inside
 * its own jar (see 'cdeps/' and 'JNI.NativeLib' in the CirclePack
 * repository) and extracts + loads it on first use via
 * {@code JNI.NativeLib.ensureLoaded("gopack_jni")} below -- not via
 * {@link System#loadLibrary(String)} and {@code java.library.path}, since a
 * library bundled as a jar resource isn't reachable that way. Loading only
 * happens the first time this class is actually referenced (ordinary Java
 * static-initializer semantics: runs exactly once, thread-safe), so simply
 * not touching this class until a large packing actually needs GOPack keeps
 * native-library loading out of CirclePack's startup path.
 *
 * <p>Every native method below is a direct 1:1 mapping onto the C++ core API
 * in {@code core/include/gopack/Packer.h} (GOPack-cpp repository); see that
 * header for the authoritative semantics. This class intentionally carries
 * no packing logic of its own -- it is a pure bridge.
 *
 * <p>Source of truth for this file and {@link GOPackException} is the
 * GOPack-cpp repository ({@code jni/java/org/kensmath/gopack/}); they are
 * vendored here as plain source (not a jar dependency), since CirclePack is
 * currently the only consumer. If that changes, keep this file in sync with
 * upstream by hand, aside from the static-block loading strategy below,
 * which is CirclePack-specific and intentionally diverges from upstream's
 * {@code System.loadLibrary} default.
 */
public final class GOPackNative {

    static {
        JNI.NativeLib.ensureLoaded("gopack_jni");
    }

    private GOPackNative() {}

    /**
     * Loads a *.p packing/triangulation file (the CirclePack-compatible
     * format documented in GOPack's docs/GO_Formats.txt) and computes a
     * maximal packing (mode 1: max pack in the disc/plane, or on the sphere
     * if the complex has no boundary).
     *
     * <p>This bridge method only exposes maximal-packing mode (mode 1).
     * Polygonal/rectangle packing (mode 2) is ported in the C++ core
     * ({@code Packer::setMode}/{@code setPolyCenters}/{@code setRectCenters})
     * but not yet wired up to a JNI entry point -- the CLI's {@code
     * --polygon} flag is the only way to reach it today.
     *
     * <p>Radii only (no centers) -- see {@link #computeMaximalPackingFromComplex}
     * for the in-memory counterpart, which returns both.
     *
     * @param inputPath  path to a *.p triangulation/packing file
     * @param geometryHint reserved for future use (currently ignored --
     *                     geometry is read from the file's GEOMETRY: field);
     *                     pass 0
     * @param tolerance    reserved for future use (currently ignored -- the
     *                     port uses GOPack's fixed 0.01 visual-error cutoff);
     *                     pass 0.0
     * @param maxPasses    upper bound on riffle passes (GOPack default is 20)
     * @return euclidean radii for every vertex, as an array of length
     *         nodeCount+1 where index v holds vertex v's radius for
     *         v = 1..nodeCount (matching GOPack/CirclePack's own 1-indexed
     *         vertex numbering -- the same convention used throughout the
     *         C++ core); index 0 is unused. Throws {@link GOPackException}
     *         if the native call fails or the file cannot be read.
     */
    public static native double[] computeMaximalPacking(
            String inputPath, int geometryHint, double tolerance, int maxPasses)
            throws GOPackException;

    /**
     * The in-memory counterpart to {@link #computeMaximalPacking}: computes a
     * maximal packing (mode 1) directly from a triangulation already held in
     * memory -- e.g. CirclePack's own per-vertex neighbor ("flower"/"bouquet")
     * data -- instead of a *.p file path. Skips both the file write a caller
     * would otherwise need to serialize its in-memory complex to text, and
     * the native side's own text parsing (readpack()) to read it back; for
     * large complexes that parsing step can dominate the actual packing
     * computation, which is the whole reason this entry point exists. See
     * {@code gopack::Packer::loadComplex} in {@code core/src/PackerIO.cpp}
     * for the full semantics this mirrors.
     *
     * @param nodeCount number of vertices in the complex
     * @param flowers   length {@code nodeCount+1}, 1-indexed ({@code
     *                  flowers[0]} is ignored/unused). {@code flowers[v]} is
     *                  vertex v's petal list, in the same convention as the
     *                  *.p FLOWERS format (CirclePack's own "bouquet", see
     *                  {@code packing.PackData#getBouquet()}): CLOSED (first
     *                  element == last element) iff v is an interior vertex,
     *                  OPEN (first != last) iff v is a boundary vertex.
     * @param geometry  0 = Euclidean, -1 = Hyperbolic, +1 = Spherical
     *                  (matches the *.p file's GEOMETRY: field, {@code
     *                  packing.PackData#hes}, and the C++ core's {@code
     *                  gopack::Geometry} enum values directly)
     * @param tolerance reserved for future use (currently ignored -- the
     *                  port uses GOPack's fixed 0.01 visual-error cutoff);
     *                  pass 0.0
     * @param maxPasses upper bound on riffle passes (GOPack default is 20)
     * @return a 3-row {@code double[][]}, each row of length {@code
     *         nodeCount+1} (1-indexed, index 0 unused), matching {@link
     *         #computeMaximalPacking}'s indexing convention:
     *         <ul>
     *           <li>{@code result[0]} -- radii</li>
     *           <li>{@code result[1]} -- center real parts</li>
     *           <li>{@code result[2]} -- center imaginary parts</li>
     *         </ul>
     *         Unlike {@link #computeMaximalPacking}, this returns centers as
     *         well as radii, since GOPack computes both together as part of
     *         producing a valid packing. These are GOPack's internal working
     *         values: per {@code core/include/gopack/Packer.h}, GOPack always
     *         computes in euclidean coordinates regardless of {@code
     *         geometry} -- no hyperbolic/spherical conversion (the
     *         {@code eToHData}/{@code eToSData} logic in {@code writepack()})
     *         is applied before these are returned. For a Euclidean packing
     *         ({@code geometry == 0}) these can be written straight into
     *         {@code PackData.setRadius}/vertex centers; for hyperbolic or
     *         spherical packings, CirclePack is responsible for whatever
     *         conversion its own geometry model requires before using them
     *         -- not yet wired up on the CirclePack side as of this writing.
     *         Throws {@link GOPackException} if the native call fails (e.g. a
     *         malformed complex -- wrong array lengths, no interior vertex,
     *         etc.).
     */
    public static native double[][] computeMaximalPackingFromComplex(
            int nodeCount, int[][] flowers, int geometry, double tolerance, int maxPasses)
            throws GOPackException;

    /** Returns the linked native library's version string, for diagnostics. */
    public static native String nativeVersion();
}
