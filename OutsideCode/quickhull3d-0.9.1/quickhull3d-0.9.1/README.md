[![](https://jitpack.io/v/micycle1/quickhull3d.svg)](https://jitpack.io/#micycle1/quickhull3d)

quickhull3d - A Robust 3D Convex Hull Algorithm in Java
===========

This fork of [quickhull3d](https://github.com/Quickhull3d/quickhull3d) improves the original with two new key features:
- `PowerDiagram2D`. Creates a 2D Power Diagram (Laguerre–Voronoi) with the _lifting and lower hull_ technique, using the 3D hull routines.
- Improved numerical robustness. Now uses (Shewchuk-style) robust predicates for geometric calculations (rather than tolerance-based distance checks, as before).
