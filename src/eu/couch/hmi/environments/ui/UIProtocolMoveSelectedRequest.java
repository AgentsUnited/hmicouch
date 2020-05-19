package eu.couch.hmi.environments.ui;

public class UIProtocolMoveSelectedRequest extends UIProtocolRequest {
	public String moveID;
	public String actorIdentifier;
	public boolean skipPlanner;
	public String userInput;
	public String target;
	public UIProtocolMoveSelectedRequest() {}
}