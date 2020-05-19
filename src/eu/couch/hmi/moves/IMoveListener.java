package eu.couch.hmi.moves;

public interface IMoveListener {
	
	void onNewMoves(FilteredMoveSet[] moveSets);
	
}
