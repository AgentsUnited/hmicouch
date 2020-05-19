package eu.couch.hmi.dialogue;

import eu.couch.hmi.environments.DGEPEnvironment.DialogueStatus;

public interface IDialogueStatusListener {

	public void onDialogueStatusChange(String topic, DialogueStatus status);
	
}
