package eu.couch.hmi.environments.ui;

import eu.couch.hmi.actor.DialogueActor;
import eu.couch.hmi.environments.UIEnvironment;
import eu.couch.hmi.moves.MoveSet;

public abstract class BaseCustonUIAction implements ICustomUIAction {

	private String command;
	private String description;
	
	public BaseCustonUIAction(String command, String description) {
		this.command = command;
		this.description = description;
	}
	
	@Override
	public String getCommandString() {
		return this.command;
	}

	@Override
	public String getDescriptionString() {
		return this.description;
	}

	@Override
	public boolean isApplicableNow(UIEnvironment env, DialogueActor da, MoveSet[] moveSets) {
		// default is only "global" commands
		return da == null;
	}

	@Override
	public abstract void actionCallback(UIEnvironment env, DialogueActor da, UIProtocolCustomActionRequest req);
	
}
