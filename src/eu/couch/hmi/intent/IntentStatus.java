package eu.couch.hmi.intent;


public enum IntentStatus {
	INTENT_PLANNED,
	INTENT_REALIZATION_STARTED,
	INTENT_REALIZATION_COMPLETED,
	INTENT_SUCCESS,
	INTENT_FAILED,
	INTENT_CANCELLED;
	
	public static String nameList() {
		String nameList = "";
		for (IntentStatus s : IntentStatus.values()) {
			nameList += s.name() + " ";
		}
		return nameList;
	}

}
