package packing;

import java.awt.geom.Path2D;
import java.io.BufferedWriter;
import java.util.Iterator;
import java.util.Vector;

import baryStuff.BaryPacket;
import complex.Complex;
import exceptions.DataException;
import exceptions.InOutException;
import komplex.EdgeSimple;
import listManip.BaryCoordLink;
import listManip.FaceLink;
import listManip.GraphLink;
import util.PathBaryUtil;

/**
 * 'PackData' is bloated, so starting in 2020, I'm offloading some static
 * routines here.
 * @author kens
 *
 */
public class PackMethods {
	
	/** 
	 * Specialized routine for sending paths in barycentric coordinates
	 * to a file, part of the work with John Bowers for 3D printing 
	 * software. 
	 * 
	 * We write the dual graph of 'p' to a file. The path is 
	 * represented as a chain of face/start bary/end bary groups
	 * representing segments in barycentric not for 'p' itself, 
	 * but rather for a second packing 'bp' (associated, e.g., 
	 * with an underlying conformal map).
	 * Thus we need to find the paths in the dual graph of 'p', but
	 * convert those based on 'bp'. Since the face indices of 'bp' are 
	 * not persistent, have to indicate faces with triples of vertices.
	 * 
	 * In .q files, use key word "BARY_PATH:" followed by groups of 
	 * three lines:
	 * 
	 *      v1 v2 v3 
	 *         s1 s2  
	 *         e1 e2 
	 *         
	 * <v1 v2 v3> is face, s_j bary coords of start, e_j bary coords of end, 
	 * (third bary coord is computed from first 2). There may be many such segments.
	 * @param fp BufferedWriter
	 * @param p PackData
	 * @param bp PackData, base packing 
	 * @return int count, 0 on error
	 */
	public static int writeDualBarys(BufferedWriter fp, PackData p, PackData bp) {
		if (p.hes != 0)
			throw new InOutException("usage: writeDualBarys must be euclidean");

		int count = 0;
		GraphLink dualgraph = new GraphLink(p, "a");
		Iterator<EdgeSimple> dits = dualgraph.iterator();
		while (dits.hasNext()) {
			try {
				EdgeSimple edge = dits.next();
				Complex[] pts = p.ends_dual_edge(edge, null); // ends
				Path2D.Double path = new Path2D.Double();
				path.moveTo(pts[0].x, pts[0].y);
				path.lineTo(pts[1].x, pts[1].y);
				Vector<BaryCoordLink> barycoordlink = PathBaryUtil.fromPath(bp, path);

				// iterate through; there may be more than one barycoordlink
				Iterator<BaryCoordLink> bclits = barycoordlink.iterator();
				if (bclits.hasNext())
					fp.write("BARY_PATH:\n");
				while (bclits.hasNext()) {
					BaryCoordLink bcl = bclits.next();
					Iterator<BaryPacket> bits = bcl.iterator();
					while (bits.hasNext()) {
						BaryPacket bpkt = bits.next();
						int f = bpkt.faceIndx;

						// put in the three vertices of this face
						fp.write(bp.faces[f].vert[0] + "   " + bp.faces[f].vert[1] + "   " + bp.faces[f].vert[2]
								+ "\n   ");
						fp.write(String.format("%.6f",bpkt.start.b0)+" "+
								String.format("%.6f", bpkt.start.b1) + "\n   "+
								String.format("%.6f", bpkt.end.b0)+" "+
								String.format("%.6f", bpkt.end.b1)+"\n");
						count++;
					}
				}
			} catch (Exception ex) {
				throw new InOutException("error in writing barycenters for dual" + ex.getMessage());
			}

		}
		return count;
	}

	/**
	 * For eucl packing with 'xyzpoint' data, this computes the ratios 3D/2D.
	 * The first spot is unused. The 3D areas are stored in p.utilDoubles
	 * @param p PackData
	 * @return Vector<Double>, null on error
	 */
	public static Vector<Double> areaRatio(PackData p,FaceLink flink) {
		if (p.hes!=0 || ColorCoding.setXYZ_areas(p)==0)
			return null;
		if (flink==null || flink.size()==0)
			flink=new FaceLink(p,"a");
		Vector<Double>ratios=new Vector<Double>(flink.size()+1);
		ratios.add(0,null); // first spot unused
		Iterator<Integer> flist=flink.iterator();
		while(flist.hasNext()) {
			int f=flist.next();
			ratios.add(p.utilDoubles.get(f)/p.faceArea(f));
		}
		return ratios;
	}
	
}
