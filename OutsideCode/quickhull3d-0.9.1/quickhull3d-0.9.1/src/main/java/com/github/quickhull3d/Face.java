package com.github.quickhull3d;

/**
 * Basic triangular face used to form the hull.
 * <p>
 * The information stored for each face consists of a planar normal, a planar
 * offset, and a doubly-linked list of three <a href=HalfEdge>HalfEdges</a>
 * which surround the face in a counter-clockwise direction.
 *
 * @author John E. Lloyd, Fall 2004
 */
public class Face {

	protected static final int VISIBLE = 1;
	protected static final int NON_CONVEX = 2;
	protected static final int DELETED = 3;

	protected double area;

	protected HalfEdge he0;

	protected int mark = VISIBLE;

	protected Face next;
	protected int numVerts;
	protected Vertex outside;

	protected double planeOffset;

	private Point3d centroid;
	private Vector3d normal;

	Vertex pa, pb, pc; // CCW triple defining the face plane for exact predicates

	// Cached plane for fast detToFace() that matches orient(pa,pb,pc, v)
	double pAx, pAy, pAz; // anchor = pa
	double pNx, pNy, pNz; // unnormalized normal = (pb-pa) x (pc-pa)
	double pAbsNx, pAbsNy, pAbsNz; // abs for faster error bound
	double pNxTerm, pNyTerm, pNzTerm;

	public Face() {
		normal = new Vector3d();
		centroid = new Point3d();
		mark = VISIBLE;
	}

	public static Face create(Vertex[] vtxArray, int[] indices) {
		Face face = new Face();
		HalfEdge hePrev = null;
		for (int index : indices) {
			HalfEdge he = new HalfEdge(vtxArray[index], face);
			if (hePrev != null) {
				he.setPrev(hePrev);
				hePrev.setNext(he);
			} else {
				face.he0 = he;
			}
			hePrev = he;
		}
		face.he0.setPrev(hePrev);
		hePrev.setNext(face.he0);

		// compute the normal and offset
		face.computeNormalAndCentroid();
		return face;
	}

	public static Face createTriangle(Vertex v0, Vertex v1, Vertex v2) {
		return createTriangle(v0, v1, v2, 0);
	}

	/**
	 * Constructs a triangule Face from vertices v0, v1, and v2.
	 *
	 * @param v0 first vertex
	 * @param v1 second vertex
	 * @param v2 third vertex
	 */
	public static Face createTriangle(Vertex v0, Vertex v1, Vertex v2, double minArea) {
		Face face = new Face();
		HalfEdge he0 = new HalfEdge(v0, face);
		HalfEdge he1 = new HalfEdge(v1, face);
		HalfEdge he2 = new HalfEdge(v2, face);

		he0.prev = he2;
		he0.next = he1;
		he1.prev = he0;
		he1.next = he2;
		he2.prev = he1;
		he2.next = he0;

		face.he0 = he0;

		// compute the normal and offset
		face.computeNormalAndCentroid(minArea);
		return face;
	}

	public void computeCentroid(Point3d centroid) {
		HalfEdge he = he0;

		// Reference point to reduce cancellation (important when coords are huge)
		Point3d pref = he.head().pnt;

		double sx = 0, sy = 0, sz = 0; // sum of deltas
		double cx = 0, cy = 0, cz = 0; // Kahan compensation
		int n = 0;

		do {
			Point3d p = he.head().pnt;

			double dx = p.x - pref.x;
			double dy = p.y - pref.y;
			double dz = p.z - pref.z;

			// Kahan summation for dx
			double yx = dx - cx;
			double tx = sx + yx;
			cx = (tx - sx) - yx;
			sx = tx;

			double yy = dy - cy;
			double ty = sy + yy;
			cy = (ty - sy) - yy;
			sy = ty;

			double yz = dz - cz;
			double tz = sz + yz;
			cz = (tz - sz) - yz;
			sz = tz;

			n++;
			he = he.next;
		} while (he != he0);

		numVerts = n; // keep consistent

		double inv = 1.0 / n;
		centroid.x = pref.x + sx * inv;
		centroid.y = pref.y + sy * inv;
		centroid.z = pref.z + sz * inv;
	}

