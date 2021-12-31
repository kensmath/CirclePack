package panels;

import java.util.Hashtable;
import java.util.Vector;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import allMains.CirclePack;
import images.CPIcon;
import packing.PackData;

/**
 * DataTree organizes the data on a packing in hashtables and 
 * a tree for display in the 'Pack Info' button.
 * @author kstephe2
 *
 */
public class DataTree extends JPanel {

	private static final long 
	serialVersionUID = 1L;

	private JScrollPane dataTreeScroller;
	private JTree dataTree;

	// Constructor
	public DataTree() {
		super();

		createGUI();
	}

	protected void createGUI() {
		// AF: Just cleaning up the GUI code here for legibility and layout manager compatibility.
		dataTree = createDataTree(CirclePack.cpb.getActivePackData());
		
		dataTreeScroller = new JScrollPane(dataTree);
		dataTreeScroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		
		this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		this.add(dataTreeScroller);
	}

	/**
	 * Execute 'pdata' and update packing data in 'DataPanel'.
	 */
	public void updatePackingData(PackData p) {
		dataTreeScroller.setViewportView(createDataTree(p));
	}

	/**
	 * Initiates button, tree, and treescroll for "Pack Info" data.
	 * Also used to update in 'updatePackingData'.
	 * @param p PackData
	 * @return JTree
	 */
	protected JTree createDataTree(PackData p) {
		JTree dataTree=null;
		try {
			dataTree = new JTree(getDataAsHashtable(p));
			DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
			renderer.setLeafIcon(CPIcon.CPImageIcon("GUI/tree_leaf.gif"));
			dataTree.setCellRenderer(renderer);
			dataTree.expandRow(0);
			dataTree.expandRow(1);
		} catch (Exception ex) {
			CirclePack.cpb.errMsg("Some problem in creating DataTree");
		}
		
		return dataTree;
	}

	/**
	 *  Use:  Bundles up the data on packing in hashtable 
	 *  for display by a JTree.
	 */
	public static Hashtable<String,Hashtable<String,Vector<String>>>
	getDataAsHashtable(PackData p){
		Hashtable<String,Hashtable<String,Vector<String>>> 
		root = new Hashtable<String,Hashtable<String,Vector<String>>>(2);
		Hashtable<String,Vector<String>> sub = new Hashtable<String,Vector<String>>(4);
		if (!p.status || p.nodeCount==0) 
			return null;

		Vector<String> v1 = new Vector<String>(4);
		double []curvErr=p.packCurvError();
		if (p.packDCEL!=null) {
			v1.add("Topology: " + setTopologyStr(p));
			v1.add("Node/Face Count = " + p.nodeCount+" / "+p.faceCount);
			v1.add("Genus/Euler = "+p.genus+" / "+p.euler);
			v1.add("Alpha/Gamma vertices = "+p.packDCEL.alpha.origin.vertIndx+
					" / "+p.packDCEL.gamma.origin.vertIndx);
			v1.add("Total/Average angle error = "+
					String.format("%.6e",curvErr[0])+" / "+
					String.format("%.6e",curvErr[1]));
			sub.put("Basic (with DCEL structure):", v1);
		}
		else {
			v1.add("Topology: " + setTopologyStr(p));
			v1.add("Node/Face Count = " + p.nodeCount+" / "+p.faceCount);
			v1.add("Genus/Euler = "+p.genus+" / "+p.euler);
			v1.add("Alpha/Gamma Vertices = "+p.getAlpha()+" / "+p.getGamma());
			v1.add("Total/Average angle error = "+
					String.format("%.6e",curvErr[0])+" / "+
					String.format("%.6e",curvErr[1]));
			sub.put("Basic", v1);
		}

		StringBuilder strbuf=null;
		Vector<String> v2 = new Vector<String>(4);
		// TODO: problem here that I haven't figured out.
		try {
			v2.add("Area = " + String.format("%.6e",p.carrierArea()));
		} catch (Exception ex) {}
		
		if (p.packDCEL.redChain==null) {
			v2.add("First Face = "+p.packDCEL.layoutOrder.get(0).face.faceIndx);
		}
		else 
			v2.add("First Face/BdryFace = " + 
					p.packDCEL.layoutOrder.get(0).face.faceIndx+" / "+
					p.packDCEL.redChain.myEdge.face.faceIndx);
		int bcount=p.packDCEL.idealFaceCount;
		v2.add("Bdry Component Count = " + bcount);
		if (bcount>0) {
			strbuf=new StringBuilder(p.packDCEL.idealFaces[1].edge.origin.vertIndx);
			for (int j=2;j<bcount;j++)
				strbuf.append(" "+p.packDCEL.idealFaces[j].edge.origin.vertIndx);
			v2.add("Bdry Start verts = " +strbuf.toString());
		}
		sub.put("Technical", v2);

		Vector<String> v3 = new Vector<String>(4);

		// lists
		strbuf=new StringBuilder("vlist/elist/flist = ");
		int cnt=0;
		if (p.vlist!=null) 
			cnt=p.vlist.size();
		strbuf.append(cnt);
		
		cnt=0;
		if (p.elist!=null) 
			cnt=p.elist.size();
		if (p.packDCEL!=null && p.hlist!=null) // for future use
			cnt=p.hlist.size();
		strbuf.append(" / "+cnt);
		
		cnt=0;
		if (p.flist!=null) 
			cnt=p.flist.size();
		strbuf.append(" / "+cnt);
		v3.add("List Counts: "+strbuf.toString());

		// degrees: min/max/avg and std deviation
		int mn=p.nodeCount;
		int mx=1;
		double avgdeg=0.0;
		for (int j=1;j<=p.nodeCount;j++) {
			int dg=p.countFaces(j)+p.getBdryFlag(j);
			avgdeg +=dg;
			mn=(dg<mn) ? dg : mn;
			mx=(dg>mx) ? dg : mx;
		}
		avgdeg /=(double)p.nodeCount;
		double std_dev=0.0;
		for (int j=1;j<=p.nodeCount;j++) {
			int dg=p.countFaces(j)+p.getBdryFlag(j);
			double dev=(double)dg-avgdeg;
			dev *=dev;
			std_dev += dev;
		}
		std_dev=Math.sqrt(std_dev);

		v3.add("Degrees, min/max = " + mn+" / "+mx);
		v3.add("Avg degree/std_dev = "+String.format("%.6e",avgdeg)+" / "+
				String.format("%.6e",std_dev));

		// inversive distances
		if (p.haveInvDistances()) 
			v3.add("Some non-trivial inverse distances are set");
		else 
			v3.add("No non-trivial inverse distances are set");

		sub.put("Lists", v3);
		String s = new String("Pack "+p.packNum+" Data");
		root.put(s, sub);

		return root;
	}  

	public static String setTopologyStr(PackData p) {
		if (!p.status) return new String("pack is empty");
		if (p.genus==0) { // genus zero 
			if (p.getBdryCompCount()==0) // sphere 
				return new String("sphere");
			if (p.getBdryCompCount()==1) // disc 
				return new String("topological disc");
			if (p.getBdryCompCount()==2) // annulus 
				return new String("topological annulus");
			else // n-connected, planar 
				return new String("planar, "+p.getBdryCompCount()+"-connected");
		}
		if (p.getBdryCompCount()==0) // n-torus 
			return new String("topological "+p.genus+"-torus");
		else return new String("genus "+p.genus+", bordered");
	}
}
