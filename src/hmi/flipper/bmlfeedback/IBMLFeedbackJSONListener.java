package hmi.flipper.bmlfeedback;

public interface IBMLFeedbackJSONListener {
	
	public void onBlockProgressFeedback(BMLBlockProgressFeedbackJSON fb);
	public void onSyncPointProgressFeedback(BMLSyncPointProgressFeedbackJSON fb);
	public void onPredictionFeedback(BMLPredictionFeedbackJSON fb);
	public void onWarningFeedback(BMLWarningFeedbackJSON fb);

}