	public void computeNormal(Vector3d normal) {
		// Use a local origin to reduce cancellation
		Point3d p0 = he0.head().pnt;

		double nx = 0, ny = 0, nz = 0;
		double cx = 0, cy = 0, cz = 0; // Kahan compensation for nx,ny,nz

		int n = 0;
		HalfEdge he = he0;
		do {
			n++;
			he = he.next;
		} while (he != he0);
		numVerts = n;

		if (n < 3) {
			normal.setZero();
			area = 0;
			return;
		}

		// Sum cross((pi - p0), (p(i+1) - p0)) for i around the polygon
		HalfEdge e = he0;
		do {
			Point3d pi = e.head().pnt;
			Point3d pj = e.next.head().pnt;

			double aix = pi.x - p0.x;
			double aiy = pi.y - p0.y;
			double aiz = pi.z - p0.z;

			double bix = pj.x - p0.x;
			double biy = pj.y - p0.y;
			double biz = pj.z - p0.z;

			// cross(a,b) using fma where it helps rounding:
			double cxp = Math.fma(aiy, biz, -aiz * biy);
			double cyp = Math.fma(aiz, bix, -aix * biz);
			double czp = Math.fma(aix, biy, -aiy * bix);

			// Kahan accumulate into (nx,ny,nz)
			double yx = cxp - cx;
			double tx = nx + yx;
			cx = (tx - nx) - yx;
			nx = tx;

			double yy = cyp - cy;
			double ty = ny + yy;
			cy = (ty - ny) - yy;
			ny = ty;

			double yz = czp - cz;
			double tz = nz + yz;
			cz = (tz - nz) - yz;
			nz = tz;

			e = e.next;
		} while (e != he0);

		normal.x = nx;
		normal.y = ny;
		normal.z = nz;

		area = normal.norm();
		if (!(area > 0) || !Double.isFinite(area)) { // handles 0, NaN, inf
			normal.setZero();
			area = 0;
			return;
		}
		normal.scale(1.0 / area);
	}

	public void computeNormal(Vector3d normal, double minArea) {
		computeNormal(normal);

		if (!(area > 0) || !Double.isFinite(area)) {
			return;
		}

		if (area < minArea) {
			HalfEdge hedgeMax = null;
			double lenSqrMax = 0;

			HalfEdge hedge = he0;
			do {
				double lenSqr = hedge.lengthSquared();
				if (lenSqr > lenSqrMax) {
					hedgeMax = hedge;
					lenSqrMax = lenSqr;
				}
				hedge = hedge.next;
			} while (hedge != he0);

			if (!(lenSqrMax > 0) || hedgeMax == null) {
				return;
			}

			Point3d p2 = hedgeMax.head().pnt;
			Point3d p1 = hedgeMax.tail().pnt;

			double lenMax = Math.sqrt(lenSqrMax);
			double ux = (p2.x - p1.x) / lenMax;
			double uy = (p2.y - p1.y) / lenMax;
			double uz = (p2.z - p1.z) / lenMax;

			double dot = Math.fma(normal.x, ux, Math.fma(normal.y, uy, normal.z * uz));

			double nx = normal.x - dot * ux;
			double ny = normal.y - dot * uy;
			double nz = normal.z - dot * uz;

			double n2 = nx * nx + ny * ny + nz * nz;
			if (!(n2 > 0) || !Double.isFinite(n2)) {
				return;
			}

			double inv = 1.0 / Math.sqrt(n2);
			normal.x = nx * inv;
			normal.y = ny * inv;
			normal.z = nz * inv;
		}
	}

	/**
	 * Computes the distance from a point p to the plane of this face.
	 *
	 * @param p the point
	 * @return distance from the point to the plane
	 */
	public double distanceToPlane(Point3d p) {
		return normal.x * p.x + normal.y * p.y + normal.z * p.z - planeOffset;
	}

