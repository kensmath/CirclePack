package panels;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.LayoutStyle;

import allMains.CPBase;
import allMains.CirclePack;
import circlePack.PackControl;
import complex.Complex;
import exceptions.ParserException;
import geometry.CircleSimple;
import geometry.SphericalMath;
import input.CPFileManager;
import input.FileDialogs;
import komplex.EdgeSimple;
import listManip.EdgeLink;
import listManip.FaceLink;
import listManip.NodeLink;
import math.Mobius;
import math.Point3D;
import packing.PackData;
import util.CallPacket;
import util.DataFormater;
import util.PathUtil;
import util.StringUtil;

/**
* This code was edited or generated using CloudGarden's Jigloo
* SWT/Swing GUI Builder, which is free for non-commercial
* use. If Jigloo is being used commercially (ie, by a corporation,
* company or business for any purpose whatever) then you
* should purchase a license for each developer using Jigloo.
* Please visit www.cloudgarden.com for details.
* Use of Jigloo implies acceptance of these licensing terms.
* A COMMERCIAL LICENSE HAS NOT BEEN PURCHASED FOR
* THIS MACHINE, SO JIGLOO OR THIS CODE CANNOT BE USED
* LEGALLY FOR ANY CORPORATE OR COMMERCIAL PURPOSE.
*/
public class OutPanel extends javax.swing.JPanel implements ActionListener {

	private static final long 
	serialVersionUID = 1L;
		
	public static enum dataCode {VERT_INDEX,VERT_CENTER,VERT_CURV,
		VERT_AIM,VERT_DEG,VERT_COLOR,VERT_RADII,VERTEX_MAP,VERT_XYZ,VERT_FLOWER,
		FACE_INDEX,FACE_CORNERS,PAVER_CORNERS,FACE_COLOR,FACE_AREA,FACE_VERTICES,
		EDGE_INDICES,EDGE_COLOR,EDGE_LENGTH,EDGE_INT_LENGTH,EDGE_DUAL_CENTERS,
		EDGE_DUAL_INDICES,SHARP_PQ,FACE_DUAL_RADII,FACE_DUAL_CENTER,
		CALL_PACKET,NULL,MOBIUS_LABELS}
		
	private JLabel preLabel;
	private JLabel objLabel;
	private JLabel suffLabel;
	private JTextField suffField;
	private JButton writeButton;
	private JButton AppendButton;
	private JButton codeButton;
	private JTextField objField;
	private JTextField dataField;
	private JLabel dataLabel;
	private JTextField preField;
	static PackData pData;
	static PackData qData;
	
	public static int Vfe;
	
	// Constructor
	public OutPanel() {
		super();
		initGUI();
	}
	
