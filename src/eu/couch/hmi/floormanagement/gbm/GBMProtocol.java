package eu.couch.hmi.floormanagement.gbm;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.couch.hmi.moves.FilteredMove;
import eu.couch.hmi.moves.FilteredMoveSet;
import eu.couch.hmi.moves.Move;

/**
 * The Group Behaviour Module (GBM) is Sorbonne's (Reshma's) module responsible for doing floormanagement once actors have selected their move.
 * This includes simulating floorbattles using (nonverbal) behaviours, selecting the actor who can take the floor, and generating listening behaviours once an actor is making a move
 * This collection of classes define the protocol of JSON message structures used to communicate with the GBM module
 * @author Daniel
 *
 */
public class GBMProtocol {

	public String cmd;

	//////////////////////////////////
	// Moves available protocol classes
	
/*
 * Example: 
{
  "cmd": "moves_available",
  "params": {
    "actors": {
      "Olivia": {
        "agentBMLID": "COUCH_CAMILLE",
        "agentType": "BOT",
        "moves": [
          {
            "target": "Bob",
            "moveID": "Propose",
    		"moveUID": "uid1",
            "opener": "How about we set you a goal of 100 steps?"
          },
          {
            "target": "Bob",
            "moveID": "Propose",
    		"moveUID": "uid2",
            "opener": "How about we set you a goal of 500 steps?"
          }
        ]
      },
      "Francois": {
        "agentBMLID": "COUCH_M_1",
        "agentType": "WOZ",
        "moves": [
          {
            "target": "Bob",
            "moveID": "Propose",
    		"moveUID": "uid3",
            "opener": "How about we set you a goal of 100 steps?"
          }
        ]
      },
      "Bob": {
        "agentBMLID": "",
        "agentType": "USER",
        "moves": [
          {
            "target": "Olivia",
            "moveID": "Question",
    		"moveUID": "uid4",
            "opener": "How are you doing?"
          }
        ]
      }
    }
  }
}
 */
	public static class MovesAvailable extends GBMProtocol {
		public ActorMovesAvailableParams params;
	}
	
	public static class ActorMovesAvailableParams {
		public Map<String, ActorMoves> actors;
		
		public ActorMovesAvailableParams(Map<String, ActorMoves> actors) {
			this.actors = actors;
		}
		
		public ActorMovesAvailableParams() {
			this(new HashMap<String, ActorMoves>());
		}
		
		public void addNewActorMoves(String actorName, ActorMoves moves) {
			actors.put(actorName, moves);
		}
	}
	
	public static class ActorMoves {
		public String agentBMLID;
		public String agentType;
		public AvailableMove[] moves;
		
		public ActorMoves(String agentBMLID, String agentType, AvailableMove[] moves) {
			this.agentBMLID = agentBMLID;
			this.agentType = agentType;
			this.moves = moves;
		}
		
		public ActorMoves(String agentBMLID, String agentType) {
			this.agentBMLID = agentBMLID;
			this.agentType = agentType;
			moves = new AvailableMove[0];
		}
		
		public ActorMoves() {}
		
		public void addAvailableMove(AvailableMove move) {
			moves = Arrays.copyOf(moves, moves.length + 1);
			moves[moves.length - 1] = move;
		}
	}
	
	public static class AvailableMove {
		public String moveID;
		public String moveUID;
		public String target;
		public String opener;
		
		public AvailableMove(String moveID, String moveUID, String target, String opener) {
			this.moveID = moveID;
			this.moveUID = moveUID;
			this.target = target;
			this.opener = opener;
		}
		
		public AvailableMove() {}
	}
	
	//////////////////////////////////
	// Move request protocol classes
	/*
	 * Example:
{
  "cmd": "move_selected",
  "params": {
    "actorName": "Olivia",
    "targetID": "Bob",
    "moveID": "Propose",
    "moveUID": "uid1"
  }
}
	 */
	public static class MoveSelected extends GBMProtocol {
		public MoveSelectedParams params;
	}
	
	public static class MoveSelectedParams {
		public String actorName;
		public String targetID;
		public String moveID;
		public String moveUID;
		
		public MoveSelectedParams(String actorName, String targetID, String moveID, String moveUID) {
			this.actorName = actorName;
			this.targetID = targetID;
			this.moveID = moveID;
			this.moveUID = moveUID;
		}
		
		public MoveSelectedParams() {}
	}

	//////////////////////////////////
	// Move granted protocol classes
	/*
{
  "cmd": "move_granted",
  "params": {
    "actorName": "Olivia",
    "moveID": "Propose",
    "moveUID": "uid2",
    "BMLTemplate": "somefile.xml"
    }
  }
}
	 */
	public static class MoveGranted extends GBMProtocol {
		public MoveGrantedParams params;
	}
	
	public static class MoveGrantedParams {
		public String actorName;
		public String moveID;
		public String moveUID;
		public String BMLTemplate;
	}

}
