package JNI;

/** Thrown when the native GOPack engine reports an error (including "not yet implemented"). */
public class GOPackException extends Exception {
    public GOPackException(String message) {
        super(message);
    }
}
