package rePack;

import JNI.RandomComplexResult;

import complex.Complex;
import dcel.CombDCEL;
import dcel.PackDCEL;
import exceptions.CombException;
import geometry.CircleSimple;
import geometry.HyperbolicMath;
import geometry.SphericalMath;
import packing.CPdrawing;
import packing.PackData;

/**
 * Converts a native GOPack random-triangulation result
 * ({@link RandomComplexResult}) into a standalone
 * CirclePack {@link PackData}, ready to be swapped into a pack slot via
 * {@code CirclePack.cpb.swapPackData}.
 *
 * <p>Shared by every GOPack-backed random generator command (e.g.
 * {@code random_disc}, and eventually {@code random_sphere}/
 * {@code random_square}/{@code random_rectangle}/{@code random_tri}), since
 * they all hand back the same {@link RandomComplexResult} shape: this class
 * has no generator-specific logic in it.
 *
 * <p>{@code result}'s {@code flowers} are turned back into a DCEL the same
 * way CirclePack reads a *.p file's own FLOWERS/BOUQUET: section (see
 * {@code packing.ReadWrite}), via {@code CombDCEL.getRawDCEL}.
 *
 * <p><b>{@code centersRe}/{@code centersIm} are NOT uniformly "raw euclidean"
 * across generators</b> -- despite what an earlier version of this comment
 * claimed. {@code Packer.h} does always compute internally in euclidean
 * coordinates when it actually riffles, but most of these generators never
 * riffle at all (see {@code JNI.GOPackNative}'s class doc): {@link
 * JNI.GOPackNative#computeRandomDisc} is the one generator that genuinely
 * riffles, so its {@code centersRe}/{@code centersIm} really are raw
 * euclidean disc-model values needing {@link HyperbolicMath#e_to_h_data}'s
 * conversion (the same one {@code rePack.HypPacker}/{@code SphPacker} apply
 * to GOPack's actual max-pack results) to become hyperbolic centers. {@link
 * JNI.GOPackNative#computeRandomSphere}, by contrast, is never euclidean at
 * any point in its pipeline -- {@code randTriangulationSphere()} places
 * points directly on the unit sphere and returns their real {@code
 * (theta,phi)} position (see {@code RandomGen.h}'s {@code
 * RandTriangulationSphereResult::Z} doc and {@code
 * gopack::geom::projVecToS()}), which is already CirclePack's own native
 * spherical-center representation -- compare {@code
 * geometry.SphericalMath#proj_vec_to_sph}'s Javadoc ("{@code @param z
 * Complex, (theta,phi) center}") and {@code
 * komplex.Triangulation}'s own {@code hes>0} branch, which assigns a raw
 * triangulation's already-3D-projected node positions straight into {@code
 * p.setCenter} with no stereographic re-projection. Running an already-
 * {@code (theta,phi)} pair back through {@link SphericalMath#e_to_s_data}
 * (which expects a genuine planar euclidean center to stereographically
 * project) reinterprets {@code theta} as a planar x-coordinate and {@code
 * phi} as a planar y-coordinate and projects that nonsense point onto the
 * sphere -- garbage centers, even though the triangulation's combinatorics
 * (which never pass through this loop) stay perfectly correct. So the
 * spherical branch below assigns {@code centersRe}/{@code centersIm}
 * directly, unconverted -- see the loop's own comment.
 * {@link JNI.GOPackNative#computeRandomTri}/{@link
 * JNI.GOPackNative#computeRandomRectangle}/{@link
 * JNI.GOPackNative#computeRandomSquare} are euclidean-geometry raw
 * triangulations (never riffled either), so their centers need no
 * conversion at all -- the euclidean branch below already reflects that.
 *
 * @author Claude, 8/2026
 */
public class GORandom {

	private GORandom() {} // static utility class only

	/**
	 * Build a standalone PackData from a GOPack random-generator result.
	 * @param result RandomComplexResult, non-null
	 * @return PackData, ready for swapPackData
	 */
	public static PackData buildPackData(RandomComplexResult result) {
		PackData p=new PackData((CPdrawing)null);
		p.hes=result.geometry;

		PackDCEL pdc=CombDCEL.getRawDCEL(result.flowers,result.alpha);
		if (pdc==null)
			throw new CombException(
					"GOPack random generator: failed to build DCEL from returned flowers");
		pdc.fixDCEL(p); // sets p.nodeCount, attaches pdc as p.packDCEL
		if (result.gamma>0)
			p.packDCEL.setGamma(result.gamma);

		// Only computeRandomDisc (p.hes<0) ever actually riffles, so it's
		// the only case whose centersRe/centersIm are raw euclidean values
		// needing e_to_h_data's disc-model-to-hyperbolic conversion.
		// computeRandomSphere (p.hes>0) never riffles either -- its
		// centersRe/centersIm are already real (theta,phi) spherical
		// positions (see this class's own doc comment above), so they're
		// assigned directly, exactly like the euclidean (p.hes==0) case
		// just below assigns its own already-final raw positions.
		for (int v=1;v<=p.nodeCount;v++) {
			Complex ez=new Complex(result.centersRe[v],result.centersIm[v]);
			double er=result.radii[v];
			if (p.hes<0) { // hyperbolic: genuinely raw euclidean, convert
				CircleSimple sc=HyperbolicMath.e_to_h_data(ez,er);
				p.setCenter(v,new Complex(sc.center));
				p.setRadius(v,sc.rad);
			}
			else { // spherical or euclidean: already final, no conversion
				p.setCenter(v,ez);
				p.setRadius(v,er);
			}
		}

		// polygonal generators (rectangle/square) report their corners in
		// ccw order; move them to vertex slots 1..n, matching the existing
		// convention used for rectangles in the Java 'rand_tri' command.
		if (result.corners!=null && result.corners.length>=3) {
			for (int i=0;i<result.corners.length;i++)
				p.packDCEL.swapNodes(result.corners[i],i+1);
		}

		p.fillcurves();
		p.set_plotFlags(); // set all to 1
		return p;
	}

}
