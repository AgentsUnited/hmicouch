
package eu.couch.hmi.environments;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.couch.hmi.actor.Actor;
import eu.couch.hmi.actor.ActorJSON;
import eu.couch.hmi.actor.DialogueActor;
import eu.couch.hmi.actor.GenericDialogueActor;
import eu.couch.hmi.actor.UIControllableActor;
import eu.couch.hmi.dialogue.IDialogueStatusListener;
import eu.couch.hmi.environments.DGEPEnvironment.DialogueStatus;
import eu.couch.hmi.floormanagement.FCFSFloorManager;
import eu.couch.hmi.floormanagement.IFloorManager;
import eu.couch.hmi.intent.planner.AsapIntentRealizer;
import eu.couch.hmi.intent.planner.DefaultIntentPlanner;
import eu.couch.hmi.intent.planner.GretaIntentRealizer;
import eu.couch.hmi.intent.planner.IIntentPlanner;
import eu.couch.hmi.intent.planner.NoEmbodimentIntentPlanner;
import eu.couch.hmi.intent.realizer.IIntentRealizer;
import eu.couch.hmi.moves.IMoveDistributor;
import eu.couch.hmi.moves.selector.DefaultMoveSelector;
import eu.couch.hmi.moves.selector.IMoveSelector;
import hmi.flipper.ExperimentFileLogger;
import hmi.flipper.environment.BaseFlipperEnvironment;
import hmi.flipper.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper.environment.IFlipperEnvironment;

/* 
 * This Environment is responsible for initializing the dialogue and participating actors / agents.
 * The overall flow of this Environment:
 *  1. Environment Initialization: store the DGEP and other Environments 
 *  2. "init_dialogue" command (called from Flipper/through message bus) - See this.loadDialogue()
 *  	a.) Start dialogue in DGEP with params.protocolParams (
 *  	b.) Initialize actors in params.dialogueActors.
 *  	c.) Wait for all actors be be joined.
 *  3. For each joined Actor in DGEP, initialize a "DialogueActor" - See this.loadDialogueActors()
 *  4. Return a representation of all DialogueActors to Flipper (response to "init_dialogue" command).
 * (5. Request the moveset from DGEP to kickstart the dialogue)
 */
public class DialogueLoaderEnvironment extends BaseFlipperEnvironment implements IDialogueStatusListener {
	
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(DialogueLoaderEnvironment.class.getName());
    
    IFlipperEnvironment[] allEnvs;
    DGEPEnvironment dgepEnv;
    ObjectMapper om;
    List<DialogueActor> dialogueActors;

	private IFloorManager fm;
    
	public DialogueLoaderEnvironment() {
		super();
		dialogueActors = new ArrayList<DialogueActor>();
		om = new ObjectMapper();
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}
	

	@Override
	public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {
		this.allEnvs = envs;
		this.dgepEnv = null;
		
		for (IFlipperEnvironment env : envs) {
			if (env instanceof DGEPEnvironment) this.dgepEnv = (DGEPEnvironment) env;
		}
		if (this.dgepEnv == null) throw new Exception("Required loader of type DGEPEnvironment not found.");
		

	}

	@Override
	public void init(JsonNode params) throws Exception {
		dgepEnv.registerDialogueStatusListener(this);
	}
	
	@Override
	public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws Exception {
		switch (fenvmsg.cmd) {
		case "init_dialogue":
			ExperimentFileLogger.getInstance().log("init_dialogue", fenvmsg.params);
			DialogueInitJSON dij = om.treeToValue(fenvmsg.params, DialogueInitJSON.class);
			return buildResponse(fenvmsg, loadDialogue(dij.topicParams, dij.dialogueActors, dij.utteranceParams));
		default:
			logger.warn("Unhandled message: "+fenvmsg.cmd);
			break;
		}
		
		return null;
	}
	
