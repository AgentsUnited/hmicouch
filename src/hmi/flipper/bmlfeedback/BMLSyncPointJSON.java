package hmi.flipper.bmlfeedback;

import java.util.ArrayList;
import java.util.List;

import saiba.bml.parser.SyncPoint;

public class BMLSyncPointJSON {
	
	//public String behaviourId;
	public String name;
	public double offset;
	public double refOffset;
	public String refstring;
	public String constraint;
	public String afterConstraint;
	public int iteration;
	
	public BMLSyncPointJSON(SyncPoint sp) {
		//this.behaviourId = sp.getBehaviourId();
		if (sp.getAfterConstraint() != null) {
			this.afterConstraint = sp.getAfterConstraint().toString();
		}
		if (sp.getConstraint() != null) {
			String res = "";
			for (SyncPoint csp : sp.getConstraint().getTargets()) {
				// TODO: ...
				//res += ":"+csp.getName();
				res += ":"+csp.toString();
			}
			this.constraint = res;
		}
		this.name = sp.getName();
		this.offset = sp.getOffset();
		if (sp.getRef() != null) {
			this.refOffset = sp.getRef().offset;
		}
		this.refstring = sp.getRefString();
		this.iteration = sp.getIteration();
	}
	
	public static BMLSyncPointJSON[] FromList(List<SyncPoint> sl) {
		List<BMLSyncPointJSON> res = new ArrayList<BMLSyncPointJSON>();
		for (SyncPoint s : sl) {
			res.add(new BMLSyncPointJSON(s));
		}
		return res.toArray(new BMLSyncPointJSON[0]);
	}
}
