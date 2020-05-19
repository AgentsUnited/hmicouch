package hmi.flipper.environment;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;


public abstract class BaseFlipperEnvironment implements IFlipperEnvironment {

    private static org.slf4j.Logger logger = LoggerFactory.getLogger(BaseFlipperEnvironment.class.getName());

	private String id;
    protected BlockingQueue<FlipperEnvironmentMessageJSON> outQueue = null;
	
    public BaseFlipperEnvironment() {
    	outQueue = new LinkedBlockingQueue<FlipperEnvironmentMessageJSON>(128);
    }
    
	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public boolean hasMessage() {
		return !outQueue.isEmpty();
	}

	@Override
	public FlipperEnvironmentMessageJSON getMessage() throws InterruptedException {
		FlipperEnvironmentMessageJSON message = outQueue.take();
		return message;
	}
	
	protected FlipperEnvironmentMessageJSON buildResponse(FlipperEnvironmentMessageJSON responseTo, JsonNode params) {
		FlipperEnvironmentMessageJSON msg = new FlipperEnvironmentMessageJSON(responseTo.cmd, responseTo.environment, responseTo.msgId, params);
		return msg;
	}

	protected void enqueueMessage(FlipperEnvironmentMessageJSON msg) {
		if (outQueue.remainingCapacity() < 1) {
			outQueue.poll();
			logger.warn("OutQueue is full, dropping oldest message.");
		}
		outQueue.add(msg);
	}
	
	protected void enqueueMessage(JsonNode params, String cmd, String msgId) {
		FlipperEnvironmentMessageJSON msg = new FlipperEnvironmentMessageJSON(cmd, this.getId(), msgId, params);
		enqueueMessage(msg);
	}
	
	protected String enqueueMessage(JsonNode params, String cmd) {
		String msgId = "M"+RandomStringUtils.randomAlphanumeric(11);
		enqueueMessage(params, cmd, msgId);
		return msgId;
	}

	@Override
	public abstract FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws Exception;

	@Override
	public abstract void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception;

	@Override
	public abstract void init(JsonNode params) throws Exception;
	
	// Return value of "loaderClass", and adds all other values in spec to pout
	public static Properties getGMLProperties(JsonNode spec) {
		Properties res = new Properties();
		JsonNode jnprops = null;
		if (spec.has("properties")) {
			jnprops = spec.get("properties");
		} else {
			return null;
		}
		Iterator<String> fieldNames = jnprops.fieldNames();
        while (fieldNames.hasNext()) {
        	String fieldName = fieldNames.next();
        	res.put(fieldName, jnprops.get(fieldName).asText());
        }
        return res;
	}
	
	public static String getGMLClass(JsonNode spec) {
		String loaderClass = "";
		if (spec.has("loaderClass")) {
			loaderClass = spec.get("loaderClass").asText();
		} else {
			return null;
		}
        return loaderClass;
	}

}