package util;

import circlePack.PackControl;
import complex.Complex;
import geometry.CircleSimple;
import geometry.HyperbolicMath;
import packing.PackData;

/** 
 * For use with 'SetBuilderParser' for holding selection descriptions.
 * Note that 'negation' is a standard part of selection specification.
 * However, 'isConnective' true, then this is purely a connective, no
 * other data is set.
 * @author kens
 *
 */
public class SelectSpec {
	public String left_str;       // left side of 'condition'
	public Double left_value;     // null unless double value is specified
	public String right_str;      // right side of 'condition' (or null in unary case)
	public Double right_value;    // null unless double value is specified
	Condition condition;          // encoded conditions: <=, >=, >, <, ==, !=, (new: le, ge, gt, lt, eq, ne)
	public boolean unary;       // unary condition, place in 'target' (eg. 'b')
	public boolean negation;      // negation of a unary condition
	public boolean isConnective;  // is this a connective (&&, ||)? see 'isAnd'
	public boolean isAnd;         // true, connective is &&, else ||
	public int object;        // bit encoded for intended object:
	// bits: 1 for circles, 2 for faces, 4 for tiles (8 for edges?)
	
	enum  Condition {LT,LE,GT,GE,EQ,NE,NULL};
	
	static final double TOLER=.0000000001;

	// Constructor
	public SelectSpec(int objt) {
		left_str=null;
		left_value=null;
		condition=Condition.NULL;
		right_str=null;
		right_value=null;
		unary=false;
		negation=false;
		isConnective=false;
		isAnd=false; // default to 'or' 
		object=objt;
	}
	
	public void setCondition(String str) {
		str=str.trim();
		if (str.equals("=") || str.equals("==")) condition=Condition.EQ;
		else if (str.equals(">")) condition=Condition.GT;
		else if (str.equals(">=")) condition=Condition.GE;
		else if (str.equals("<")) condition=Condition.LT;
		else if (str.equals("<=")) condition=Condition.LE;
		else if (str.equals("!=")) condition=Condition.NE;
		else condition=Condition.NULL;
	}
	
	/** OBE: interchange inequalities to get target/value order */
	public void flipCondition() {
		if (condition==Condition.GT) condition=Condition.LT;
		else if (condition==Condition.GE) condition=Condition.LE;
		else if (condition==Condition.LT) condition=Condition.GT;
		else if (condition==Condition.LE) condition=Condition.GE;
	}
	
