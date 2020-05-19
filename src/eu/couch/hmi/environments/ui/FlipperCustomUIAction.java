package eu.couch.hmi.environments.ui;

import eu.couch.hmi.actor.DialogueActor;
import eu.couch.hmi.environments.UIEnvironment;
import eu.couch.hmi.intent.planner.NoEmbodimentIntentPlanner;
import eu.couch.hmi.moves.MoveSet;

public class FlipperCustomUIAction extends BaseCustonUIAction {
	
	private String scope;
	
	public FlipperCustomUIAction(UIProtocolCustomFlipperAction action) {
		super(action.command, action.description);
		this.scope = action.scope;
	}

	@Override
	public void actionCallback(UIEnvironment env, DialogueActor da, UIProtocolCustomActionRequest req) {
		env.flipperActionCallback(req);
	}

	@Override
	public boolean isApplicableNow(UIEnvironment env, DialogueActor da, MoveSet[] moveSets) {
		if (scope.equalsIgnoreCase("global") && da == null) return true;
		if (scope.equalsIgnoreCase("users") && da != null && da.getMovePlanner() instanceof NoEmbodimentIntentPlanner) return true;
		if (scope.equalsIgnoreCase("agents") && da != null && !(da.getMovePlanner() instanceof NoEmbodimentIntentPlanner)) return true;
		if (scope.equalsIgnoreCase("all") && da != null) return true;
		return false;
	}
}