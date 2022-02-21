package allMains;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;

import JNI.JNIinit;
import input.CPFileManager;
import input.CommandStrParser;
import packing.PackData;
import packing.ReadWrite;

/**
 * A standalone package for computing maximal packings using Java
 * and C libraries. The command line argument gives an incoming 
 * packing "name.p" (relative to the current directory) and the 
 * resulting max packing is stored as "name_max.p". 
 * @author kens 6/8/2015
 *
 */
public class MaxPackman {
	
	static PackData packData;
	public static File HomeDirectory=new File(System.getProperty("user.home"));

	public static void main(String[] args) {

		if (args.length==0) {
			System.err.println("MaxPackman: no argument given; exit");
			System.exit(1);
		}
		
		new JNIinit(); // start the C libraries
		
		// read the packing
		packData=new PackData(null);
		String name=null;
		File dir=null;
		try {
			File file=new File(args[0]);
			dir=new File(HomeDirectory+File.separator+file.getParent());
			name=file.getName();
			BufferedReader fp=CPFileManager.openReadFP(dir,name,false);
			ReadWrite.readpack(fp,packData,name);
			fp.close();
		} catch(Exception ex) {
			System.err.println("failed to read '"+name+"'; error: "+ex.getMessage());
			System.exit(2);
		}
		
		StringBuilder outmsg=new StringBuilder("Loaded "+name+", "+packData.nodeCount+" vertices; ");
		
		// max_pack it
		int ans=CommandStrParser.jexecute(packData,"max_pack");
		if (ans<=0) {
			System.err.println("MaxPackman: packing of '"+name+"' has failed.");
			System.exit(3);
		}
		
		// get outgoing name
		int k=name.lastIndexOf('.');
		StringBuilder strb=new StringBuilder();
		if (k>0) {
			strb.append(name.substring(0, k));
			strb.append("_max");
			strb.append(name.substring(k));
		}
		else {
			strb.append(name);
			strb.append("_max.p");
		}
		
		String outname=strb.toString();
		
		// write the maximal packing
		BufferedWriter fpout=CPFileManager.openWriteFP(dir,false,outname,false);
		try {
			ReadWrite.writePack(fpout,packData,0017,false);
			fpout.flush();
			fpout.close();
		} catch (Exception ex) {
			System.err.println("error in writing max packing result");
			System.exit(4);
		}
		
		outmsg.append(ans+" repack cycles; max packing '"+dir+File.separator+outname+"'\n");
		System.out.println(outmsg.toString());
	}

}