	public JsonNode loadDialogue(JsonNode topic, JsonNode actors, JsonNode utteranceParams) {
    	Actor[] dgepActors = null;
    	dgepActors = dgepEnv.initSession(topic, actors, utteranceParams);
    	JsonNode res = loadDialogueActors(dgepActors);
    	if (res == null) {
    		logger.error("Failed to initialize (all) dialogue actors (initializing DGEP was successful)");
    	} else {
    		// TODO: maybe leave this to Flipper?
    		dgepEnv.requestMoves();
    	}
    	return res;
	}
	
	
	/* Initialize a DialogueActor for each actor that partakes in the DGEP dialogue.
	 * TODO: This method (and the specification of DialogueActors) need to be re-implemented to support more flexible
	 * 		 initialization of actiors, allowing to specify 
	 */
	public JsonNode loadDialogueActors(Actor[] actors) {
		for(DialogueActor da : dialogueActors) {
			da.disableActor();
		}
		this.dialogueActors.clear();
		
		UIEnvironment uie = null;
		DGEPEnvironment dgepm = null;
		FloorManagementEnvironment fme = null;
		GretaIntentRealizer gir = null;
		AsapIntentRealizer air = null;
		FlipperIntentPlannerEnvironment fpe = null;
		
		IMoveDistributor moveDistributor = null;
//todo dennis: je kan ook hier dynamische in de fmlenv de nieuwe middleware laten maken voor deze nieuwe actor. moet je de fml env hier hebben, maar dan hoeft in environmentsxml niet meer laura en camille te staan
		for (IFlipperEnvironment e : allEnvs) {
			if (e instanceof UIEnvironment) uie = (UIEnvironment) e;
			if (e instanceof FloorManagementEnvironment) fme = (FloorManagementEnvironment) e;
			if (e instanceof GretaIntentRealizer) gir = (GretaIntentRealizer) e;
			if (e instanceof AsapIntentRealizer) air = (AsapIntentRealizer) e;
			if (e instanceof FlipperIntentPlannerEnvironment) fpe = (FlipperIntentPlannerEnvironment) e;
			
			// We can insert a "proxy" move distributor in the environments
			// agents will then listen to the proxy if one is supplied...
			if (e instanceof DGEPEnvironment) {
				dgepm = (DGEPEnvironment) e;
				if (moveDistributor == null) {
					moveDistributor = dgepm;
				}
			} else if (e instanceof IMoveDistributor) {
				moveDistributor = (IMoveDistributor) e;
			}
			
		}

		//if there is a floormanagementenvironment we should use the defined floormanager, but otherwise we just use a very simple one instead
		if(fme != null) {
			fm = fme.getFloorManager();
		} else {
			fm = new FCFSFloorManager();
		}
		
		for (Actor a : actors) {
			logger.info("Setting up DialogueActor "+a.identifier+" (Player: "+a.player+"):");
			IMoveSelector ms = new DefaultMoveSelector(true);
			logger.info(" => Using DefaultMoveSelector");
			IIntentPlanner mp = null;
			
			int countGretas;

			if (a.bml_name == null || a.bml_name.length() == 0) {
				mp = new NoEmbodimentIntentPlanner();
				logger.info("  => Using NoEmbodimentMovePlanner");
			} else if (air != null && a.engine.equalsIgnoreCase("ASAP")) {
				mp = new DefaultIntentPlanner(air, a.bml_name);
				logger.info("  => Using BMLMovePlanner (bml char id: "+a.bml_name+")");
			} else if (fpe != null && a.engine.equalsIgnoreCase("ASAP")) {
				mp = new DefaultIntentPlanner(fpe, a.bml_name);
				logger.info("  => Using FlipperMovePlanner (bml char id: "+a.bml_name+")");
			} else if (gir != null && a.engine.equalsIgnoreCase("greta")) {
				//if (countg){
					//TODO: for multiple greta agents. nesting for charID, i/o Topic suffics met charID (in fml environment)
					mp = new DefaultIntentPlanner(gir, a.bml_name);
					logger.info("  => Using FMLMovePlanner (bml char id: "+a.bml_name+")");
				//}
			} else {
				logger.error("  => Failed to find suitable MovePlanner");
			}
 
			if (a.dialogueActorClass.contentEquals("UIControllableActor")) {
				logger.info("  => As UIControllableActor");
				this.dialogueActors.add(new UIControllableActor(a, fm, moveDistributor, ms, mp, dgepm, uie));
			} else {
				logger.info("  => As GenericDialogueActor");
				this.dialogueActors.add(new GenericDialogueActor(a, fm, moveDistributor, ms, mp, dgepm));
			}
		}
		
		LoadedDialogueActorsJSON res = new LoadedDialogueActorsJSON(this.dialogueActors.toArray(new DialogueActor[0]));
		return om.convertValue(res, JsonNode.class);
	}


	@Override
	public void onDialogueStatusChange(String topic, DialogueStatus status) {
		logger.info("Dialogue {} has reached status {}", topic, status.toString());
		this.enqueueMessage(om.createObjectNode().put("status", status.toString()), "topic_status");
	}
	
	public List<DialogueActor> getDialogueActors() {
		return dialogueActors;
	}
	
	public DialogueActor getDialogueActorByIdentifier(String id) {
		for (DialogueActor da : dialogueActors) {
			if (id.equals(da.identifier)) {
				return da;
			}
		}
		return null;
	}


}

// (De-)serialization helper classes:

class DialogueInitJSON {
	public JsonNode topicParams;
	public JsonNode dialogueActors;
	public JsonNode utteranceParams;
}

class LoadedDialogueActorsJSON {
	
	public String response = "loadedActors";
	public ActorJSON[] actors;
	
	public LoadedDialogueActorsJSON(DialogueActor[] actors) {
		List<ActorJSON> res = new ArrayList<ActorJSON>();
		for (DialogueActor da : actors) {
			res.add(new ActorJSON(da));
		}
		
		this.actors = res.toArray(new ActorJSON[0]);
	}
	
	public LoadedDialogueActorsJSON(ActorJSON[] actors) {
		this.actors = actors;
	}
}

