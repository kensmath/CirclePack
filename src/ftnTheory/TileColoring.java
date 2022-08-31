package ftnTheory;

import java.awt.Color;
import java.io.BufferedReader;
import java.util.StringTokenizer;
import java.util.Vector;

import allMains.CPBase;
import circlePack.PackControl;
import complex.Complex;
import exceptions.DataException;
import exceptions.InOutException;
import input.CPFileManager;
import packing.PackData;
import packing.PackExtender;
import util.DispFlags;
import util.StringUtil;

public class TileColoring extends PackExtender {

	// tile 'types'; may want to add or change
	final int TRIANGLE=1;
	final int QUADRANGLE=2;
	final int PENTAGON=3;
	
	PackData packData;
	int color_mode;  // default to 0; 
	int colorHit;    // increment in intensity for each hit
	int depth;       // depth of tiles as given in history file
	String histFile; // name of the history file
	String postFile; // name of output file (default to 'CPDrawing.customPS')
	Vector<TileInfo> tiles;  // vector of 'TileInfo' information
	
	final int ERROR=0;
	final int INITIAL=1;
	final int READING=2;
	final int HAVE_HISTORY=3;
	final int COLORS_SET=4;
	final int POST_OPEN=5;
	public String []stateStr={"ERROR","INITIAL","READING","HAVE_HISTORY",
			"COLORS_SET","POST_OPEN"};
	private int tcState;  // processing state
	
	// Constructor
	public TileColoring(PackData p) {
		super(p);
		packData=p;
		extensionType="TILECOLORING";
		extensionAbbrev="TC";
		toolTip="'TileColoring': for color coding subdivision "+
			"tilings of Cannon, Floyd, Parry by history";
		registerXType();
		
		color_mode=0; // default (currently, only mode)
		if (running) { 
			packData.packExtensions.add(this);
		}
		colorHit=30; // default
		tcState=INITIAL;
		postFile=packData.cpDrawing.customPS;
	}
	
