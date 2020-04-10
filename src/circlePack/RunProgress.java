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

}
