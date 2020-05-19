package hmi.flipper.bmlfeedback;

import saiba.bml.feedback.BMLFeedback;
import saiba.bml.feedback.BMLWarningFeedback;

public class BMLWarningFeedbackJSON extends BMLFeedbackJSON {

	public String characterId;
	public String id;
	public String description;
	public String namespace;
	public String type;

	public BMLWarningFeedbackJSON(BMLWarningFeedback fb) {
		super((BMLFeedback) fb, fb.getXMLTag());
		this.characterId = fb.getCharacterId();
		this.id = fb.getId();
		this.description = fb.getDescription();
		this.namespace = fb.getNamespace();
		this.type = fb.getType();
	}
}
