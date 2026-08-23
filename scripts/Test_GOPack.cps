<?xml version="1.0"?>
<CP_Scriptfile date="Aug 21, 2026">
<CPscript title="Testing GOPack calls" >
<text> This gives a random disc, max_packed. </text>
<cmd iconname="klines.png">act 0;random_pack 5000;disp -w -c </cmd>
<text> This creates a random spherical packing. </text>
<cmd iconname="kghostview.png">act 1;random_triangulation -S 7000;Disp -w -f -uc5t5 </cmd>
<cmd iconname="amarok.png">random_triangulation -N 8000;max_pack;geom_to_e;polypack;Disp -w -f </cmd>
<cmd iconname="apollon.png">random_triantulation -N 9000 -A 2.0;Disp -w -f </cmd>
<text> This filles a given curve with a random triangulation of a region surrounded by a curve. </text>
<cmd iconname="centrejust.png">act 2;cleanse;seed 6 -q;disp -w -g;random_tri -N 6000 -gs testcurve.g;Disp -w -g -f </cmd>
  </CPscript>
<CPdata>
    <path name="testcurve.g">
PATH:
0.15400000000000014 0.7590000000000001
-0.23099999999999998 0.6930000000000001
-0.6416666666666668 0.42900000000000005
-0.7406666666666667 0.20533333333333326
-0.748 -0.05499999999999994
-0.539 -0.15400000000000014
-0.1246666666666667 -0.09899999999999998
-0.19800000000000006 -0.32633333333333336
-0.4986666666666667 -0.5720000000000001
-0.45100000000000007 -0.7186666666666666
-0.17599999999999993 -0.8176666666666668
0.15766666666666662 -0.8030000000000002
0.484 -0.671
0.5866666666666667 -0.5536666666666668
0.7333333333333332 -0.31166666666666676
0.7406666666666668 -0.036666666666666625
0.5169999999999999 0.04033333333333333
0.4876666666666667 0.12099999999999989
0.5426666666666669 0.264
0.6086666666666667 0.42166666666666663
0.5536666666666668 0.682
0.385 0.8176666666666668
0.15400000000000014 0.7590000000000001
END
    </path>
  </CPdata>
</CP_Scriptfile>
