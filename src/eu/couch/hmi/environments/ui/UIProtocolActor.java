package eu.couch.hmi.environments.ui;

import eu.couch.hmi.actor.ActorJSON;
import eu.couch.hmi.actor.DialogueActor;

public class UIProtocolActor extends ActorJSON {
	
	public String activeMove;
	public String fallbackMove;
	public String[] controlledBy;
	public UIProtocolCustomAction[] customActions;
	
	public UIProtocolActor(DialogueActor src, String[] controlledBy, String activeMove, String fallbackMove, UIProtocolCustomAction[] customActions) {
		super((DialogueActor) src);
		this.controlledBy = controlledBy;
		this.activeMove = activeMove;
		this.fallbackMove = fallbackMove;
		this.customActions = customActions;
	}
}