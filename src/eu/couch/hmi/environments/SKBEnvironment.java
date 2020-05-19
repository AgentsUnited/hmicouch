package eu.couch.hmi.environments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import eu.couch.hmi.middleware.IDMiddlewareListener;
import hmi.flipper.ExperimentFileLogger;
import hmi.flipper.environment.BaseFlipperEnvironment;
import hmi.flipper.environment.MiddlewareEnvironment;
import hmi.flipper.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper.environment.IFlipperEnvironment;
import nl.utwente.hmi.middleware.Middleware;
import nl.utwente.hmi.middleware.MiddlewareListener;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;

public class SKBEnvironment extends MiddlewareEnvironment {
	private static org.slf4j.Logger logger = LoggerFactory.getLogger(SKBEnvironment.class.getName());

	private ObjectMapper om;

	private AuthEnvironment authEnv;

	//the maximum amount of time to wait for a correct response when doing a blocking call to the SKB
	private static final long BLOCKING_TIMEOUT = 5000;
	
	//we use a busy loop to block execution and check every now and then if there is a correct response from the SKB, this is the time that we sleep in each iteration of the loop
	private static final long RESPONSE_CHECK_SLEEP_TIME = 25;
	
	//the most recent response from the SKB is stored here
	private JsonNode requestVarsResponse;

	/** A list of middlewares that are required for this environment */
	private String[] requiredMiddlewares = new String[] {"set-variables","get-variables"};
	
	private List<SKBListener> skbListeners = new ArrayList<SKBListener>();

	
	@Override
	public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws Exception {
		switch (fenvmsg.cmd) {
		case "set-variables":
			storeVariables(fenvmsg.params);
			break;
		case "get-variables":
			List<String> vars = new ArrayList<String>();
			//assume params is a JSON array with var names
			for(JsonNode v : fenvmsg.params) {
				vars.add(v.asText());
			}
			requestVariables(vars);
			break;
		default:
			logger.warn("Unhandled message: "+fenvmsg.cmd);
			break;
		}
		return null;
	}
	
	/**
	 * Builds the required format for requesting variables from the SKB, each variable separated by a space and put in a node called names: {"names":"var1 var2 var3"}
	 * @param variables the list of variables to retrieve
	 * @return a JsonNode of the correct format for requesting from SKB
	 */
	private JsonNode makeVariablesRequest(List<String> variables) {
		return om.createObjectNode().put("names", String.join(" ", variables));
	}

	/**
	 * Requests variables from the SKB asynchronously. Responses will be published to all registered SKBListeners and on the flipper env topic "get-variables"
	 * @param variables a JSON object with a field "names" with space-separated variables names: {"names":"variable1 variable2"}
	 */
	public void requestVariables(List<String> variables) {
		JsonNode varsRequest = makeVariablesRequest(variables);
		logger.debug("Requesting variables from SKB: {}", varsRequest.toString());
		getMW("get-variables").sendData(varsRequest);
	}
	
