package eu.couch.hmi.intent.realizer.status;

public class IntentRequestResponseStatusPair {
	public String syncRef;
	public String status;
	
	public IntentRequestResponseStatusPair(String syncRef, String status) {
		this.syncRef = syncRef;
		this.status = status;
	}

	public IntentRequestResponseStatusPair() {}
}
