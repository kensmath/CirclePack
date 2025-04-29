package ftnTheory;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.io.BufferedReader;
import java.io.File;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import geometry.HyperbolicMath;
import geometry.CircleSimple;
import geometry.SphericalMath;
import input.CPFileManager;
import komplex.EdgeSimple;
import listManip.NodeLink;
import listManip.VertexMap;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.ColorUtil;
import util.DispFlags;
import util.PathUtil;
import util.RH_curve;
import util.StringUtil;

public class RiemHilbert extends PackExtender {

	enum ReadState {OPEN,FIND_CURVE,PATH,CIRCLE,READ_XY,READ_CR,NULL};
	
	public static final int THICKNESS=3;
	public int bdryCount;
	public Vector<RH_curve> restCurves; // vector of curves 
	public VertexMap vertCurve;        // 'edge' {v,j}, associate curve j with 'v'
	public static RH_curve defaultCurve= // default is unit circle
			new RH_curve(new Complex(0.0),1.0); 
	
	// Constructor
	public RiemHilbert(PackData p) {
		super(p);
		extenderPD=p;
		extensionType="RIEMANN_HILBERT";
		extensionAbbrev="RH";
		toolTip="'RiemHilbert': for manipulating packings "+
		"in manner of Riemann-Hilbert problems";
		
		restCurves=new Vector<RH_curve>(50);
		restCurves.add(defaultCurve);
		vertCurve=null;
		registerXType();
		extenderPD.packExtensions.add(this);
	}
	
	/**
	 * Read a formated file of Riemann Hilbert curves.
	 * @param fp
	 * @param addon: true, then add to existing vector of curves
	 * @return number of curves read
	 */
	public int readCurves(BufferedReader fp,boolean addon) {
		if (fp==null) return 0;
		if (!addon) // restart;
			restCurves=new Vector<RH_curve>(50);
		String line;
		int safety=10000;
		Vector<Complex> pts=null;
		ReadState state=ReadState.FIND_CURVE;
		String str=null;
		String xstr=null;
		
		// look for 'PATH' or 'CIRCLE' (may reenter in one of these modes already)
		while(safety>0 && (state==ReadState.FIND_CURVE 
				|| state==ReadState.PATH || state==ReadState.CIRCLE)
				&& (line=StringUtil.ourNextLine(fp))!=null) {
			StringTokenizer tok = new StringTokenizer(line);
			if (state==ReadState.FIND_CURVE) {
				while (tok.hasMoreTokens()) {
					str=(String)tok.nextToken();
					if (str.startsWith("PATH:")) {
						state=ReadState.PATH;
					}
					else if (str.startsWith("CIRCLE"))
						state=ReadState.CIRCLE;
				}
			}
			if (state==ReadState.PATH) {
				state=ReadState.READ_XY;
				while (safety>0 && state==ReadState.READ_XY 
						&& (line=StringUtil.ourNextLine(fp))!=null) {
					tok = new StringTokenizer(line);
					while(tok.hasMoreTokens()) {
	        			// expect two doubles per line
	        			try {
	        				xstr=(String)tok.nextToken().trim();
	        				double x=Double.parseDouble(xstr);
	        				double y=Double.parseDouble((String)tok.nextToken());
	        				if (pts==null) { // new vector
	        					pts=new Vector<Complex>(25);
	        					pts.add(new Complex(x,y));
	        				}
	        				else {
	        					pts.add(new Complex(x,y));
	        				}
	        			} catch (NumberFormatException nfe) { // 'xstr' should have last string
	        				if (xstr.startsWith("PATH"))
	        					state=ReadState.PATH;
	        				else if (xstr.startsWith("CIRCLE"))
	        					state=ReadState.CIRCLE;
	        				else state=ReadState.FIND_CURVE;
	        			}
					}
				} // end of while
				if (safety<=0) {
					CirclePack.cpb.myErrorMsg("RH error in reading a curve.");
					return 0;
				}
				if (pts!=null && pts.size()>=3) {
					RH_curve rhc=new RH_curve(pts);
					restCurves.add(rhc);
				}
				pts=null;
	        }  // end of PATH case
			else if (state==ReadState.CIRCLE) {
				tok = new StringTokenizer(line);
				while(tok.hasMoreTokens()) {
        			// expect 'x y rad'
        			try {
        				xstr=(String)tok.nextToken().trim();
        				double x=Double.parseDouble(xstr);
        				double y=Double.parseDouble((String)tok.nextToken());
        				double rad=Double.parseDouble((String)tok.nextToken());
        				Complex z=new Complex(x,y);
    					RH_curve rhc=new RH_curve(z,rad);
    					restCurves.add(rhc);
        				state=ReadState.FIND_CURVE;
        			} catch (NumberFormatException nfe) { // 'xstr' should have last
        				if (xstr.startsWith("PATH"))
        					state=ReadState.PATH;
        				else if (xstr.startsWith("CIRCLE"))
        					state=ReadState.CIRCLE;
        				else state=ReadState.FIND_CURVE;
        			}
				} // end of while
			}
		} // end of outer 'while'
		return restCurves.size();
	}

