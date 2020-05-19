package eu.couch.hmi.intent.realizer;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.couch.hmi.environments.IBMLFeedbackProvider;
import eu.couch.hmi.intent.Intent;
import eu.couch.hmi.intent.IntentStatus;
import eu.couch.hmi.intent.realizer.status.*;
import hmi.flipper.bmlfeedback.BMLBlockProgressFeedbackJSON;
import hmi.flipper.bmlfeedback.BMLPredictionFeedbackJSON;
import hmi.flipper.bmlfeedback.BMLSyncPointProgressFeedbackJSON;
import hmi.flipper.bmlfeedback.BMLWarningFeedbackJSON;
import hmi.flipper.bmlfeedback.IBMLFeedbackJSONListener;
import hmi.flipper.environment.BaseFlipperEnvironment;
import hmi.flipper.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper.environment.IFlipperEnvironment;

public abstract class IntentRealizationStatusWatcher extends BaseFlipperEnvironment implements IIntentRealizer, IBMLFeedbackJSONListener {
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(IntentRealizationStatusWatcher.class.getName());
    
	private Map<String, IntentStatusWatch> watch = null;
	private Map<String, IntentIdStatusPair> watchStatus = null;

	ObjectMapper om;
	
	protected IBMLFeedbackProvider feedback = null;
	protected List<IIntentRealizationJSONListener> parsedFeedbackListeners;
	
	public IntentRealizationStatusWatcher() {
		super();
		watch = new HashMap<String, IntentStatusWatch>();
		watchStatus = new HashMap<String, IntentIdStatusPair>();
		om = new ObjectMapper();
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		parsedFeedbackListeners = new ArrayList<IIntentRealizationJSONListener>();
	}
	
	public void setIntentStatusRef(String intentId, String syncRef, IntentStatus status) {
		if (watchStatus.containsKey(syncRef) && !watchStatus.get(syncRef).intentId.equals(intentId)) {
			logger.warn("Got same endSync reference of muliple intents (old will be overridden)");
		}
		
		if (watch.containsKey(intentId)) {
			watchStatus.put(syncRef, new IntentIdStatusPair(intentId, status));
		} else {
			logger.error("Got end sync reference for unknown intentId: "+intentId);
		}
	}

	public void setIntentStatusRef(String intentId, String syncRef, String status) {
		try {
			IntentStatus _status = IntentStatus.valueOf(status);
			setIntentStatusRef(intentId, syncRef, _status);
		} catch (IllegalArgumentException e) {
			logger.warn("Intent status unknown: "+status+" must be one of: "+IntentStatus.nameList());
		}
	}
	
	public String registerNewIntentWatch(IIntentRealizationObserver observer) {
		String intentId = "I"+RandomStringUtils.randomAlphanumeric(11);
		watch.put(intentId, new IntentStatusWatch(intentId, observer));
		return intentId;
	}
	
	public String getSyncRef(String charId, String blockId, String sync) {
		return charId + "___" + blockId + "___" + sync;
	}

	//normally use this just before sending off the behaviour: parsedFeedbackListeners.forEach(listener -> listener.onIntentPlanned(Intent i));
	@Override
	public abstract String realizeIntent(Intent intent, IIntentRealizationObserver observer);
	
	
	@Override
	public void registerIntentRealizationJSONListener(IIntentRealizationJSONListener l) {
		if (!parsedFeedbackListeners.contains(l)) {
			parsedFeedbackListeners.add(l);
		}
	}

	/// IBMLFeedbackJSONListener:
	
	
	@Override
	public void onBlockProgressFeedback(BMLBlockProgressFeedbackJSON fb) {
		String syncRef = getSyncRef(fb.characterId, fb.bmlId, fb.syncId);
		if (watchStatus.containsKey(syncRef)) {
			String intentId = watchStatus.get(syncRef).intentId;
			IntentStatus status = watchStatus.get(syncRef).status;
			logger.info("Got block progress with watched sync: "+syncRef+". Intent: "+intentId+" status: "+status.name());
			watch.get(intentId).observer.onIntentStatus(intentId, status);
			watchStatus.remove(syncRef);
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
	
	
	
	/// ENVIRONMENT: 
	


	@Override
	public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {
		for (IFlipperEnvironment fe : envs) {
			if (fe instanceof IBMLFeedbackProvider) {
				feedback = ((IBMLFeedbackProvider) fe);
				feedback.registerBMLFeedbackJSONListener(this);
			}
		}
		System.out.println("setRequiredEnvironments: "+feedback==null);
	}

	@Override
	public void init(JsonNode params) throws Exception {
		// TODO Auto-generated method stub
		
	}
	
}
