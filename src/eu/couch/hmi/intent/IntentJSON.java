package eu.couch.hmi.intent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


/* TODO: just imitating what was implemented in flipper before
return {
"engine":"ASAP",
"bmlTopic":"ASAP",
"charId":"COUCH_M_1",
"moveId":"move25", 
"intent": {             
    "parameters": {
        "minimalMoveCompletionId":"move25",
        "text":"Hello, this is a test",      
        "addressee":"COUCH_M_2"
    }
}
};
*/
public class IntentJSON {
	
	public String engine;
	public String charId;
	public String moveId;
	public IntentBody intent;
	
	public IntentJSON(Intent intent) {
		this.engine = intent.engine;
		this.charId = intent.charId;
		this.moveId = intent.moveId;
		this.intent = new IntentBody(intent);
	}
	
	public static JsonNode FromIntent(Intent intent) {
		ObjectMapper mapper = new ObjectMapper(); 
		JsonNode res = mapper.convertValue(new IntentJSON(intent), JsonNode.class);
		return res;
	}
}

class IntentBody {
	public IntentParams parameters;
	
	public IntentBody(Intent intent) {
		this.parameters = new IntentParams(intent);
	}
}

class IntentParams {
	public String text;
	public String addressee;
	
	public IntentParams(Intent intent) {
		if (intent.addressee == null) {
			this.addressee = "";
		} else this.addressee = intent.addressee;
		this.text = intent.text;
	}
}
