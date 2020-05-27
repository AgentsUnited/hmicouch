package eu.couch.hmi.floormanagement;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.LoggerFactory;

import eu.couch.hmi.actor.Actor;
import eu.couch.hmi.environments.DGEPEnvironment;
import eu.couch.hmi.environments.FloorManagementEnvironment;
import eu.couch.hmi.environments.UIEnvironment;
import eu.couch.hmi.floormanagement.gbm.GBMProtocol.MoveSelected;
import eu.couch.hmi.moves.FilteredMoveSet;
import eu.couch.hmi.moves.IMoveListener;
import eu.couch.hmi.moves.Move;

/**
 * This floor manager takes in floor requests for a certain duration, then decides randomly who gets the floor
 * @author Daniel
 *
 */
public class RandomFloorManager implements IFloorManager, IMoveListener {

    private static org.slf4j.Logger logger = LoggerFactory.getLogger(RandomFloorManager.class.getName());
    
	private FloorStatus floorStatus = FloorStatus.FLOOR_FREE;
	private List<IFloorStatusListener> fsls = new ArrayList<IFloorStatusListener>();
	private Actor currentActorOnFloor = null;

	//how often to check when there are still no selected moves after the initial delay has passed
	private static final long CHECK_INTERVAL = 250;
	
	//timestamp that we received our latest set of moves
	private long latestMoveSetReceivedTS;
	
	//timestamp for the deadline of accepting new incoming floor requests, after this time we will ignore new requests and choose one at random from all those that we received before
	private long floorRequestDeadlineTS;
	
	private Map<String,FloorRequest> floorRequests;

	private FloorManagementEnvironment fme;
	
	private UIEnvironment uie;

	private int autoTimeout = 1000;
	private int wozTimeout;
	private int userTimeout;

	private SelectActorTask task;

	private Timer timer;

	private FilteredMoveSet[] moveSets;

	/**
	 * Initiate a floor manager that waits for a specified timeout (in milliseconds) when there are WOZ- and/or user-controlled agents with moves.
	 * If there are both woz and user moves, the longest duration will be taken
	 * @param fme the floormanagerenvironment, used for accessing information about agents and uis etc
	 * @param wozTimeout timeout to use if there is a woz-controlled agent with moves
	 * @param userTimeout timeout to use if there is a user-controlled agent with moves
	 */
	public RandomFloorManager(FloorManagementEnvironment fme, int wozTimeout, int userTimeout) {
		logger.info("Initialising RandomFloorManager");
		this.fme = fme;
		this.uie = fme.getUIEnvironment();
		this.wozTimeout = wozTimeout;
		this.userTimeout = userTimeout;
		floorRequests = new HashMap<String,FloorRequest>();
		fme.getMoveDistributor().registerMoveListener(this);
	}
	
	/**
	 * Actors may register a move during a certain window of time. 
	 * They may overrule their earlier choice by calling this function again
	 * Will select at random one actor from all registered actors after the window elapses
	 */
	@Override
	public synchronized void registerMove(IGrantFloorCallbackListener callback, Actor actor, Move move, MoveSelectionType type) {
		if(floorStatus != FloorStatus.FLOOR_FREE) {
			logger.warn("Actor {} has registered a move, while the floor currently belongs to Actor {}", actor.identifier, currentActorOnFloor.identifier);
			return;
		}
		//TODO: we might want a configurable setting whether we take fallback selections for human-controlled actors into account or not.. right now: yes, they are also eligible for floor selection 
		floorRequests.put(actor.identifier, new FloorRequest(callback, actor, move, type));
		logger.debug("Actor {} has registered a {} move; adding to list.", actor.identifier, type);

		//let's see if all human-controlled actors have given their final moves: if so, we can fasttrack our selection process
		if(areAllUserMovesFinal()) {
			logger.debug("All human-controlled actors have registered their final moves, now selecting an actor to take the floor");
			scheduleActorSelectionTask(1);
			return;
		}

		logger.debug("Still {} ms remaining before I hand out the floor", floorRequestDeadlineTS - System.currentTimeMillis());
	}

	private boolean areAllUserMovesFinal() {
		boolean allUserMovesFinal = false;
		if(floorRequests.size() == moveSets.length) {
			allUserMovesFinal = true;
			for(FloorRequest fr : getAllUserMoves().values()) {
				if(fr.type != MoveSelectionType.FINAL) {
					allUserMovesFinal = false;
					break;
				}
			}
		}
		return allUserMovesFinal;
	}

	private Map<String, FloorRequest> getAllUserMoves(){
		Map<String, FloorRequest> userMoves = new HashMap<String, FloorRequest>();
		for(FloorRequest fr : floorRequests.values()) {
			if(uie.isActorControlled(fr.actor.identifier)) {
				userMoves.put(fr.actor.identifier, fr);
			}
		}
		return userMoves;
	}

