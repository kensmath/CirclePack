package JNI;

/**
 * The result of a native "geometrically random" triangulation generator.
 *
 * <p>Most of these generators ({@link GOPackNative#computeRandomTri}, {@link
 * GOPackNative#computeRandomSphere}, {@link
 * GOPackNative#computeRandomRectangle}, {@link
 * GOPackNative#computeRandomSquare}) deliberately return a <b>raw,
 * unpacked</b> triangulation -- combinatorics plus the actual point layout
 * the random generator produced -- with {@code riffle()} never called; see
 * each method's own Javadoc for what {@link #radii}/{@link #centersRe}/
 * {@link #centersIm} mean in that case (typically: {@link #radii} is a
 * meaningless uniform placeholder, and {@link #centersRe}/{@link #centersIm}
 * hold the real raw positions). The lone exception is {@link
 * GOPackNative#computeRandomDisc}, which genuinely does riffle to a maximal
 * packing -- see that method's Javadoc.
 *
 * <p>Unlike {@link GOPackNative#computeMaximalPacking} and
 * {@link GOPackNative#computeMaximalPackingFromComplex} -- where the caller
 * already supplies the combinatorics and only needs radii back -- a random
 * generator invents the combinatorics on the native side, so the caller
 * needs the full complex back: the flowers (to build its own vertex/complex
 * objects), and both the radii and centers of the computed packing (not just
 * radii), plus the bookkeeping fields (geometry, alpha, gamma, corners) that
 * describe how the packing was set up.
 *
 * <p>All per-vertex arrays ({@link #flowers}, {@link #radii},
 * {@link #centersRe}, {@link #centersIm}) are 1-indexed, length
 * {@code nodeCount+1} with index 0 unused/ignored -- the same convention
 * used throughout the native core and the rest of this JNI bridge, so a
 * caller never has to apply an off-by-one translation.
 */
public final class RandomComplexResult {

    /** Number of vertices in the generated complex. */
    public final int nodeCount;

    /**
     * v's petal list, 1-indexed ({@code flowers[0]} is an empty/unused
     * placeholder). Matches the *.p FLOWERS format convention: CLOSED (first
     * element == last element) iff v is an interior vertex, OPEN (first !=
     * last) iff v is a boundary vertex.
     */
    public final int[][] flowers;

    /**
     * 1-indexed, {@code radii[0]} unused. For {@link
     * GOPackNative#computeRandomDisc} (the one generator that actually
     * packs), these are real Euclidean radii after {@code riffle()}. For
     * every other generator in this class -- {@link
     * GOPackNative#computeRandomTri}, {@link GOPackNative#computeRandomSphere},
     * {@link GOPackNative#computeRandomRectangle}, {@link
     * GOPackNative#computeRandomSquare} -- {@code riffle()} never runs, so
     * this is instead a uniform placeholder ({@code 0.5} for every vertex)
     * with no packing meaning -- ignore it and use {@link #centersRe}/
     * {@link #centersIm} for the raw layout's actual point positions.
     */
    public final double[] radii;

    /**
     * Real part of each vertex's center, 1-indexed, index 0 unused. For
     * {@link GOPackNative#computeRandomDisc}, this is the real center after
     * {@code riffle()}. For every other generator in this class, this is
     * instead the vertex's raw (pre-packing) x coordinate as placed by the
     * random Delaunay generator -- for {@link
     * GOPackNative#computeRandomSphere} specifically, this is the vertex's
     * {@code theta} polar coordinate ({@code projVecToS()}'s convention),
     * not a planar x coordinate.
     */
    public final double[] centersRe;

    /**
     * Imaginary part of each vertex's center, 1-indexed, index 0 unused.
     * For {@link GOPackNative#computeRandomDisc}, this is the real center
     * after {@code riffle()}. For every other generator in this class, this
     * is instead the vertex's raw (pre-packing) y coordinate as placed by
     * the random Delaunay generator -- for {@link
     * GOPackNative#computeRandomSphere} specifically, this is the vertex's
     * {@code phi} polar coordinate ({@code projVecToS()}'s convention), not
     * a planar y coordinate.
     */
    public final double[] centersIm;

    /**
     * 0 = Euclidean, -1 = Hyperbolic, +1 = Spherical -- matches
     * {@code gopack::Geometry}'s underlying values and the *.p file's
     * GEOMETRY: field, same convention already used by
     * {@link GOPackNative#computeMaximalPackingFromComplex}'s geometry
     * parameter.
     */
    public final int geometry;

    /** The packing's alpha (centering) vertex, chosen by the generator. */
    public final int alpha;

    /** The packing's gamma vertex (if any; 0 if unset), chosen by the generator. */
    public final int gamma;

    /**
     * Corner vertex numbers, in counterclockwise order -- populated by
     * {@link GOPackNative#computeRandomRectangle}/{@link
     * GOPackNative#computeRandomSquare} (identified via an internal {@code
     * setMode(2, ...)} bookkeeping call that records corners without
     * riffling -- see those methods' Javadoc); empty for every other
     * generator in this class ({@link GOPackNative#computeRandomTri}, {@link
     * GOPackNative#computeRandomSphere}, {@link
     * GOPackNative#computeRandomDisc}), none of which have a polygonal
     * "corners" concept.
     */
    public final int[] corners;

    public RandomComplexResult(int nodeCount, int[][] flowers, double[] radii,
            double[] centersRe, double[] centersIm, int geometry, int alpha, int gamma,
            int[] corners) {
        this.nodeCount = nodeCount;
        this.flowers = flowers;
        this.radii = radii;
        this.centersRe = centersRe;
        this.centersIm = centersIm;
        this.geometry = geometry;
        this.alpha = alpha;
        this.gamma = gamma;
        this.corners = corners;
    }
}
