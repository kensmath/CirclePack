package util;

import java.util.Random;

/**
 * In a geometrically random circle packing of the disc, here is a
 * representation of the distribution of degrees for interior vertices
 * based on three runs of 10,000 circle random packings. From this one
 * can make reasonably valid random choices of degrees for flowers in 
 * typical packings.
 * @author kstephe2
 *
 */
public class DegreeDistribution {

	
	public static int getRandDegree() {
		Random rand=new Random();
		int n=rand.nextInt(28844);
		if (n<=328)
			return 3;
		if (n<=3481)
			return 4;
		if (n<=10878)
			return 5;
		if (n<=19502)
			return 6;
		if (n<=25162)
			return 7;
		if (n<=27763)
			return 8;
		if (n<=28578)
			return 9;
		if (n<=28803)
			return 10;
		if (n<=28840)
			return 11;
		else return 12;
	}
	
}
