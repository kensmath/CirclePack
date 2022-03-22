package dataObject;

import dcel.DcelFace;
import dcel.PackDCEL;
import exceptions.ParserException;
import packing.PackData;
import util.ColorUtil;

/**
 * Gathers data on a face, as needed for inquiries or for
 * the 'Pack Info' window in GUI mode.
 * @author kstephe2
 *
 */
public class FaceData {
	PackData parent;
	public int findx;
	public String vertsStr;
	public int colorCode;
	public int mark;
	
	public FaceData(PackData p,int indx) {
		parent=p;
		PackDCEL pdcel=p.packDCEL;
		DcelFace face;
		findx=indx;
		if (indx<0) { // ideal face
			int iindx=-indx;
			if (iindx>pdcel.idealFaceCount)
				throw new ParserException("ideal face index out of range");
			face=pdcel.idealFaces[iindx];
		}
		else {
			face=pdcel.faces[findx];
		}
		int[] verts=face.getVerts();
		StringBuilder fbld=new StringBuilder();
		int vlength=verts.length;
		if (vlength>5)
			vlength=5;
		for (int i=0;i<vlength;i++) {
			fbld.append(Integer.toString(verts[i]));
			if (i < (vlength-1)) 
				fbld.append(" ");
		}
		if (vlength!=verts.length)
			fbld.append("...");
		vertsStr=fbld.toString();
		colorCode=ColorUtil.col_to_table(face.getColor());
		mark=face.mark;
	}

}
