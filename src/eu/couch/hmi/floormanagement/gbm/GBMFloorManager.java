package eu.couch.hmi.floormanagement.gbm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.couch.hmi.actor.Actor;
import eu.couch.hmi.environments.DGEPEnvironment;
import eu.couch.hmi.environments.FloorManagementEnvironment;
import eu.couch.hmi.environments.UIEnvironment;
import eu.couch.hmi.floormanagement.FloorStatus;
import eu.couch.hmi.floormanagement.IFloorManager;
import eu.couch.hmi.floormanagement.IFloorStatusListener;
import eu.couch.hmi.floormanagement.IGrantFloorCallbackListener;
import eu.couch.hmi.floormanagement.MoveSelectionType;
import eu.couch.hmi.floormanagement.gbm.GBMProtocol;
import eu.couch.hmi.moves.FilteredMove;
import eu.couch.hmi.moves.FilteredMoveSet;
import eu.couch.hmi.moves.IMoveListener;
import eu.couch.hmi.moves.Move;
import nl.utwente.hmi.middleware.Middleware;
import nl.utwente.hmi.middleware.MiddlewareListener;

/**
 * Proxy floor manager that outsources the actor selection and nonverbal behaviour generation to Reshma's Group Behaviour Module (GBM)
 * @author Daniel
 *
 */
public class GBMFloorManager implements IFloorManager, IMoveListener, MiddlewareListener {

    private static org.slf4j.Logger logger = LoggerFactory.getLogger(GBMFloorManager.class.getName());
    
    private ObjectMapper om = new ObjectMapper();
    
	private FloorStatus floorStatus = FloorStatus.FLOOR_FREE;
	private List<IFloorStatusListener> fsls = new ArrayList<IFloorStatusListener>();
	private Actor currentActorOnFloor = null;
	
	private FloorManagementEnvironment fme;
	
	private UIEnvironment uie;

	private Middleware mw;

	private Timer timer;
	
	private int moveUIDCounter = 1;
	
	//look-up-table for the moveUIDs
	private Map<String, Move> moveUIDLUT = new HashMap<String, Move>();

	private FilteredMoveSet[] moveSets;
	private Map<String, FloorRequest> floorRequests = new HashMap<String, FloorRequest>();

	/**
	 * Initiate a floor manager that outsources all floor management to an external component
	 * Communicates through the supplied middleware
	 * @param fme the floormanagerenvironment, used for accessing information about agents and uis etc
	 * @param mw the middleware to use for communication
	 */
	public GBMFloorManager(FloorManagementEnvironment fme, Middleware mw) {
		logger.info("Initialising GBMFloorManager");
		this.fme = fme;
		this.uie = fme.getUIEnvironment();
		this.mw = mw;
		this.mw.addListener(this);
		fme.getMoveDistributor().registerMoveListener(this);
	}
	
	/**
	 * Actors may register a move which is forwarded directly to the external GBM module
	 */
	@Override
	public synchronized void registerMove(IGrantFloorCallbackListener callback, Actor actor, Move move, MoveSelectionType type) {
		if(floorStatus != FloorStatus.FLOOR_FREE) {
			logger.warn("Actor {} has registered a move, while the floor currently belongs to Actor {}", actor.identifier, currentActorOnFloor.identifier);
		}
		
		if(type == MoveSelectionType.FALLBACK) {
			logger.info("Actor {} registered a FALLBACK move {}, this will not be sent to GBM at the moment", actor.identifier, move.moveID);
			//TODO: discuss with Reshma & Fajrian whether they could make use of such preselected fallback moves
			return;
		}
		
		GBMProtocol.MoveSelectedParams msp = new GBMProtocol.MoveSelectedParams();
		msp.actorName = actor.name;
		msp.moveID = move.moveID;
		//msp.moveUID = move.moveUID; //TODO: hmm apparently this doesn't work, the moveUID is not saved from when we set it before.. but we can look up the move in our stored movesets instead I guess
		msp.moveUID = lookupMoveUID(move);
		msp.targetID = move.target;
		

		GBMProtocol.MoveSelected moveSelectedCmd = new GBMProtocol.MoveSelected();
		moveSelectedCmd.cmd = "move_selected";
		moveSelectedCmd.params = msp;

		JsonNode jn = om.convertValue(moveSelectedCmd, JsonNode.class);
		
		logger.debug("Actor {} has selected a {} move {}; sending to GBM: {}", new String[] {actor.identifier, type.toString(), move.moveID, jn.toString()});
		mw.sendData(jn);

		floorRequests.put(msp.moveUID, new FloorRequest(callback, actor, move, type));
	}
	