	/**
	 * Give restriction curve associated with vert 'v', null on error. 
	 * @param v, vertex
	 * @return RH_curve associated with 'v'
	 */
	public RH_curve curveForVert(int v) {
		if (vertCurve==null	|| vertCurve.size()==0 || !extenderPD.status) return null; 
		Iterator<EdgeSimple> vc=vertCurve.iterator();
		EdgeSimple edge=null;
		while (vc.hasNext()) {
			edge=(EdgeSimple)vc.next();
			if (edge.v==v) return (RH_curve)restCurves.get(edge.w);
		}
		return null;
	}
	
	public int drawRestCurves(NodeLink vertlist) {
		int count=0;
		if (vertCurve==null || vertlist==null || vertlist.size()==0) return count;
		Iterator<Integer> vlist=vertlist.iterator();
		RH_curve rc=null;
		int v;
		int orig_thickness=extenderPD.cpDrawing.linethickness;
		extenderPD.cpDrawing.setLineThickness(THICKNESS);
		while(vlist.hasNext()) {
			v=(Integer)vlist.next();
			rc=curveForVert(v);
			rc.drawMe(extenderPD.cpDrawing);
			count++;
		}
		extenderPD.cpDrawing.setLineThickness(orig_thickness);
		PackControl.activeFrame.activeScreen.repaint();
		return count;
	}
	
	/**
	 * TODO: Not used; would need work, esp. in sph case
	 * 
	 * Signed distance from center of 'v' to curve:
	 * >=0 if bdry vert 'v' has center inside/on 
	 * its restriction curve; else < 0.
	 * @param v bdry vertex
	 * @return double signed distance
	 */
	public double centerDistance(int v) {
		RH_curve rhc=curveForVert(v);
		Complex cent=null;
		if (extenderPD.hes<0) {
			CircleSimple sc =HyperbolicMath.h_to_e_data(
					extenderPD.getCenter(v),extenderPD.getRadius(v));
			// what if sc.flag==-1? outside as disc
			cent=sc.center;
		}
		else if (extenderPD.hes>0) {
			CircleSimple sc =SphericalMath.s_to_e_data(
					extenderPD.getCenter(v),extenderPD.getRadius(v));
			cent=sc.center;
		}
		else cent=extenderPD.getCenter(v);
		if (rhc.isCircle) {
			return rhc.rad-rhc.center.minus(cent).abs();
		}
		double dist=PathUtil.gpDistance(rhc.restCurve,cent);
		if (rhc.restCurve.contains(cent.x,cent.y)) return dist;
		return -dist;
	}
	
	/**
	 * Signed distance from circle to its curve. Plus: lies inside curve,
	 * minimum distance. Negative: negative of (roughly) max distance to
	 * @param v int
	 * @return signed distance
	 */
	public double circleDistance(int v) {
		RH_curve rhc=curveForVert(v);
		Complex cent=null;
		if (extenderPD.hes<0) {
			CircleSimple sc =HyperbolicMath.h_to_e_data(
					extenderPD.getCenter(v),extenderPD.getRadius(v));
			cent=sc.center;
		}
		else if (extenderPD.hes>0) {
			CircleSimple sc =SphericalMath.s_to_e_data(
					extenderPD.getCenter(v),extenderPD.getRadius(v));
			// what if sc.flag==-1? outside as disc
			cent=sc.center;
		}
		else cent=extenderPD.getCenter(v);
		double radius=extenderPD.getRadius(v);
		double dist;
		if (rhc.isCircle) {
			dist=radius+rhc.center.minus(cent).abs();
			return rhc.rad-dist;
		}
		dist=PathUtil.gpDistance(rhc.restCurve,cent);
		if (dist>=0.0) return dist-radius;
		return dist-radius;
	}
	
	public int linkPackCurves() {
		return linkPackCurves(extenderPD.bdryStarts[1]);
	}
	
