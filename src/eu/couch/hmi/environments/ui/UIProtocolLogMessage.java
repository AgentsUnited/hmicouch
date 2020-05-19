package eu.couch.hmi.environments.ui;

public class UIProtocolLogMessage extends UIProtocolResponse {
	public String logString;
	
	public UIProtocolLogMessage(String logString) {
		this.logString = logString;
		this.cmd = "log";
	}
}