	private String lookupMoveUID(Move move) {
		String mUID = "";
		for(Entry<String, Move> e : moveUIDLUT.entrySet()) {
			Move m = e.getValue();
			//hopefully this is sufficient for distinguishing unique moves.... else we need to implement the equals method in move
			if(m.moveID.equals(move.moveID) && m.actorIdentifier.equals(move.actorIdentifier) && m.opener.equals(move.opener) && m.target.equals(move.target)) {
				mUID = e.getKey();
				break;
			}
		}
		return mUID;
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
		//TODO: should we also tell external GBM that move has finished..? or will they listen for feedback from agent?
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

	private String getNextMoveUID() {
		return "uid"+moveUIDCounter++;
	}
	
	/**
	 * Sends the collection of movesets to the external GBM so it can already start generating floorbattle behaviours while we wait for actors to select moves
	 */
	@Override
	public synchronized void onNewMoves(FilteredMoveSet[] moveSets) {
		this.moveSets = moveSets;
		floorRequests.clear();

		GBMProtocol.ActorMovesAvailableParams params = new GBMProtocol.ActorMovesAvailableParams();
		
		//extract relevant info for each of the movesets to construct the actormoves protocol
		for(FilteredMoveSet fms : moveSets) {
			GBMProtocol.ActorMoves am = new GBMProtocol.ActorMoves(fms.actorIdentifier, uie.getActorControlType(fms.actorIdentifier).toString());
			
			//extract relevant infor for each of the moves to construct the availablemoves protocol
			for(FilteredMove fm : fms.moves) {
				String moveUID = getNextMoveUID();
				fm.moveUID = moveUID;
				moveUIDLUT.put(moveUID, fm);
				am.addAvailableMove(new GBMProtocol.AvailableMove(fm.moveID, moveUID, fm.target, fm.opener));
			}
			
			params.addNewActorMoves(fms.actorName, am);
		}
		
		//construct the command message
		GBMProtocol.MovesAvailable movesAvailableCmd = new GBMProtocol.MovesAvailable();
		movesAvailableCmd.cmd = "moves_available";
		movesAvailableCmd.params = params;
		
		JsonNode jn = om.convertValue(movesAvailableCmd, JsonNode.class);
		
		logger.debug("Sending all moves available this turn to GBM: {}", jn.toString());
		mw.sendData(jn);
	}

	@Override
	public synchronized void receiveData(JsonNode jn) {
		logger.debug("Got message from GBM: {}", jn.toString());
		if(jn.has("cmd") && "move_granted".equals(jn.get("cmd").asText(""))) {
			try {
				GBMProtocol.MoveGranted moveGranted = om.treeToValue(jn, GBMProtocol.MoveGranted.class);
				FloorRequest fr = floorRequests.get(moveGranted.params.moveUID);
				
				if(fr == null) {
					logger.warn("GBM sent an incorrect moveUID {} : ", moveGranted.params.moveUID, jn.toString());
					return;
				}

				//TODO: also parse the bml_template and pass it along with the callback of the agent when granting them the floor
				logger.info("GBM has selected actor {} to take the floor and perform move {}", fr.actor.identifier, fr.move.moveID);
	    		currentActorOnFloor = fr.actor;
	    		floorStatus = FloorStatus.FLOOR_TAKEN;
	        	fr.callback.floorGranted(fr.move);
	        	notifyListeners();
				
			} catch (JsonProcessingException e) {
				logger.warn("GBM sent a malformed message: {}", jn.toString());
				e.printStackTrace();
			}

		} else {
			logger.warn("GBM sent a malformed message: {}", jn.toString());
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
