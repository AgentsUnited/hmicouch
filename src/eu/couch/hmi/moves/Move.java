package eu.couch.hmi.moves;

import java.util.Map;

public class Move {
	// From DGEP move object
	public MoveReply reply;
	public String opener; //this is the surface text of UI button, and the value that will be written to SKB on complete
	public String moveID;
	public String target;
	public Map<String,MoveVarValue> vars;// if vars are defined for a specific move, they must be stored in the SKB on move complete
	
	//for the communication with the GBM, this is a unique (sequential) ID of the move
	//FIXME: this does not seem to be stored persistent throughout the whole lifecycle of a move... when we get it back from the UI it is null
	public String moveUID;
	
	public boolean requestUserInput;//to trigger a text input in UI
	public String userInput;// the text that was entered by the user in the UI
	
    //if set to a template name, this template will be found in a fixed location; if move is passed into intent for Greta, that fml template will be used
    public String fml_template="";
    //if set to a template name, this template will be found in a fixed location; if intent is passed into ASAP, that bml template will be used
	public String bml_template = "";
	//if set, and sent to ASAP, ASAP will attempt to load this URL in the in-game browser
    public String url="";//http://localhost:8161/admin
	public String deictic="";
	public int dialogueID;
	public String actorIdentifier;
	public String actorName;
	
	public Move(Move copy) {
		this.reply = copy.reply;
		this.opener = copy.opener;
		this.moveID = copy.moveID;
		this.moveUID = copy.moveUID;
		this.target = copy.target;
		this.vars = copy.vars;
		this.dialogueID = copy.dialogueID;
		this.actorIdentifier = copy.actorIdentifier;
		this.actorName = copy.actorName;
		this.requestUserInput = copy.requestUserInput;
		this.userInput = copy.userInput;
	}

	public Move() {}
}
