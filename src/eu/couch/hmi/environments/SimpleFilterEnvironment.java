package eu.couch.hmi.environments;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import eu.couch.hmi.moves.FilteredMove;
import eu.couch.hmi.moves.FilteredMoveSet;
import eu.couch.hmi.moves.IMoveDistributor;
import eu.couch.hmi.moves.IMoveFilter;
import eu.couch.hmi.moves.Move;
import eu.couch.hmi.moves.MoveSet;
import hmi.flipper.environment.BaseFlipperEnvironment;
import hmi.flipper.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper.environment.IFlipperEnvironment;

public class SimpleFilterEnvironment extends BaseFlipperEnvironment implements IMoveFilter {
	
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(SimpleFilterEnvironment.class.getName());

	@Override
	public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws Exception {
		switch (fenvmsg.cmd) {
		default:
			logger.warn("Unhandled message: "+fenvmsg.cmd);
			break;
		}
		return null;
	}

	@Override
	public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {
		IMoveDistributor md = null;
		for (IFlipperEnvironment env : envs) {
			if (env instanceof IMoveDistributor) md = (IMoveDistributor) env;
		}
		if (md == null) throw new Exception("Required loader of type IMoveDistributor (such as DGEPEnvironment) not found.");
		md.registerMoveFilter(this);
	}

	@Override
	public void init(JsonNode params) throws Exception {}

	// TODO: The idea is to have a KB or similar inform ranking of possible moves...
	@Override
	public List<FilteredMoveSet> filterMoves(List<FilteredMoveSet> moveSets) {
		float s = 0.1f;
		List<FilteredMoveSet> res = new ArrayList<FilteredMoveSet>();
		for (FilteredMoveSet moveSet : moveSets) {
			List<FilteredMove> fmoves = new ArrayList<FilteredMove>();
			for (Move move : moveSet.moves) {
				s += 0.25f;
				FilteredMove fmove = new FilteredMove(move);
				fmove.score = s;
				fmoves.add(fmove);
			}
			res.add(new FilteredMoveSet(moveSet.actorIdentifier, moveSet.actorName, moveSet.dialogueID, fmoves.toArray(new FilteredMove[0])));
		}
		return res;
	}


}
