package branching;

import java.util.Iterator;
import java.util.Vector;

import exceptions.CombException;
import exceptions.ParserException;
import listManip.FaceLink;
import listManip.HalfLink;
import packing.PackData;
import util.StringUtil;
import util.UtilPacket;

/**
 * This is a new approach to "chaperone" generalized branch
 * points. The old approach in 'ChapBranchPt' excised a flower,
 * then manipulated it separately from the parent packing. In
 * this new approach, we will modify the parent packing itself
 * in a way that can be undone. 
 *  
 * @author kstephe2, 9/2021
 *
 */
public class ChapMod extends GenBranchPt {
	
	// parameters: indices start with 1 (not 0)
	int[] jumpCircle;	// jump circles
	int[] preJump;	    // circles preceding jumps
	int[] chap;		    // chaperone chap[j]
	double[] cos_overs; // overlap angles for chaperones:

	// there are four added circles
	int sister2;	// index of sister2 to 'myIndex'
	int newBrSpot;	// circle at branch value, radius essentially 0, aim=myAim
	
	// Constructor
	public ChapMod(PackData p,int bID,double aim,
			int v,int w1,int w2,double o1,double o2) {
		super(p,bID,(FaceLink)null,aim);
		myType=GenBranchPt.CHAPERONE;
		myIndex=v;
		
		if (packData.getBdryFlag(myIndex)!=0 || packData.countFaces(myIndex)<5)
			throw new CombException("chaperone vert must be interior, degree at least 5");

		int[] petals=packData.getPetals(myIndex);
		int num=petals.length;
		int indx1=packData.nghb(myIndex, w1);
		int indx2=packData.nghb(myIndex, w2);
		if (indx1==indx2 || (indx1+1)%num==indx2 ||
				(indx2+1)%num==indx1)
			throw new CombException(
					"petals "+w1+" and "+w2+" are too close");
		
		jumpCircle=new int[3];
		jumpCircle[1]=w1;
		jumpCircle[2]=w2;
		
		preJump=new int[3];
		preJump[1]=petals[(indx1-1+num)%num];
		preJump[2]=petals[(indx2-1+num)%num];

		// set overlaps
		cos_overs=new double[3];
		cos_overs[1]=Math.cos(o1*Math.PI);
		cos_overs[2]=Math.cos(o2*Math.PI);

		// debug help
		System.out.println("chap attempt: a = "+aim/Math.PI+"; v = "+v+"; jumps "+w1+" "+w2+"; "+
				"overlaps/Pi "+o1+" "+o2);
				
		// this calls createMyPack (which saves 'origChild')
		modifyPackData();
	}
	
	// **** abstract methods *************
	public PackData createMyPack() {
		return null;
	}
	
	public void delete() {
		return;
	}
	
	public UtilPacket riffleMe(int cycles) {
		return new UtilPacket();
	}
	
	public double layout(boolean norm) {
		return 1.0;
	}
	
	public int placeMyCircles() {
		return 0;
	}
	
	public double currentError() {
		return 1.0;
	}
	
	public String reportExistence() {
		return new String(
				"Started 'chaperone' branch point; v = "+
						myIndex);				
	}
	
	public String reportStatus() {
		return new String("'chap' ID"+branchID+": vert="+myIndex+
				"; j1="+jumpCircle[1]+"; j2="+jumpCircle[2]+
				"; over1="+Math.acos(cos_overs[1])/Math.PI+"; over2="+Math.acos(cos_overs[2])/Math.PI+
				"; aim="+myAim/Math.PI+"; holonomy err="+
				super.myHolonomyError());
	}

	/**
	 * The parameters for 'chaperone' type are (jump1, jump2),
	 * (indices for circles jumping to new sister) and 
	 * associated overlap angles over1, over2.
	 * @return String
	 */
	public String getParameters() {
		return new String("'chaperone' branch point: aim "+
				myAim/Math.PI+"*Pi, vertex "+myIndex+", jumps "+
				jumpCircle[1]+" "+jumpCircle[2]+
				", overlaps "+Math.acos(cos_overs[1])/Math.PI+"*Pi "+
				Math.acos(cos_overs[2])/Math.PI+"*Pi");
	}
	

	/**
	 * Chaperone branch point parameters are jump circles and an overlap 
	 * angle for each (to be multiplied by PI here). We read "v0 v1 a0 a1", 
	 * where v0 v1 are petal vertices in parent --- store in jumpIndx[]
	 * as local flower indices --- and a0 and a1 are multiplied by Pi; 
	 * their cosines are stored in cos_overs[] (negative is cosine of 
	 * supplementary angle).
	 * Then call 'modifyMyPack' to actually create the new 'myPackData'. 
	 * @param flagSegs Vector<Vector<String>>, "v0 v1 a0 a1"
	 * @return int count; 0 on error, negative if new 'layoutTree' is required in parent
	 */
	public int setParameters(Vector<Vector<String>> flagSegs) {
		if (flagSegs==null || flagSegs.size()==0)
			throw new ParserException("usage: -a aim -j w1 w2 -o o1 o2");
		int count=0;
		boolean gotjumps=false;
		boolean gotovers=false;
		double []ovlp=new double[2];
		
		// parse the parameter info: -a aim, -j j1 j2, -o o1 o2
		Iterator<Vector<String>> fit=flagSegs.iterator();
		while (fit.hasNext()) {
			Vector<String> items=fit.next();
			try {
				if (!StringUtil.isFlag(items.get(0)))
					throw new ParserException("usage: -a aim -j j1 j2 -o o1 o2");
				String str=items.remove(0);
				switch (str.charAt(1)) {
				case 'a': // new aim
				{
					myAim=Double.parseDouble(items.get(0))*Math.PI;
					myPackData.setAim(newBrSpot,myAim);
					myPackData.setRadius(newBrSpot,0.5); // kick-start repacking
					count++;
					break;
				}
				case 'j': // jump petals (as vertex indices from parent)
				{
					jumpCircle=new int[3]; // jumpIndx[0] empty
					jumpCircle[1]=Integer.parseInt(items.get(0));
					jumpCircle[2]=Integer.parseInt(items.get(1));
					count += 2;
					gotjumps=true;
					break;
				}
				case 'o': // overlaps
				{
					for (int i=0;i<2;i++) {
						ovlp[i]=Double.parseDouble(items.remove(0));
						if (ovlp[i]<0 || ovlp[i]>1.0) {
							throw new ParserException("overlap not in [0,1]");
						}
						cos_overs[i+1]=Math.cos(ovlp[i]*Math.PI);
						count++;
					}
					count++;
					gotovers=true;
					break;
				}
				} // end of switch
			
				// TODO: may want to accommodate more jumps/overlaps in future,
				//       depending, e.g., on 'myAim'.
			} catch(Exception ex) {
				throw new ParserException(ex.getMessage());
			}
		}

		if (gotjumps) {
			// remove old poisons
			if (parentHPoison!=null)
				packData.poisonHEdges=HalfLink.removeDuplicates(parentHPoison,false);
			int ans=modifyPackData();
			if (ans==0) {
				throw new ParserException("failed to modify packData");
			}
			myPackData.fillcurves();
		}
		if (gotovers) {

			// reset overlaps
			return resetOverlaps(ovlp[0],ovlp[1]);
		}
		return count;
	}
	
	// **********************
	
	public int modifyPackData() {
		return 1;
	}
	
	public int resetOverlaps(double o1,double o2) {
		return 0;
	}
	
	
	
}