	/**
	 * Need to tell which boundary vertex of a packing is associated
	 * with which restriction curve. We also set colors: color the
	 * boundary circles using 'spreadColorCodes' and record in the curves.
	 * @param v, bdry vertex at which we start linking.
	 * @return, 0 on error, else, number of boundary vertices
	 */
	public int linkPackCurves(int v) {
		NodeLink bdrylist=null;
		if (!extenderPD.status || (bdrylist=new NodeLink(extenderPD,"b"))==null) {
			CirclePack.cpb.myErrorMsg("RiemHilbert: the packing does not have boundary");
			return 0;
		}
		if (restCurves==null || restCurves.size()==0) {
			CirclePack.cpb.myErrorMsg("RiemHilbert: no 'restriction curves' loaded, using unit circle.");
			restCurves=new Vector<RH_curve>(50);
			restCurves.add(defaultCurve);
		}
		if (v<0 || v>extenderPD.nodeCount || !extenderPD.isBdry(v)) {
			v=extenderPD.bdryStarts[1];
		}

		/* proceed around the boundary; use as many curves as needed, clone
		 * curves as necessary if there are more bdry vertices.*/
		vertCurve=new VertexMap();
		Iterator<Integer> blist=bdrylist.iterator();
		int w;
		int count=0;
		int num=restCurves.size();
		while (blist.hasNext()) {
			w=(Integer)blist.next();
			extenderPD.setCircleColor(w,ColorUtil.spreadColor(count%16));
			if (count>=num) // need to clone to get new 'RH_curve's
				restCurves.add(restCurves.get(count%num).clone());
			vertCurve.add(new EdgeSimple(w,count));
			Color col=extenderPD.getCircleColor(w);
			restCurves.get(count).color=new Color(col.getRed(),col.getGreen(),col.getBlue());
			count++;
		}
		return count;
	}
	
	/** 
	 * Scale curves for listed boundary vertices by 'factor' 
	 * @param vertlist
	 * @param factor
	 * @return
	 */
	public int scaleCurves(NodeLink vertlist,double factor) {
		int count = 0;
		int v;
		try {
			Iterator<Integer> vlist = vertlist.iterator();
			while (vlist.hasNext()) {
				v=(Integer)vlist.next();
				RH_curve rhc=curveForVert(v);
				try {
					if (rhc.isCircle) {
					rhc.center=rhc.center.times(factor);
					rhc.rad*=factor;
				}
				else {
					rhc.restCurve.transform(AffineTransform.getScaleInstance(factor,factor));
				}
				count++;
				} catch (Exception ex) {}
			}
		} catch (Exception ex) {}
		return count;
	}
		
	/** 
	 * Rotate curves for listed boundary vertices by 'arg' radians 
	 * @param vertlist
	 * @param arg in radians
	 * @return count
	 */
	public int rotateCurves(NodeLink vertlist,double arg) {
		int count = 0;
		int v;
		try {
			Iterator<Integer> vlist = vertlist.iterator();
			while (vlist.hasNext()) {
				v=(Integer)vlist.next();
				RH_curve rhc=curveForVert(v);
				try {
					if (!rhc.isCircle) {
						rhc.restCurve.transform(AffineTransform.getRotateInstance(arg));
					}
					count++;
				} catch (Exception ex) {}
			}
		} catch (Exception ex) {}
		return count;
	}

	/**
	 * Shade filled boundary circles to reflect how far each is from its
	 * restriction curve: red indicates the circle intersects the
	 * outside of the curve, blue that it lies fully inside. The
	 * darker the shading, the further out/in (using radius as a
	 * reference). Circle bdry color is that of its curve.
	 * TODO: not dependable in spherical geometry.
	 * @param vertlist
	 * @return
	 */
	public int shadeCircles(NodeLink vertlist) {
		int count = 0;
		try {
			Iterator<Integer> vlist = vertlist.iterator();
			int v;
			Color col;
			double sdist, rad;
			int old_thickness=extenderPD.cpDrawing.linethickness;
			extenderPD.cpDrawing.setLineThickness(THICKNESS);
			DispFlags dflags=new DispFlags("fc");
			while (vlist.hasNext()) {
				v = (Integer) vlist.next();
				if (!extenderPD.isBdry(v))
					break;
				sdist = circleDistance(v);
				rad = extenderPD.getRadius(v);
				// lies inside? shade of blue, further=darker
				if (sdist > 0) {
					if (sdist > rad)
						sdist = rad;
					col = ColorUtil.coLor((int) (1 + 99 * (1.0 - sdist / rad)));
				}
				// hits outside? shade of red, further=darker
				else {
					if (-sdist > rad)
						sdist = -rad;
					col = ColorUtil.coLor((int) (100 + 99 * (-sdist / rad)));
				}

				// do the fill first
				dflags.setColor(col);
				extenderPD.cpDrawing.drawCircle(extenderPD.getCenter(v),
						extenderPD.getRadius(v), dflags);
				
				// change color for the bdry
				dflags.setColor(extenderPD.getCircleColor(v));
				dflags.fill=false;
				dflags.draw=true;
				dflags.colBorder=true;
				extenderPD.cpDrawing.drawCircle(extenderPD.getCenter(v),
						extenderPD.getRadius(v), dflags);
				count++;
			}
			extenderPD.cpDrawing.setLineThickness(old_thickness);
			PackControl.activeFrame.activeScreen.repaint();
		} catch (Exception ex) {
		}
		return count;
	}

