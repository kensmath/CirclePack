package JNI;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import allMains.CirclePack;
import exceptions.InOutException;
import exceptions.JNIException;
import geometry.SphericalMath;
import input.CPFileManager;
import util.StringUtil;

/**
 * Static routines for creating Delaunay triangulations with
 * calls to 'qhull' (for the sphere) and 'triangle' for regions
 * in the plane. I'm using 'ProcessBuilder'.
 * The idea is to put the qhull executable in CPBase.LibDirectory when
 * the program first loads. For a run, the data is placed into a 
 * 'DelaunayData' object, the code is run, and the output triangulation
 * is put back into 'DelaunayData'.
 * @author kstephe2, 3/2022
 *
 */
public class ProcessDelaunay {
	static int localID=0;

/**
 * For the sphere, the data is theta/phi; here it is converted
 * to xyz form and put in an input file with this format:
 * 
 * 3
 * <N>
 * x1 y1 z1
 * x2 y2 z2
 * ...
 * xN yN zN
 * 
 * The call is ".\qhull i TI <infile> TO <outfile>"
 * The output has the form:
 * 
 * F
 * a1 b1 c1
 * a2 b2 c2
 * ...
 * aF bF cF
 * 
 * F is the number of faces. 
 * @author kstephe2
 *
 */
public static int sphDelaunay(DelaunayData deldata) {
	localID++;
	
	// TODO: settle on canonical directories, particularly so
	//    we can put the executables in the jar file
	File dir=CPFileManager.HomeDirectory; // where temp files go
	File codedir=CPFileManager.HomeDirectory; // where the code goes
	// write data to "sphInput_id" file
	File infile=new File(dir,"sphin_"+localID);
	String outfilename=new String("sphout_"+localID);
	File outfile=new File(dir,outfilename);
	BufferedWriter fpw=CPFileManager.openWriteFP(infile,false,false);
	try {
		fpw.write("3\n"+deldata.pointCount+"\n");
		for (int j=1;j<=deldata.pointCount;j++) {
			double[] xyz=SphericalMath.s_pt_to_vec(deldata.ptX[j],deldata.ptY[j]);
			fpw.write(String.format("%.8e",xyz[0])+" "+
					String.format("%.8e",xyz[1])+" "+
					String.format("%.8e",xyz[2])+"\n");
		}
		fpw.flush();
		fpw.close();
	} catch (Exception ex) {
		throw new InOutException("write error in sphDelaunay: "+ex.getMessage());
	}
	
	// now call 'qhull'
	ProcessBuilder pb=new ProcessBuilder();
	pb.directory(codedir);
	ArrayList<String> strlist=new ArrayList<String>();
	strlist.add(new String(codedir.toString()+"\\qhull"));
	strlist.add("i");
	strlist.add("TI");
	strlist.add(infile.toString());
	strlist.add("TO");
	strlist.add(outfile.toString());
	pb.command((List<String>)strlist);
	Process process=null;
	
	try {
		// log
		File log=new File(dir,"processLog");
		pb.redirectError(log);
		
		// the call itself
		process=pb.start();
	} catch(Exception ex) {
		throw new JNIException(
				"problem with processBuilder start "+ex.getMessage());
	}
	while (process.isAlive())
		continue;
	int exitvalue=process.exitValue();
	process.destroy();
	if (exitvalue!=0)
		CirclePack.cpb.errMsg("seems to be error");
		
	// store results in deldata, adjusting indexing to start at 0
	BufferedReader fp=CPFileManager.openReadFP(dir,outfilename,false);
	try {
		String line = StringUtil.ourNextLine(fp);
		if (line==null)
			throw new InOutException("'sphOutput' is empty in 'SphDelaunay");
		deldata.myfaceCount=Integer.parseInt(line);
		deldata.triLite=new int[3*deldata.myfaceCount]; // linear list
		int tick=0;
		while ((line= StringUtil.ourNextLine(fp))!=null) { // add 1 for indexing from 1
			StringTokenizer loctok = new StringTokenizer(line);
			deldata.triLite[tick++]=Integer.parseInt((String) loctok.nextToken())+1;
			deldata.triLite[tick++]=Integer.parseInt((String) loctok.nextToken())+1;
			deldata.triLite[tick++]=Integer.parseInt((String) loctok.nextToken())+1;
		}
	} catch (Exception ex) {
		throw new InOutException("sphDelaunay read error: "+ex.getMessage());
	}
	
	return deldata.myfaceCount;
}

}