	public int cmdParser(String cmd,Vector<Vector<String>> flagSegs) {
		Vector<String> items=null;

		if (cmd.startsWith("hist")) {
			BufferedReader fp=null;
			try {
				items=flagSegs.get(0);
				String str=items.get(0);
				boolean script_flag=false;
				if (str.equals("-s")) {
					script_flag=true;
					items.remove(0);
				}
				histFile=StringUtil.reconItem(items);
				fp=CPFileManager.openReadFP(histFile,script_flag);
			} catch (Exception ex) {
				errorMsg("Failed to open 'history' file");
				return 0;
			}
			
	        tcState = INITIAL; // starting over
			String line;
			
	        // Must find 'HISTORIES:'
            int count=0;
	        try {
	            while(tcState==INITIAL && (line=StringUtil.ourNextLine(fp))!=null) {
	                StringTokenizer tok = new StringTokenizer(line);
	                while(tok.hasMoreTokens()) {
	                    String mainTok = tok.nextToken();
	                    if(mainTok.equals("HISTORIES:")) {
	                    	tcState=READING;
	                    }
	                }
	            }
	            tiles=new Vector<TileInfo>(10);
	            while(tcState==READING && (line=StringUtil.ourNextLine(fp))!=null) {
	                StringTokenizer tok = new StringTokenizer(line);
	                TileInfo newTile=new TileInfo();
	                newTile.vert=Integer.parseInt(tok.nextToken());
	                if (newTile.vert<1 || newTile.vert>packData.nodeCount) {
	                	throw new DataException("circle index out of range");
	                }
	                
	                // set type
	                newTile.type=packData.countFaces(newTile.vert)-2;
	                
	                String lineage=tok.nextToken();
	                depth=lineage.length();
	                newTile.history=new int[depth];
	                for (int j=0;j<depth;j++) {
	                	String nextchar=lineage.substring(j,j+1);
	                	newTile.history[j]=Integer.parseInt(nextchar);
	                }
	                tiles.add(newTile);
	                count++;
	            }
	        } catch (Exception ex) {
	        	errorMsg("Exception in reading '"+histFile+"': "+ex.getMessage());
	        	tcState=INITIAL; // ready to restart
	        	return 0;
	        }
            tcState=HAVE_HISTORY;
            return count;
		}
		if (cmd.startsWith("set_col")) {
			if (tcState < HAVE_HISTORY) {
				errorMsg("can't set colors, TileColoring state is only "+stateStr[tcState]);
				return 0;
			}
			
			// cycle through the tiles and set colors
			int Red,Green,Blue;
			for (int n=0;n<tiles.size();n++) {
		      Red=Green=Blue=0;
		      TileInfo tc=tiles.get(n); 

		      // ======= here's where 'mode' chooses strategy 
		      if (color_mode==0) { // equal weight 
		          for (int i=1;i<depth;i++) {
		        	  // equal weight (could change weight depending on color) 
		              if (tc.history[i]==TRIANGLE) 
		            	  Red++;
		              else if (tc.history[i]==QUADRANGLE) 
		            	  Green++;
		              else if (tc.history[i]==PENTAGON) 
		            	  Blue++;
		          }
		          tc.red=(Red*colorHit)%256;
		          tc.green=(Green*colorHit)%256;
		          tc.blue=(Blue*colorHit)%256;
		      }
			} // end of for loop
			tcState=COLORS_SET;
		}
		if (cmd.startsWith("postfile")) { // get PostScript filename and open
			if (tcState<COLORS_SET) {
				errorMsg("can't open post, TileColoring state is only "+stateStr[tcState]);
				return 0;
			}
			try {
				items=flagSegs.get(0);
				postFile=StringUtil.reconItem(items);
				if (!postFile.endsWith(".ps"))
					postFile=new String(postFile+".ps");
			} catch (Exception ex) {
				errorMsg("error in postscript file name"+postFile);
			}
		}
		if (cmd.startsWith("set_hit")) {
			try {
				items=flagSegs.get(0);
				int hit=Integer.parseInt((String)items.get(0));
				if (hit<0 || hit>200) {
					errorMsg("TileColoring increment must be between 0 and 200");
					return 0;
				}
				colorHit=hit;
			} catch (Exception ex) {
				errorMsg("error in 'set_hit'");
				return 0;
			}
		}
		if (cmd.startsWith("draw")) {
			if (tcState<COLORS_SET) {
				errorMsg("colors not set yet, TileColoring state is only "+stateStr[tcState]);
				return 0;
			}
			try {
				cpCommand("set_screen -a");
				DispFlags dflags=new DispFlags("ff",packData.cpDrawing.fillOpacity);
				for (int i=0;i<tiles.size();i++) {
					TileInfo td=tiles.get(i);
					int v=td.vert;
					int num=packData.countFaces(v);
					int[] flower=packData.getFlower(v);
					int N=num+2*packData.getBdryFlag(v);
					double []crnr=new double[2*N];
					for (int j=0;j<num;j++) {
						Complex Z=packData.getCenter(flower[j]);
						if (packData.hes>0)
							Z=cpDrawing.sphView.toApparentSph(Z);
						crnr[2*j]=Z.x;
						crnr[2*j+1]=Z.y;
					}
					if (packData.isBdry(v)) { // need extra points
						Complex Z=packData.getCenter(flower[num]);
						if (packData.hes>0)
							Z=cpDrawing.sphView.toApparentSph(Z);
						crnr[2*num]=Z.x;
						crnr[2*num+1]=Z.y;
					}
					
					// TODO: something wrong? this should be going to postscript.
					dflags.setColor(new Color(255-td.red,255-td.green,255-td.blue));
					cpDrawing.drawClosedPoly(N,crnr,dflags);
				}
				CPBase.postManager.close_psfile(cpDrawing);
				msg("TileColoring: PostScript image saved in "+postFile);
			} catch (Exception ex) {
				throw new InOutException("problem in creating the PostScript file: "+ex.getMessage());
			}
			PackControl.activeFrame.reDisplay();
		}
		if (cmd.startsWith("post")) {
			if (tcState<COLORS_SET) {
				errorMsg("colors not set yet, TileColoring state is only "+stateStr[tcState]);
				return 0;
			}
			try {
				CPBase.postManager.open_psfile(packData.cpDrawing,1,postFile,null);
			} catch (Exception ex) {
				errorMsg("error in opening "+postFile+" for PostScript output");
				return 0;
			}
			try {
				cpCommand("set_screen -a");
				for (int i=0;i<tiles.size();i++) {
					TileInfo td=tiles.get(i);
					int v=td.vert;
					int num=packData.countFaces(v);
					int[] flower=packData.getFlower(v);
					int N=num+2*packData.getBdryFlag(v);
					Complex []Z=new Complex[N];
					for (int j=0;j<num;j++)
						Z[j]=packData.getCenter(flower[j]);
					if (packData.isBdry(v)) { // need extra points
						Z[num]=packData.getCenter(flower[num]);
						Z[num+1]=packData.getCenter(v); // bdry edge 
					}
					CPBase.postManager.pF.postFilledPoly(packData.hes,N,Z,
							new Color(255-td.red,255-td.green,255-td.blue));
				}
				CPBase.postManager.close_psfile(cpDrawing);
				msg("TileColoring: PostScript image saved in "+postFile);
			} catch (Exception ex) {
				throw new InOutException("problem in creating the PostScript file: "+ex.getMessage());
			}
			
		}
		return super.cmdParser(cmd, flagSegs);
	}

	public void helpInfo() {
		helpMsg("Commands for PackExtender "+extensionType+"(TileColoring)\n"+
				"mode <n>:        set mode:\n"+
				"set_hit:         set impact of each generation; default 30"+
				"history <name>:  read file with tile lineages\n"+
				"set_color:       set color in 'TileInfo' based on 'mode'\n"+
				"postfile <name>: give the file for PostScript output\n"+
				"post:            create the PostScript file\n"+
				"draw:            display the tiling on screen");
	}
	
}

/**
 * Utility class containing tile histories: 'history[]' show tile
 * types for successive subdivisions, so history[0] is the global 
 * parent type, history[1] is type within, history[2] is type
 * within that, etc., until last entry is type of this tile itself.
 * @author kens
 */
class TileInfo {
	int vert; // index of barycenter circle
	int type; // numbered from 1: typically 1=triangle, 2=rectangle, etc.
	int red;  // from 0 to 255
	int green;
	int blue;
	int []history; // integer tile types for successive subdivisions
}