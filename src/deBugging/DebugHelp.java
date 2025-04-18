package deBugging;

import java.io.BufferedWriter;
import java.io.File;

import JNI.SolverData;
import allMains.CirclePack;
import exceptions.DataException;
import exceptions.InOutException;
import input.CPFileManager;
import input.CommandStrParser;
import math.Mobius;
import packing.PackData;
import packing.ReadWrite;
import tiling.Tile;
import tiling.TileData;

/**
 * Some static debugging aids. @see LayoutBugs for various layout
 * debugging aides.
 * @author kens
 *
 */
public class DebugHelp {
	
	public static File tmpdir=new File(System.getProperty("java.io.tmpdir"));

	/**
	 * When you want to display an intermediate packing
	 * during testing. This loads it into one of the
	 * 'CPBase.packings' and applies the given commands,
	 * typically, 'repack', 'layout', 'disp', etc.
	 * CAUTION: current contents of 'pnum' are replaced.
	 * @param p PackData
	 * @param pnum int, 0,1,2
	 * @param cmds String
	 * @return nodeCount
	 */
	public static int debugPackDisp(PackData p,int pnum,String cmds) {
		CirclePack.cpb.swapPackData(p, pnum,false);
		CommandStrParser.jexecute(p, cmds);
		return p.nodeCount;
	}
	
	/**
	 * See if there's an error in a tileFlower
	 * @param tileData TileData
	 */
	public static int tFlowerCk(TileData tileData) {
		for (int t=1;t<=tileData.tileCount;t++) {
			Tile tile=tileData.myTiles[t];
			if (tile!=null) {
				int[][] tflower=tile.tileFlower;
				for (int i=0;i<tile.vertCount;i++) {
					if (tflower[i][0]!=0 && tflower[i][1]<0)
						System.err.println("bad flower: tile "+t+" and side "+i);
					return 1;
				}
			}
		}
		return 0;
	}
	
	/**
	 * For debugging SolveData usage: create *.m 'matlab' file in the default
	 * diretory. This gets size info, and various matrices Aentries, Ai, Ap, etc., 
	 * and applies matlab code to reconstitute and then solve the resulting 
	 * system A*Z=B. This can be compared to the Java call results.
	 * @param sdata SolverData
	 * @param fname String, filename should end in .m
	 * @return 0 on error, nz_entries on success
	 */
	public static int debugSolverData(SolverData sdata,String fname) {
		  BufferedWriter dbw=CPFileManager.openWriteFP(tmpdir,fname,false);
		  if (sdata==null || sdata.nz_entries<=0 || sdata.intNum<=0 ||
				  sdata.Aentries==null ||  sdata.Ai==null || sdata.Ap==null ||
				  sdata.rhsX==null || sdata.rhsY==null)
			  return 0;
		  try {
			  // write various counts
			  dbw.write("intNum = "+sdata.intNum+"\n");
			  dbw.write("bdryNum = "+sdata.bdryNum+"\n");
			  dbw.write("nz_entries = "+sdata.nz_entries+"\n");
			  
			  // write Ap
			  dbw.write("Ap = [\n");
			  for (int i=0;i<=sdata.intNum;i++)
				  dbw.write(sdata.Ap[i]+" ");
			  dbw.write("\n];\n");
			  
			  // write Ai
			  dbw.write("Ai = [\n");
			  for (int i=0;i<sdata.nz_entries;i++)
				  dbw.write(sdata.Ai[i]+" "); 
			  dbw.write("\n];\n");
			  
			  // write Aentries
			  dbw.write("Aentries = [\n");
			  for (int i=0;i<sdata.nz_entries;i++)
				  dbw.write(sdata.Aentries[i]+" ");
			  dbw.write("\n];\n");
			  
			  // write rhsX as column vector
			  dbw.write("rhsX = [\n");
			  for (int i=0;i<sdata.intNum;i++)
				  dbw.write(sdata.rhsX[i]+";");
			  dbw.write("\n];\n");
			  
			  // write rhsY as column vector
			  dbw.write("rhsY = [\n");
			  for (int i=0;i<sdata.intNum;i++)
				  dbw.write(sdata.rhsY[i]+";");
			  dbw.write("\n];\n");
			  
			  // adjust indexing for matlab's use
			  dbw.write("Ai_m = Ai+1;\n");
			  
			  // solve using matlab
			  dbw.write("\n% convert to systems A*x=rhsX, A*x=rhsY and solve\n");
			  dbw.write("A=zeros(intNum,intNum);\n");
			  dbw.write("tick=1;\nindxaccum=0;\n");
			  dbw.write("for j=2:intNum+1\n   n=Ap(j);\n   for ii=1:(n-indxaccum)\n");
			  dbw.write("      A(Ai_m(ii),j-1)=Aentries(tick);\n      tick=tick+1;\n   end\n");
			  dbw.write("   indxaccum=indxaccum+Ap(j);\nend\n\n");
			  dbw.write("Zreal=A\\rhsX;\nZimag=A\\rhsY;\n\n");
			  dbw.write("Z=Zreal+Zimag*1i;\n");
			  			  
		      dbw.flush();
		      dbw.close();
		  } catch (Exception ex) {
			  throw new DataException("error writing sdata to matlab: "+ex.getMessage());
		  }
		  
		  return sdata.nz_entries;
	}