	/**
	 * Parsing commands sent here from 'CommandStrParser'
	 */
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		int count=0;
		boolean script_flag=false;
		Vector<String> items=null;
		
		// ============ read_curves ===============
		if (cmd.startsWith("read_cur") || cmd.startsWith("infile_cur")) {
			if (cmd.startsWith("i")) script_flag=true;
			StringBuilder namebuf=new StringBuilder("");
			CPFileManager.trailingFile(flagSegs,namebuf);
			File file=new File(namebuf.toString());
			String dir=file.getParent();
			if (dir==null)
				dir=CPFileManager.CurrentDirectory.toString();
			try {
				BufferedReader fp=CPFileManager.openReadFP(new File(dir),file.getName(),script_flag);
				count=readCurves(fp,false); // for now, overwrite previous curves.
				fp.close();
				if (count>0 && extenderPD.status) linkPackCurves();
				return count;
			} catch (Exception ex) {
				return 0;
			}
		}
		
		// ============= link =================
		// designate starting boundary vertex for linking
		else if (cmd.startsWith("link")) {
			int v;
			try {
				v=NodeLink.grab_one_vert(extenderPD,flagSegs);
				if (v!=0 && extenderPD.isBdry(v))
					return linkPackCurves(v);
			} catch (Exception ex) {}
			return linkPackCurves(extenderPD.bdryStarts[1]);
		}
		
		// ============= draw_curves ===========
		else if (cmd.startsWith("draw_cur")) {
			if (flagSegs==null || flagSegs.size()==0)  
				return drawRestCurves(new NodeLink(extenderPD,"b")); // default to all
			items=(Vector<String>)flagSegs.get(0);
			return drawRestCurves(new NodeLink(extenderPD,items));
		}
		
		// ============= shade =========
		else if (cmd.startsWith("shade")) {
			if (flagSegs==null || flagSegs.size()==0)  
				return shadeCircles(new NodeLink(extenderPD,"b")); // default to all
			items=(Vector<String>)flagSegs.get(0);
			return shadeCircles(new NodeLink(extenderPD,items));
		}
		
		// ============= scale =========
		else if (cmd.startsWith("scale")) {
			items=(Vector<String>)flagSegs.get(0);
			double factor=1.0;
			try {
				factor=Double.parseDouble((String)items.remove(0));
				if (factor<=0) return 0;
				if (items.size()>0) 
					return scaleCurves(new NodeLink(extenderPD,items),factor);
				else return scaleCurves(new NodeLink(extenderPD,"b"),factor);
			} catch (Exception ex) {}
		}
		
		// ============= rotate =========
		else if (cmd.startsWith("rotate")) {
			if (flagSegs.size()==0) return 0;
			items=(Vector<String>)flagSegs.get(0);
			double arg=0.0;
			try {
				arg=Double.parseDouble((String)items.remove(0));
				if (items.size()>0) 
					return rotateCurves(new NodeLink(extenderPD,items),arg);
				else return rotateCurves(new NodeLink(extenderPD,"b"),arg);
			} catch (Exception ex) {}
			return 0;
		}
		
		return super.cmdParser(cmd, flagSegs);
	}
	
	/** 
	 * Override method for cataloging command structures
	 */
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("read_curves","{filename}",null,"read target curve file"));
		cmdStruct.add(new CmdStruct("infile_read","{filename}",null,"read target curves from the script"));
		cmdStruct.add(new CmdStruct("link","[{v}]",null,"designate starting boundary vertex for linking"));
		cmdStruct.add(new CmdStruct("rotate","{ang} {v..}",null,"rotate"));
		cmdStruct.add(new CmdStruct("scale","{factor} {v..}",null,"scale"));
		cmdStruct.add(new CmdStruct("shade","{v..}",null,"shade the circles"));
		cmdStruct.add(new CmdStruct("draw_curves","{v..}",null,"draw the target curves"));
	}
	
}