	private Map<String, FloorRequest> getAllUserMovesOfType(MoveSelectionType type){
		Map<String, FloorRequest> userMoves = new HashMap<String, FloorRequest>();
		for(FloorRequest fr : getAllUserMoves().values()) {
			if(fr.type == type) {
				userMoves.put(fr.actor.identifier, fr);
			}
		}
		return userMoves;
	}

	@Override
	public synchronized void releaseFloor(Actor a) {
		if(currentActorOnFloor != null && !a.equals(currentActorOnFloor)) {
			logger.warn("Actor {} is attempting to release the floor, while it currently belongs to {}", a.identifier, currentActorOnFloor.identifier);
		}
		
		currentActorOnFloor = null;
		floorStatus = FloorStatus.FLOOR_FREE;
		logger.debug("Floor has been released by actor {}", a.identifier);
		notifyListeners();
	}

	private void notifyListeners() {
		for(IFloorStatusListener fsl : fsls) {
			fsl.onFloorStatusChange(floorStatus);
		}
	}
	
	@Override
	public synchronized FloorStatus getFloorStatus() {
		return floorStatus;
	}


	@Override
	public void registerFloorStatusListener(IFloorStatusListener fsl) {
		if(!fsls.contains(fsl)) {
			fsls.add(fsl);
		}
	}

	@Override
	public synchronized Actor getCurrentActorOnFloor() {
		return currentActorOnFloor;
	}




	@Override
	public synchronized void onNewMoves(FilteredMoveSet[] moveSets) {
		floorRequests.clear();
		this.moveSets = moveSets;
		
		long delay = 1;
		
		boolean userHasMoves = false;
		boolean wozHasMoves = false;
		for(FilteredMoveSet fms : moveSets) {
			userHasMoves = userHasMoves || uie.isActorControlledByUser(fms.actorIdentifier);
			wozHasMoves = wozHasMoves || uie.isActorControlledByWoz(fms.actorIdentifier);
		}
		if(userHasMoves && wozHasMoves) {
			delay = Math.max(wozTimeout, userTimeout);
			logger.debug("There are moves for woz actors and user actors, delay is set to {} ms", delay);
		} else if(userHasMoves) {
			delay = userTimeout;
			logger.debug("There are only moves for user actors, delay is set to {} ms", delay);
		} else if(wozHasMoves) {
			delay = wozTimeout;
			logger.debug("There are only moves for woz actors, delay is set to {} ms", delay);
		} else {
			//in case there are no woz or user agents present: give all the autonomous agents at least a bit of time to filter and select their move
			delay = autoTimeout;
			logger.debug("There are only moves for automatic actors, delay is set to {} ms", delay);
		}
		
		latestMoveSetReceivedTS = System.currentTimeMillis();
		floorRequestDeadlineTS = latestMoveSetReceivedTS + delay;
		
		scheduleActorSelectionTask(delay);
	}

	private void scheduleActorSelectionTask(long delay) {
		if(task != null) task.cancel();
		if(timer != null) timer.cancel();
		
		task = new SelectActorTask();
	    timer = new Timer("ActorSelector");
	     
	    //TODO: maybe have a machanism to modify the delay if the controlled checkbox in the UI is clicked during a turn?
	    timer.schedule(task, delay);
	}
	
	private class SelectActorTask extends TimerTask {
		
		@Override
		public void run() {
			Random rand = new Random();
        	synchronized(RandomFloorManager.this) {
        		if(floorRequests.size() == 0) {
        			logger.debug("No actors have requested the floor yet, will check again in {} ms", CHECK_INTERVAL);
        			scheduleActorSelectionTask(CHECK_INTERVAL);
        			return;
        		}
        		FloorRequest[] frs = floorRequests.values().toArray(new FloorRequest[] {});
        		
        		//if the user has selected a final move, give it priority
        		Map<String, FloorRequest> finalUserMoves = getAllUserMovesOfType(MoveSelectionType.FINAL);
        		if(finalUserMoves.size() > 0) {
        			frs = finalUserMoves.values().toArray(new FloorRequest[] {});
        			logger.debug("There are move(s) selected by a user, ignoring the auto-selected moves");
        		}
        		
        		FloorRequest fr = frs[rand.nextInt(frs.length)];
	        	logger.debug("I have randomly randomly selected actor {} to take the floor", fr.actor.identifier);
	    		currentActorOnFloor = fr.actor;
	    		floorStatus = FloorStatus.FLOOR_TAKEN;
	        	fr.callback.floorGranted(fr.move);
	        	notifyListeners();
        	}
		}
		
	}

	private class FloorRequest{
		public IGrantFloorCallbackListener callback;
		public Actor actor;
		public Move move;
		private MoveSelectionType type;
		
		public FloorRequest(IGrantFloorCallbackListener callback, Actor actor, Move move, MoveSelectionType type) {
			this.callback = callback;
			this.actor = actor;
			this.move = move;
			this.type = type;
		}
		
	}
}
