package hmi.flipper.bmlfeedback;

import saiba.bml.feedback.BMLFeedback;

public class BMLFeedbackJSON {
	public String bmlFeedbackType;
	
	public BMLFeedbackJSON(String type) {
		this.bmlFeedbackType = type;
	}
	
	public BMLFeedbackJSON(BMLFeedback fb, String type) {
		this.bmlFeedbackType = type;
	}
}