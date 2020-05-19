package eu.couch.hmi.starters;

import java.util.Properties;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.couch.hmi.actor.UIControllableActor;
import eu.couch.hmi.floormanagement.FloorStatus;
import eu.couch.hmi.floormanagement.gbm.GBMProtocol;
import eu.couch.hmi.moves.FilteredMove;
import eu.couch.hmi.moves.FilteredMoveSet;
import nl.utwente.hmi.middleware.Middleware;
import nl.utwente.hmi.middleware.MiddlewareListener;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;

/**
 * A very very stupid & dummy stateless implementation of the GBM using the first-come-first-serve selection 
 * (don't do this for real, this is just for testing the protocol :)
 * @author Daniel
 *
 */
public class DummyGBMFCFS implements MiddlewareListener {
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(DummyGBMFCFS.class.getName());

	Middleware mw;
	private ObjectMapper om;
	private boolean isFree = true;
	
	public DummyGBMFCFS() {
		this.om = new ObjectMapper();

        String propFile = "defaultmiddleware.properties";
        GenericMiddlewareLoader.setGlobalPropertiesFile(propFile);

		Properties props = new Properties();
		props.put("iTopic","COUCH/GBM/REQUEST");
		props.put("oTopic","COUCH/GBM/RESPONSE");
		
        String loaderclass = "nl.utwente.hmi.middleware.activemq.ActiveMQMiddlewareLoader";

		GenericMiddlewareLoader gml = new GenericMiddlewareLoader(loaderclass, props);
		mw = gml.load();
		mw.addListener(this);

	}
	
	public static void main(String[] args) {
		DummyGBMFCFS gbm = new DummyGBMFCFS();
	}

	@Override
	public void receiveData(JsonNode jn) {
		logger.debug("GBM got message: {}", jn.toString());
		if(jn.has("cmd") && "moves_available".equals(jn.get("cmd").asText(""))) {
			try {
				GBMProtocol.MovesAvailable movesAvailable = om.treeToValue(jn, GBMProtocol.MovesAvailable.class);
				isFree = true;
				logger.debug("Got a bunch of moves, means the floor is free again");
			} catch (JsonProcessingException e) {
				logger.warn("GBM got a malformed moves_available message: {}", jn.toString());
				e.printStackTrace();
			}

		} else if(jn.has("cmd") && "move_selected".equals(jn.get("cmd").asText(""))) {
			try {
				
				GBMProtocol.MoveSelected moveSelected = om.treeToValue(jn, GBMProtocol.MoveSelected.class);

				if(!isFree) {
					logger.warn("Floor is not free, ignoring move_selection from actor {}", moveSelected.params.actorName);
					return;
				}
				
				logger.debug("I will now grant the floor to actor {}", moveSelected.params.actorName);
				
				//construct the return message parameters
				GBMProtocol.MoveGrantedParams mgp = new GBMProtocol.MoveGrantedParams();
				mgp.actorName = moveSelected.params.actorName;
				mgp.moveID = moveSelected.params.moveID;
				mgp.moveUID = moveSelected.params.moveUID;
				mgp.BMLTemplate = "nothing_for_now.xml";
				
				//construct the command message
				GBMProtocol.MoveGranted moveGranted = new GBMProtocol.MoveGranted();
				moveGranted.cmd = "move_granted";
				moveGranted.params = mgp;
				
				JsonNode ret = om.convertValue(moveGranted, JsonNode.class);

				isFree = false;
				mw.sendData(ret);
				
			} catch (JsonProcessingException e) {
				logger.warn("GBM got a malformed move_selected message: {}", jn.toString());
				e.printStackTrace();
			}

		} else {
			logger.warn("GBM got a malformed message: {}", jn.toString());
		}
	}
	
}
