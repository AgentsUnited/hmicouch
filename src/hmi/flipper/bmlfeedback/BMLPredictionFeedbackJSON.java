package hmi.flipper.bmlfeedback;

import asap.bml.ext.bmla.feedback.BMLAPredictionFeedback;
import saiba.bml.feedback.BMLFeedback;
import saiba.bml.feedback.BMLPredictionFeedback;

public class BMLPredictionFeedbackJSON extends BMLFeedbackJSON {

	public String characterId;
	public String namespace;
	public BMLBehaviourJSON[] bmlBehaviourPredictions;
	public BMLBlockPredictionFeedbackJSON[] bmlBlockPredictions;
	
	public BMLPredictionFeedbackJSON(BMLPredictionFeedback fb) {
		super((BMLFeedback) fb, fb.getXMLTag());
		if (fb instanceof BMLAPredictionFeedback) {
			BMLAPredictionFeedback fba = (BMLAPredictionFeedback) fb;
			this.characterId = fba.getCharacterId();
			this.namespace = fba.getNamespace();
			this.bmlBlockPredictions = BMLBlockPredictionFeedbackJSON.FromListBMLA(fba.getBMLABlockPredictions());
			this.bmlBehaviourPredictions = BMLBehaviourJSON.FromList(fba.getBmlBehaviorPredictions());
		} else {
			this.characterId = fb.getCharacterId();
			this.namespace = fb.getNamespace();
			this.bmlBlockPredictions = BMLBlockPredictionFeedbackJSON.FromList(fb.getBmlBlockPredictions());
			this.bmlBehaviourPredictions = BMLBehaviourJSON.FromList(fb.getBmlBehaviorPredictions());
		}
	}
}





