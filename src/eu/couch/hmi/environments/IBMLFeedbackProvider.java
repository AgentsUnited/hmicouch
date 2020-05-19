package eu.couch.hmi.environments;

import hmi.flipper.bmlfeedback.IBMLFeedbackJSONListener;

public interface IBMLFeedbackProvider {
	public void registerBMLFeedbackJSONListener(IBMLFeedbackJSONListener l);
	/** charId can be empty / null; if set, some realisers may use it eg because they cannot read the characterId in the BML, such as with Greta */
	public void performRealizationRequest(String bml, String charId);
}
