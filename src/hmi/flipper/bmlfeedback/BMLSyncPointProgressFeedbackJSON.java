package hmi.flipper.bmlfeedback;

import saiba.bml.feedback.BMLFeedback;
import saiba.bml.feedback.BMLSyncPointProgressFeedback;

public class BMLSyncPointProgressFeedbackJSON extends BMLFeedbackJSON {
	
	public String characterId;
	public String behaviorId;
	public String bmlId;
	public double globalTime;
	public String namespace;
	public String syncId;
	public String syncRef;
	public double time;
	
	public String flipperSyncId;
	
	// TODO: bmla:posixTime
	
	public BMLSyncPointProgressFeedbackJSON(BMLSyncPointProgressFeedback fb) {
		super((BMLFeedback) fb, fb.getXMLTag());
		this.characterId = fb.getCharacterId();
		this.behaviorId = fb.getBehaviourId();
		this.bmlId = fb.getBMLId();
		this.globalTime = fb.getGlobalTime();
		this.namespace = fb.getNamespace();
		this.syncId = fb.getSyncId();
		this.syncRef = fb.getSyncRef().toString(this.bmlId);
		this.time = fb.getTime();
		this.flipperSyncId = this.characterId + "___" + this.bmlId + "___" + this.behaviorId + "___" + this.syncId;
	}
}