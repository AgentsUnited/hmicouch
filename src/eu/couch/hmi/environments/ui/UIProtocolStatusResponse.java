package eu.couch.hmi.environments.ui;

import eu.couch.hmi.moves.MoveSet;

public class UIProtocolStatusResponse extends UIProtocolResponse {
	public UIProtocolActor[] actors;
	public MoveSet[] moveSets;
	public UIProtocolCustomAction[] customActions;
	
	public UIProtocolStatusResponse(UIProtocolActor[] actors, MoveSet[] moveSets, UIProtocolCustomAction[] customActions) {
		this.actors = actors;
		this.moveSets = moveSets;
		this.cmd = "status";
		this.customActions = customActions;
	}
}