package eu.couch.hmi.environments.ui;

public class UIProtocolCustomFlipperAction extends UIProtocolCustomAction {
	public String scope;
	
	public UIProtocolCustomFlipperAction(String command, String description, String scope) {
		super(command, description);
		this.scope = scope;
	}
	
	public UIProtocolCustomFlipperAction(FlipperCustomUIAction action, String scope) {
		super(action);
		this.scope = scope;
	}

	public UIProtocolCustomFlipperAction() {}
}