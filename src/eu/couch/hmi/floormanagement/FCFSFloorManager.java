package eu.couch.hmi.floormanagement;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.LoggerFactory;

import eu.couch.hmi.actor.Actor;
import eu.couch.hmi.environments.DGEPEnvironment;
import eu.couch.hmi.moves.Move;

/**
 * Very simple First-Come-First-Serve floor manager that keeps track of which actor is currently taking the floor.
 * Basically, whoever requests the floor first, gets it and all subsequent requests are ignored
 * TODO: check how this works out with users
 * @author Daniel
 *
 */
public class FCFSFloorManager implements IFloorManager {

    private static org.slf4j.Logger logger = LoggerFactory.getLogger(FCFSFloorManager.class.getName());
    
	private FloorStatus floorStatus = FloorStatus.FLOOR_FREE;
	private List<IFloorStatusListener> fsls = new ArrayList<IFloorStatusListener>();
	private Actor currentActorOnFloor = null;

	@Override
	public synchronized void registerMove(IGrantFloorCallbackListener callback, Actor a, Move m, MoveSelectionType type) {
		//TODO: allow a user or woz to override ongoing moves and take the floor by force
		if(floorStatus != FloorStatus.FLOOR_FREE) {
			logger.warn("Actor {} has requested the floor, while the floor currently belongs to Actor {}... ignoring the floor request", a.bml_name, currentActorOnFloor.bml_name);
			return;
		}
		logger.debug("Floor has been taken by actor {}", a.bml_name);
		currentActorOnFloor = a;
		floorStatus = FloorStatus.FLOOR_TAKEN;
		callback.floorGranted(m);
		notifyListeners();
	}


	@Override
	public synchronized void releaseFloor(Actor a) {
		if(currentActorOnFloor != null && !a.equals(currentActorOnFloor)) {
			logger.warn("Actor {} is attempting to release the floor, while the floor currently belongs to Actor {}", a.bml_name, currentActorOnFloor.bml_name);
		}
		
		currentActorOnFloor = null;
		floorStatus = FloorStatus.FLOOR_FREE;
		logger.debug("Floor has been released by actor {}", a.bml_name);
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




}
