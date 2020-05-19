package eu.couch.hmi.middleware;

import java.util.HashMap;
import java.util.Map;

import nl.utwente.hmi.middleware.Middleware;

/** A LookUpTable that uses a Map<String, IDMiddleware> to store and retrieve middlewares based on ID */
public class MultiMiddlewareManager {

	private Map<String, IDMiddleware> mwLUT = new HashMap<String, IDMiddleware>();
	
	public void addIDMiddleware(IDMiddleware mw) {
		mwLUT.put(mw.getID(), mw);
	}
	
	public void addIDMiddleware(String mwID, Middleware mw, IDMiddlewareListener listener) {
		addIDMiddleware(new IDMiddleware(mwID, mw, listener));
	}
	
	public IDMiddleware getMW(String mwID){
		return mwLUT.get(mwID);
	}
	
}
