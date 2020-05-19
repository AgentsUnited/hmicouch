package eu.couch.hmi.intent.realizer;

import eu.couch.hmi.intent.IntentStatus;

public interface IIntentRealizationObserver {
	
	void onIntentStatus(String intentId, IntentStatus intentStatus);

}
