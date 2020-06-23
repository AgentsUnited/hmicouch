package eu.couch.hmi.environments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;
import java.util.Queue;

import org.jfree.util.Log;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import eu.couch.hmi.actor.Actor;
import eu.couch.hmi.dialogue.IDialogueStatusListener;
import eu.couch.hmi.middleware.IDMiddleware;
import eu.couch.hmi.middleware.IDMiddlewareListener;
import eu.couch.hmi.moves.Move;
import eu.couch.hmi.moves.FilteredMove;
import eu.couch.hmi.moves.FilteredMoveSet;
import eu.couch.hmi.moves.IMoveCollector;
import eu.couch.hmi.moves.IMoveDistributor;
import eu.couch.hmi.moves.IMoveFilter;
import eu.couch.hmi.moves.IMoveListener;
import eu.couch.hmi.moves.MoveReply;
import eu.couch.hmi.moves.MoveSet;
import eu.couch.hmi.moves.MoveVarValue;
import hmi.flipper.ExperimentFileLogger;
import hmi.flipper.environment.BaseFlipperEnvironment;
import hmi.flipper.environment.MiddlewareEnvironment;
import hmi.flipper.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper.environment.IFlipperEnvironment;
import nl.utwente.hmi.middleware.MiddlewareListener;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;

public class DGEPEnvironment extends MiddlewareEnvironment implements IMoveCollector, IMoveDistributor {

    private static org.slf4j.Logger logger = LoggerFactory.getLogger(DGEPEnvironment.class.getName());

	public enum STATE {
	    NONE, INITIALIZING, INITIALIZED
	}
	
	public enum DialogueStatus {
		NONE, ACTIVE, TERMINATED
	}

	/** A list of middlewares that are required for this environment */
	private String[] requiredMiddlewares = new String[] {"dgepCtrlMiddleware","dgepMovesMiddleware"};
	
	STATE state = STATE.NONE;
	
	Actor[] actors;
	
	List<HashListener> moveListeners;
	List<IMoveFilter> moveFilters;

	private List<IDialogueStatusListener> dialogueStatusListeners;
	
	ObjectMapper om;
	private AuthEnvironment authEnv;
	private SKBEnvironment skbEnv;

	//the most recent status of the dialogue, whether it is still ACTIVE or whether it has reached a TERMINATED state
	private DialogueStatus dialogueStatus = DialogueStatus.NONE;

	private String topic;
	
	public DGEPEnvironment() {
		super();
		om = new ObjectMapper();
		moveListeners = new ArrayList<HashListener>();
		moveFilters = new ArrayList<IMoveFilter>();
		dialogueStatusListeners = new ArrayList<IDialogueStatusListener>();
	}

	@Override
	public void init(JsonNode params) throws Exception {
		//load the required middlewares (these will then become acsessible through the mwLUT object
		loadRequiredMiddlewares(params, requiredMiddlewares);
		
		state = STATE.NONE;
	}
	
	public DialogueStatus getDialogueStatus() {
		return dialogueStatus;
	}
	
	public String getTopic() {
		return topic;
	}
	
	public void registerDialogueStatusListener(IDialogueStatusListener dsl) {
		dialogueStatusListeners.add(dsl);
	}
	
