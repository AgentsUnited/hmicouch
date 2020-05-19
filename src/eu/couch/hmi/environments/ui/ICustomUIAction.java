package eu.couch.hmi.environments.ui;

import eu.couch.hmi.actor.DialogueActor;
import eu.couch.hmi.environments.UIEnvironment;
import eu.couch.hmi.moves.MoveSet;

public interface ICustomUIAction {
	
	public String getCommandString();
	public String getDescriptionString();
	
	// Actor may be null if it is not concerning a specific actor:
	public boolean isApplicableNow(UIEnvironment env, DialogueActor da, MoveSet[] moveSets);
	
	// Actor may be null if it is not concerning a specific actor:
	public void actionCallback(UIEnvironment env, DialogueActor da, UIProtocolCustomActionRequest req);
	
}