package hmi.flipper.bmlfeedback;

import saiba.bml.feedback.BMLBlockProgressFeedback;
import saiba.bml.feedback.BMLFeedback;

public class BMLBlockProgressFeedbackJSON extends BMLFeedbackJSON {

	public String characterId;
	public String bmlId;
	public double globalTime;
	public String namespace;
	public String syncId;

	public String flipperSyncId;
	
	// TODO: bmla:posixTime, bmla:status
	
	public BMLBlockProgressFeedbackJSON(BMLBlockProgressFeedback fb) {
		super((BMLFeedback) fb, fb.getXMLTag());
		this.characterId = fb.getCharacterId();
		this.globalTime = fb.getGlobalTime();
		this.bmlId = fb.getBmlId();
		this.namespace = fb.getNamespace();
		this.syncId = fb.getSyncId();
        this.flipperSyncId = this.characterId+"___"+this.bmlId+"___"+this.syncId;
	}

	public BMLBlockProgressFeedbackJSON() {
		super("blockProgress");
	}
}
