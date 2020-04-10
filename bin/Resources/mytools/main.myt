<?xml version="1.0"?>
<CP_ToolBox>
<version>CirclePack 0.0.1, 2006</version>

<name>/home/circle/kens/workspace/CirclePack/mytools/main.myt</name>
<creator> </creator>
<date>Fri May 26 14:11:50 EDT 2006</date>

<MyTool name="Display Action Menu" type="MAIN:"  dropable="no" >
  <iconname>main/xeyes.png</iconname>
  <tooltip>Menu of canvas display options</tooltip>
  <menu heading="display options" attachTo="canvas">
  	<submenu heading="Clear/Repeat">
  		<cmd>disp -wr</cmd>
  		<item text="Clear/Repeat">
  			<cmd>disp -wr</cmd>
  		</item>
  		<item text="Clear">
  			<cmd>disp -w</cmd>
  		</item>
  	</submenu>
  	<submenu heading="Circles">
  		<cmd>disp -c</cmd>
  		<item text="Open">
  			<cmd>disp -c</cmd>
  		</item>
  		<item text="Filled">
  			<cmd>disp -cf</cmd>
  		</item>
  		<item text="Marked">
  			<cmd>disp -c m</cmd>
  		</item>
  	</submenu>
  	<submenu heading="Complex">
  		<cmd>disp -f</cmd>
		<item text="Open">
			<cmd>disp -f</cmd>
  		</item>
  		<item text="Filled">
  			<cmd>disp -ff</cmd>
  		</item>
  			<item text="Marked">
  		<cmd>disp -f m</cmd>
  		</item>
  	</submenu>
  	<item text="Circles/Complex">
  		<cmd> disp -c -f</cmd>
  	</item>
  	<submenu heading="Labels">
  		<cmd>disp -nc</cmd>
		<item text="Circles">
			<cmd>disp -nc</cmd>
		</item>
		<item text="Faces">
			<cmd>disp -nf</cmd>
		</item>
  	</submenu>
  	<item text="Path">
  		<cmd>disp -g</cmd>
  	</item>
  	<item text="Screendump">
  		<cmd>screendump</cmd>
	</item>  		
  	<item text="Repack/Layout/Display">
  		<cmd>repack;layout;disp -wr</cmd>  	
	</item>  			
  </menu>  		
</MyTool>

<MyTool name="Reset the display" type="MAIN:" dropable="no" >
  <cmd>set_screen -b -1.1 -1.1 1.1 1.1;disp -wr;</cmd>
  <iconname>main/reload.png</iconname>
  <tooltip>Reset to original screen and redisplay</tooltip>
</MyTool>

<MyTool name="redisplay" type="MAIN:" dropable="no" >
  <cmd>disp -wr;</cmd>
  <iconname>main/graphics.png</iconname>
  <tooltip>Clear and repaint the packing</tooltip>
</MyTool>

<MyTool name="ZoomOut" type="MAIN:" dropable="no" >
  <cmd>set_screen -f 1.5;disp -wr;</cmd>
  <iconname>main/viewmag-.png</iconname>
  <tooltip>Zoom out</tooltip>
</MyTool>

<MyTool name="ZoomIn" type="MAIN:" dropable="no" >
  <cmd>set_screen -f .6666667;disp -wr;</cmd>
  <iconname>main/viewmag+.png</iconname>
  <tooltip>Zoom in</tooltip>
</MyTool>

<MyTool name="Show axes" type="MAIN:"  dropable="no" >
  <iconname>main/coords.png</iconname>
  <tooltip>Impose coordinates on canvas</tooltip>
</MyTool>

<MyTool name="ScreenDump" type="MAIN:"  dropable="no" >
  <cmd>screendump</cmd>
  <iconname>main/snapCamera.png</iconname>
  <tooltip>Save a jpg Screendump</tooltip>
</MyTool>

<MyTool name="Add cursor" type="MAIN:" dropable="no" >
  <iconname>main/enfold.png</iconname>
  <cmd>add_cir z #XY;disp -wr</cmd>
  <cmd_m2>enclose 0 z #XY;disp -wr</cmd_m2>
  <canvasmode>Add bdry circle</canvasmode>  
  <cursorpoint>2 20</cursorpoint>
  <tooltip>Add neighboring boundary circle (select: l-mouse)</tooltip>
</MyTool>

<MyTool name="Delete cursor" type="MAIN:" dropable="no" >
  <iconname>main/ken_delete.gif</iconname>
  <canvasmode>Delete circle</canvasmode>  
  <cmd>rm_cir z #XY;disp -wr</cmd>
  <cursorpoint>0 19</cursorpoint>
  <tooltip>Delete boundary circle (select: l-mouse)</tooltip>
</MyTool>

<MyTool name="Increase cursor" type="MAIN:" dropable="no" >
  <iconname>main/up_cursor.png</iconname>
  <canvasmode>Increase radius</canvasmode>  
  <cmd>adjust_rad 1.05 z #XY;disp -c z #XY</cmd>
  <tooltip>Increase radius 5%</tooltip>
</MyTool>

<MyTool name="Decrease cursor" type="MAIN:" dropable="no" >
  <iconname>main/down_cursor.png</iconname>
  <canvasmode>Decrease radius</canvasmode>  
  <cmd>adjust_rad .95 z #XY;disp -c z #XY</cmd>
  <tooltip>Decrease radius 5%</tooltip>
</MyTool>

</CP_ToolBox>

