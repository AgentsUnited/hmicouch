package eu.couch.hmi.moves;

public class FilteredMoveSet extends MoveSet {

	public FilteredMoveSet(String actorIdentifier, String actorName, int dialogueID, FilteredMove[] moves) {
		super(actorIdentifier, actorName, dialogueID, moves);
	}

	public FilteredMoveSet(MoveSet moveSet) {
		super(moveSet.actorIdentifier, moveSet.actorName, moveSet.dialogueID, moveSet.moves);
	}
	
	public FilteredMoveSet() {
		super();
	}

}
