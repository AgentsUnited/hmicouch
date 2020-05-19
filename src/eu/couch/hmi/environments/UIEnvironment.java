package eu.couch.hmi.environments;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import hmi.flipper.ExperimentFileLogger;
import hmi.flipper.environment.BaseFlipperEnvironment;
import hmi.flipper.environment.MiddlewareEnvironment;
import hmi.flipper.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper.environment.IFlipperEnvironment;
import eu.couch.hmi.actor.Actor;
import eu.couch.hmi.actor.DialogueActor;
import eu.couch.hmi.actor.UIControllableActor;
import eu.couch.hmi.environments.ui.FlipperCustomUIAction;
import eu.couch.hmi.environments.ui.ICustomUIAction;
import eu.couch.hmi.environments.ui.UIActorDefaults;
import eu.couch.hmi.environments.ui.UIDefaults;
import eu.couch.hmi.environments.ui.UIProtocolActor;
import eu.couch.hmi.environments.ui.UIProtocolCustomAction;
import eu.couch.hmi.environments.ui.UIProtocolCustomActionRequest;
import eu.couch.hmi.environments.ui.UIProtocolCustomFlipperAction;
import eu.couch.hmi.environments.ui.UIProtocolInitRequest;
import eu.couch.hmi.environments.ui.UIProtocolLogMessage;
import eu.couch.hmi.environments.ui.UIProtocolMoveSelectedRequest;
import eu.couch.hmi.environments.ui.UIProtocolMoveStatusUpdate;
import eu.couch.hmi.environments.ui.UIProtocolPassRequest;
import eu.couch.hmi.environments.ui.UIProtocolRequest;
import eu.couch.hmi.environments.ui.UIProtocolSetWaitsRequest;
import eu.couch.hmi.environments.ui.UIProtocolStatusResponse;
import eu.couch.hmi.middleware.IDMiddlewareListener;
import eu.couch.hmi.moves.Move;
import eu.couch.hmi.moves.FilteredMoveSet;
import eu.couch.hmi.moves.IMoveDistributor;
import eu.couch.hmi.moves.IMoveListener;
import eu.couch.hmi.moves.MoveSet;
import nl.utwente.hmi.middleware.MiddlewareListener;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;

public class UIEnvironment extends MiddlewareEnvironment implements IMoveListener {
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(UIEnvironment.class.getName());

	public static final int HEARTBEAT_TIMEOUT_S = 5;
	
	public static enum ActorControlType {BOT,WOZ,USER};
	
	private MoveSet[] moveSets = null;
	
	private Map<String, UIMiddlewareClient> uiClients;
	private Map<String, UIControllableActor> actors; 
	private Map<String, DialogueActor> otherActors;
	
	private List<ICustomUIAction> customUIActions;
	
	private static UIEnvironment singleton = null;
	
	public UIEnvironment() {
		super();
		if (singleton != null) {
			logger.warn("Multiple instances for UIEnvironment?");
		}
		singleton = this;
		this.customUIActions = new ArrayList<ICustomUIAction>();
		this.uiClients = new HashMap<String, UIMiddlewareClient>();
		this.actors = new HashMap<String, UIControllableActor>();
		this.otherActors = new HashMap<String, DialogueActor>();
		this.moveSets = new MoveSet[0];
	}	

	public void registerCustomUIAction(ICustomUIAction a) {
		if (customUIActions.contains(a)) return;
		for (ICustomUIAction action : customUIActions) {
			if (action.getCommandString().equals(a.getCommandString())) {
				logger.warn("Duplicate CustomAction command strings: "+a.getCommandString());
				return;
			}
		}
		customUIActions.add(a);
		broadcastStatus();
	}
	
	@Override
	public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {
		// TODO: do we want/need multiple MoveListeners?
		IMoveDistributor md = null;
		for (IFlipperEnvironment env : envs) {
			if (env instanceof IMoveDistributor) md = (IMoveDistributor) env;
		}
		if (md == null) throw new Exception("Required loader of type MoveDistributor (such as DGEPEnvironment) not found.");
		init(md);
	}

	@Override
	public void init(JsonNode params) throws Exception {
		int nrMWLoaded = loadMiddlewares(params);
		
		if(nrMWLoaded < 1) {
			throw new Exception("UIEnvironment needs at least 1 defined middleware");
		}
        if (params.has("uidefaults")) {
        	setDefaults(params.get("uidefaults"));
        }
	}
	
	@Override
	public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws Exception {
		switch (fenvmsg.cmd) {
		case "set_defaults":
			// params: { actors: [ ..defaults.. ]}
        	setDefaults(fenvmsg.params);
        	break;
		case "log":
			log(fenvmsg.params.get("logString").asText());
		default:
			logger.warn("Unhandled message: "+fenvmsg.cmd);
			break;
		}
		
		return null;
	}
	
