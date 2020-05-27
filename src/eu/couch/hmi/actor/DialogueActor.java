package eu.couch.hmi.actor;

import eu.couch.hmi.moves.Move;
import eu.couch.hmi.moves.MoveStatus;
import eu.couch.hmi.floormanagement.IFloorManager;
import eu.couch.hmi.intent.planner.IIntentPlanner;
import eu.couch.hmi.moves.IMoveCollector;
import eu.couch.hmi.moves.IMoveDistributor;
import eu.couch.hmi.moves.IMoveListener;
import eu.couch.hmi.moves.selector.IMoveSelector;

// A crosspoint between flipper/the centralized DGEP, KB, ... systems and  (semi-)decentralized
// implementations of individual agent behavior generation and sensing modules for actors partaking
// in the dialogue.
// ...Remote components "talk" to instances of this instead of directly to DGEP...
// ...and may talk through middleware to this class... and therefore we try to stick to "asynchronous" patterns...
public abstract class DialogueActor extends Actor implements IMoveListener {
	
	protected IMoveSelector moveSelector;
	protected IIntentPlanner intentPlanner;
	protected IMoveDistributor moveDistributor;
	protected IMoveCollector moveCollector;
	protected IFloorManager floorManager;
		
	public DialogueActor(Actor dgepActor, IFloorManager fm, IMoveDistributor md, IMoveSelector ms, IIntentPlanner ip, IMoveCollector mc) {
		super(dgepActor);
		this.floorManager = fm;
		this.moveDistributor = md;
		this.moveSelector = ms;
		this.intentPlanner = ip;
		this.moveCollector = mc;

		this.moveDistributor.registerMoveListener(this);
	}

	// TODO: factor these MoveSelector / MovePlanner specific callbacks out to interfaces?
	public abstract void onMoveSelected(Move move);

	// TODO: factor these MoveSelector / MovePlanner specific callbacks out to interfaces?
	public abstract void onMoveStatus(Move move, MoveStatus status);
	
	// TODO: should move some getters/setters to the Actor baseclass
	public String getIdentifier() {
		return this.identifier;
	}

	public IIntentPlanner getMovePlanner() {
		return this.intentPlanner;
	}
	
	public IMoveSelector getMoveSelector() {
		return this.moveSelector;
	}
	
	/**
	 * Disables this actor instance, so it will not respond to new moves etc.
	 * TODO: right now, implementing classes just ignore any future moves that come their way, but the instance of the actor still hangs around in various listener queues (this is a memory leak). Nicer would be to de-register each instance with the various places where it is used
	 */
	public abstract void disableActor();
	
}
