package eu.couch.hmi.middleware;

import com.fasterxml.jackson.databind.JsonNode;

import nl.utwente.hmi.middleware.MiddlewareListener;

/**
 * Extended listener.
 * The callback function includes the middleware source ID, this makes it possible to listen to multiple middlewares in one place and distinguish between what is returned
 * @author Daniel
 *
 */
public interface IDMiddlewareListener extends MiddlewareListener {
	public void receiveDataFromMW(String src, JsonNode jn);	
}