	@Override
	public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) {
		switch (fenvmsg.cmd) {
		default:
			logger.warn("Unhandled message: "+fenvmsg.cmd);
			break;
		}
		return null;
	}

	@Override
	public void setRequiredEnvironments(IFlipperEnvironment[] envs) {
		for (IFlipperEnvironment env : envs) {
			if(env instanceof AuthEnvironment) {
				this.authEnv = (AuthEnvironment)env;
				authEnv.waitForAuthentication(-1);
			} else if(env instanceof SKBEnvironment) {
				this.skbEnv = (SKBEnvironment)env;
			} else {
				logger.warn("DGEPEnvironment doesn't need environment: "+env.getId());
			}
		}
		
	}
	
	public boolean isInitializationCompleted() {
		return state == STATE.INITIALIZED;
	}
	
	/**
	 * Initiates a dialogue game in DGEP, specifying both the topic and the actors in one command
	 * @param _topic
	 * @param _actors
	 * @return a list of initiated actors with their DGEP ID filled in
	 */
	public Actor[] initSession(JsonNode _topic, JsonNode _actors, JsonNode _utteranceParams) {
		if (state != STATE.NONE) {
			return null;
		}
		
		this.topic = _topic.findPath("topic").asText();
		
		logger.debug("Loading actors for dialogue {}: {}", _topic, _actors.toString());
		
		//DGEP needs a specific JSON structure to init the dialogue:
		//{"cmd":"new", "params":{"topic":"GoalSettingNew","participants":[{"name":"bob","player":"Agent"}, {"name":"Alice","player":"User"}]}}
		try {
			actors = om.treeToValue(_actors, Actor[].class);
		} catch (JsonProcessingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return null;
		}
		
		//this generated the part with participants, strips out all unneccessary actor information:
		//"participants":[{"name":"bob","player":"Agent"}, {"name":"Alice","player":"User"}]
		ParticipantInit[] participants = new ParticipantInit[actors.length];
		for(int i = 0; i < participants.length; i++) {
			participants[i] = new ParticipantInit(actors[i].name, actors[i].player);
		}
		
		ProtocolInitRequest pir = new ProtocolInitRequest(new ProtocolInitRequestParams(topic, authEnv.getUsername(), participants, _utteranceParams));

		// TODO: could check/manipulate pir ...
		
		JsonNode jn = om.convertValue(pir, JsonNode.class);
		
		logger.debug("Initiating topic DGEP: {}", jn.toString());
		
		state = STATE.INITIALIZING;
		getMW("dgepCtrlMiddleware").sendData(jn);
		
		while (state != STATE.INITIALIZED) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		return (Actor[]) actorsJoined.toArray(new Actor[0]);
	}
	
	
	public void requestMoves() {
		if (state != STATE.INITIALIZED) {
			logger.error("Invalid state - not (yet) initialized.");
			return;
		}
		JsonNode jn = om.convertValue(new ProtocolMovesRequest(initResponse), JsonNode.class);
		logger.debug("Requesting moves from DGEP CRTL: {}", jn.toString());
		getMW("dgepCtrlMiddleware").sendData(jn);
	}
	
	private ProtocolInitResponse initResponse;
	private List<Actor> actorsJoined;


	public void handleCTRL(JsonNode jn) {
		actorsJoined = new ArrayList<Actor>();
		
		//response for dialogue game init request looks something like this: {"participants":[{"participantID":1,"name":"Alexa"},{"participantID":2,"name":"Francois"},{"participantID":3,"name":"Bob"}],"dialogueID":2,"moves":{"Francois":[{"reply":{"p":"$p"},"opener":"\"$p\"","target":"Bob","moveID":"Question"},{"reply":{"p":"$p"},"opener":"\"$p\"","target":"Bob","moveID":"Assert"}],"Alexa":[{"reply":{"p":"$p"},"opener":"\"$p\"","target":"Bob","moveID":"Question"},{"reply":{"p":"$p"},"opener":"\"$p\"","target":"Bob","moveID":"Assert"}]}}
		//at init time we only need the dialogueid and the participants, ignore moves for now... these should come explicitly from the filstantiator later
		logger.debug("Got from DGEP CTRL: {}", jn.toString());
		if (jn.has("dialogueID") && jn.has("participants")) {
			try {
				initResponse = om.treeToValue(jn, ProtocolInitResponse.class);
			} catch (JsonProcessingException e1) {
				e1.printStackTrace();
				return;
			}
			
			for(ParticipantResponse p : initResponse.participants) {
				for(Actor a : actors) {
					if(a.name.equals(p.name)) {
						a.dialogueID = initResponse.dialogueID;
						a.participantID = p.participantID;
						actorsJoined.add(a);
					}
				}
			}
			
			clearVarsInSKB(initResponse.clearvars);
			
			//have all requested actors actually managed to join the game according to DGEP?
			if(actorsJoined.size() == actors.length) {
				logger.info("DGEP game has sucessfully been initialised with all participants");
				state = STATE.INITIALIZED;
			} else {
				logger.error("Not all requested participants have been succesfully initialised by DGEP");
			}
			
		} else {
			logger.warn("Unknown CTRL message from DGEP: {}", jn.toString());
		}
		
	}
	
	public void handleMOVES(JsonNode jn) {
		ExperimentFileLogger.getInstance().log("dgepmoves", jn);
		
		logger.debug("Got from DGEP MOVES: {}", jn.toString());
		
		if (jn.has("dialogueID") && jn.has("moves")) {
			int moveSetHash = 0;
			try {
				moveSetHash = om.writeValueAsString(jn).hashCode();
			} catch (JsonProcessingException e1) {
				logger.error("Failed to compute hash for moveSet.");
				return;
			}
			
			ProtocolMovesResponse moves;
			try {
				moves = om.treeToValue(jn, ProtocolMovesResponse.class);
			} catch (JsonProcessingException e) {
				e.printStackTrace();
				return;
			}
			
			clearVarsInSKB(moves.clearvars);
			
			logger.info("New moveset with moves for "+moves.moves.size()+" actor. (hash: "+moveSetHash+")");
			UIEnvironment.log("New moveset with moves for "+moves.moves.size()+" actor. (hash: "+moveSetHash+")");
			publishMoveSets(moves, moveSetHash);
			
			updateDialogueStatus(moves.status);
		}
	}

	/**
	 * store the status of the dialogue (whether it is active or terminated) and notify listeners if status has changed
	 * @param status the new status of the dialogue
	 */
	private void updateDialogueStatus(String status) {
		DialogueStatus oldStatus = dialogueStatus;
		if("active".equals(status)) {
			dialogueStatus = DialogueStatus.ACTIVE;
		} else if("terminated".equals(status)) {
			dialogueStatus = DialogueStatus.TERMINATED;
		}

		logger.debug("Dialogue status is now: {}",dialogueStatus);
		
		if(oldStatus != dialogueStatus) {
			notifyDialogueStatusListeners();
		}
		
		//store in the SKB that this topic has been completed on this date
		if(dialogueStatus == DialogueStatus.TERMINATED) {
			String var = "lastCompletedxxx"+topic;
			logger.info("Storing dialogue termination timestamp to SKB in variable: {}", var);
			//TODO: also maintain a total count...? ask tessa what info the CCE needs to determine the next suitable topic
			skbEnv.storeVariablesBlocking(om.createObjectNode().put(var, System.currentTimeMillis()));
		}
		
		//finally we have to set the state of DGEP back to NONE, so that we can load the next dialogue
		this.state = STATE.NONE;
	}
	
	private void notifyDialogueStatusListeners() {
		for(IDialogueStatusListener dsl : dialogueStatusListeners) {
			dsl.onDialogueStatusChange(topic, dialogueStatus);
		}
	}

	private Actor getActorByIdentifier(String id) {
		for(Actor a : actors) {
			if(a.identifier.equals(id)) {
				return a;
			}
		}
		logger.warn("Trying to find an unknown actor with ID: {}", id);
		return null;
	}
	
	private Actor getActorByName(String name) {
		for(Actor a : actors) {
			if(a.name.equals(name)) {
				return a;
			}
		}
		logger.warn("Trying to find an unknown actor with name: {}", name);
		return null;
	}
	
	/**
	 * Searches in the various reply fields of the move (p or q, as far as I can tell) whether there is an open/missing variable that DAF still requires input for
	 * This variable will take the form of $[name_of_variable], so e.g. "reply":{"p":"$step_goal"} tells us that DAF still misses the variable "step_goal"
	 * @param m the move to search
	 * @return the name of the missing variable, or null if there is no missing variable
	 */
	private String extractMissingVariable(Move m) {
		String re = "";
		if(m.reply.p != null && !"".equals(m.reply.p)) {
			re = m.reply.p;
		} else if (m.reply.q != null && !"".equals(m.reply.q)) {
			re = m.reply.q;
		} else {
			return null;
		}
		
		if(re.startsWith("$")) {
			return re.substring(1);
		} else {
			return null;
		}
	}
	
	/**
	 * Plugs in the missing variable in the movereply in a format that DAF can understand
	 * DAF expects something like "[name_of_variable]([contents])", so e.g. step_goal(5000) tells DAF that user has selected a step goal of 5000
	 * @param m the move to analyse
	 * @return the MoveReply with the variable plugged in
	 */
	private MoveReply plugInMissingVariable(Move m) {
		if(m.requestUserInput) {
			String re = extractMissingVariable(m) + "(" + m.userInput + ")";

			if(m.reply.p != null && !"".equals(m.reply.p)) {
				m.reply.p = re;
			} else if (m.reply.q != null && !"".equals(m.reply.q)) {
				m.reply.q = re;
			}
		}
		
		return m.reply;
	}
	
	public void publishMoveSets(ProtocolMovesResponse res, int moveSetHash) {
		List<FilteredMoveSet> _moveSets = new ArrayList<FilteredMoveSet>();
		
		for (Entry<String, FilteredMove[]> kvp : res.moves.entrySet()) {
			FilteredMove[] moves = kvp.getValue();
			Actor actor = getActorByName(kvp.getKey());//DGEP returns only the name (e.g. Olivia), but we need the identifier (e.g. COUCH_M_1)
			for (Move m : moves) {
				m.actorIdentifier = actor.getIdentifier();
				m.actorName = actor.getName();
				m.dialogueID = res.dialogueID;
				m.requestUserInput = extractMissingVariable(m) != null;
			}
			MoveSet moveSet = new MoveSet(actor.getIdentifier(), actor.getName(), res.dialogueID, moves);
			_moveSets.add(new FilteredMoveSet(moveSet));
		}
		
		for (IMoveFilter f : moveFilters) {
			_moveSets = f.filterMoves(_moveSets);
		}

		FilteredMoveSet[] moveSets = _moveSets.toArray(new FilteredMoveSet[0]);
		for (HashListener hl : moveListeners) {
			if (moveSetHash != hl.getLastHash()) {
				hl.updateHash(moveSetHash);
				hl.getListener().onNewMoves(moveSets);
			}
		}

		enqueueMessage(om.convertValue(res, JsonNode.class), "dgepmoves");
	}

	@Override
	public void receiveDataFromMW(String src, JsonNode jn) {
		switch(src) {
		case "dgepCtrlMiddleware":
			handleCTRL(jn);
			break;
		case "dgepMovesMiddleware":
			handleMOVES(jn);
			break;
		}
	}
	
	@Override
	public void registerMoveListener(IMoveListener listener) {
		for (HashListener hl : moveListeners) {
			if (listener.equals(hl.getListener())) return;
		}
		moveListeners.add(new HashListener(listener));
	}

	@Override
	public void onMoveCompleted(IMoveListener actor, Move move) {
		//check whether there is any freetext input for this move
		String userInputVar = extractMissingVariable(move);
		if(move.requestUserInput && !"".equals(move.userInput) && userInputVar != null && !"".equals(userInputVar)) {
			logger.debug("Variable {} has user input: {}", userInputVar, move.userInput);
			move.vars.put(userInputVar, new MoveVarValue(false, move.userInput));
		}
		
		storeInSKB(move);
		
		// TODO: check if move is proper?
		move.reply = plugInMissingVariable(move);
		ProtocolMoveSelectedRequest moveSelectedRequest = new ProtocolMoveSelectedRequest(move);
		JsonNode jn = om.convertValue(moveSelectedRequest, JsonNode.class);
		logger.debug("Sending move complete to DGEP: {}", jn.toString());
		getMW("dgepCtrlMiddleware").sendData(jn);
		ExperimentFileLogger.getInstance().log("dgepselectedmove", jn);
		enqueueMessage(jn, "move_made");
	}
	
	/**
	 * Moves may be flagged in filstantiator/DGEP to indicate that they should be stored in the SKB when completed. 
	 * This function is called when a move is completed and checks whether there is anything to store
	 * This mechanism is used mostly on User moves to store whatever response the user has selected or entered in freetext (e.g. when setting a stepgoal)
	 * @param move the completed move
	 */
	private void storeInSKB(Move move) {
		if(move.vars.size() > 0) {
			ObjectNode store = om.createObjectNode();
			for(Entry<String, MoveVarValue> v : move.vars.entrySet()) {
				String var = v.getKey();
				MoveVarValue val = v.getValue();
				
				if(val.append) {
					//get the value(s) currently stored in the variable
					JsonNode inSKB = skbEnv.requestVariablesBlocking(new ArrayList<String>(Arrays.asList(var)));
					
					ArrayNode newVals = om.createArrayNode();
					
					if(inSKB != null) {
						JsonNode currVals = inSKB.path(var);
						
						//should we use an existing array, or use a new one with the existing variable in it?
						if(currVals.isArray()) {
							newVals = (ArrayNode)currVals;
						} else if(!currVals.isMissingNode() && !currVals.isNull()) {
							newVals.add(currVals.asText());
						}
					}
					
					newVals.add(val.value);

					logger.debug("Variable {} with value {} will be stored in SKB, appending whatever was there before", var, val.value);
					store.set(var, newVals);
				} else {
					logger.debug("Variable {} with value {} will be stored in SKB, overwriting whatever was there before", var, val.value);
					store.put(var, val.value);
				}
			}
			skbEnv.storeVariablesBlocking(store);
		} else {
			logger.debug("Nothing to store for this move");
		}
	}
	
	/**
	 * Resets each of the given variables to an empty list in the SKB
	 * @param vars an array of vars to clear
	 */
	private void clearVarsInSKB(String[] vars) {
		if(vars == null) {
			return;
		}
		for(String v : vars) {
			//TODO: check the type of the current value stored in SKB and clear accordingly (i.e. make empty array for array object, make null for anything else..?)
			logger.debug("Clearing variable {} in the SKB -- reset to empty array", v);
			skbEnv.storeVariables(om.createObjectNode().set(v, om.createArrayNode()));
		}
	}

	@Override
	public void registerMoveFilter(IMoveFilter filter) {
		if (!moveFilters.contains(filter)) {
			moveFilters.add(filter);
		}
	}


}

