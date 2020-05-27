package eu.couch.hmi.actor;

import java.util.Random;

import org.slf4j.LoggerFactory;

import eu.couch.hmi.environments.UIEnvironment;
import eu.couch.hmi.floormanagement.FloorStatus;
import eu.couch.hmi.floormanagement.IFloorManager;
import eu.couch.hmi.floormanagement.IFloorStatusListener;
import eu.couch.hmi.floormanagement.IGrantFloorCallbackListener;
import eu.couch.hmi.floormanagement.MoveSelectionType;
import eu.couch.hmi.intent.planner.IIntentPlanner;
import eu.couch.hmi.moves.Move;
import eu.couch.hmi.moves.MoveIntent;
import eu.couch.hmi.moves.MoveStatus;
import eu.couch.hmi.moves.FilteredMoveSet;
import eu.couch.hmi.moves.IMoveCollector;
import eu.couch.hmi.moves.IMoveDistributor;
import eu.couch.hmi.moves.selector.IMoveSelector;

public class UIControllableActor extends DialogueActor implements IFloorStatusListener, IGrantFloorCallbackListener {

    private static org.slf4j.Logger logger = LoggerFactory.getLogger(UIControllableActor.class.getName());
	
	public static Random RANDOM = new Random();

	private UIEnvironment uiMiddleware;
	private FilteredMoveSet[] currentMoveSets;

	private DelayedMovePlanner dmp;
	
	public UIControllableActor(Actor dgepActor, IFloorManager fm, IMoveDistributor md, IMoveSelector ms, IIntentPlanner mp, IMoveCollector mc, UIEnvironment uim) {
		super(dgepActor, fm, md, ms, mp, mc);
		this.uiMiddleware = uim;
		uim.addControllableActor(this);
		fm.registerFloorStatusListener(this);
	}

	public synchronized void UIMoveSelected(Move move) {
		logger.info("{} selected move through UI: {}", this.getIdentifier(), move.moveID);
		floorManager.registerMove(this, this, move, MoveSelectionType.FINAL);
		
		//cancel any delayed moves that may have been scheduled for this actor
		if(dmp != null) {
			dmp.cancel();
		}
	}
	
	@Override
	public synchronized void onNewMoves(FilteredMoveSet[] moveSets) {
		currentMoveSets = moveSets;
		// Select default/fallback move
		this.moveSelector.selectMove(this, moveSets);
	}
	
	@Override
	public synchronized void onMoveSelected(Move move) {
		uiMiddleware.updateMoveStatus(this, move, "SELECTED");
		logger.info(this.getIdentifier()+" selected (fallback) move: "+move.moveID);
		
		boolean hasUserControlOverPossibleMoves = false;
		for (FilteredMoveSet moveSet : currentMoveSets) {
			if (uiMiddleware.isActorControlled(moveSet.actorIdentifier) &&
				moveSet.moves.length > 0) {
				hasUserControlOverPossibleMoves = true;
				break;
			}
		}
		
		if (hasUserControlOverPossibleMoves) {
			//we still do a short delay here, in case we are using the very basic FCFS floor manager, this still gives the user/woz a few seconds to select something if they want to overrule the fallback
			delayedPlanMove(move, 2000);
		} else {
			floorManager.registerMove(this, this, move, MoveSelectionType.FINAL);
		}
	}
	
	private boolean isValidMove(Move move) {
		boolean res = false;
		for (FilteredMoveSet moveSet : currentMoveSets) {
			if (!moveSet.actorIdentifier.equals(this.getIdentifier())) continue;
			for (Move m : moveSet.moves) {
				if (m.moveID.equals(move.moveID)) return true;
			}
		}
		return res;
	}
	
	private void delayedPlanMove(Move move, int delay) {
		dmp = new DelayedMovePlanner(this, move, delay);
        Thread t = new Thread(dmp);
        t.start();
	}
	
	public synchronized void delayedPlanMoveCompleted(Move move) {
		if (isValidMove(move)) {
			if(uiMiddleware.isActorControlled(this.identifier)) {
				floorManager.registerMove(this, this, move, MoveSelectionType.FALLBACK);
			} else {
				floorManager.registerMove(this, this, move, MoveSelectionType.FINAL);
			}
		}
	}
	
/*
	@Override
	public void onMoveSelected(Move move) {
		// show UI clients what move the fallback selector chose...
		// (todo: could do this right away...)
		uiMiddleware.updateSelectedFallbackMove(this, move);

		// If no UI controls this actor, make default plan...
		if (!uiMiddleware.isActorControlled(this.getIdentifier())) {
			// TODO: AUTO SELECT IF NOT CONTROLLED!!!!
			//System.out.println("TODO: AUTOSELECT IF NOT CONTROLLED");
			intentPlanner.planIntent(this, new MoveIntent(this, move));
		}
	}
*/
	
	@Override
	public void onMoveStatus(Move move, MoveStatus status) {
		// show UI clients when the move is completed...
		uiMiddleware.updateMoveStatus(this, move, status.name());
		if (status == MoveStatus.MOVE_COMPLETED) {
			moveCollector.onMoveCompleted(this, move);
			floorManager.releaseFloor(this);
		}
	}

	
	@Override
	public void onFloorStatusChange(FloorStatus fs) {
		logger.trace("Actor {} has just found out that the floor is now {}", this.bml_name, fs.toString());
		// TODO: we could do something smart here, like re-plan when the floor becomes free again and other move was not fully completed or something
	}

	@Override
	public void floorGranted(Move move) {
		if (isValidMove(move)) {
			intentPlanner.planIntent(this, new MoveIntent(this, move));
		} else {
			logger.warn("The move for which actor {} has been granted the floor is no longer valid: {} - {}", new String[] {this.bml_name, move.moveID, move.opener});
		}
	}
	
}


class DelayedMovePlanner implements Runnable {

    private Move move;
    private UIControllableActor actor;
	private int delay;
	private boolean cancelled = false;

    public DelayedMovePlanner(UIControllableActor actor, Move move, int delay) {
        this.move = move;
        this.actor = actor;
        this.delay = delay;
    }

    public void cancel() {
    	this.cancelled = true;
    }
    
    public void run() {
    	try {
			Thread.sleep(delay+UIControllableActor.RANDOM.nextInt(100));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    	if(!cancelled) {
    		actor.delayedPlanMoveCompleted(move);
    	}
    }
}