	private void initGUI() {
		try {
			GroupLayout thisLayout = new GroupLayout((JComponent)this);
			this.setLayout(thisLayout);
			this.setPreferredSize(new java.awt.Dimension(550, 250));
			this.setSize(550, 250);
			{
				preLabel = new JLabel();
				preLabel.setText("Prefix (text)");
			}
			{
				preField = new JTextField();
				preField.setToolTipText("Optional prefix text; use \"\\n\" for line break");
			}
			{
				dataLabel = new JLabel();
				dataLabel.setText("Data codes");
			}
			{
				dataField = new JTextField();
				dataField.setToolTipText("desired data (see 'Codes' list); \"\\n\" for line breaks");
			}
			{
				objLabel = new JLabel();
				objLabel.setText("Object list");
			}
			{
				objField = new JTextField();
				objField.setToolTipText("for which objects? e.g. 'a' all, 'b' bdry, etc.");
			}
			{
				suffLabel = new JLabel();
				suffLabel.setText("Suffix (text)");
			}
			{
				suffField = new JTextField();
				suffField.setToolTipText("Optional suffix text");
			}
			{
				codeButton = new JButton();
				codeButton.setText("Codes");
				JPopupMenu objMenu=ObjectMenu();
				codeButton.setComponentPopupMenu(objMenu);
				codeButton.setToolTipText("Codes for desired Data --- right mouse button");
			}
			{
				writeButton = new JButton();
				writeButton.setText("Write to File");
				writeButton.addActionListener(this);
				writeButton.setActionCommand("Write");
			}
			{
				AppendButton = new JButton();
				AppendButton.setText("Append to File");
				AppendButton.addActionListener(this);
				AppendButton.setActionCommand("Append");
			}
				thisLayout.setVerticalGroup(thisLayout.createSequentialGroup()
					.addContainerGap()
					.addGroup(thisLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
					    .addComponent(preField, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					    .addComponent(preLabel, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, 18, GroupLayout.PREFERRED_SIZE))
					.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
					.addGroup(thisLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
					    .addComponent(dataField, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					    .addComponent(dataLabel, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, 17, GroupLayout.PREFERRED_SIZE)
					    .addComponent(codeButton, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
					.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
					.addGroup(thisLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
					    .addComponent(objField, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					    .addComponent(objLabel, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, 16, GroupLayout.PREFERRED_SIZE))
					.addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
					.addGroup(thisLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
					    .addComponent(suffField, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					    .addComponent(suffLabel, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
					.addGap(23)
					.addGroup(thisLayout.createParallelGroup(GroupLayout.Alignment.BASELINE)
					    .addComponent(writeButton, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, 21, GroupLayout.PREFERRED_SIZE)
					    .addComponent(AppendButton, GroupLayout.Alignment.BASELINE, GroupLayout.PREFERRED_SIZE, 22, GroupLayout.PREFERRED_SIZE))
					.addContainerGap(91, 91));
				thisLayout.setHorizontalGroup(thisLayout.createSequentialGroup()
					.addContainerGap(20, 20)
					.addGroup(thisLayout.createParallelGroup()
					    .addGroup(thisLayout.createSequentialGroup()
					        .addGroup(thisLayout.createParallelGroup()
					            .addComponent(objLabel, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 119, GroupLayout.PREFERRED_SIZE)
					            .addComponent(suffLabel, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 118, GroupLayout.PREFERRED_SIZE)
					            .addComponent(dataLabel, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 119, GroupLayout.PREFERRED_SIZE)
					            .addComponent(preLabel, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 119, GroupLayout.PREFERRED_SIZE))
					        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
					        .addGroup(thisLayout.createParallelGroup()
					            .addComponent(suffField, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 380, GroupLayout.PREFERRED_SIZE)
					            .addComponent(objField, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 380, GroupLayout.PREFERRED_SIZE)
					            .addGroup(GroupLayout.Alignment.LEADING, thisLayout.createSequentialGroup()
					                .addComponent(dataField, 0, 261, Short.MAX_VALUE)
					                .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
					                .addComponent(codeButton, GroupLayout.PREFERRED_SIZE, 107, GroupLayout.PREFERRED_SIZE))
					            .addComponent(preField, GroupLayout.Alignment.LEADING, GroupLayout.PREFERRED_SIZE, 380, GroupLayout.PREFERRED_SIZE)))
					    .addGroup(GroupLayout.Alignment.LEADING, thisLayout.createSequentialGroup()
					        .addGap(47)
					        .addComponent(writeButton, GroupLayout.PREFERRED_SIZE, 182, GroupLayout.PREFERRED_SIZE)
					        .addGap(66)
					        .addComponent(AppendButton, GroupLayout.PREFERRED_SIZE, 188, GroupLayout.PREFERRED_SIZE)
					        .addGap(28)))
					.addContainerGap(19, 19));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Get output info from the active packing's DataFormater
	 */
	public void update(int old_pnum) {
		if (old_pnum==PackControl.getActiveCPScreen().getPackNum()) 
			return;
		// save the old
		PackControl.cpScreens[old_pnum].dataFormater.update(
			preField.getText(),dataField.getText(),objField.getText(),
			suffField.getText());
		// bring in new
		DataFormater dFmt=PackControl.getActiveCPScreen().dataFormater;
		preField.setText(dFmt.prefixText);
		suffField.setText(dFmt.suffixText);
		dataField.setText(dFmt.dataTypes);
		objField.setText(dFmt.objList);
	}

	/**
	 * Process an 'output' specification and write the data to an open 
	 * 'BufferedWriter'. Return the number of data items written.
	 * @param fp BufferedWriter: already open
	 * @param p PackData
	 * @param prefix String
	 * @param data String
	 * @param loop String
	 * @param suffix String
	 * @return int: 0=error, -1=error closing file, on success = count>0
	 */
	public static int outputter(BufferedWriter fp,PackData p,
			String prefix,String datastr,String loopstr,String suffix) {
	
		Vector<DataObj> dataObjects=null;
		// process 'prefix' section
		prefix=parsePrefix(p,prefix);
		if (prefix!=null) {
			try {
				fp.write(prefix+" ");
			} catch (Exception ex) {
				return 0;
			}
		}
	  	  
		// First, check if loopstr exists: this guides the 'data' search
		boolean loopstate=true;
		if (loopstr==null || loopstr.trim().length()==0) loopstate=false;

		// If 'non-looping', no lists, perhaps we're after data about packing
		if (!loopstate) { // non-looping data:
			boolean hit=true;
			StringBuilder loopbuf=new StringBuilder(datastr);
			String lit=null;
			String thing=null;
			while (hit && loopbuf.length()>0) {
				hit=false;
				// look for literal first
				if ((lit=StringUtil.find_literal(loopbuf))!=null && lit.length()>0) {
					try {
						fp.write(lit);
					} catch (Exception e) {}
					hit=true;
				}
				else if ((thing=StringUtil.grabNext(loopbuf))!=null && thing.length()>0) {
					switch(thing.charAt(0)) {
					case '_': // variable indicator
					{
						String var_lit_str=StringUtil.varSub(thing);
						try {
							fp.write(var_lit_str+" "); // add trailing space
						} catch (Exception e) {}
						hit=true;
						break;
					}
					case 'M': 
					{
					  if (thing.equals("Mob")) { // current mobius 
						  Mobius mob=CPBase.Mob;
						  try {
							  fp.write(mob.a.x+" "+mob.a.y+"\n");
							  fp.write(mob.b.x+" "+mob.b.y+"\n");
							  fp.write(mob.c.x+" "+mob.c.y+"\n");
							  fp.write(mob.d.x+" "+mob.d.y+"\n\n");
							  hit=true;
						  } catch (Exception e) {}
					  }
					  break;
					}
					case 'P': 
					{
						if (thing.equals("PATH")) { // current path 
							Vector<Vector<Complex>> path=PathUtil.gpPolygon(CPBase.ClosedPath);
							Iterator<Vector<Complex>> ZP=path.iterator();
							while (ZP.hasNext()) {
								Vector<Complex> loc_path=(Vector<Complex>)ZP.next();
								Iterator<Complex> zp=loc_path.iterator();
								Complex z1=(Complex)zp.next();
								Complex z=null;
								try {
									fp.write(+z1.x+" "+z1.y+" m\n");
									while (zp.hasNext()) {
										z=(Complex)zp.next();
										fp.write(z.x+" "+z.y+" l\n");
									}
									// intended to be closed
									if (z1.minus(z).abs()<.000001) fp.write("cp \n");
									fp.write("s\n"); // stroke
								} catch (IOException iox) {}
							} // end of while
							hit=true;
						}
					  break;
					}
					case 't': // trace^2 of given mobius
					{
						if (thing.equals("tr")) { 
							Complex tr2=CPBase.Mob.getTraceSqr();
							try {
								fp.write(tr2.x+" "+tr2.y+"\n");
							} catch(Exception e) {}
							hit=true;
						}
						break;
					}
					case '#': {
					  if (thing.equals("#N")) { // packing nodecount 
						  try {
							  fp.write(p.nodeCount);
						  } catch(Exception e) {}
					  	  hit=true;
					  }
					  else if (thing.equals("#F")) { // packing facecount 
						  try {
							  fp.write(p.faceCount);
						  } catch(Exception e) {}
						  hit=true;
					  }
					  break;
					}
					case 'C': { // 'CPcallPacket' for passing computed data
						if (thing.equals("CP")) { // print double value from 'CPcallPacket'
							CallPacket cp=CPBase.CPcallPacket;
							if (cp!=null && !cp.error) {
								try {
									fp.write(Double.toString(cp.double_vec.get(0)));
								} catch (Exception e) {}
								hit=true;
							}
						}
						break;
					}
					} // end of switch 
					if (!hit) { // not found? treat as literal --- e.g. a numerical value
						try {
							fp.write(thing);
						} catch (Exception e) {}
						hit=true;
					}
				}
			} // end of while
		} // end of non-looping data
		
		// looping data: process 'data' section
		else {
			StringBuilder dataBuffer=new StringBuilder(datastr);
			dataObjects=parseData(p,dataBuffer);
			if (dataObjects.size()==0) {
				CirclePack.cpb.myErrorMsg("error: output: problems parsing data section");
				return 0;
			}
		}
	  
		// process 'loop' section
		int count=0;
		if (loopstate) {
			loopstr=loopstr.trim();
			count = outputLoop(fp,p,loopstr,dataObjects);
//			if (count==0) {
//				CirclePack.cpb.myErrorMsg("error: output: error in looping description");
//				return 0;
//			}
		}
	  
		// process 'suffix' section
		suffix=suffix.trim();
		suffix=parseSuffix(p,suffix);
		if (suffix!=null && suffix.length()>0) {
			try {
				fp.write(suffix);
			} catch (Exception ex) {
				return 0;
			}
		}
	  
		// clean up and return
		try {
			fp.flush();
			fp.close();
		} catch (Exception ex) {
			CirclePack.cpb.myErrorMsg("error: output: some error is closing the file");
			return -1;
		}
		if (count==0)
			return 1;
		return count;
	}

	public static String parsePrefix(PackData p,String prefix) {
		// TODO: may need more processing for escaped characters.
		String back=prefix.replaceAll("\\\\n","\n").replaceAll("\\\\t","\t");
		back=back.replaceAll("\"\\n\"","\n"); // if "\n" instead of just \n is used
		return back;
	}
	
	public static Vector<DataObj> parseData(PackData p,StringBuilder dataBuffer) {
		Vector<DataObj> dataObjs=new Vector<DataObj>(5);
		Vfe=0;
		String nextStr=null;
		while (dataBuffer.length()>0) { // cycle through data objects
			int k=0;
			while (k<dataBuffer.length() && dataBuffer.charAt(k)<=' ') k++;
			if (k==dataBuffer.length()) break;
			if (dataBuffer.charAt(k)=='\"' || dataBuffer.charAt(k)=='\'') { // literal
				if ((nextStr=StringUtil.find_literal(dataBuffer))!=null) {
					DataObj dObj=new DataObj();
					dObj.spec=new String(nextStr);
					dataObjs.add(dObj);
				}
				// didn't find literal?
				else dataBuffer.deleteCharAt(k); // to prevent infinite loop
			}
			else { // NOTE: 'Vfe' update may disqualify previous 'dataObjs'
				int kk=k;
				while (kk<dataBuffer.length() && dataBuffer.charAt(kk)>' ') 
					kk++;
				nextStr=dataBuffer.substring(k,kk);
				dataBuffer.delete(k,kk);
				DataObj dobj=data_obj_parse(nextStr);
				if (dobj!=null && dobj.code!=dataCode.NULL) {
					Vfe=dobj.vfe;
					dataObjs.add(dobj);
				}
			}
		} // end of while 
		return dataObjs;
	}
	
	public static int print_vert_obj(PackData p,BufferedWriter fp,
			dataCode code,int v) {
		  int w;
		  Complex z=null;

		  try {
		  if (code==dataCode.VERT_INDEX) {
			  fp.write(Integer.toString(v)+" ");
			  return 1;
		  }
		  if (code==dataCode.VERT_FLOWER) {
			  StringBuilder strbld=new StringBuilder("");
			  for (int j=0;j<=(p.countFaces(v)+p.getBdryFlag(v));j++)
				  strbld.append(p.kData[v].flower[j]+" ");
			  fp.write(strbld.toString());
			  return 1;
		  }
		  if (code==dataCode.VERT_CENTER) {
			  z=p.getCenter(v);
			  fp.write(z.x+" "+z.y+" ");
			  return 1;
		  }
		  if (code==dataCode.VERT_CURV) {
			  fp.write(p.getCurv(v)+" ");
			  return 1;
		  }
		  if (code==dataCode.VERT_AIM) {
		    fp.write(p.getAim(v)+" ");
		    return 1;
		  }
		  if (code==dataCode.VERT_DEG) {
			  fp.write(Integer.toString((p.countFaces(v)+p.getBdryFlag(v)))+" ");
			  return 1;
		  }
		  if (code==dataCode.VERT_COLOR) {
			  Color col=p.getCircleColor(v);
			  fp.write(col.getRed()+" "+col.getGreen()+" "+col.getBlue()+" ");
			  return 1;
		  }
		  if (code==dataCode.VERTEX_MAP && p.vertexMap!=null && (w=p.vertexMap.findW(v))>0) { // vertex map image for v
			  fp.write(w+" ");
			  return 1;
		  }
		  if (code==dataCode.VERT_RADII) {
			  fp.write(p.getRadius(v)+" ");
			  return 1;
		  }
		  if (code==dataCode.VERT_XYZ) {
			  double []xyz;
			  
			  // have xyz data stored?
			  if (p.xyzpoint!=null) {
				  Point3D pd=p.xyzpoint[v];
				  xyz=new double[3];
				  xyz[0]=pd.x;
				  xyz[1]=pd.y;
				  xyz[2]=pd.z;
			  }
			  
			  else  if (p.hes>0) {
				  xyz=SphericalMath.s_pt_to_vec(p.getCenter(v));
			  }
			  else { // eucl/hyp, just store flat data
				  xyz=new double[3];
				  xyz[0]=p.getCenter(v).x;
				  xyz[1]=p.getCenter(v).y;
				  xyz[2]=0.0;
			  }
			  
			  fp.write(xyz[0]+" "+xyz[1]+" "+xyz[2]+" ");
			  return 1;
		  }
		  if (code==dataCode.PAVER_CORNERS) { // a 'paver' is polygon 
			  // formed by union of faces containing a given vertex; 
			  // we save the open list of polygon corners. For 
			  // boundary v, include corner at v itself.
				Complex []pts=p.corners_paver(v); // non-closed list
				int num=pts.length;
				fp.write(pts[0].x+" "+pts[0].y+"i   ");
				for (int j=1;j<num;j++) {
					fp.write(pts[j].x+" "+pts[j].y+"i   ");
				}
				fp.write("\n");
				return 1;
		  }
		  if (code==dataCode.SHARP_PQ) {
		    double shp;
		    if (pData.status && qData.status && v<qData.nodeCount) {
		      shp=qData.getActualRadius(v)/pData.getActualRadius(v);
		      fp.write(shp+" ");
		      return 1;
		    }
		  }
		  } catch (Exception ex) {}
		  
		  return 0;
		} 
	
		public static int print_face_obj(PackData p,
				BufferedWriter fp,dataCode code,int f) {

			try {
		  if (code==dataCode.FACE_INDEX) { // face index
		    fp.write(Integer.toString(f)+" ");
		    return 1;
		  }
		  if (code==dataCode.FACE_CORNERS) { // corner locations
			  Complex []pts=p.corners_face(f);
			  fp.write(pts[0].x+" "+pts[0].y+"   "+pts[1].x+" "+pts[1].y+"   "+pts[2].x+" "+pts[2].y+"   ");
			  return 1;
		  }
		  if (code==dataCode.FACE_DUAL_CENTER) {
			  Complex z=p.faceIncircle(f).center;
			  fp.write(z.x+" "+z.y+" ");
			  return 1;
		  }
		  if (code==dataCode.FACE_DUAL_RADII) {
			  CircleSimple sc=p.faceIncircle(f);
			  fp.write(sc.rad+" ");
			  return 1;
		  }
		  if (code==dataCode.FACE_COLOR) { // color 
			  Color col=p.getFaceColor(f);
			  fp.write(col.getRed()+" "+col.getGreen()+" "+col.getBlue()+" ");
			  return 1;
		  }
		  if (code==dataCode.FACE_AREA) { // area 
		    fp.write("fixup");
		    return 1;
		  }
		  if (code==dataCode.FACE_VERTICES) { // indices of vertices
			  fp.write(p.faces[f].vert[0]+" "+p.faces[f].vert[1]+" "+p.faces[f].vert[2]+" ");
			  return 1;
		  } 
			} catch (Exception ex) {}
		  return 0;
		} 
									       
		public static int print_edge_obj(PackData p,
				BufferedWriter fp,dataCode code,int v,int w) {

			try {
		  if (code==dataCode.EDGE_COLOR) { // color
			  // TODO: don't yet have color stored for edges
			  CirclePack.cpb.myErrorMsg("output: edges don't yet have recorded colors");
			  return 1;
		  }
		  if (code==dataCode.EDGE_LENGTH) { // actual length (based on centers)
			    fp.write(p.edgeLength(v, w)+" ");
			    return 1;
			  }
		  if (code==dataCode.EDGE_INT_LENGTH) { // intended length (based on radii/overlaps)
			    fp.write(p.intendedEdgeLength(v, w)+" ");
			    return 1;
			  }
		  if (code==dataCode.EDGE_INDICES) { // length (based on radii/overlaps)
			    fp.write(v+" "+w+" ");
			    return 1;
			  }
		  if (code==dataCode.EDGE_DUAL_CENTERS) { // centers of dual edgea
			  Complex []pts=p.ends_dual_edge(new EdgeSimple(v,w));
			  fp.write(pts[0].x+" "+pts[0].y+"  "+pts[1].x+" "+pts[1].y);
			  return 1;
		  }
		  if (code==dataCode.EDGE_DUAL_INDICES) { // face indices for dual edges
			  int []lf=p.left_face(v,w);
			  int []rf=p.left_face(w,v);
			  if (lf[0]==0 || rf[0]==0) return 0;
			  fp.write(lf[0]+" "+rf[0]);
			  return 1;
		  }
		  
			} catch (Exception ex) {}
		  return 0;
		} 

		/**
		 * Loop through data objects to print. Note, 'Vfe' has been
		 * set based on the types of objects selected: vertices, faces,
		 * edges, and a few others. There may be more than one 'DataObj' 
		 * specified. E.g, if Vfe==1 for vertices, we might want both 
		 * centers and radii. There may also we 'DataObj's for literals.
		 * @param fp BufferedWriter
		 * @param p PackData
		 * @param loopstr String, specifying vertices, edges, faces, or other
		 * @param dataobj Vector<DataObj>
		 * @return int
		 */
		public static int outputLoop(BufferedWriter fp,PackData p,
				String loopstr,Vector<DataObj> dataobj) {
			int count=0;

			try {
			// output calls for vertex indices? expect vert list
		    if (Vfe==1) {
		    	int v;
		    	NodeLink vertlist= new NodeLink(p,loopstr);
		    	
		    	if (vertlist.size()==0) 
		    		throw new ParserException("error: output: no vertices specified");
		    	Iterator<Integer> vlist=vertlist.iterator();
		    	while (vlist.hasNext()) { // loop through vertices
		    		v=(Integer)vlist.next();
		    		for (int i=0;i<dataobj.size();i++) {
		    			DataObj dtrace=(DataObj)dataobj.get(i);
		    			// loop through objects, stopping at any improper one.
		    			if (dtrace!=null && (dtrace.spec!=null || dtrace.vfe==Vfe)) { 
		    				if (dtrace.spec!=null) { // literal 
		    					fp.write(dtrace.spec);
		    					count++;
		    				}
		    				else count+=print_vert_obj(p,fp,dtrace.code,v);
		    			} 
		    		} // done going through objects
		    	} // done going through vertices
		    	return count;
		    }
		    
		    // output calls for face indices? expect face list
		    if (Vfe==2) {
		    	int f;
		    	FaceLink facelist= new FaceLink(p,loopstr);
		    	
		    	if (facelist.size()==0) 
		    		throw new ParserException("error: output: no faces specified");
		    	Iterator<Integer> flist=facelist.iterator();
		    	while (flist.hasNext()) { // loop through vertices
		    		f=(Integer)flist.next();
		    		for (int i=0;i<dataobj.size();i++) {
		    			DataObj dtrace=(DataObj)dataobj.get(i);
		    			// loop through objects, stopping at any improper one.
		    			if (dtrace!=null && (dtrace.spec!=null|| dtrace.vfe==Vfe)) { 
		    				if (dtrace.spec!=null) { // literal 
		    					fp.write(dtrace.spec);
		    					count++;
		    				}
		    				else {
		    					count+=print_face_obj(p,fp,dtrace.code,f);
		    				}
		    			} 
		    		} // done going through objects
		    	} // done going through faces
		    	return count;
		    }

		    // output calls for edges? expect edge list
		    else if (Vfe==3) {
		    	EdgeLink edgelist=new EdgeLink(p,loopstr);

		    	if (edgelist.size()==0) 
		    		throw new ParserException("error: output: no edges specified");
		    	Iterator<EdgeSimple> elist=edgelist.iterator();
		    	EdgeSimple edge=null;
		    	while (elist.hasNext()) { // loop through vertices
		    		edge=(EdgeSimple)elist.next();
		    		for (int i=0;i<dataobj.size();i++) {
		    			DataObj dtrace=(DataObj)dataobj.get(i);
		    			// loop through objects, stopping at any improper one.
		    			if (dtrace!=null && (dtrace.spec!=null|| dtrace.vfe==Vfe)) { 
		    				if (dtrace.spec!=null) { // literal 
		    					fp.write(dtrace.spec);
		    					count++;
		    				}
		    				else count+=print_edge_obj(p,fp,dtrace.code,edge.v,edge.w);
		    			} 
		    		} // done going through objects
		    	} // done going through vertices
		    	return count;
		    }

		    // others, such as MOBIUS_LABELS
		    else if (Vfe==4) {
		    	Iterator<DataObj> dit=dataobj.iterator();
		    	while (dit.hasNext()) {
		    		DataObj dtrace=dit.next();
		    		if (dtrace.code==dataCode.MOBIUS_LABELS) {
		    			String []mlabels=loopstr.split(" ");
		    			int n=mlabels.length;
		    			if (n==0)
		    				return count;
		    			else {
		    				for (int k=0;k<n;k++) {
		    					Mobius mb=p.namedSidePair(mlabels[k]);
		    					if (mb!=null) 
		    						fp.write(mb.mob2String().toString()+"\n\n");
		    				}
		    			}
		    		}
		    	} // end of while
		    }
		    
		    // error 
		    else { 
		    	CirclePack.cpb.myErrorMsg("output: didn't find proper list.");
		    	return 0;
		    }
			} catch (Exception ex) {}
			return 1;
		}
		
		public static String parseSuffix(PackData p,String suffix) {
			// TODO: may need more processing for escaped characters.
			String back=suffix.replaceAll("\\\\n","\n");
			return back; 
		}
		
	/**
	 * Parse a data string to see what data it specifies; return 'dataCode'
	 * or null if there's no data.
	 * @param datastr String
	 * @return 'dataCode'
	 */
	public static DataObj data_obj_parse(String datastr) {
		if (datastr==null || datastr.length()==0) return null; 
		datastr=datastr.trim();
		if (datastr.length()==0) return null; 
		char c=datastr.charAt(0);
		DataObj dobj=new DataObj();
		dobj.spec=null;
		dobj.code=dataCode.NULL;
		switch(c) {
		case 'V':
		{
			dobj.vfe=1;
			if (datastr.equals("VI")) 
				dobj.code=dataCode.VERT_INDEX;
			else if (datastr.equals("VF")) 
				dobj.code=dataCode.VERT_FLOWER;
			else if (datastr.equals("VZ"))
				dobj.code=dataCode.VERT_CENTER;
			else if (datastr.equals("VA"))
				dobj.code=dataCode.VERT_CURV;
			else if (datastr.equals("VT"))
				dobj.code=dataCode.VERT_AIM;
			else if (datastr.equals("VD"))
				dobj.code=dataCode.VERT_DEG;
			else if (datastr.equals("VC"))
				dobj.code=dataCode.VERT_COLOR;
			else if (datastr.equals("VR"))
				dobj.code=dataCode.VERT_RADII;
			else if (datastr.equals("VM"))
				dobj.code=dataCode.VERTEX_MAP;
			else if (datastr.equals("VXYZ"))
				dobj.code=dataCode.VERT_XYZ;
			else if (datastr.startsWith("VS")) { // 'sharp' function (ratios)
				try {
					int pnum=Integer.parseInt(datastr.substring(2,3));
					int qnum=Integer.parseInt(datastr.substring(3,4));
					pData=PackControl.cpScreens[pnum].getPackData();
					qData=PackControl.cpScreens[qnum].getPackData();
				} catch (Exception ex) {
					throw new ParserException("error: output: bad 'VS' perscription");
				}
				dobj.code=dataCode.SHARP_PQ;
			}
			break;
		} 
	    case 'F':
	    {
	    	dobj.vfe=2;
	    	if (datastr.equals("FI")) 
	    		dobj.code=dataCode.FACE_INDEX;
	    	if (datastr.equals("FZ")) 
	    		dobj.code=dataCode.FACE_CORNERS;
	    	if (datastr.equals("FC")) 
	    		dobj.code=dataCode.FACE_COLOR;
	    	if (datastr.equals("FA")) 
	    		dobj.code=dataCode.FACE_AREA;
	    	if (datastr.equals("FV")) 
	    		dobj.code=dataCode.FACE_VERTICES;
			if (datastr.equals("FDR"))
				dobj.code=dataCode.FACE_DUAL_RADII;
			if (datastr.equals("FDZ"))
				dobj.code=dataCode.FACE_DUAL_CENTER;
	    	break;
	    }
	    case 'E':
	    {
	    	dobj.vfe=3;
	    	if (datastr.equals("EC")) 
	    		dobj.code=dataCode.EDGE_COLOR;
	    	if (datastr.equals("EL")) 
	    		dobj.code=dataCode.EDGE_LENGTH;
	    	if (datastr.equals("ER"))
	    		dobj.code=dataCode.EDGE_INT_LENGTH;
	    	if (datastr.equals("EI")) 
	    		dobj.code=dataCode.EDGE_INDICES;
	    	if (datastr.equals("EDZ"))
	    		dobj.code=dataCode.EDGE_DUAL_CENTERS;
	    	if (datastr.equals("EDI"))
	    		dobj.code=dataCode.EDGE_DUAL_INDICES;
	    	break;
	    }
	    case 'C': // 'CPcallPacket' is global for passing information
	    {
	    	if (datastr.equals("CP"))
	    		dobj.vfe=0;
	    		dobj.code=dataCode.CALL_PACKET;
	    	break;
	    }
	    case 'P':
	    {
	    	if (datastr.equals("PZ")) 
	    		dobj.vfe=1; // based on vertices
	    		dobj.code=dataCode.PAVER_CORNERS;
	    	break;
	    }
	    case 'M':
	    {
	    	if (datastr.equals("Mob")) 
	    		dobj.vfe=4;
	    		dobj.code=dataCode.MOBIUS_LABELS;
	    	break;
	    }
	    } /* end of switch */
	  return dobj; // no legal code 
	} 
	
	protected int popupDialog(String cmd){
	  	boolean append=false;
	  	BufferedWriter fp;

	    File theFile=null;
    	if ((theFile=FileDialogs.saveDialog(FileDialogs.FILE,true))!=null) {
    		if (cmd.equals("Append")) 
    			append=true;
	    	fp=CPFileManager.openWriteFP(theFile,append,false);
	    	// call 'outputter' to do the actual processing
	    	return outputter(fp,CirclePack.cpb.getActivePackData(),preField.getText(),
	    			dataField.getText(),objField.getText(),suffField.getText());
    	}
		else return -1;
	}
	
	/**
	 * Build the object menu
	 *
	 */
	public JPopupMenu ObjectMenu() {
		JPopupMenu jpm=new JPopupMenu();
		JMenuItem item;
		
		item=new JMenuItem("\\n -- line feed");
		item.addActionListener(this);
		item.setActionCommand(" \"\\n\" ");
		jpm.add(item);
		
		item=new JMenuItem("VI -- circle index");
		item.addActionListener(this);
		item.setActionCommand(" VI ");
		jpm.add(item);

		item=new JMenuItem("VF -- vert flower");
		item.addActionListener(this);
		item.setActionCommand(" VF ");
		jpm.add(item);

		item=new JMenuItem("VR -- radii");
		item.addActionListener(this);
		item.setActionCommand(" VR ");
		jpm.add(item);
		
		item=new JMenuItem("VZ -- centers");
		item.addActionListener(this);
		item.setActionCommand(" VZ ");
		jpm.add(item);
		
		item=new JMenuItem("VXYZ -- 3D centers");
		item.addActionListener(this);
		item.setActionCommand(" VXYZ ");
		jpm.add(item);
		
		item=new JMenuItem("VA -- angle sums");
		item.addActionListener(this);
		item.setActionCommand(" VA ");
		jpm.add(item);
		
		item=new JMenuItem("VT -- angle targets");
		item.addActionListener(this);
		item.setActionCommand(" VT ");
		jpm.add(item);
		
		item=new JMenuItem("VD -- degrees");
		item.addActionListener(this);
		item.setActionCommand(" VD ");
		jpm.add(item);
		
		item=new JMenuItem("VM -- vertex map");
		item.addActionListener(this);
		item.setActionCommand(" VM ");
		jpm.add(item);
		
		item=new JMenuItem("VC -- color");
		item.addActionListener(this);
		item.setActionCommand(" VC ");
		jpm.add(item);
		
		item=new JMenuItem("VSpq -- sharp functon");
		item.addActionListener(this);
		item.setActionCommand(" VSpq ");
		jpm.add(item);
		
		item=new JMenuItem("Varg -- arg(center)");
		item.addActionListener(this);
		item.setActionCommand(" Varg ");
		jpm.add(item);
		
		item=new JMenuItem("FI -- face indices");
		item.addActionListener(this);
		item.setActionCommand(" FI ");
		jpm.add(item);
		
		item=new JMenuItem("FC -- colors");
		item.addActionListener(this);
		item.setActionCommand(" FC ");
		jpm.add(item);
		
		item=new JMenuItem("FA -- areas");
		item.addActionListener(this);
		item.setActionCommand(" FA ");
		jpm.add(item);
		
		item=new JMenuItem("FV -- vertices");
		item.addActionListener(this);
		item.setActionCommand(" FV ");
		jpm.add(item);
		
		item=new JMenuItem("FZ -- corners");
		item.addActionListener(this);
		item.setActionCommand(" FZ ");
		jpm.add(item);
		
		item=new JMenuItem("PZ -- paver corners");
		item.addActionListener(this);
		item.setActionCommand(" PZ ");
		jpm.add(item);
		
		item=new JMenuItem("FDR -- dual rad");
		item.addActionListener(this);
		item.setActionCommand(" FDR ");
		jpm.add(item);
		
		item=new JMenuItem("FDZ -- dual cent");
		item.addActionListener(this);
		item.setActionCommand(" FDZ ");
		jpm.add(item);
		
		item=new JMenuItem("EI -- edge end indices");
		item.addActionListener(this);
		item.setActionCommand(" EI ");
		jpm.add(item);
		
		item=new JMenuItem("EC -- colors");
		item.addActionListener(this);
		item.setActionCommand(" EC ");
		jpm.add(item);
		
		item=new JMenuItem("EL -- actualy lengths");
		item.addActionListener(this);
		item.setActionCommand(" EL ");
		jpm.add(item);
		
		item=new JMenuItem("ER -- intended lengths");
		item.addActionListener(this);
		item.setActionCommand(" ER ");
		jpm.add(item);
		
		item=new JMenuItem("EDZ -- dual edge ends");
		item.addActionListener(this);
		item.setActionCommand(" EDZ ");
		jpm.add(item);
		
		item=new JMenuItem("EDB -- dual edge bary ends");
		item.addActionListener(this);
		item.setActionCommand(" EDB ");
		jpm.add(item);
		
		item=new JMenuItem("EDI -- dual edge face indices");
		item.addActionListener(this);
		item.setActionCommand(" EDI ");
		jpm.add(item);
		
		item=new JMenuItem("Mob: Mobius");
		item.addActionListener(this);
		item.setActionCommand(" Mob ");
		jpm.add(item);
		
		item=new JMenuItem("CP -- Call packet value");
		item.addActionListener(this);
		item.setActionCommand(" CP ");
		jpm.add(item);
		
		item=new JMenuItem("tr -- trace");
		item.addActionListener(this);
		item.setActionCommand(" tr ");
		jpm.add(item);
		
		item=new JMenuItem("Path");
		item.addActionListener(this);
		item.setActionCommand(" Path ");
		jpm.add(item);
		
		item=new JMenuItem("#N modecount");
		item.addActionListener(this);
		item.setActionCommand(" #N ");
		jpm.add(item);
		
		item=new JMenuItem("#F facecount");
		item.addActionListener(this);
		item.setActionCommand(" #F ");
		jpm.add(item);
		
		return jpm;
}
	public void actionPerformed(ActionEvent e){
	 	String command = e.getActionCommand();
	  	if (command.equals("Write") || command.equals("Append")) {
			popupDialog(command);
	  	}
	  	else if (e.getSource() instanceof JMenuItem) {
	  		dataField.setText(dataField.getText()+command);
	  	}
	}
	  
}

/**
 * Specialized object for storing output information
 */
class DataObj{
    int vfe;                        // 1=vertices, 2=faces, 3=edges, 4=other
    OutPanel.dataCode code;         // code for type of data 
    String spec;                    // literal string object 
}
