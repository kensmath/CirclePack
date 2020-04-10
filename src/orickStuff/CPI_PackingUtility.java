package orickStuff;

public class CPI_PackingUtility {

	static int CPI_TERMINATED_ON_ERROR = -1;
	static int CPI_TERMINATED_ON_CYCLES = 1;
	static int CPI_TERMINATED_ON_ACCURACY = 2;

	static int normCycleLimit = 20;
	static double normAccLimit = 1e-12;

	public static CPI_Vector3 centroid(CPI_Ball3Sector[] BList) {
		int len = BList.length;
		int i;
		CPI_Vector3 xbar = new CPI_Vector3();
		for (i = 0; i < len; i++) {
			CPI_Ball3Sector B = BList[i];
			xbar.x += B.c.x;
			xbar.y += B.c.y;
			xbar.z += B.c.z;
		}
		xbar.div(Double.valueOf((double)len));
		return xbar;
	}

	public static int normalize(CPI_Ball3Sector[] BList) {
		int len = BList.length;
		int i = 0;
		CPI_Vector3 a;
		a = centroid(BList);
		while ((a.abs() > normAccLimit) && (i < normCycleLimit)) {
			i++;
			int j;
			for (j = 0; j < len; j++)
				BList[j].mobius(a);
			a = centroid(BList);
			a.print();
		}

		int retval;
		if (i == normCycleLimit)
			retval = CPI_TERMINATED_ON_CYCLES;
		else
			retval = CPI_TERMINATED_ON_ACCURACY;

		return retval;
	}

/*	public static int normalize(double[] x, double[] y, double[] z, double[] r) {
		int i = 0;
		int len = x.length; // supposing lengths are same
		CPI_Ball3Sector[] BList = new CPI_Ball3Sector[len];
		for (i = 0; i < len; i++)
			BList[i] = new CPI_Ball3Sector(x[i], y[i], z[i], r[i]);
		return (normalize(BList));
	}
*/

}