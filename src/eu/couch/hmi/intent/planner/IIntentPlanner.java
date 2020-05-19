package eu.couch.hmi.intent.planner;

import eu.couch.hmi.actor.DialogueActor;
import eu.couch.hmi.intent.Intent;

public interface IIntentPlanner {
	
	String planIntent(DialogueActor actor, Intent intent);
	void cancelIntentPlan(String intentId);

}
