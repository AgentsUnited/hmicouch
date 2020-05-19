package eu.couch.hmi.environments;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.couch.hmi.moves.FilteredMoveSet;
import eu.couch.hmi.moves.IMoveDistributor;
import eu.couch.hmi.moves.IMoveFilter;
import eu.couch.hmi.moves.IMoveListener;
import hmi.flipper.environment.BaseFlipperEnvironment;
import hmi.flipper.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper.environment.IFlipperEnvironment;

/* 
 * This class acts as a proxy between a IMoveDistributor and move listener...
 * Moves from the distributor are first sent to Flipper. The response is then forwarded to IMoveListeners registered to this proxy.
 * To use this proxy, it needs to be passed to the required environments of the DialogueLoader Environment (in Environments.xml).
 * The DialogueLoader environment then passes it to the Actor logic. 
 * Note that currently, it only proxies one way - I.e. it does not act as a MoveCollector (so dialogue actors use the DGEPEnvironment directly to communicate that they completed a move).
 */
public class FlipperMoveProxy extends BaseFlipperEnvironment implements IMoveDistributor, IMoveListener {
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(FlipperMoveProxy.class.getName());

	IMoveDistributor proxy;
	List<IMoveListener> moveListeners;
	ObjectMapper om;
	
	String lastRequestId;
	
	public FlipperMoveProxy() {
		moveListeners = new ArrayList<IMoveListener>();
		om = new ObjectMapper();
		lastRequestId = "";
	}

	@Override
	public void init(JsonNode params) throws Exception {
		// could parse/use params given in environment.xml loader...
	}

	// We should get passed another instance of move distributor that we "proxy"...
	@Override
	public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {
		for (IFlipperEnvironment env : envs) {
			if (env instanceof IMoveDistributor) this.proxy = (IMoveDistributor) env;
		}
		if (this.proxy == null) throw new Exception("Required loader of type IMoveDistributor not found.");
		else this.proxy.registerMoveListener(this);
	}
	
	
	// MESSAGE FROM FLIPPER (i.e. a (new) set of moves from flipper)
	@Override
	public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws Exception {
		switch (fenvmsg.cmd) {
		case "movesets":
			if (lastRequestId.length() == 0 || !fenvmsg.msgId.equals(lastRequestId)) {
				logger.warn("Got unsolicited (or already handled/duplicate) moveset response from flipper: {}", fenvmsg.msgId);
				return null;
			}
			
			FlipperMoveSetMessage fmsm = null;
			
			try {
				fmsm = om.convertValue(fenvmsg.params, FlipperMoveSetMessage.class);
				logger.info("Got {} movesets", fmsm.moveSets.length);
			} catch (IllegalArgumentException e) {
				logger.warn("Failed to parse movesets msg! ", e);
				return null;
			}
			
			logger.info("Got {} movesets", fmsm.moveSets.length);
			for (IMoveListener ml : moveListeners) {
				ml.onNewMoves(fmsm.moveSets);
			}
			
			lastRequestId = "";
			
			break;
		default:
			logger.warn("Unhandled message: "+fenvmsg.cmd);
			break;
		}
		return null;
	}
	
	// New moveset from the IMoveDistributor we proxy...
	@Override
	public void onNewMoves(FilteredMoveSet[] moveSets) {
		logger.info("Forwarding {} movesets to flipper.", moveSets.length);
		if (lastRequestId.length() > 0) {
			logger.warn("Got new set of moves before flipper handled the previous one? "+lastRequestId);
		}
		lastRequestId = enqueueMessage(om.convertValue(new FlipperMoveSetMessage(moveSets), JsonNode.class), "movesets");
	}

	@Override
	public void registerMoveListener(IMoveListener listener) {
		for (IMoveListener ml : moveListeners) {
			if (listener.equals(ml)) return;
		}
		moveListeners.add(listener);
	}

	@Override
	public void registerMoveFilter(IMoveFilter filter) {
		// TODO: do we want to support filters in a proxy?
	}

}

class FlipperMoveSetMessage {
	public FilteredMoveSet[] moveSets;
	public FlipperMoveSetMessage() {}
	public FlipperMoveSetMessage(FilteredMoveSet[] moveSets) {
		this.moveSets = moveSets;
	}
}
