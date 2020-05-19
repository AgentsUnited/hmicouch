package eu.couch.hmi.intent;

import eu.couch.hmi.actor.DialogueActor;

// TODO: could be done better. THis is an intent that has been planned and now has a BML ID
public class PlannedIntent extends Intent {

	public String intentRealizerClass;
	public String requestId;
	
	public PlannedIntent(Intent i, String intentRealizerClass, /** the BML or FML id */ String requestId ) 
	{
		minimalMoveCompletionId=i.minimalMoveCompletionId;
		addressee=i.addressee;
		text=i.text;
		charId=i.charId;
		moveId=i.moveId;
		engine=i.engine;
		uniqueIntentID=i.uniqueIntentID;
		fml_template=i.fml_template;
		bml_template=i.bml_template;
		url=i.url;
		deictic=i.deictic;		
		this.intentRealizerClass=intentRealizerClass;
		this.requestId = requestId;
	}
	
	

}