	/**
	 * Do a synchronous request to the SKB. This method is blocking until a response is received from the SKB, or the BLOCKING_TIMEOUT is reached
	 * CAUTION: This will block the execution of the dialogue if called on the main thread!
	 * @param variables the variables to request
	 * @return the variables obtained from the SKB, or null if no response is received before timeout
	 */
	public synchronized JsonNode requestVariablesBlocking(List<String> variables) {
		JsonNode varsRequest = makeVariablesRequest(variables);
		
		logger.debug("Doing a blocking request for variables {}", variables.toString());
		
		requestVarsResponse = null; //this will be set in receiveDataFromMW() as soon as we get a response from the SKB
		long requestTime = System.currentTimeMillis();

		//TODO: maybe try to request multiple times (make it an optional parameter?) if there is no response before timeout..?
		getMW("get-variables").sendData(varsRequest);
		
		while(requestVarsResponse == null) {
			if(System.currentTimeMillis() > requestTime + BLOCKING_TIMEOUT) {
				logger.warn("Timeout when doing blocking request for variables from SKB {}", variables.toString());
				//TODO: maybe throwing an exception would be nicer here, this is potentially a nullpointer waiting to happen
				return null;
			}
			
			try {
				Thread.sleep(RESPONSE_CHECK_SLEEP_TIME);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			//these are not the variables you are looking for
			if(requestVarsResponse != null && !isRequestedVars(variables, requestVarsResponse)) {
				logger.warn("Got incorrect variables when doing blocking request for SKB requested: {} received: {} --> ignore and continue waiting for response", variables.toString(), requestVarsResponse.toString());
				requestVarsResponse = null;
			}
			
		}
		
		logger.debug("Got the requested vars from the SKB: {}", requestVarsResponse.toString());
		
		return requestVarsResponse;
	}
	
	/**
	 * Checks whether the given response variables matches the requested variables
	 * @param requestVars
	 * @param responseVars
	 */
	private boolean isRequestedVars(List<String> requestVars, JsonNode responseVars) {
		Set<String> req = new HashSet<String>(requestVars);
		Set<String> resp = new HashSet<String>();

		for(Iterator<String> it = responseVars.fieldNames(); it.hasNext(); ) {
			resp.add(it.next());
		}
		
		return req.equals(resp);
	}
	
	/**
	 * Stores variables in the SKB asynchronously.
	 * @param variables the variables to store, a JSON object with key-value pairs: {"key1":"val1","key2":"val2"}
	 */
	public void storeVariables(JsonNode variables) {
		logger.debug("Storing variables in SKB: {}", variables.toString());
		getMW("set-variables").sendData(variables);
	}
	
	/**
	 * Makes a synchronous call to store the supplied variables in the SKB. This method is blocking until we know for sure that the values have been updated, or the BLOCKING_TIMEOUT is reached.
	 * CAUTION: This will block the execution of the dialogue if called on the main thread!
	 * @param variables the variables to store, a JSON object with key-value pairs: {"key1":"val1","key2":"val2"}
	 * @return true if success or false if there was a timeout
	 */
	public synchronized boolean storeVariablesBlocking(JsonNode variables) {
		long storeTime = System.currentTimeMillis();
		
		logger.debug("Doing a blocking call to store variables in SKB: {}", variables.toString());

		//TODO: maybe do multiple attempts (make it an optional parameter?) to store the variables when they are still not set correctly after timeout..?
		getMW("set-variables").sendData(variables);
		
		List<String> varNames = new ArrayList<String>();
		for(Iterator<String> it = variables.fieldNames(); it.hasNext(); ) {
			varNames.add(it.next());
		}
		
		JsonNode storedVars = null;
		
		while(!variables.equals(storedVars)) {
			if(System.currentTimeMillis() > storeTime + BLOCKING_TIMEOUT) {
				logger.warn("Timeout when doing blocking call to store variables in SKB {}", variables.toString());
				//TODO: maybe throwing an exception would be nicer here
				return false;
			}
			
			try {
				Thread.sleep(RESPONSE_CHECK_SLEEP_TIME);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			storedVars = requestVariablesBlocking(varNames);
		}
		
		logger.debug("Variables have been successfully stored in SKB: {}", storedVars.toString());
		
		return variables.equals(storedVars);
	}
	
	
	@Override
	public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {
		for(IFlipperEnvironment e : envs) {
			if(e instanceof AuthEnvironment) {
				this.authEnv = (AuthEnvironment)e;
				authEnv.waitForAuthentication(-1);
			} else {
				logger.warn("{} doesn't need environment: {}", this.getClass().getCanonicalName(), e.getId());
			}
		}
		
		if(authEnv == null) {
			logger.error("{} needs environment: {}", this.getClass().getCanonicalName(), "AuthEnvironment");
		}
	}

	@Override
	public void init(JsonNode params) throws Exception {
		logger.debug("Creating new instance of SKBEnvironment");
		
		om = new ObjectMapper();

		//this is a bit of a hack to inject these properties hardcoded at init time.. 
		//but it means we can access the rest of the HTTPRequest middleware transparently as if it was a regular middleware, just by sending it JSON stuff as usual
		//(otherwise we would need to set the auth token in each individual message header) 
		//TODO: if we ever lose authentication during a session we will need to recreate our middlewares with an updated X-Auth-Token header
		for(String reqMW : requiredMiddlewares) {
			if(params.has(reqMW)) {
				ObjectNode props = (ObjectNode)params.get(reqMW).get("properties");
				props.put("HTTPRequestHeader_X-Auth-Token", authEnv.getAuthToken());
				((ObjectNode)params.get(reqMW)).set("properties", props);
			} else {
				logger.error("Missing required middleware: {}", reqMW);
			}
		}

		//load the required middlewares (these will then become accessible through the mwLUT object
		loadRequiredMiddlewares(params, requiredMiddlewares);
	}

	@Override
	public void receiveDataFromMW(String src, JsonNode jn) {
		switch(src) {
			case "set-variables":
				if(jn != null && !jn.isNull() && jn.size() > 0) {
					logger.warn("Got a response from SKB when setting variables (this should normally not happen): {}", jn.toString());
				}
				break;
			case "get-variables":
				jn = jn.path("response");
				
				logger.debug("Got variables from SKB: {}", jn.toString());
				enqueueMessage(jn, "get-variables");
				
				for(SKBListener skbListener : skbListeners) {
					skbListener.retrievedVariables(jn);
				}
				
				requestVarsResponse = jn;
				break;
			default:
				logger.warn("Received unhandled message from middleware {}: {}", src, jn.toString());
				break;
		}
	}

	public void registerSKBListener(SKBListener skbListener) {
		this.skbListeners.add(skbListener);
	}

	/** Callback interface for variables retrieved from the SKB */
	public interface SKBListener {
		/**
		 * Called when we have succesfully retrieved variables from the SKB
		 * @param variables whatever was retrieved
		 */
		void retrievedVariables(JsonNode variables);
	}
}


