package eu.couch.hmi.intent.realizer;

import eu.couch.hmi.intent.Intent;

/** DR: added "eavesdropping paradigm" where anyone can ask for being informed that a certain intent was sent off... good for eg the social saliency gaze */
public interface IIntentRealizer {
	/** note that an IIntentrealizer should, before actually sending off the behaviour, inform that this intent is being planned now. */
	public String realizeIntent(Intent intent, IIntentRealizationObserver observer);
	/** register a listener for that an intent is being realised / will now be sent off */
	public void registerIntentRealizationJSONListener(IIntentRealizationJSONListener l);

}
