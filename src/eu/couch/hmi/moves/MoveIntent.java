package eu.couch.hmi.moves;

import eu.couch.hmi.actor.DialogueActor;
import eu.couch.hmi.intent.Intent;

public class MoveIntent extends Intent {
	
	private Move move;
	
	public MoveIntent(DialogueActor a, Move move) {
		super(a);
		this.move = move;
		this.addressee = move.target; // TODO: where to translate to bml_name?
		this.moveId = move.moveID;
		this.text = move.opener;
		this.fml_template = move.fml_template;
		this.bml_template = move.bml_template;
		this.url = move.url;
		this.deictic = move.deictic;
	}
	
	public Move getMove() {
		return this.move;
	}
}
