package eu.couch.hmi.intent.planner;

import org.slf4j.LoggerFactory;

import eu.couch.hmi.actor.DialogueActor;
import eu.couch.hmi.intent.Intent;
import eu.couch.hmi.intent.IntentStatus;
import eu.couch.hmi.intent.realizer.IIntentRealizationObserver;
import eu.couch.hmi.intent.realizer.IIntentRealizer;
import eu.couch.hmi.moves.Move;
import eu.couch.hmi.moves.MoveIntent;
import eu.couch.hmi.moves.MoveStatus;



// This shoud extend/implement some kind of floor strategy/etc. to try and realize the plan...
public class DefaultIntentPlanner implements IIntentPlanner, IIntentRealizationObserver {
	
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(DefaultIntentPlanner.class.getName());
	
    
	private IIntentRealizer ir;
	
	private String currentIntentId = null;
	private Intent currentIntent = null;
	private DialogueActor currentActor = null;
	
	public DefaultIntentPlanner(IIntentRealizer ir, String bmlCharacterId) {
		this.ir = ir;
	}

	@Override
	public String planIntent(DialogueActor actor, Intent intent) {
		currentActor = actor;
    
		currentIntent = intent;
    
		currentIntentId = ir.realizeIntent(intent, this);
		return currentIntentId;
	}

    
	@Override
	public void cancelIntentPlan(String intentId) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onIntentStatus(String intentId, IntentStatus status) {
		logger.info("UPDATE STATUS: "+intentId+" Status: "+status);
		
		if (currentIntent instanceof MoveIntent && intentId.equals(currentIntentId)) {
			Move move = ((MoveIntent) currentIntent).getMove();
			
			switch (status) {
				case INTENT_CANCELLED:
					currentActor.onMoveStatus(move, MoveStatus.MOVE_CANCELLED);
					break;
				case INTENT_FAILED:
					currentActor.onMoveStatus(move, MoveStatus.MOVE_FAILED);
					break;
				case INTENT_PLANNED:
					currentActor.onMoveStatus(move, MoveStatus.MOVE_PLANNED);
					break;
				case INTENT_REALIZATION_STARTED:
					currentActor.onMoveStatus(move, MoveStatus.MOVE_REALIZATION);
					break;
				case INTENT_REALIZATION_COMPLETED:
					currentActor.onMoveStatus(move, MoveStatus.MOVE_COMPLETED);
					break;
				case INTENT_SUCCESS:
					// TODO... there is a difference between whether a move was realized completely, and whether it was made successfully?
					break;
				default:
					break;
			}
		}
	}
	
	
}