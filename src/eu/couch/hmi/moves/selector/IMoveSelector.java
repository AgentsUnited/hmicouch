package eu.couch.hmi.moves.selector;

import eu.couch.hmi.actor.DialogueActor;
import eu.couch.hmi.moves.IMoveListener;
import eu.couch.hmi.moves.MoveSet;

// Must call actor.onMoveSelected(Move selectedMove); once a move is selected...
public interface IMoveSelector {
	
	public void selectMove(DialogueActor actor, MoveSet[] moveSets);

}
