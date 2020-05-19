package eu.couch.hmi.moves;

public interface IMoveCollector {
	
	public void onMoveCompleted(IMoveListener actor, Move move);

}
