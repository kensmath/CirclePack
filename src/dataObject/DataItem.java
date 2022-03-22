package dataObject;

import allMains.CirclePack;

/**
 * A 'DataObject' packages information for use, eg., by 'output'.
 * As of 3/2022, I don't think this is used. 
 * @author kens
 *
 */
public class DataItem {
	
	public enum DataCategory {VERTEX,FACE,EDGE,LITERAL,NULL} 
	public enum DataType {CIR_INDEX,CIR_FLOWER,CIR_CENTER,CIR_XYZ,ANGLE_SUM,ANGLE_TARGET,
		CIR_DEGREE,CIR_COLOR,VERTEX_MAP,CIR_RADIUS,SHARP_FTN,CIR_CENTER_ARG,
		FACE_INDEX,FACE_CORNERS,FACE_COLOR,FACE_AREA,FACE_VERTICES,
		EDGE_VERTICES,EDGE_COLOR,EDGE_LENGTH,EDGE_INT_LENGTH}
	
	DataCategory category;
	DataType type;
	String literal;
	DataItem next;

	// Constructor
	public DataItem(String datastr) {
		data_obj_parse(datastr);
		next=null;
	}
	
	public DataItem(String datastr,DataItem current) {
		this(datastr);
		DataItem hold=current.next;
		current.next=this;
		this.next=hold;
	}
	
	/** parse a string to see its 'DataCategory' (vertices/faces/edges
	 * or literal) and set appropriate 'DataType'. Set category to NULL
	 * on error.
	*/
	public void data_obj_parse(String datastr) {
	    if (datastr==null || datastr.length()<2) {
		category=DataCategory.NULL;
		return;
	    }
	    switch(datastr.charAt(0)) {
	    case 'V':
	    {
			  category=DataCategory.VERTEX;
			  if (datastr.equals("VI")) { // circle index 
				  type=DataType.CIR_INDEX;
				  return;
			  }
			  else if (datastr.equals("VF")) { // flower
				  type=DataType.CIR_FLOWER;
			  }
			  else if (datastr.equals("VZ")) { // circle center 
				  type=DataType.CIR_CENTER;
				  return;
			  }
			  else if (datastr.equals("VXYZ")) { // circle 3D center 
				  type=DataType.CIR_XYZ;
				  return;
			  }
			  else if (datastr.equals("VA")) { // angle sum  
				  type=DataType.ANGLE_SUM;
				  return;
			  }
			  else if (datastr.equals("VT")) { // target angle  
				  type=DataType.ANGLE_TARGET;
				  return;
			  }
			  else if (datastr.equals("VD")) { // circle degree  
				  type=DataType.CIR_DEGREE;
				  return;
			  }
			  else if (datastr.equals("VC")) { // circle color  
				  type=DataType.CIR_COLOR;
				  return;
			  }
			  else if (datastr.equals("V")) { // vertexMap  
				  type=DataType.VERTEX_MAP;
				  return;
			  }
			  else if (datastr.equals("V")) { // circle radius  
				  type=DataType.CIR_RADIUS;
				  return;
			  }
			  else if (datastr.equals("Varg")) { // circle center argument  
				  type=DataType.CIR_CENTER_ARG;
				  return;
			  }
			  else if (datastr.equals("VS")) { // sharp function: VS p q
				  if (datastr.length()<6) {
					  CirclePack.cpb.myErrorMsg("usage: output VS p q, where 'p' and 'q' are "+
							  "numbers of existing packings.");
					  literal="";
					  type=DataType.SHARP_FTN;
					  return;
				  }
				  else literal=datastr.substring(3).trim(); // put data in 'literal'
				  type=DataType.SHARP_FTN;
				  return;
			  }
			  break;
	    }
	    case 'F':
	    {
			  category=DataCategory.FACE;
			  if (datastr.equals("FI")) { // index 
				  type=DataType.FACE_INDEX;
				  return;
			  }
			  else if (datastr.equals("FZ")) { // corners
				  type=DataType.FACE_CORNERS;
				  return;
			  }
			  else if (datastr.equals("FC")) { // colors
				  type=DataType.FACE_COLOR;
				  return;
			  }
			  else if (datastr.equals("FA")) { // area
				  type=DataType.FACE_AREA;
				  return;
			  }
			  else if (datastr.equals("FV")) { // vertices
				  type=DataType.FACE_VERTICES;
				  return;
			  }
			  break;
	    }
	    case 'E':
	    {
			  category=DataCategory.EDGE;
			  if (datastr.equals("EV")) { // vertices 
				  type=DataType.EDGE_VERTICES;
				  return;
			  }
			  else if (datastr.equals("EC")) { // color
				  type=DataType.EDGE_COLOR;
				  return;
			  }
			  else if (datastr.equals("EL")) { // actual length
				  type=DataType.EDGE_LENGTH;
				  return;
			  }
			  else if (datastr.equals("ER")) { // intended length (from radii, inv dist)
				  type=DataType.EDGE_INT_LENGTH;
				  return;
			  }
			  break;
	    }
	    default:
	    {
	    	category=DataCategory.NULL;
	    	return;
	    }
	    } // end of switch
	}

	
}
