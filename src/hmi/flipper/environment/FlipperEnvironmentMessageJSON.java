package hmi.flipper.environment;

import com.fasterxml.jackson.databind.JsonNode;

public class FlipperEnvironmentMessageJSON {
	
	public String cmd;
	public String environment;
	public String msgId;

	public JsonNode params;

	public FlipperEnvironmentMessageJSON(String cmd, String environment, String msgId, JsonNode params) {
		this.cmd = cmd;
		this.environment = environment;
		this.msgId = msgId;
		this.params = params;
	}
	
	public FlipperEnvironmentMessageJSON() {}
}
