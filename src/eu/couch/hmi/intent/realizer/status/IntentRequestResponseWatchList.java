package eu.couch.hmi.intent.realizer.status;

import eu.couch.hmi.intent.realizer.status.IntentRequestResponseStatusPair;

public class IntentRequestResponseWatchList {
	public IntentRequestResponseStatusPair[] watchrefs;
	
	public IntentRequestResponseWatchList(IntentRequestResponseStatusPair[] watchrefs) {
		this.watchrefs = watchrefs;
	}
	public IntentRequestResponseWatchList() {}
}
