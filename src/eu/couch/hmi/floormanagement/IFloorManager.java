package eu.couch.hmi.floormanagement;

import eu.couch.hmi.actor.Actor;
import eu.couch.hmi.moves.Move;

public interface IFloorManager {

	/**
	 * Actor a requests the floor by registering a move. 
	 * The implementing floormanager class will simulate a floorbattle (e.g. choose the first/last to request, or select one at random)
	 * Once an actor is selected, the callback function will be triggered to allow the move to be performed
	 * @param callback who to call when the move may be performed
	 * @param a the actor who is requesting the floor
	 * @param m the move to be made
	 */
	public void registerMove(IGrantFloorCallbackListener callback, Actor a, Move m, MoveSelectionType selectionType);
	
	public void releaseFloor(Actor a);
	public void registerFloorStatusListener(IFloorStatusListener fsl);
	public FloorStatus getFloorStatus();
	public Actor getCurrentActorOnFloor();
	
	
}
