package eu.couch.hmi.intent.realizer.status;

import eu.couch.hmi.intent.realizer.IIntentRealizationObserver;

public class IntentStatusWatch {
	public IIntentRealizationObserver observer;
	public String intentId;
	
	public IntentStatusWatch(String intentId, IIntentRealizationObserver observer) {
		this.intentId = intentId;
		this.observer = observer;
	}
}
