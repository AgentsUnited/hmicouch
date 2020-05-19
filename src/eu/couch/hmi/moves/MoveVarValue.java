package eu.couch.hmi.moves;

/**
 * JSON serialisation class for the value of variables attached to DGEP moves, which should be stored in SKB on move completed.
 * Each variable may be flagged with "append", which means it's value should be appended to the list of previous values already stored in this variable
 * @author Daniel
 *
 */
public class MoveVarValue {

	public boolean append;
	
	//TODO: if necessary, we could think about making the value a type JsonNode instead, allowing us to store whatever we want... 
	//however, this also opens up the possibility for people to start abusing some nasty variables-in-variables antipatterns, which can quickly become complex 
	public String value;
	
	public MoveVarValue() {}
	
	public MoveVarValue(boolean append, String value) {
		this.append = append;
		this.value = value;
	}
	
}
