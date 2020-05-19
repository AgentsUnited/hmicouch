package hmi.flipper.bmlfeedback;

import java.util.ArrayList;
import java.util.List;

import asap.bml.ext.bmla.feedback.BMLABlockPredictionFeedback;
import saiba.bml.feedback.BMLBlockPredictionFeedback;
import saiba.bml.feedback.BMLFeedback;

public class BMLBlockPredictionFeedbackJSON extends BMLFeedbackJSON {
	
	public String id;
	public double globalStart;
	public double globalEnd;
	public String namespace;
	public String status;
	public long posixEndTime;
	public long posixStartTime;
	
	// TODO: bmla:posixStartTime, bmla:posixEndTime, bmla:status
	// TODO: get the bml content(!)
	
	public BMLBlockPredictionFeedbackJSON(BMLBlockPredictionFeedback fb) {
		super((BMLFeedback) fb, fb.getXMLTag());
		this.id = fb.getId();
		this.namespace = fb.getNamespace();
		this.globalStart = fb.getGlobalStart();
		this.globalEnd = fb.getGlobalEnd();
		if (fb instanceof BMLABlockPredictionFeedback) {
			BMLABlockPredictionFeedback fba = (BMLABlockPredictionFeedback) fb;
			this.status = fba.getStatus().name();
			this.posixEndTime = fba.getPosixEndTime();
			this.posixStartTime = fba.getPosixStartTime();
		} else {
			this.status = "";
			this.posixEndTime = -1;
			this.posixStartTime = -1; 
		}
	}
	
	public static BMLBlockPredictionFeedbackJSON[] FromList(List<BMLBlockPredictionFeedback> bl) {
		List<BMLBlockPredictionFeedbackJSON> res = new ArrayList<BMLBlockPredictionFeedbackJSON>();
		for (BMLBlockPredictionFeedback b : bl) {
			res.add(new BMLBlockPredictionFeedbackJSON(b));
		}
		return res.toArray(new BMLBlockPredictionFeedbackJSON[0]);
	}
	
	public static BMLBlockPredictionFeedbackJSON[] FromListBMLA(List<BMLABlockPredictionFeedback> bl) {
		List<BMLBlockPredictionFeedbackJSON> res = new ArrayList<BMLBlockPredictionFeedbackJSON>();
		for (BMLABlockPredictionFeedback b : bl) {
			res.add(new BMLBlockPredictionFeedbackJSON(b));
		}
		return res.toArray(new BMLBlockPredictionFeedbackJSON[0]);
	}
}