	/**
	 * Finds the half-edge within this face which has tail <code>vt</code> and head
	 * <code>vh</code>.
	 *
	 * @param vt tail point
	 * @param vh head point
	 * @return the half-edge, or null if none is found.
	 */
	public HalfEdge findEdge(Vertex vt, Vertex vh) {
		HalfEdge he = he0;
		do {
			if (he.head() == vh && he.tail() == vt) {
				return he;
			}
			he = he.next;
		} while (he != he0);
		return null;
	}

	public Point3d getCentroid() {
		return centroid;
	}

	/**
	 * Gets the i-th half-edge associated with the face.
	 *
	 * @param i the half-edge index, in the range 0-2.
	 * @return the half-edge
	 */
	public HalfEdge getEdge(int i) {
		HalfEdge he = he0;
		while (i > 0) {
			he = he.next;
			i--;
		}
		while (i < 0) {
			he = he.prev;
			i++;
		}
		return he;
	}

	public HalfEdge getFirstEdge() {
		return he0;
	}

	/**
	 * Returns the normal of the plane associated with this face.
	 *
	 * @return the planar normal
	 */
	public Vector3d getNormal() {
		return normal;
	}

	public void getVertexIndices(int[] idxs) {
		HalfEdge he = he0;
		int i = 0;
		do {
			idxs[i++] = he.head().index;
			he = he.next;
		} while (he != he0);
	}

	public String getVertexString() {
		String s = null;
		HalfEdge he = he0;
		do {
			if (s == null) {
				s = "" + he.head().index;
			} else {
				s += " " + he.head().index;
			}
			he = he.next;
		} while (he != he0);
		return s;
	}

	public int mergeAdjacentFace(HalfEdge hedgeAdj, Face[] discarded) {
		Face oppFace = hedgeAdj.oppositeFace();
		int numDiscarded = 0;

		discarded[numDiscarded++] = oppFace;
		oppFace.mark = DELETED;

		HalfEdge hedgeOpp = hedgeAdj.getOpposite();

		HalfEdge hedgeAdjPrev = hedgeAdj.prev;
		HalfEdge hedgeAdjNext = hedgeAdj.next;
		HalfEdge hedgeOppPrev = hedgeOpp.prev;
		HalfEdge hedgeOppNext = hedgeOpp.next;

		while (hedgeAdjPrev.oppositeFace() == oppFace) {
			hedgeAdjPrev = hedgeAdjPrev.prev;
			hedgeOppNext = hedgeOppNext.next;
		}

		while (hedgeAdjNext.oppositeFace() == oppFace) {
			hedgeOppPrev = hedgeOppPrev.prev;
			hedgeAdjNext = hedgeAdjNext.next;
		}

		HalfEdge hedge;

		for (hedge = hedgeOppNext; hedge != hedgeOppPrev.next; hedge = hedge.next) {
			hedge.face = this;
		}

		if (hedgeAdj == he0) {
			he0 = hedgeAdjNext;
		}

		// handle the half edges at the head
		Face discardedFace;

		discardedFace = connectHalfEdges(hedgeOppPrev, hedgeAdjNext);
		if (discardedFace != null) {
			discarded[numDiscarded++] = discardedFace;
		}

		// handle the half edges at the tail
		discardedFace = connectHalfEdges(hedgeAdjPrev, hedgeOppNext);
		if (discardedFace != null) {
			discarded[numDiscarded++] = discardedFace;
		}

		computeNormalAndCentroid();
		checkConsistency();

		return numDiscarded;
	}

	public int numVertices() {
		return numVerts;
	}

	public void triangulate(FaceList newFaces, double minArea) {
		HalfEdge hedge;

		if (numVertices() < 4) {
			return;
		}

		Vertex v0 = he0.head();

		hedge = he0.next;
		HalfEdge oppPrev = hedge.opposite;
		Face face0 = null;

		for (hedge = hedge.next; hedge != he0.prev; hedge = hedge.next) {
			Face face = createTriangle(v0, hedge.prev.head(), hedge.head(), minArea);
			face.he0.next.setOpposite(oppPrev);
			face.he0.prev.setOpposite(hedge.opposite);
			oppPrev = face.he0;
			newFaces.add(face);
			if (face0 == null) {
				face0 = face;
			}
		}
		hedge = new HalfEdge(he0.prev.prev.head(), this);
		hedge.setOpposite(oppPrev);

		hedge.prev = he0;
		hedge.prev.next = hedge;

		hedge.next = he0.prev;
		hedge.next.prev = hedge;

		computeNormalAndCentroid(minArea);
		checkConsistency();

		for (Face face = face0; face != null; face = face.next) {
			face.checkConsistency();
		}

	}

