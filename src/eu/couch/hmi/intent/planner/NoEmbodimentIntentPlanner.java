package eu.couch.hmi.intent.planner;

import eu.couch.hmi.actor.DialogueActor;
import eu.couch.hmi.intent.Intent;
import eu.couch.hmi.moves.Move;
import eu.couch.hmi.moves.MoveIntent;
import eu.couch.hmi.moves.IMoveListener;
import eu.couch.hmi.moves.MoveSet;
import eu.couch.hmi.moves.MoveStatus;

public class NoEmbodimentIntentPlanner implements IIntentPlanner {
	
	IMoveListener currentActor;
	
	@Override
	public String planIntent(DialogueActor actor, Intent intent) {
		if (intent instanceof MoveIntent) {
			actor.onMoveStatus(((MoveIntent) intent).getMove(), MoveStatus.MOVE_COMPLETED);
		}
		return "NULLPLAN";
	}
	

	@Override
	public void cancelIntentPlan(String intentId) {}

}