package circlePack;

/**
 * Some type of progress indicator when running in standalone
 * @author kens
 *
 */
public class ShellSpinner extends RunProgress {

	/**
	 * As yet, no display of activity in standalone mode
	 */
	public void startstop(boolean ok) {
		
	}
	
	/** 
	 * As yet, no running status; default to 'false'
	 */
	public boolean isRunning() {
		return false;
	}
	
}
