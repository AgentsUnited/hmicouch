package eu.couch.hmi.environments;

import java.util.Properties;
import java.util.concurrent.BlockingQueue;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import eu.couch.hmi.middleware.IDMiddleware;
import eu.couch.hmi.middleware.IDMiddlewareListener;
import hmi.flipper.ExperimentFileLogger;
import hmi.flipper.environment.BaseFlipperEnvironment;
import hmi.flipper.environment.MiddlewareEnvironment;
import hmi.flipper.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper.environment.IFlipperEnvironment;
import nl.utwente.hmi.middleware.MiddlewareListener;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;

public class CCEEnvironment extends MiddlewareEnvironment {
	private static org.slf4j.Logger logger = LoggerFactory.getLogger(CCEEnvironment.class.getName());

	private ObjectMapper om;

	private AuthEnvironment authEnv;

	/** A list of middlewares that are required for this environment */
	private String[] requiredMiddlewares = new String[] {"get-new-topic"};

	
	@Override
	public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws Exception {
		switch (fenvmsg.cmd) {
		case "get-new-topic":
			//this request should be a GET without any query params, so just send an empty JSON node :)
			getMW("get-new-topic").sendData(om.createObjectNode().put("user", authEnv.getUsername()));
			break;
		default:
			logger.warn("Unhandled message: "+fenvmsg.cmd);
			break;
		}
		return null;
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
		logger.debug("Creating new instance of CCEEnvironment");
		
		om = new ObjectMapper();

		//this is a bit of a hack to inject these properties hardcoded at init time.. 
		//but it means we can access the rest of the HTTPRequest middleware transparently as if it was a regular middleware, just by sending it JSON stuff as usual
		//(otherwise we would need to set the auth token in each individual message header) 
		//TODO: if we ever lose authentication during a session we will need to recreate our middlewares with an updated X-Auth-Token header
		//TODO: if the CCE is succesfully separated from other Roessingh services (Tessa is working on this) then we might have to change this process... might not even need the authtoken
		for(String reqMW : requiredMiddlewares) {
			if(params.has(reqMW)) {
				ObjectNode props = (ObjectNode)params.get(reqMW).get("properties");
				props.put("HTTPRequestHeader_X-Auth-Token", authEnv.getAuthToken());
				((ObjectNode)params.get(reqMW)).set("properties", props);
			} else {
				logger.error("Missing required middleware: {}", reqMW);
			}
		}
		
		//load the required middlewares (these will then become acsessible through the mwLUT object
		loadRequiredMiddlewares(params, requiredMiddlewares);
	}

	@Override
	public void receiveDataFromMW(String src, JsonNode jn) {
		switch(src) {
		case "get-new-topic":
			logger.info("Got new topic from CCE: {}", jn.toString());
			enqueueMessage(jn, "init_topic");
			break;
		}
	}

	
}

