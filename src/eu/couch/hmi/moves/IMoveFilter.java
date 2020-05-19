package eu.couch.hmi.moves;

import java.util.List;

public interface IMoveFilter {
	
	public List<FilteredMoveSet> filterMoves(List<FilteredMoveSet> _moveSets);

}
