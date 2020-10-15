package eu.couch.hmi.intent;

import eu.couch.hmi.actor.DialogueActor;

// TODO: this is a placeholder (based on the flipper JSON object)
public class Intent {

	public String minimalMoveCompletionId;
	public String addressee;
	public String text;
	public String charId;
	public String moveId;
	public String engine;
	public String uniqueIntentID;
    
    //if set to a template name, this template will be found in a fixed location; if intent is passed into Greta, that fml template will be used
	public String fml_template = "";
    //if set to a template name, this template will be found in a fixed location; if intent is passed into ASAP, that bml template will be used
	public String bml_template = "";
	//if set, and sent to ASAP, ASAP will attempt to load this URL in the in-game browser
    public String url="";
    public String deictic="";
	
	public Intent() {}
	
	
	// TODO: should we store the actor in the Intent object?
	public Intent(DialogueActor a) {
		this.charId = a.bml_name;
		this.engine = a.engine;
	}
	
	public Intent copy() {
		Intent copy = new Intent();
		copy.minimalMoveCompletionId = minimalMoveCompletionId;
		copy.addressee = addressee;
		copy.text = text;
		copy.charId = charId;
		copy.moveId = moveId;
		copy.engine = engine;
		copy.uniqueIntentID = uniqueIntentID;
		copy.fml_template = fml_template;
		copy.bml_template = bml_template;
		copy.url = url;
		copy.deictic = deictic;
		
		return copy;
	}

}
