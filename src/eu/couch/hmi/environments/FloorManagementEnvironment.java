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
import eu.couch.hmi.floormanagement.IFloorManager;
import eu.couch.hmi.floormanagement.RandomFloorManager;
import eu.couch.hmi.floormanagement.FCFSFloorManager;
import eu.couch.hmi.floormanagement.gbm.GBMFloorManager;
import eu.couch.hmi.floormanagement.gbm.GBMProtocol;
import eu.couch.hmi.middleware.IDMiddlewareListener;
import eu.couch.hmi.moves.Move;
import eu.couch.hmi.moves.FilteredMoveSet;
import eu.couch.hmi.moves.IMoveDistributor;
import eu.couch.hmi.moves.IMoveListener;
import eu.couch.hmi.moves.MoveSet;
import nl.utwente.hmi.middleware.MiddlewareListener;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;

//TODO: maybe we could support switching floor managers during a dialogue...? perhaps during an especially heated part of a debate we need a different type of manager that allows more agressive interrupts or something...

public class FloorManagementEnvironment extends MiddlewareEnvironment {
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(FloorManagementEnvironment.class.getName());

    private ObjectMapper om = new ObjectMapper();

	private UIEnvironment uie = null;
	private DGEPEnvironment dge = null;
    private IFloorManager fm;
	
	public FloorManagementEnvironment() {
		super();
	}	
	
	@Override
	public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {
		for (IFlipperEnvironment env : envs) {
			if (env instanceof UIEnvironment) uie = (UIEnvironment) env;
			if (env instanceof DGEPEnvironment) dge = (DGEPEnvironment) env;
		}
		if (uie == null) throw new Exception("Required UIEnvironment not found.");
		if (dge == null) throw new Exception("Required DGEPEnvironment not found.");
		
	}

	@Override
	public void init(JsonNode params) throws Exception {
		int nrMWLoaded = loadMiddlewares(params);


		//TODO: we should make this better configurable through the environment params... but individual floormanager implementations might need additional parameters
		//we could do something with loaderclass magic, but for now this suffices for testing.. 
		String floorManagerStyle = params.get("style").asText("FCFS");
		switch (floorManagerStyle) {
		case "GBM":
			//TODO: make GBM its own little environment so it can load its own middleware....?
			if(nrMWLoaded < 1) {
				throw new Exception("FloorManagementEnvironment needs at least 1 defined middleware for communicating with GBM");
			} else {
				fm = new GBMFloorManager(this, getMW());
			}
			break;
		case "RANDOM":
			fm = new RandomFloorManager(this, 5000, 10000);
			break;
		default:
			fm = new FCFSFloorManager();
			break;
		}
		
	}
	
	@Override
	public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws Exception {
		switch (fenvmsg.cmd) {
		default:
			logger.warn("Unhandled message: "+fenvmsg.cmd);
			break;
		}
		
		return null;
	}
	

	public IFloorManager getFloorManager() {
		return fm;
	}
	
	public UIEnvironment getUIEnvironment() {
		return uie;
	}
	
	public IMoveDistributor getMoveDistributor() {
		return dge;
	}
}







