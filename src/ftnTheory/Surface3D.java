package ftnTheory;

import java.io.BufferedReader;
import java.io.File;
import java.util.Vector;

import input.CPFileManager;
import packing.PackData;
import packing.PackExtender;
import util.CmdStruct;
import util.StringUtil;

public class Surface3D extends PackExtender {

	public PackData basePack; // store for reference; we will be using 'this' for microgrid
	public int gridGen;
	
	public Surface3D(PackData p,int packQ,String intensityField,int genN) {
		super(p);
		extensionType="Surface3D";
		extensionAbbrev="S3";
		toolTip="'Surface3D' for 3D printing on curved surfaces.";
		registerXType();
		basePack=p.copyPackTo();
		if (running)
			packData.packExtensions.add(this);
		gridGen=genN; // number of generations of hex
	}
	
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;
		int count=0;
		
		if (cmd.startsWith("bary_field")) {
			if (flagSegs==null || flagSegs.size()==0 || (items=flagSegs.get(0))==null ||
				items.size()==0)
				Oops("usage: bary_field {filename}");
			boolean script_flag=false;
			String str=items.remove(0);
			if (StringUtil.isFlag(str)) {
				if (str.startsWith("-s"))
					script_flag=true;
			}
			String filename =items.get(0);
			File file=new File(filename);
			File dir=CPFileManager.CurrentDirectory;
			String name=file.getName();
			BufferedReader fp=CPFileManager.openReadFP(dir,name,script_flag);
			if (fp==null)
				Oops("failed to open file "+name+" in Surface3D");
			basePack.readpack(fp, filename);
			if (basePack.utilBary==null) 
				Oops("failed to fill 'utilBary'");
			
			// got the data into 'utilBary', ready for use
			this.msg("read "+filename+" for BARY_DATA");
			return 1;
		}
		
		// =============== set_grid_intensities ===============
		else if (cmd.startsWith("set_grid_int")) {
			
		}
	
		return count;
	}
	
	public void initCmdStruct() {
		super.initCmdStruct();
		cmdStruct.add(new CmdStruct("bary_field","filename",null,
				"Read {filename} for BARY_DATA giving field intensities "+
				"at the vertices of 'basePack'."));
		cmdStruct.add(new CmdStruct("set_grid_intensities",null,null,
				"Set intensities on vertices from 'utilBary' and on faces "+
				"from area density"));
	}
}
