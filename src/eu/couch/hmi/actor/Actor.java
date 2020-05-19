package eu.couch.hmi.actor;

public class Actor {
	
	// Properties for DGEP:
	public String identifier;
	public String player;
	public String name;

	// Properties set by DGEP:
	public int dialogueID;
	public int participantID;

	// Properties for dialogue management:
	public String dialogueActorClass;
	public double priority = 0.1;
	
	// Properties for behavior realization:
	public String bml_name;
	public String engine;

	public Actor() {}

	public Actor(String identifier, String player, String bml_name) {
		this.identifier = identifier;
		this.player = player;
		this.bml_name = bml_name;
		this.engine = "ASAP";
	}
	
	public String getIdentifier() {
		return identifier;
	}
	
	public String getName() {
		return name;
	}
	
	public Actor(Actor a) {
		this.identifier = a.identifier;
		this.player = a.player;
		this.name = a.name;
		
		this.dialogueID = a.dialogueID;
		this.participantID = a.participantID;
		
		this.dialogueActorClass = a.dialogueActorClass;
		this.priority = a.priority;

		this.bml_name = a.bml_name;
		this.engine = a.engine;
	}

}