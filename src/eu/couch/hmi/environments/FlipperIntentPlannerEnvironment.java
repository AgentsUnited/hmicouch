package eu.couch.hmi.environments;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.couch.hmi.intent.Intent;
import eu.couch.hmi.intent.IntentJSON;
import eu.couch.hmi.intent.IntentStatus;
import eu.couch.hmi.intent.realizer.IIntentRealizationObserver;
import eu.couch.hmi.intent.realizer.IIntentRealizationJSONListener;
import eu.couch.hmi.intent.realizer.IIntentRealizer;
import eu.couch.hmi.intent.realizer.status.*;
import hmi.flipper.bmlfeedback.BMLBlockProgressFeedbackJSON;
import hmi.flipper.bmlfeedback.BMLPredictionFeedbackJSON;
import hmi.flipper.bmlfeedback.BMLSyncPointProgressFeedbackJSON;
import hmi.flipper.bmlfeedback.BMLWarningFeedbackJSON;
import hmi.flipper.bmlfeedback.IBMLFeedbackJSONListener;
import hmi.flipper.environment.BaseFlipperEnvironment;
import hmi.flipper.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper.environment.IFlipperEnvironment;



 /* 
 * This environment acts as an IntentRealizer (and thus can be used by Behavior Planners such as the MovePlanners for intent realization).
 * This implementation is a bit of a hybrid. Intents are planned (i.e. BML generated) externally (i.e. in Flipper), 
 * and the external planner is responsible for queuing the behavior for realization. 
 * From Flipper, we can then either inform this environment about which sync points correspond to which realization statuses (and this component will watch for it by itself)
 *  ... or Flipper itself watches for the feedback and informs this component the. 
 * 
 * The flow is as follows:
 * 	1. A DialogueAgent's IntentPlanner (i.e. BMLMovePlanner) requests an Intent to be realized.
 *  2. This environments queues up an "intent_request" message to the EnvironmentBus.
 *  3. In Flipper, a template is responsible for handling these "intent_request" messages, planning & queing up the behavior for realization...
 *  4. ...and responding to the "intent_request" message with information on realization status in one of two ways:
 *  	a) With a watchrefs in the message body that tells this component to watch for BMLFeedback for associated realization status: { "watchrefs": [{ syncRef: "bml1:end", status: "COMPLETED" }] }
 *  	b) With a status in the messagebody that immedatly will be forwarded as the realization status to the DialogueAgent's IntentPlanner: { "status": "COMPLETED" } 
 *  5. If a), the intent planner then watches for this BMLFeedback and propagates the associated realization status back to the DialogueAgent's IntentPlanner
 *  
 */

public class FlipperIntentPlannerEnvironment extends BaseFlipperEnvironment implements IIntentRealizer, IBMLFeedbackJSONListener {
// TODO: the behavior as a "watch feedback for planned bml progress" is more generic and should be in a separate class 

    private static org.slf4j.Logger logger = LoggerFactory.getLogger(FlipperIntentPlannerEnvironment.class.getName());
	
	//private BMLEnvironment bmlEnv;
	
	private Map<String, IntentStatusWatch> watch = null;
	private Map<String, IntentIdStatusPair> watchStatus = null;
	
	ObjectMapper om;
	
	public FlipperIntentPlannerEnvironment() {
		super();
		watch = new HashMap<String, IntentStatusWatch>();
		watchStatus = new HashMap<String, IntentIdStatusPair>();
		om = new ObjectMapper();
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	@Override
	public void init(JsonNode params) throws Exception {
	}

	@Override
	public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {
		for (IFlipperEnvironment env : envs) {
			if (env instanceof IBMLFeedbackProvider) {
				((IBMLFeedbackProvider) env).registerBMLFeedbackJSONListener(this);
			}
		}
	}

	@Override
	public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws JsonProcessingException {
		switch (fenvmsg.cmd) {
		case "intent_request": // Response to our intent_request...
			// link certain sync points to IntentStatuses which this intent planner watches for by itself on the BmlFeedback
			if (fenvmsg.params.has("watchrefs")) {
				IntentRequestResponseWatchList ref_req = om.treeToValue(fenvmsg.params, IntentRequestResponseWatchList.class);
				for (IntentRequestResponseStatusPair statusPair : ref_req.watchrefs) {
					setIntentStatusRef(fenvmsg.msgId, statusPair.syncRef, statusPair.status);
					logger.info("Waiting for intent " +fenvmsg.msgId+ " to be " +statusPair.status+ " at "+statusPair.syncRef);
				}
			// Alternatively, a status can be manually set from flipper here....
			} else if (fenvmsg.params.has("status")) {
				IntentRequestResponseStatusInform ref_req = om.treeToValue(fenvmsg.params, IntentRequestResponseStatusInform.class);
				try {
					watch.get(fenvmsg.msgId).observer.onIntentStatus(fenvmsg.msgId, IntentStatus.valueOf(ref_req.status));
				} catch (IllegalArgumentException e) {
					logger.warn("Intent status unknown: "+ref_req.status+" must be one of: "+IntentStatus.nameList());
				}
			}
			
			break;
		default:
			logger.warn("Unhandled message: "+fenvmsg.cmd);
			break;
		}
		
		return null;
	}
	
	public void setStatus(String intentId, String endSyncRef) {
		
	}
	
	public void setIntentStatusRef(String intentId, String syncRef, String status) {
		if (watchStatus.containsKey(syncRef) && !watchStatus.get(syncRef).intentId.equals(intentId)) {
			logger.warn("Got same endSync reference of muliple intents (old will be overridden)");
		}
		
		if (watch.containsKey(intentId)) {
			try {
				watchStatus.put(syncRef, new IntentIdStatusPair(intentId, IntentStatus.valueOf(status)));
			} catch (IllegalArgumentException e) {
				logger.warn("Intent status unknown: "+status+" must be one of: "+IntentStatus.nameList());
			}
		} else {
			logger.error("Got end sync reference for unknown intentId: "+intentId);
		}
	}

	@Override
	public String realizeIntent(Intent intent, IIntentRealizationObserver observer) { 
		String intentWatch = enqueueMessage(IntentJSON.FromIntent(intent), "intent_request");
		watch.put(intentWatch, new IntentStatusWatch(intentWatch, observer));
		return intentWatch;
	}

	@Override
	public void onBlockProgressFeedback(BMLBlockProgressFeedbackJSON fb) {
		if (watchStatus.containsKey(fb.flipperSyncId)) {
			String intentId = watchStatus.get(fb.flipperSyncId).intentId;
			IntentStatus status = watchStatus.get(fb.flipperSyncId).status;
			logger.info("Got block progress with watched sync: "+fb.flipperSyncId+". Intent: "+intentId+" status: "+status.name());
			watch.get(intentId).observer.onIntentStatus(intentId, status);
			watchStatus.remove(fb.flipperSyncId);
		}
	}


	@Override
	public void onSyncPointProgressFeedback(BMLSyncPointProgressFeedbackJSON fb) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onPredictionFeedback(BMLPredictionFeedbackJSON fb) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onWarningFeedback(BMLWarningFeedbackJSON fb) {
		// TODO Auto-generated method stub
		
	}
    
    @Override
    public void registerIntentRealizationJSONListener(IIntentRealizationJSONListener a) {}
	
}