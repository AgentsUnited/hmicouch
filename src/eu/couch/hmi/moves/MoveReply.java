package eu.couch.hmi.moves;

import com.fasterxml.jackson.annotation.JsonInclude;

// From DGEP move object
public class MoveReply {
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public String p;
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public String q;
	public MoveReply() {}
}