	/**
	 * return the squared area of the triangle defined by the half edge hedge0 and
	 * the point at the head of hedge1.
	 *
	 * @param hedge0
	 * @param hedge1
	 * @return
	 */
	public double areaSquared(HalfEdge hedge0, HalfEdge hedge1) {
		Point3d p0 = hedge0.tail().pnt;
		Point3d p1 = hedge0.head().pnt;
		Point3d p2 = hedge1.head().pnt;

		double dx1 = p1.x - p0.x;
		double dy1 = p1.y - p0.y;
		double dz1 = p1.z - p0.z;

		double dx2 = p2.x - p0.x;
		double dy2 = p2.y - p0.y;
		double dz2 = p2.z - p0.z;

		double x = dy1 * dz2 - dz1 * dy2;
		double y = dz1 * dx2 - dx1 * dz2;
		double z = dx1 * dy2 - dy1 * dx2;

		return x * x + y * y + z * z;
	}

	static double areaSquared(Vertex a, Vertex b, Vertex c) {
		double abx = b.pnt.x - a.pnt.x;
		double aby = b.pnt.y - a.pnt.y;
		double abz = b.pnt.z - a.pnt.z;

		double acx = c.pnt.x - a.pnt.x;
		double acy = c.pnt.y - a.pnt.y;
		double acz = c.pnt.z - a.pnt.z;

		double cx = aby * acz - abz * acy;
		double cy = abz * acx - abx * acz;
		double cz = abx * acy - aby * acx;
		return cx * cx + cy * cy + cz * cz;
	}

	/** Choose a stable CCW plane triple from this face's vertices. */
	private void updatePlaneTriple() {
		Vertex a = he0.head();
		Vertex b = he0.next.head();

		Vertex best = null;
		double bestArea = 0;

		HalfEdge he = he0.next.next;
		while (he != he0) {
			Vertex c = he.head();
			if (c != a && c != b) {
				double asq = areaSquared(a, b, c);
				if (asq > bestArea) {
					bestArea = asq;
					best = c;
				}
			}
			he = he.next;
		}

		// Fallback (degenerate polygon): just take the next vertex
		if (best == null) {
			best = he0.next.next.head();
		}

		pa = a;
		pb = b;
		pc = best;
	}

	/** Cache an unnormalized plane that matches orient(pa,pb,pc, v). */
	private void updatePredicatePlane() {
		if (pa == null || pb == null || pc == null) {
			pAx = pAy = pAz = 0;
			pNx = pNy = pNz = 0;
			pAbsNx = pAbsNy = pAbsNz = 0;
			return;
		}

		Point3d a = pa.pnt, b = pb.pnt, c = pc.pnt;

		pAx = a.x;
		pAy = a.y;
		pAz = a.z;

		double abx = b.x - pAx, aby = b.y - pAy, abz = b.z - pAz;
		double acx = c.x - pAx, acy = c.y - pAy, acz = c.z - pAz;

		// Cross(ab, ac)
		pNx = Math.fma(acy, abz, -acz * aby);
		pNy = Math.fma(acz, abx, -acx * abz);
		pNz = Math.fma(acx, aby, -acy * abx);

		pAbsNx = Math.abs(pNx);
		pAbsNy = Math.abs(pNy);
		pAbsNz = Math.abs(pNz);
		
		// abs components
		double abxA = Math.abs(abx), abyA = Math.abs(aby), abzA = Math.abs(abz);
		double acxA = Math.abs(acx), acyA = Math.abs(acy), aczA = Math.abs(acz);

		// “magnitude” of the terms used in each cross component
		pNxTerm = Math.fma(abyA, aczA, abzA * acyA); // |aby*acz| + |abz*acy|
		pNyTerm = Math.fma(abzA, acxA, abxA * aczA);
		pNzTerm = Math.fma(abxA, acyA, abyA * acxA);
	}

