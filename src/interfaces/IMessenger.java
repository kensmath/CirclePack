package interfaces;

public interface IMessenger {
	public void sendDebugMessage(String message);
	public void sendErrorMessage(String message);
	public void sendOutputMessage(String message);
}
