package exceptions;

/**
 * Thrown from inside a long-running packing computation (e.g. the
 * 'continueRiffle' loops in 'rePack.EuclPacker'/'HypPacker', via
 * 'allMains.CPBase.checkCancel()') when the user has pressed the
 * emergency-stop button that 'images.OwlSpinner' shows next to the
 * progress spinner.
 * <p>
 * Deliberately NOT a subclass of 'PackingException': this signals an
 * computation the user chose to abandon, not a numerical/packing
 * failure. That said, most existing 'catch (Exception ex)' blocks
 * along the call chain will still catch it like any other exception
 * and unwind normally -- that's fine, since 'input.TrafficCenter'
 * checks 'CPBase.cancelRequested' independently of exactly how far
 * this exception actually propagates, so the aborted command still
 * gets reported correctly and control still returns to the console.
 * @author kens
 */
public class PackingCancelledException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PackingCancelledException() {
		super("computation aborted by user (emergency stop)");
	}

	public PackingCancelledException(String msg) {
		super(msg);
	}

}
