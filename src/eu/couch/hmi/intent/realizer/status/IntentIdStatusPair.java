package eu.couch.hmi.intent.realizer.status;

import eu.couch.hmi.intent.IntentStatus;

public class IntentIdStatusPair {
	public String intentId;
	public IntentStatus status;
	
	public IntentIdStatusPair(String intentId, IntentStatus status) {
		this.intentId = intentId;
		this.status = status;
	}
}