	/**
	 * write pack data to packing directory
	 * @param p 
	 * @param fname
	 * @return String for msg.
	 */
	public static void debugPackWrite(PackData p,String fname) {
		File dir=CPFileManager.PackingDirectory;
		BufferedWriter fp = CPFileManager.openWriteFP(dir,false, fname, false);
		try {
			ReadWrite.writePack(fp,p,0017,false); 
		} catch (Exception ex) {
			throw new InOutException("debugPackWrite failed");
		}
		CirclePack.cpb.msg("Wrote temp packing to "+fname+" in "+dir.toString());
	}
	
	/**
	 * Send tile info to System.out
	 * @param tile Tile
	 */
	public static void debugTileVerts(Tile tile) {
		System.out.println("\n  Tile "+tile.tileIndex+" info: baryVert = "+tile.baryVert+"; ");
//		StringBuilder info=new StringBuilder("  verts: ");
//		for (int j=0;j<tile.vertCount;j++)
//			info.append(tile.vert[j]+" ");
//		System.out.println(info.toString());		
		StringBuilder info=new StringBuilder("    augVerts: ");
		for (int j=0;j<tile.vertCount;j++)
			info.append(tile.augVert[4*j]+" "+tile.augVert[4*j+1]+" "+tile.augVert[4*j+2]+" "+tile.augVert[4*j+3]+"    ");
		System.out.println(info.toString());		
	}

	/** 
	 * List the tileFlower info, (t,e), t=nghbindex, e=edgeindex in t.
	 * @param td TileData
	 */
	public static void debugTileFlowers(TileData td) {
		System.out.println("\n Tile flowers ("+td.tileCount+"): \n");
		for (int t=1;t<=td.tileCount;t++) {
			StringBuilder strb=new StringBuilder(" Tile "+t+": (nghb,edge) ");
			Tile tile=td.myTiles[t];
			for (int j=0;j<tile.vertCount;j++)
				strb.append(" ("+tile.tileFlower[j][0]+","+tile.tileFlower[j][1]+")");
			System.out.println(strb.toString());
		}
	}
	
	/**
	 * System print our a mobius in a form that can be copied to matlab
	 * @param name String
	 * @param mob Mobius
	 */
	public static void mob4matlab(String name,Mobius mob) {
		System.out.println(name+"=[ \n"+
				mob.a+"   "+mob.b+"\n"+
				mob.c+"   "+mob.d+"];\n");
	}

	public static void printtileflowers(TileData td) {
		for (int t=1;t<=td.tileCount;t++) {
			Tile tile=td.myTiles[t];
			if (tile.tileFlower==null) {
				System.out.println("tile "+t+" has no flower");
				continue;
			}
			StringBuilder strbld=new StringBuilder("Tile "+t+": vertices ");
			for (int j=0;j<tile.vertCount;j++)
				strbld.append(" "+tile.vert[j]);
			strbld.append("\n  tile flower: ");
			for (int j=0;j<tile.vertCount;j++)
				strbld.append(" "+tile.tileFlower[j][0]+" "+
						tile.tileFlower[j][1]+"   ");
			strbld.append("\n");
			System.out.println(strbld.toString());
		}
	}
}
