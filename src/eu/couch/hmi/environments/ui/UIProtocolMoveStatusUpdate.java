package eu.couch.hmi.environments.ui;


public class UIProtocolMoveStatusUpdate extends UIProtocolResponse {
	public String actorIdentifier;
	public String moveId;
	public String status;
	
	public UIProtocolMoveStatusUpdate(String actorIdentifier, String moveId, String status) {
		this.actorIdentifier = actorIdentifier;
		this.moveId = moveId;
		this.status = status;
		this.cmd = "move_status";
	}
}