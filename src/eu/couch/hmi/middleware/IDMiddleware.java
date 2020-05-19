package eu.couch.hmi.middleware;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import hmi.flipper.environment.MiddlewareEnvironment;
import nl.utwente.hmi.middleware.Middleware;
import nl.utwente.hmi.middleware.MiddlewareListener;

/**
 * A wrapper that allows us to identify a middleware based on its ID
 * The registered listener gets the ID and the message on callback
 * @author Daniel
 *
 */
public class IDMiddleware implements Middleware, MiddlewareListener {

	private String id;
	private Middleware mw;
	private List<IDMiddlewareListener> listeners;

	public IDMiddleware(String id, Middleware mw, IDMiddlewareListener... listeners) {
		this.id = id;
		this.listeners = new ArrayList<IDMiddlewareListener>(Arrays.asList(listeners));
		this.mw = mw;
		mw.addListener(this);

		for(IDMiddlewareListener listener : listeners) {
			mw.addListener(listener);
		}
	}
	
	@Override
	public void receiveData(JsonNode jn) {
		for(IDMiddlewareListener listener : listeners) {
			listener.receiveDataFromMW(id, jn);
		}
	}
	
	public void sendData(JsonNode jn) {
		mw.sendData(jn);
	}
	
	public void sendDataRaw(String data) {
		mw.sendDataRaw(data);
	}

	public String getID() {
		return id;
	}

	@Override
	public void addListener(MiddlewareListener ml) {
		if(ml instanceof IDMiddlewareListener) {
			listeners.add((IDMiddlewareListener)ml);
		} else {
			mw.addListener(ml);
		}
	}
	
}
