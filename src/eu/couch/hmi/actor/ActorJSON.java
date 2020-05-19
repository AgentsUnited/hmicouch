package eu.couch.hmi.actor;

/* 
 * This is for serializing/sharing Dialogue Actor representation with other modules
 * 
 */
public class ActorJSON extends Actor {
	
	public String moveSelector;
	public String movePlanner;
	
	public ActorJSON(DialogueActor src) {
		super((Actor) src);
		if (src.getMovePlanner() == null) {
			this.movePlanner = "";
		} else this.movePlanner = src.getMovePlanner().toString();
		if (src.getMoveSelector() == null) {
			this.moveSelector = "";
		} else this.moveSelector = src.getMoveSelector().toString();
	}
}

