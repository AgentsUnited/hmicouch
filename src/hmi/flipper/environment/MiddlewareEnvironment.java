package hmi.flipper.environment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import eu.couch.hmi.environments.SKBEnvironment;
import eu.couch.hmi.middleware.IDMiddleware;
import eu.couch.hmi.middleware.IDMiddlewareListener;
import eu.couch.hmi.middleware.MultiMiddlewareManager;
import nl.utwente.hmi.middleware.Middleware;
import nl.utwente.hmi.middleware.MiddlewareListener;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;

/**
 * An extension of the base flipper environment with some middleware functionality already built-in
 * Has some useful functions for creating and managing multiple middleware instances that can be distributed over various multi middleware managers (mmm)
 * This base implementation uses sensible defaults if you just want to load 1 middleware ({@link #loadMiddlewares}) and use it as default ({@link #getMW})
 * (But if you want, this can also give you a 2-level access hierarchy for organising many different middlewares)
 * @author Daniel
 *
 */
public abstract class MiddlewareEnvironment extends BaseFlipperEnvironment implements IDMiddlewareListener {
	private static org.slf4j.Logger logger = LoggerFactory.getLogger(MiddlewareEnvironment.class.getName());

	public static final String MMM_DEFAULT = "default_mmm";
	protected Map<String, MultiMiddlewareManager> mmms;

	private String defaultMWID;
	

	public MiddlewareEnvironment() {
		super();
		this.mmms = new HashMap<String,MultiMiddlewareManager>();
	}
	
	/**
	 * Attempts to load the requiredMiddlewares from the properties given in params
	 * All instantiated middlewares will be stored in {@value MMM_DEFAULT}
	 * @param mmmID the MultiMiddlewareManager in which you want to store this middleware
	 * @param params the loader parameters for the various middlewares
	 * @param requiredMiddlewares a list of required middlewares
	 * @throws Exception when unable to initiate a middleware based on the supplied parameters
	 */
	protected void loadRequiredMiddlewares(String mmmID, JsonNode params, String... requiredMiddlewares) throws Exception {
		for(String mwID : requiredMiddlewares) {
			logger.info("Attempting to load required middleware from properties: {}", mwID);
			if(!params.has(mwID)) { throw new Exception(mwID+" middleware spec required in params");}
			
			String loaderClass = getGMLClass(params.get(mwID));
			Properties mwProperties = getGMLProperties(params.get(mwID));
			
			if (loaderClass == null || mwProperties == null) throw new Exception("Invalid "+mwID+" middleware spec in params");

			logger.info("Found required middleware \"{}\" storing in mmm \"{}\"", mwID, mmmID);
	        GenericMiddlewareLoader gmw = new GenericMiddlewareLoader(loaderClass, mwProperties);
	        Middleware mw = gmw.load();

		    addIDMiddleware(MMM_DEFAULT, mwID, mw, this);
		}
		
		if(requiredMiddlewares.length > 0) {
			defaultMWID = requiredMiddlewares[0];
		}
	}

	protected void loadRequiredMiddlewares(JsonNode params, String... requiredMiddlewares) throws Exception {
		loadRequiredMiddlewares(MMM_DEFAULT, params, requiredMiddlewares);
	}
	
	/**
	 * Tries to parse all provided params for any middleware that might be defined within
	 * It sets the default middleware returned by {@link #getMW()} to the first middleware it encounters.. Use {@link #setDefaultMWID(String)} to override later
	 * @param params 
	 * @return the total number of middlewares that have been loaded this way
	 */
	protected int loadMiddlewares(JsonNode params) {
		int nrMW = 0;
		if(params.isObject()) {
			Iterator<Entry<String, JsonNode>> i = ((ObjectNode)params).fields();
			while(i.hasNext()) {
				Entry<String, JsonNode> e = i.next();
				String mwID = e.getKey();
				String loaderClass = getGMLClass(e.getValue());
				Properties mwProperties = getGMLProperties(e.getValue());
				
				if (loaderClass == null || mwProperties == null) {
					continue;				
				}
				
				//yay we found one!
			    nrMW ++;
				
				GenericMiddlewareLoader gmw = new GenericMiddlewareLoader(loaderClass, mwProperties);
		        Middleware mw = gmw.load();

				logger.info("Found a middleware \"{}\" storing in mmm \"{}\"", mwID, MMM_DEFAULT);
			    addIDMiddleware(MMM_DEFAULT, mwID, mw, this);
			    
			    //store the ID of the first middleware found for easy default access later
			    if(nrMW == 1) {
			    	defaultMWID = mwID;
			    }
			    
			}
		}
		return nrMW;
	}

	/**
	 * Adds a middleware with a certain ID to a certain MultiMiddlewareManager 
	 * @param mmmID the multi middleware manager to use
	 * @param mwID the id of the middleware
	 * @param mw the actual middleware
	 * @param listener
	 */
	protected void addIDMiddleware(String mmmID, String mwID, Middleware mw, IDMiddlewareListener listener) {
		if(!mmms.containsKey(mmmID)) {
			mmms.put(mmmID, new MultiMiddlewareManager());
		}
		mmms.get(mmmID).addIDMiddleware(mwID, mw, listener);
	}

	protected void addIDMiddleware(String mwID, Middleware mw, IDMiddlewareListener listener) {
		addIDMiddleware(MMM_DEFAULT, mwID, mw, listener);
	}

	/**
	 * Get a middleware stored in a particular multi-middleware-manager
	 * @param mmmID the multi middleware manager to use
	 * @param mwID the id of the middleware
	 * @return
	 */
	protected IDMiddleware getMW(String mmmID, String mwID) {
		if(!mmms.containsKey(mmmID)) {
			logger.warn("Attempting to load MW \"{}\" from non-existent MMM \"{}\"", mwID, mmmID);
			return null;
		}
		if(mmms.get(mmmID).getMW(mwID) == null) {
			logger.warn("Attempting to load non-existent MW \"{}\" from MMM \"{}\"", mwID, mmmID);
		}
		return mmms.get(mmmID).getMW(mwID);
	}

	protected IDMiddleware getMW(String mwID) {
		return getMW(MMM_DEFAULT, mwID);
	}
	
	/**
	 * Returns the default middleware
	 * @return the default middleware
	 */
	protected IDMiddleware getMW() {
		return getMW(MMM_DEFAULT, defaultMWID);
	}

	public String getDefaultMWID() {
		return defaultMWID;
	}

	public void setDefaultMWID(String defaultMWID) {
		this.defaultMWID = defaultMWID;
	}
	
	@Override
	public void receiveData(JsonNode jn) {
		//use this if you're not interested in where a message comes from
	}
	
	@Override
	public void receiveDataFromMW(String src, JsonNode jn) {
		//use this if you want to know where an incoming message is from
	}
	
}
