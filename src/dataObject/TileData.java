package dataObject;

import packing.PackData;
import tiling.Tile;
import util.ColorUtil;

/**
 * Gathers data on a tile, as needed for inquiries or for
 * the 'Pack Info' window in GUI mode.
 * @author kstephe2
 *
 */
public class TileData {
	PackData parent;
	public int tindx;
	public int degree;
	public String nghbStr;
	public int colorCode;
	public int mark;
	
	public TileData(PackData p,int indx) {
		parent=p;
		tindx=indx;
		Tile tile=p.tileData.myTiles[tindx];
		degree=tile.vertCount;
		nghbStr="";
		try {
			StringBuilder tbldr = new StringBuilder();
			for (int i = 0; i < tile.vertCount; i++) {
				tbldr.append(Integer.toString(tile.tileFlower[i][0]));
				tbldr.append(" ");
			}
			nghbStr=tbldr.toString();
		} catch (Exception ex) {
			nghbStr="";
		}
		colorCode=ColorUtil.col_to_table(tile.color);
		mark=tile.mark;
	}

}
