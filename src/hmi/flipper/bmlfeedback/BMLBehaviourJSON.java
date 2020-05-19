package hmi.flipper.bmlfeedback;

import java.util.ArrayList;
import java.util.List;

import saiba.bml.core.Behaviour;
import saiba.bml.core.GazeBehaviour;
import saiba.bml.core.GazeShiftBehaviour;
import saiba.bml.core.GestureBehaviour;
import saiba.bml.core.PostureShiftBehaviour;
import saiba.bml.core.SpeechBehaviour;

public class BMLBehaviourJSON {
	
	//public String namespace;
	public String behaviourType;
	public String bmlId;
	public String behaviourId;
	public BMLSyncPointJSON[] syncPoints;
	
	// TODO: make this a generic JsonNode with more data...
	public String content;
	
	public BMLBehaviourJSON(Behaviour b) {
		this.bmlId = b.getBmlId();
		//this.namespace = b.getNamespace();
		this.syncPoints = BMLSyncPointJSON.FromList(b.getSyncPoints());
		this.behaviourType = b.getXMLTag();//"UNKNOWN";
		this.behaviourId = b.id;
		
		if (b instanceof SpeechBehaviour) {
			SpeechBehaviour sb = (SpeechBehaviour) b;
			this.content = sb.getContent().replaceAll("<[^>]+>", "").trim();
		} else if (b instanceof PostureShiftBehaviour) {
			PostureShiftBehaviour psb = (PostureShiftBehaviour) b;
			this.content = ""; // can't we read the pose lexeme here?
		} else if (b instanceof GazeBehaviour) {
            GazeBehaviour gb = (GazeBehaviour) b;
            this.content = gb.getStringParameterValue("target");
            String offsetAngle  = gb.getStringParameterValue("offsetAngle");
            if (offsetAngle!=null && Float.parseFloat(offsetAngle) > 0) {
            	this.content = "elsewhere";
            }
		} else if (b instanceof GazeShiftBehaviour) {
			GazeShiftBehaviour gsb = (GazeShiftBehaviour) b;
			// ...
        } else if (b instanceof GestureBehaviour) {
        	GestureBehaviour gb = (GestureBehaviour) b;
        	this.content = gb.getStringParameterValue("lexeme");
        } else {
			this.content = "";
		}
	}
	
	public static BMLBehaviourJSON[] FromList(List<Behaviour> bl) {
		List<BMLBehaviourJSON> res = new ArrayList<BMLBehaviourJSON>();
		for (Behaviour b : bl) {
			res.add(new BMLBehaviourJSON(b));
		}
		return res.toArray(new BMLBehaviourJSON[0]);
	}
}