	private void init(IMoveDistributor md) {
		md.registerMoveListener(this); // TODO: store md?
	}
	
	public UIEnvironment(JsonNode params, IMoveDistributor md) {
		this();
		try {
			init(params);
			init(md);
		} catch (Exception e) {
			logger.error("Failed to initialize: ", e);
		}
	}

	public void setDefaults(JsonNode jn) {
		ObjectMapper om = new ObjectMapper();
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		UIDefaults def = null;

		try {
			def = om.treeToValue(jn, UIDefaults.class);
		} catch (JsonProcessingException jpe) {
			logger.error("Failed to parse UI Defaults JSON: ", jpe);
			return;
		}

		if (def.actors != null) {
			for (UIActorDefaults adef : def.actors) {
				for (String uiId : adef.controlledBy) {
					if (!uiClients.containsKey(uiId)) {
						uiClients.put(uiId, new UIMiddlewareClient(uiId));
					}
					uiClients.get(uiId).AddActorControlled(adef.identifier);
					uiClients.get(uiId).HeartbeatReceived();
				}
			}
		}
		if (def.customActions != null) {
			for (UIProtocolCustomFlipperAction action : def.customActions) {
				this.registerCustomUIAction(new FlipperCustomUIAction(action));
			}
		}
	}
	
	public UIControllableActor getControllableActor(String actorID) {
		return actors.get(actorID);
	}
	
	public void addControllableActor(UIControllableActor a) {
		actors.put(a.getIdentifier(), a);
	}
	
	public void addOtherActor(DialogueActor a) {
		otherActors.put(a.getIdentifier(), a);
	}
	
	@Override
	public void onNewMoves(FilteredMoveSet[] moveSets) {
		// TODO construct moves message for ui 
		
		// TODO: new moves == clear status?
		this.moveSets = moveSets;
		broadcastStatus();
	}
	
	public String[] getUIsControllingActor(String actorID) {
		List<String> res = new ArrayList<String>();
		for (Map.Entry<String, UIMiddlewareClient> entry : uiClients.entrySet()) {
			if (entry.getValue().isActorControlled(actorID)) 
				res.add(entry.getValue().getClientId());
		}
		return res.toArray(new String[0]);
	}
	
	public ActorControlType getActorControlType(String actorID) {
		if(isActorControlledByWoz(actorID)) {
			return ActorControlType.WOZ;
		} else if(isActorControlledByUser(actorID)) {
			return ActorControlType.USER;
		} else {
			return ActorControlType.BOT;
		}
	}

	public boolean isActorControlled(String actorID) {
		for (Map.Entry<String, UIMiddlewareClient> entry : uiClients.entrySet()) {
			if (entry.getValue().isActorControlled(actorID)) return true;
		}

		return false;
	}

	public boolean isActorControlledByWoz(String actorID) {
		Actor a = actors.get(actorID);
		return isActorControlled(actorID) && a.bml_name != null && !"".equals(a.bml_name);
	}

	public boolean isActorControlledByUser(String actorID) {
		Actor a = actors.get(actorID);
		return isActorControlled(actorID) && (a.bml_name == null || "".equals(a.bml_name));
	}
	
	public void broadcastStatus() {
		ObjectMapper om = new ObjectMapper();
		JsonNode jn = om.convertValue(buildStatusResponse(), JsonNode.class);

		logger.info("UI published moves to clients.");
		getMW().sendData(jn);
	}
	
	UIProtocolCustomAction[] getCustomActions(DialogueActor a, MoveSet[] ms) {
		List<UIProtocolCustomAction> res = new ArrayList<UIProtocolCustomAction>();
		for (ICustomUIAction action : customUIActions) {
			if (action.isApplicableNow(this, a, ms)) {
				res.add(new UIProtocolCustomAction(action));
			}
		}
		return res.toArray(new UIProtocolCustomAction[0]);
	}
	
	UIProtocolStatusResponse buildStatusResponse() {
		List<UIProtocolActor> _actors = new ArrayList<UIProtocolActor>();
		
		
		// Todo get/set fallback move and current move from actors...
		for (UIControllableActor a : actors.values()) {
			_actors.add(new UIProtocolActor(a, getUIsControllingActor(a.getIdentifier()), "", "", getCustomActions(a, moveSets)));
		}
		
		for (DialogueActor a : otherActors.values()) {
			_actors.add(new UIProtocolActor(a, new String[0], "", "", getCustomActions(a, moveSets)));
		}
		
		UIProtocolStatusResponse res = new UIProtocolStatusResponse(
				_actors.toArray(new UIProtocolActor[0]), moveSets,
				getCustomActions(null, moveSets));

		return res;
	}
	
	void handleInit(UIProtocolInitRequest req) {
		broadcastStatus();
	}