	private void computeNormalAndCentroid() {
		computeNormal(normal);
		computeCentroid(centroid);
		planeOffset = normal.dot(centroid);
		int numv = 0;
		HalfEdge he = he0;
		do {
			numv++;
			he = he.next;
		} while (he != he0);
		if (numv != numVerts) {
			throw new IllegalStateException("face " + getVertexString() + " numVerts=" + numVerts + " should be " + numv);
		}
		updatePlaneTriple();
		updatePredicatePlane();
	}

	private void computeNormalAndCentroid(double minArea) {
		computeNormal(normal, minArea);
		computeCentroid(centroid);
		planeOffset = normal.dot(centroid);
		updatePlaneTriple();
		updatePredicatePlane();
	}

	private Face connectHalfEdges(HalfEdge hedgePrev, HalfEdge hedge) {
		Face discardedFace = null;

		if (hedgePrev.oppositeFace() == hedge.oppositeFace()) { // then there is
																// a redundant
																// edge that we
																// can get rid
																// off

			Face oppFace = hedge.oppositeFace();
			HalfEdge hedgeOpp;

			if (hedgePrev == he0) {
				he0 = hedge;
			}
			if (oppFace.numVertices() == 3) { // then we can get rid of the
												// opposite face altogether
				hedgeOpp = hedge.getOpposite().prev.getOpposite();

				oppFace.mark = DELETED;
				discardedFace = oppFace;
			} else {
				hedgeOpp = hedge.getOpposite().next;

				if (oppFace.he0 == hedgeOpp.prev) {
					oppFace.he0 = hedgeOpp;
				}
				hedgeOpp.prev = hedgeOpp.prev.prev;
				hedgeOpp.prev.next = hedgeOpp;
			}
			hedge.prev = hedgePrev.prev;
			hedge.prev.next = hedge;

			hedge.opposite = hedgeOpp;
			hedgeOpp.opposite = hedge;

			// oppFace was modified, so need to recompute
			oppFace.computeNormalAndCentroid();
		} else {
			hedgePrev.next = hedge;
			hedge.prev = hedgePrev;
		}
		return discardedFace;
	}

	void checkConsistency() {
		// do a sanity check on the face
		HalfEdge hedge = he0;
		double maxd = 0;
		int numv = 0;

		if (numVerts < 3) {
			throw new IllegalStateException("degenerate face: " + getVertexString());
		}
		do {
			HalfEdge hedgeOpp = hedge.getOpposite();
			if (hedgeOpp == null) {
				throw new IllegalStateException("face " + getVertexString() + ": " + "unreflected half edge " + hedge.getVertexString());
			} else if (hedgeOpp.getOpposite() != hedge) {
				throw new IllegalStateException("face " + getVertexString() + ": " + "opposite half edge " + hedgeOpp.getVertexString() + " has opposite "
						+ hedgeOpp.getOpposite().getVertexString());
			}
			if (hedgeOpp.head() != hedge.tail() || hedge.head() != hedgeOpp.tail()) {
				throw new IllegalStateException(
						"face " + getVertexString() + ": " + "half edge " + hedge.getVertexString() + " reflected by " + hedgeOpp.getVertexString());
			}
			Face oppFace = hedgeOpp.face;
			if (oppFace == null) {
				throw new IllegalStateException("face " + getVertexString() + ": " + "no face on half edge " + hedgeOpp.getVertexString());
			} else if (oppFace.mark == DELETED) {
				throw new IllegalStateException("face " + getVertexString() + ": " + "opposite face " + oppFace.getVertexString() + " not on hull");
			}
			double d = Math.abs(distanceToPlane(hedge.head().pnt));
			if (d > maxd) {
				maxd = d;
			}
			numv++;
			hedge = hedge.next;
		} while (hedge != he0);

		if (numv != numVerts) {
			throw new IllegalStateException("face " + getVertexString() + " numVerts=" + numVerts + " should be " + numv);
		}

	}
}
