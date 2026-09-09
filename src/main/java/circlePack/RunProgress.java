package circlePack;

/**
 * Abstract class for indicating progress during computations.
 * E.g. use differs between standalone and GUI runs.
 * @author kens
 *
 */
public abstract class RunProgress {

	public abstract void startstop(boolean ok);
	public abstract boolean isRunning();

	/**
	 * Unconditionally reset to the "not running" state, regardless of
	 * how many nested 'startstop(true)' calls are outstanding. Used
	 * after an emergency stop (or any other abnormal unwind) so the
	 * spinner (and stop button, in 'OwlSpinner') can't be left running
	 * forever by a mismatched nesting of start/stop calls.
	 */
	public abstract void forceOff();

}