	void handleMoveSelected(UIProtocolMoveSelectedRequest req) {
		Move res = null;
		for (MoveSet moveSet : moveSets) {
			if (!moveSet.actorIdentifier.equals(req.actorIdentifier)) continue;
			for (Move move : moveSet.moves) {
				if (move.moveID.equals(req.moveID)) {
					res = move;
					res.userInput = req.userInput;
					break;
				}
			}
		}
		if (res == null) {
			logger.warn("Move selected from UI not legal anymore: "+req.moveID);
			return;
		}
		actors.get(req.actorIdentifier).UIMoveSelected(res);
	}
	
	void handleSetWaits(UIProtocolSetWaitsRequest req) {
		uiClients.get(req.uiId).UpdateControlledActors(req.actors);
		broadcastStatus();
	}
	
	void handlePass(UIProtocolPassRequest req) {

		// TODO: implement pass (play nice with "floormanager" etc..
		
	}
	
	void handleActionSelected(UIProtocolCustomActionRequest req) {
		DialogueActor actor = actors.get(req.actorIdentifier);
		for (ICustomUIAction action : customUIActions) {
			if (action.getCommandString().equals(req.command)) {
				action.actionCallback(this, actor, req);
			}
		}
	}
	
	@Override
	public void receiveData(JsonNode jn) {
		ObjectMapper om = new ObjectMapper();
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		logger.info("UIM IN: "+jn.toString());
		try {
			UIProtocolRequest req = om.treeToValue(jn, UIProtocolRequest.class);
			
			if (!uiClients.containsKey(req.uiId)) {
				uiClients.put(req.uiId, new UIMiddlewareClient(req.uiId));
			}
			uiClients.get(req.uiId).HeartbeatReceived();
			
			switch (req.cmd) {
			case "init_request":
				UIProtocolInitRequest init_req = om.treeToValue(jn, UIProtocolInitRequest.class);
				handleInit(init_req);
				break;
			case "move_selected":
				UIProtocolMoveSelectedRequest move_req = om.treeToValue(jn, UIProtocolMoveSelectedRequest.class);
				ExperimentFileLogger.getInstance().log("uimoveselected", jn);
				handleMoveSelected(move_req);
				break;
			case "set_waits":
				UIProtocolSetWaitsRequest waits_req = om.treeToValue(jn, UIProtocolSetWaitsRequest.class);
				handleSetWaits(waits_req);
			case "pass":
				UIProtocolPassRequest pass_req = om.treeToValue(jn, UIProtocolPassRequest.class);
				handlePass(pass_req);
			case "action_selected":
				UIProtocolCustomActionRequest action_req = om.treeToValue(jn, UIProtocolCustomActionRequest.class);
				ExperimentFileLogger.getInstance().log("uiactionselected", jn);
				handleActionSelected(action_req);
			}
		} catch (JsonProcessingException jpe) {
			logger.warn("Failed to parse UI Request JSON: ", jpe);
		}
	}

	public void updateMoveStatus(DialogueActor actor, Move move, String status) {
		ObjectMapper om = new ObjectMapper();
		JsonNode jn = om.convertValue(new UIProtocolMoveStatusUpdate(actor.getIdentifier(), move.moveID, status), JsonNode.class);
		getMW().sendData(jn);
	}
	
	// TODO: make this a propper logger?
	public static void log(String logString) {
		ObjectMapper om = new ObjectMapper();
		JsonNode jn = om.convertValue(new UIProtocolLogMessage(logString), JsonNode.class);
		if (singleton != null) singleton.getMW().sendData(jn);
	}

	public void flipperActionCallback(UIProtocolCustomActionRequest res) {
		ObjectMapper om = new ObjectMapper();
		JsonNode jn = om.convertValue(res, JsonNode.class);
		enqueueMessage(jn, res.command);
	}
}

class UIMiddlewareClient {
	private String clientId;
	LocalTime lastHeartbeatReceived;
	private List<String> controlledActors;
	
	public UIMiddlewareClient(String clientId) {
		this.clientId = clientId;
		this.controlledActors = new ArrayList<String>();
	}
	
	public boolean isActorControlled(String id) {
		return this.isAlive() && controlledActors.contains(id);
	}
	
	public void HeartbeatReceived() {
		lastHeartbeatReceived = LocalTime.now();
	}
	
	public boolean isAlive() {
		LocalTime now = LocalTime.now();
		//return Duration.between(lastHeartbeatReceived, now).getSeconds() <= UIEnvironment.HEARTBEAT_TIMEOUT_S;
		return true; // TODO implement client-side heartbeat sending.
	}
	
	public void AddActorControlled(String a) {
		if (!this.controlledActors.contains(a))
			this.controlledActors.add(a);
	}
	
	public void UpdateControlledActors(String[] ca) {
		this.controlledActors.clear();
		for (String s : ca) {
			this.controlledActors.add(s);
		}
	}
	
	public String getClientId() {
		return clientId;
	}
}

// PROTOCOL = MESSAGES FROM UI CLIENT


















