package eu.couch.hmi.actor;

import eu.couch.hmi.moves.Move;
import eu.couch.hmi.moves.MoveIntent;
import eu.couch.hmi.floormanagement.IFloorManager;
import eu.couch.hmi.intent.planner.IIntentPlanner;
import eu.couch.hmi.moves.FilteredMoveSet;
import eu.couch.hmi.moves.IMoveCollector;
import eu.couch.hmi.moves.IMoveDistributor;
import eu.couch.hmi.moves.MoveSet;
import eu.couch.hmi.moves.MoveStatus;
import eu.couch.hmi.moves.selector.IMoveSelector;

// Mostly to show the basic flow of information
public class GenericDialogueActor extends DialogueActor {

	private boolean isActive = true;
	
	public GenericDialogueActor(Actor dgepActor, IFloorManager fm, IMoveDistributor md, IMoveSelector ms, IIntentPlanner mp, IMoveCollector mc) {
		super(dgepActor, fm, md, ms, mp, mc);
	}

	// We receive an updated set of possible moves from the MoveDistributor
	@Override
	public void onNewMoves(FilteredMoveSet[] moveSets) {
		if(!isActive) return;
		// The move selector then makes a choice on what move to take next.
		this.moveSelector.selectMove(this, moveSets);
	}
	
	// We receive a move from the move selector. This could be triggered by the UI, an AI, or ASR
	@Override
	public void onMoveSelected(Move m) {	
		if(!isActive) return;
		// Update the planner with the new that should be planned/executed...
		this.intentPlanner.planIntent(this, new MoveIntent(this, m));
	}
	
	// When a plan is is completed (i.e. based on ASR speech level detection, UI, BML feedback)
	@Override
	public void onMoveStatus(Move move, MoveStatus status) {
		if(!isActive) return;
		if (status == MoveStatus.MOVE_COMPLETED) {
			moveCollector.onMoveCompleted(this, move);
		}
	}

	@Override
	public void disableActor() {
		isActive = false;
	}

}