	/**
	 * Convert string specification to double value for a given 
	 * vertex, face, or tile index, depending on 'object' bits:
	 *    bit: 1=circle, 2=face, 4=tile
	 * uP.rtnflag=0 for error or if we're after a righthand side
	 * but 'value_str' is empty
	 * 
	 * Target quantities: rad=r, degree=d, bdry=b, int=i, angle sum=s, aim=a,
	 * marked=m, ratio(p,q)=cpq, ratio(p,q)=epq (but converted to eucl), modulus
	 * of (eucl) center=z (or ze), tile type=t,plot_flag false=x. 
	 * X(resp. Y,Z)-coord in xyz data=X(resp. Y,Z).
	 * 
	 * @param packData PackData
	 * @param node int, vertex or face index
	 * @param leftright int, 0 if getting value for lefthand side, 1 for right
	 * @return UtilPacket, rtnflag=0 on error (results shouldn't be used)
	 */
	public UtilPacket node_to_value(PackData packData, int node,int leftright)
			throws Exception {
		UtilPacket uP = new UtilPacket();
		uP.rtnFlag = 0; // default: indicates error
		
		// explicit value? 
		if (leftright==0 && left_value!=null) {
			uP.value=left_value.doubleValue();
			uP.rtnFlag=1;
			return uP;
		}
		else if (leftright==1) {
			// must be unary situation
			if (right_str==null || right_str.length()==0) {
				return uP;
			}
			else if (right_value!=null) {
				uP.value=right_value.doubleValue();
				uP.rtnFlag=1;
				return uP;
			}
		}
		
		String myStr=left_str;
		if (leftright==1) {
			myStr=right_str;
		}
		if (myStr.length() == 0)
			return uP;
		
		char c = myStr.charAt(0);
		
		// circles
		if ((object & 01)==01) {   
			switch (c) {

			// unary specifications 'b', 'i'
			case 'b': // boundary circle or face
			{
				uP.value = (double) (packData.getBdryFlag(node));
				uP.rtnFlag = 1;
				return uP;
			}
			case 'i': // interior circle or face?
			{
				uP.value = (double) (1 - packData.getBdryFlag(node));
				uP.rtnFlag = 1;
				return uP;
			}

			// specifications requiring double 'value' to compare to
			case 'a': // aim?
			{
				uP.value = packData.getAim(node)/Math.PI;
				uP.rtnFlag = 1;
				return uP;
			}
			case 'c': // color code
			{
				uP.value = ColorUtil.col_to_table(packData.kData[node].color);
				uP.rtnFlag = 1;
				return uP;
			}
			case 'd': // degree for circle; edge count for tile
			{
				uP.value = (double) (packData.countFaces(node) + packData.getBdryFlag(node));
				uP.rtnFlag = 1;
				return uP;
			}
			case 'm': // marked object?
			{
				uP.value = (double) packData.getVertMark(node);
				uP.rtnFlag = 1;
				return uP;
			}
			case 'n': // vert, face, or tile index
			{
				uP.value = (double) node;
				uP.rtnFlag = 1;
				return uP;
			}
			case 'q': // active circle
			{
				if (packData.activeNode == node) {
					uP.value = 1.0;
					uP.rtnFlag=1;
					return uP;
				}
				uP.rtnFlag = 0;
				return uP;
			}
			case 'r': // rad?
			{
				if (packData.hes < 0) // hyp
					uP.value = (-Math.log(packData.rData[node].rad));
				else
					uP.value = packData.rData[node].rad;
				uP.rtnFlag = 1;
				return uP;
			}
			case 's': // angle sum?
			{
				uP.value = packData.getCurv(node)/Math.PI;
				uP.rtnFlag = 1;
				return uP;
			}
			case 'u': // utilFlag?
			{
				uP.value = (double) packData.kData[node].utilFlag;
				uP.rtnFlag = 1;
				return uP;
			}
			case 'x': // !plot_flag?
			{
				uP.value = (double) packData.kData[node].plotFlag;
				uP.rtnFlag = 1;
				return uP;
			}
			case 'z': // modulus of center; not yet for sph case 
			{
				if (packData.hes > 0) {
					uP.rtnFlag = 0;
					return uP;
				}
				
				Complex ctrp = packData.getCenter(node);
				if (packData.hes < 0 && myStr.charAt(1) == 'e') { // eucl cent
					CircleSimple sc = HyperbolicMath.h_to_e_data(ctrp,
							packData.rData[node].rad);
					ctrp = new Complex(sc.center);
				}
				uP.value = ctrp.abs();
				uP.rtnFlag = 1;
				return uP;
			}
			case 'R': // ratio of radii?
			case 'e': // ratio, but using eucl radii? (e.g. "e21", pack2/pack1)
			{
				char cc = myStr.charAt(1);
				char ccc = myStr.charAt(2);
				int pp=0;
				int pq=0;
				if (cc >= '0' && cc <= '9')
					pp = cc - '0';
				if (ccc >= '0' && cc <= '9')
					pq = ccc - '0';
				// caution: exception if pp or pq is out of range for packdata
				PackData Pp = PackControl.cpScreens[pp].getPackData();
				PackData Pq = PackControl.cpScreens[pq].getPackData();
				if (node > Pp.nodeCount || node > Pq.nodeCount) {
					uP.rtnFlag = 0;
					return uP;
				}
				double rq = Pq.rData[node].rad;
				double rp = Pp.rData[node].rad;
				if (c == 'e') { // compare in eucl geom
					if (Pp.hes < 0) {
						CircleSimple sc = HyperbolicMath.h_to_e_data(
								Pp.getCenter(node), rp);
						rp = sc.rad;
					}
					if (Pq.hes < 0) {
						CircleSimple sc = HyperbolicMath.h_to_e_data(
								Pq.getCenter(node), rq);
						rq = sc.rad;
					}
				}
				uP.value = rp / rq; // may be NaN
				if (Double.isNaN(uP.value))
					uP.rtnFlag = 0;
				else
					uP.rtnFlag = 1;
				return uP;
			}
			case 'X': // x-coord in xyz-data
			{
				if (packData.xyzpoint == null) { // no xyz info available
					uP.rtnFlag = 0;
					return uP;
				} 
				uP.value = (double) packData.xyzpoint[node].x;
				uP.rtnFlag = 1;
				return uP;
			}
			case 'Y': // y-coord in xyz-data
			{
				if (packData.xyzpoint == null) { // no xyz info available
					uP.rtnFlag = 0;
					return uP;
				} 
				uP.value = (double) packData.xyzpoint[node].y;
				uP.rtnFlag = 1;
				return uP;
			}
			case 'Z': // z-coord in xyz-data
			{
				if (packData.xyzpoint == null) { // no xyz info available
					uP.rtnFlag = 0;
					return uP;
				} 
				uP.value = (double) packData.xyzpoint[node].z;
				uP.rtnFlag = 1;
				return uP;
			}

			} // end of switch
			
			return uP; // no valid symbol
		} // end of circle case
		
		// face case
		else if ((object & 02)==02) {
			switch(c) {
			// unary specifications 'b', 'i'
			case 'b': // boundary face (some bdry vertex)
			{
				if (packData.isBdry(packData.faces[node].vert[0])
					|| packData.isBdry(packData.faces[node].vert[1])
					|| packData.isBdry(packData.faces[node].vert[2]))
					uP.value = 1;
				else
					uP.value=0;
				uP.rtnFlag = 1;
				return uP;
			}
			case 'i': // interior face?
			{

				if (!packData.isBdry(packData.faces[node].vert[0])
						&& !packData.isBdry(packData.faces[node].vert[1])
						&& !packData.isBdry(packData.faces[node].vert[2]))
					uP.value = 1;
				else
					uP.value = 0;
				uP.rtnFlag = 1;
				return uP;
			}
			case 'c': // color code
			{
				uP.value = ColorUtil.col_to_table(packData.faces[node].color);
				uP.rtnFlag = 1;
				return uP;
			}
			case 'm': // marked face
			{
				uP.value = (double) packData.faces[node].mark;
				uP.rtnFlag = 1;
				return uP;
			}
			case 'n': // face index
			{
				uP.value = (double) node;
				uP.rtnFlag = 1;
				return uP;
			}
			case 'u': // utilFlag?
			{
				if (packData.fUtil != null) 
					uP.value = (double) packData.fUtil[node];
				uP.rtnFlag = 1;
				return uP;
			}
			case 'x': // !plot_flag?
			{
				uP.value = (double) packData.faces[node].plotFlag;
				uP.rtnFlag = 1;
				return uP;
			}
			} // end of switch
			
			return uP; // no valid symbol
		} // end of face case

		// tile case
		else if ((object & 04)==04 ) {

			switch(c) {
			// 	unary specifications 'b', 'i'
			case 'b': // boundary tile (at least one bdry vert)
			{
				int []verts=packData.tileData.myTiles[node].vert;
				boolean hit=false;
				for (int j=0;(j<verts.length && !hit);j++) {
					if (packData.isBdry(verts[j]))
						hit=true;
				}
				if (hit)
					uP.value=1;
				else
					uP.value=0;
				uP.rtnFlag=1;
				return uP;
			}
			case 'i': // 
			{
				int []verts=packData.tileData.myTiles[node].vert;
				boolean hit=false;
				for (int j=0;(j<verts.length && !hit);j++) {
					if (packData.isBdry(verts[j]))
						hit=true;
				}
				if (hit)
					uP.value=0;
				else
					uP.value=1;
				uP.rtnFlag=1;
				return uP;
			}
			case 'c': // color code
			{
				uP.value = ColorUtil.col_to_table(packData.tileData.myTiles[node].color);
				uP.rtnFlag = 1;
				return uP;
			}
			case 'd': // degree (edge count for tile)
			{
				uP.value=(double) packData.tileData.myTiles[node].vertCount;
				uP.rtnFlag = 1;
				return uP;
			}
			case 'm': // marked object?
			{
				uP.value=(double) packData.tileData.myTiles[node].mark;
				uP.rtnFlag = 1;
				return uP;
			}
			case 'n': // tile index
			{
				uP.value = (double) node;
				uP.rtnFlag = 1;
				return uP;
			}
			case 't': // tile type
			{
				uP.value=packData.tileData.myTiles[node].tileType;
				uP.rtnFlag=1;
				return uP;
			}
			case 'u': // utilFlag?
			{
				uP.value = (double) packData.tileData.myTiles[node].utilFlag;
				uP.rtnFlag = 1;
				return uP;
			}
			case 'z': // modulus of barycenter circle 
			{
				int v=packData.tileData.myTiles[node].baryVert;
				Complex ctrp = packData.getCenter(v);
				if (packData.hes < 0 && myStr.charAt(1) == 'e') { // eucl cent
					CircleSimple sc = HyperbolicMath.h_to_e_data(ctrp,
							packData.rData[node].rad);
					ctrp = new Complex(sc.center);
				}
				uP.value = ctrp.abs();
				uP.rtnFlag = 1;
				return uP;
			}
			} // end of switch 
			
			return uP; // no valid symbol
		}
		
		// no valid symbol 
		return uP;
	}
	