// Wrapper for listeners noting down the hash of the last messages, to avoid multiples...

class HashListener {
	
	private IMoveListener listener;
	private int lastHash;
	
	public HashListener(IMoveListener l) {
		this.listener = l;
		this.lastHash = -1;
	}
	
	public IMoveListener getListener() {
		return listener;
	}
	
	public int getLastHash() {
		return this.lastHash;
	}
	
	public void updateHash(int hash) {
		this.lastHash = hash;
	}
}



// DGEP Protocol classes...

class ProtocolInitRequest {
	public String cmd = "new";
	public ProtocolInitRequestParams params;
	
	public ProtocolInitRequest() {}
	public ProtocolInitRequest(ProtocolInitRequestParams params) {
		this.params = params;
	}
}

class ProtocolInitRequestParams {
	public String topic;
	public ParticipantInit[] participants;
	public JsonNode utteranceParams;
	public String username;
	
	public ProtocolInitRequestParams(String topic, String username, ParticipantInit[] participants, JsonNode utteranceParams) {
		this.topic = topic;
		this.username = username;
		this.participants = participants;
		this.utteranceParams = utteranceParams;
	}
}

class ParticipantInit {
	public String name;
	public String player;
	
	public ParticipantInit() {}
	public ParticipantInit(String name, String player) {
		this.name = name;
		this.player = player;
	}
}

