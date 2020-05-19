package eu.couch.hmi.moves.selector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.couch.hmi.actor.DialogueActor;
import eu.couch.hmi.moves.FilteredMove;
import eu.couch.hmi.moves.FilteredMoveSet;
import eu.couch.hmi.moves.Move;
import eu.couch.hmi.moves.MoveSet;

// The default move selector choses the first not previously selected move...
// or the last move that may already have been selected.
// 
// An instance of this moveselector can be shared between multiple dialogue actors
// (extensions of this class could implement centralized move selection strategies for a set of actors)
public class DefaultMoveSelector implements IMoveSelector {
	
	boolean random = false;
	Map<String, List<String>> selectedMoves;
	
	public DefaultMoveSelector(boolean random) {
		this.random = random;
		selectedMoves = new HashMap<String, List<String>>();
	}

	@Override
	public void selectMove(DialogueActor actor, MoveSet[] moveSets) {
		Move selectedMove = null;
		String actorId = actor.getIdentifier();

		if (!selectedMoves.containsKey(actorId)) {
			selectedMoves.put(actorId, new ArrayList<String>());
		}
		
		List<MoveSet> _moveSets = Arrays.asList(moveSets);
		if (random) Collections.shuffle(_moveSets);
		
		for (MoveSet moveSet : _moveSets) {
			if (!moveSet.actorIdentifier.equals(actorId)) continue;
			
			if (moveSet instanceof FilteredMoveSet) {
				List<FilteredMove> _fmoves = Arrays.asList((FilteredMove[]) moveSet.moves);
				if (random) Collections.shuffle(_fmoves);
				else Collections.sort(_fmoves);
				for (Move move : _fmoves) {
					selectedMove = move;
					if (!selectedMoves.get(actorId).contains(selectedMove.moveID)) {
						selectedMoves.get(actorId).add(selectedMove.moveID);
						break;
					}
				}
			} else {
				List<Move> _moves = Arrays.asList(moveSet.moves);
				if (random) Collections.shuffle(_moves);
				for (Move move : _moves) {
					selectedMove = move;
					if (!selectedMoves.get(actorId).contains(selectedMove.moveID)) {
						selectedMoves.get(actorId).add(selectedMove.moveID);
						break;
					}
				}
			}
		}
		
		if (selectedMove != null) actor.onMoveSelected(selectedMove);
	}

}
