package util;

/**
 * Unsophisticated timer for measuring wall clock time.
 * @author kens
 *
 */
public class CPTimer {
	
	static long Second=1000;
	static long Minute=Second*60;
	static long Hour=Minute*60;
	static long Day=Hour*24;
	
	private Long singleTimer;
	
	public CPTimer() {
		singleTimer=null;
	}
	
	/**
	 * For single timings: start timer or read elapsed time and 
	 * restart timer. * I 'startTime' is null, this sets
	 * it via java call. If not null, this finds 
	 * Get the elapsed time if 'startTime' is not null and
	 * convert to string "elapsed time: ? d, ? hrs, ? min, ? sec". 
	 * Reset 'startTime' to null. 
	 * If 'startTime' is null, return "timer not set"
	 * @return String
	 */
	public String singleTime() {
		if (singleTimer==null) {
			singleTimer= Long.valueOf(System.currentTimeMillis()); 
			return new String("Start Timer");
		}
		double diff=(long)(System.currentTimeMillis())-(long)singleTimer;
		if (diff<0) {
			singleTimer=null;
			return new String("elapsed time was <=0");
		}
		StringBuilder str=new StringBuilder("Elapsed time: ");
		int days=(int)Math.floor(diff/Day);
		diff -= Day*days;
		if (days>0) 
			str.append(days+"d, ");
		int hours=(int)Math.floor(diff/Hour);
		diff -= Hour*hours;
		if (hours>0 || days>0)
			str.append(hours+"hr, ");
		int minutes=(int)Math.floor(diff/Minute);
		diff -= Minute*minutes;
		if (minutes>0 || hours>0 || days>0)
			str.append(minutes+"min, ");
		double seconds=diff/Second;
		if (seconds>0)
			str.append(seconds+"secs.");
		singleTimer=null;
		return str.toString();
	}
	
	public void reset() {
		singleTimer=null;
	}
	
}