	/**
	 * Compare double 'x' to 'y' according to 'this.condition'
	 * return true or false (and false for error).
	 * @param x double
	 * @param y double (irrelevant for unitary conditions)
	 * @return boolean
	 */
	public boolean comparison(double x,double y) {
		if (unary) { // for unary conditions (eg., 'b'), x>1 means true.
			if (x > 0.0) {
				if (negation) return false;
				return true;
			}
			if (negation) return true;
			return false;
		}
		if (condition == Condition.NULL)
			return false;
		else if (condition == Condition.EQ) {
			if (Math.abs(x - y) > TOLER) {
				if (negation) return true;
				return false;
			}
			if (negation) return false;
			return true;
		} else if (condition == Condition.NE) {
			if (Math.abs(x - y) < TOLER) {
				if (negation) return true;
				return false;
			}
			if (negation) return false;
		} else if (condition == Condition.GT) {
			if (x <= y) {
				if (negation) return true;
				return false;
			}
			if (negation) return false;
		} else if (condition == Condition.GE) {
			if (x < y) {
				if (negation) return true;
				return false;
			}
			if (negation) return false;
		} else if (condition == Condition.LT) {
			if (x >= y) {
				if (negation) return true;
				return false;
			}
			if (negation) return false;
		} else if (condition == Condition.LE) {
			if (x > y) {
				if (negation) return true;
				return false;
			}
			if (negation) return false;
		}
		return true;
	}

}