class ParticipantResponse {
	public int participantID;
	public String name;
	
	public ParticipantResponse() {}
}

class ProtocolInitResponse {
	public int dialogueID;
	public ParticipantResponse[] participants;
	public JsonNode moves;
	public String authToken;
	public String sessionID;
	public String[] clearvars;
	
	public ProtocolInitResponse() {}
}

class ProtocolMovesRequest {
	public String cmd = "moves";
	public ProtocolMovesRequestParams params;
	public ProtocolMovesRequest() {}
	public ProtocolMovesRequest(ProtocolInitResponse p) {
		this.params = new ProtocolMovesRequestParams(p);
	}
}

class ProtocolMovesRequestParams {
	public int dialogueID;
	public ParticipantResponse[] participants;
	
	//this is a temporary flag to always clear the moves cache until Mark adds functionality internally to the UG for caclulating whether or not to clear the cache
	public boolean cached = false;

	public ProtocolMovesRequestParams(ProtocolInitResponse p) {
		this.dialogueID = p.dialogueID;
		this.participants = p.participants;
	}
}

class ProtocolMovesResponse {
	public int dialogueID;
	public String status;
	public Map<String, FilteredMove[]> moves;
	public String[] clearvars;
	
	public ProtocolMovesResponse() {}
}

class ProtocolMoveSelectedRequest {
	public String cmd = "interaction";	
	public ProtocolMoveSelectedRequestParams params;
	public ProtocolMoveSelectedRequest() {}
	public ProtocolMoveSelectedRequest(Move m) {
		params = new ProtocolMoveSelectedRequestParams(m);
	}
}

class ProtocolMoveSelectedRequestParams {
	public int dialogueID; // ...
	public String speaker; // this is the name in dgep
	public String moveID;
	public String target;
	public MoveReply reply; // 
	public ProtocolMoveSelectedRequestParams() {}
	public ProtocolMoveSelectedRequestParams(Move m) {
		this.moveID = m.moveID;
		this.reply = m.reply;
		this.dialogueID = m.dialogueID;
		this.speaker = m.actorName;
		this.target = m.target;
	}
}
