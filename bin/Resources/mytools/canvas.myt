<?xml version="1.0"?>
<CP_ToolBox>
<version>CirclePack 0.0.1, 2006</version>

<name>canvas.myt</name>
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

<MyTool name="Load packing" dropable="no">
  <cmd>load_pack;</cmd>
  <iconname>main/folder_green_open.png</iconname>
  <tooltip>Load a circle packing</tooltip>
</MyTool>

<MyTool name="Reset display" dropable="no" >
  <cmd>set_screen -b -1.1 -1.1 1.1 1.1;disp -wr;</cmd>
  <iconname>main/reload.png</iconname>
  <tooltip>Reset to original screen and redisplay</tooltip>
</MyTool>

<MyTool dropable="no" >
  <cmd>disp -wr;</cmd>
  <iconname>main/graphics.png</iconname>
  <tooltip>Clear and repaint the packing</tooltip>
</MyTool>

<MyTool name="Zoom out" dropable="no" >
  <cmd>set_screen -f 1.5;disp -wr;</cmd>
  <iconname>main/viewmag-.png</iconname>
  <tooltip>Zoom out</tooltip>
</MyTool>

<MyTool name="Zoom in" dropable="no" >
  <cmd>set_screen -f .6666667;disp -wr;</cmd>
  <iconname>main/viewmag+.png</iconname>
  <tooltip>Zoom in</tooltip>
</MyTool>

<MyTool name="ScreenDump" type="MAIN:"  dropable="no" >
  <cmd>screendump</cmd>
  <iconname>main/snapCamera.png</iconname>
  <tooltip>Save a jpg Screendump</tooltip>
</MyTool>

</CP_ToolBox>
