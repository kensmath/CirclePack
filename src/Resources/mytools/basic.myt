<?xml version="1.0"?>
<CP_ToolBox>
<version>CirclePack 0.0.1, 2006</version>

<name>/home/circle/kens/workspace/CirclePack/basic2.myt</name>
<creator> </creator>
<date>Fri May 26 11:35:10 EDT 2006</date>

<MyTool type="MYTOOL:" dropable="yes">
  <cmd>seed 6;</cmd>
  <iconname>tool/Seed_6.png</iconname>
  <tooltip>Create a hyperbolic 6-flower</tooltip>
</MyTool>

<MyTool type="MYTOOL:" dropable="yes" needsC="yes">
  <cmd>random_pack 50;max_pack 10000;disp -w -c;</cmd>
  <iconname>tool/rand_pack.png</iconname>
  <tooltip>Create a random packing with 50 circles</tooltip>
</MyTool>

<MyTool type="MYTOOL:" dropable="yes">
  <cmd>add_gen 1 6;rld;</cmd>
  <iconname>tool/add_layer_icon.png</iconname>
  <tooltip>Add a degree 6 boundary layer</tooltip>
</MyTool>

<MyTool type="MYTOOL:" dropable="yes">
  <cmd>repack;fix;disp -wr;</cmd>
  <iconname>tool/kdevelop3.png</iconname>
  <tooltip>Computes radii, lays out circles, then redisplays</tooltip>
</MyTool>

<MyTool type="MYTOOL:" dropable="yes" >
  <cmd>max_pack;disp -wr;</cmd>
  <iconname>tool/M_icon.png</iconname>
  <tooltip>Compute the "maximal" packing</tooltip>
</MyTool>

<MyTool type="MYTOOL:" dropable="yes" >
  <cmd>hex_refine;disp -wr;</cmd>
  <iconname>tool/hex_ref.png</iconname>
  <tooltip>Refine the complex hexagonally</tooltip>
</MyTool>

<MyTool type="MYTOOL:" dropable="yes" >
  <cmd>geom_to_e;disp -wr;</cmd>
  <iconname>tool/ksame.png</iconname>
  <tooltip>Convert packing to euclidean geometry</tooltip>
</MyTool>

<MyTool type="MYTOOL:" dropable="yes" >
  <cmd>geom_to_s;disp -wr;</cmd>
  <iconname>tool/sph_icon.png</iconname>
  <tooltip>Convert packing to spherical geometry</tooltip>
</MyTool>

<MyTool type="MYTOOL:" dropable="yes" >
  <cmd>geom_to_h;disp -wr;</cmd>
  <iconname>tool/disc_icon.png</iconname>
  <tooltip>Convert packing to hyperbolic geometry</tooltip>
</MyTool>

<MyTool type="MYTOOL:" dropable="yes" >
  <cmd>disp -wr;</cmd>
  <iconname>tool/graphics.png</iconname>
  <tooltip>Clear and repaint the packing</tooltip>
</MyTool>

<MyTool type="MYTOOL:" dropable="yes" >
  <cmd>set_screen -f .80;disp -wr;</cmd>
  <iconname>tool/viewmag+.png</iconname>
  <tooltip>Zoom in </tooltip>
</MyTool>

<MyTool type="MYTOOL:" dropable="yes" >
  <cmd>set_screen -f 1.25;disp -wr;</cmd>
  <iconname>tool/viewmag-.png</iconname>
  <tooltip>Zoom out </tooltip>
</MyTool>

<MyTool type="MYTOOL:" dropable="yes" >
  <cmd>set_screen -d;disp -wr;</cmd>
  <iconname>tool/reload.png</iconname>
  <tooltip>Reset to original screen and redisplay </tooltip>
</MyTool>


</CP_ToolBox>
