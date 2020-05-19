package eu.couch.hmi.environments.ui;

public class UIProtocolCustomAction {
	public String command;
	public String description;
	
	public UIProtocolCustomAction(String command, String description) {
		this.command = command;
		this.description = description;
	}
	
	public UIProtocolCustomAction(ICustomUIAction action) {
		this.command = action.getCommandString();
		this.description = action.getDescriptionString();
	}

	public UIProtocolCustomAction() {}
}