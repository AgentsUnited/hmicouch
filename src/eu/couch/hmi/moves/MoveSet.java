package eu.couch.hmi.moves;

// Set of moves from DGEP
public class MoveSet {
	public int dialogueID;
	public String actorIdentifier;
	public String actorName;
	public FilteredMove[] moves;
	
	public MoveSet(String actorIdentifier, String actorName, int dialogueID, FilteredMove[] moves) {
		this.actorIdentifier = actorIdentifier;
		this.actorName = actorName;
		this.moves = moves;
		this.dialogueID = dialogueID;
	}

	public MoveSet() {
	}
	
}
