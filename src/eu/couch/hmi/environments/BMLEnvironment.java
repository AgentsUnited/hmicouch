package eu.couch.hmi.environments;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import asap.middlewareadapters.BMLRealizerToMiddlewareAdapter;
import asap.realizerport.BMLFeedbackListener;
import hmi.flipper.ExperimentFileLogger;
import hmi.flipper.bmlfeedback.*;
import hmi.flipper.environment.BaseFlipperEnvironment;
import hmi.flipper.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper.environment.IFlipperEnvironment;
import hmi.xml.XMLTokenizer;
import saiba.bml.builder.BehaviourBlockBuilder;
import saiba.bml.core.BehaviourBlock;
import saiba.bml.core.SpeechBehaviour;
import saiba.bml.feedback.*;
import asap.bml.ext.bmla.feedback.*;
import asap.bml.ext.bmlt.BMLTInfo;

public class BMLEnvironment extends BaseFlipperEnvironment implements BMLFeedbackListener, IBMLFeedbackProvider  {
	
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(BMLEnvironment.class.getName());
    
	private BMLRealizerToMiddlewareAdapter bmlMiddleware;
	private List<IBMLFeedbackJSONListener> parsedFeedbackListeners;
	
	private boolean publishBmlFeedback = false;
	

	ObjectMapper om;
	
	public static BehaviourBlockBuilder GetBlockBuilder() {
		BehaviourBlockBuilder builder = new BehaviourBlockBuilder();
		return builder;
	}

	
	public BMLEnvironment() {
		super();
		parsedFeedbackListeners = new ArrayList<IBMLFeedbackJSONListener>();

		om = new ObjectMapper();
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        BMLTInfo.init();
	}

	@Override
	public void init(JsonNode params) throws Exception {
		if (!params.has("middleware")) throw new Exception("middleware object requied in params");
		Properties mwProperties = getGMLProperties(params.get("middleware"));
		String loaderClass = getGMLClass(params.get("middleware"));
		if (loaderClass == null || mwProperties == null) throw new Exception("Invalid middleware spec in params");
		
		if (params.has("publishBmlFeedback")) {
			publishBmlFeedback = params.get("publishBmlFeedback").asBoolean();
		}

		// TODO: check if middlewares are successfully loaded and throw exception if not?


		this.bmlMiddleware = new BMLRealizerToMiddlewareAdapter(loaderClass, mwProperties);
		this.bmlMiddleware.addListeners(this);
	}

	@Override
	public void setRequiredEnvironments(IFlipperEnvironment[] envs) {
		for (IFlipperEnvironment env : envs) {
			logger.warn("BMLEnvironment doesn't need environment: "+env.getId());
		}
	}

	@Override
	public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) {
		switch (fenvmsg.cmd) {
		case "send_bml":
			if (!fenvmsg.params.has("bml")) {
				logger.info("Got send_bml request, but params don't contain a bml property...");
				break;
			}

			if (fenvmsg.params.get("bml").isArray()) {
				String[] bmls = om.convertValue(fenvmsg.params.get("bml"), String[].class);
				for (String bml : bmls) {
					logger.debug("Performing bml: \n\n"+bml+"\n");
					this.bmlMiddleware.performBML(bml);
					ExperimentFileLogger.getInstance().log("bml", bml);
				}
			} else if (fenvmsg.params.get("bml").isTextual()) {
				String bml = fenvmsg.params.get("bml").asText();
				logger.debug("Performing bml: \n\n"+bml+"\n");
				this.bmlMiddleware.performBML(bml);
				ExperimentFileLogger.getInstance().log("bml", bml);
			} else {
				logger.info("Got send_bml request, but bml is neither array nor text...");
			}
			break;
		default:
			logger.warn("Unhandled message: "+fenvmsg.cmd);
			break;
		}
		return null;
	}
	
	@Override
	public void registerBMLFeedbackJSONListener(IBMLFeedbackJSONListener l) {
		if (!parsedFeedbackListeners.contains(l)) {
			parsedFeedbackListeners.add(l);
		}
	}
	
	@Override
	public void feedback(String feedback) {
		ExperimentFileLogger.getInstance().log("bmlfeedback", feedback);
		JsonNode node = null;
		try {
            //parse, get syncId, and put in queue...
            BMLFeedback fb = BMLAFeedbackParser.parseFeedback(feedback);
            if (fb instanceof BMLASyncPointProgressFeedback) {
            	BMLSyncPointProgressFeedbackJSON fbval = new BMLSyncPointProgressFeedbackJSON((BMLASyncPointProgressFeedback)fb);
            	parsedFeedbackListeners.forEach(listener -> listener.onSyncPointProgressFeedback(fbval));
            	node = om.convertValue(fbval, JsonNode.class);
            } else if (fb instanceof BMLABlockProgressFeedback) {
            	BMLBlockProgressFeedbackJSON fbval = new BMLBlockProgressFeedbackJSON((BMLABlockProgressFeedback)fb);
            	parsedFeedbackListeners.forEach(listener -> listener.onBlockProgressFeedback(fbval));
            	node = om.convertValue(fbval, JsonNode.class);
            } else if (fb instanceof BMLAPredictionFeedback) {
            	BMLPredictionFeedbackJSON fbval = new BMLPredictionFeedbackJSON((BMLAPredictionFeedback)fb);
            	parsedFeedbackListeners.forEach(listener -> listener.onPredictionFeedback(fbval));
            	node = om.convertValue(fbval, JsonNode.class);
            } else if (fb instanceof BMLWarningFeedback) {
            	BMLWarningFeedbackJSON fbval = new BMLWarningFeedbackJSON((BMLWarningFeedback)fb);
            	parsedFeedbackListeners.forEach(listener -> listener.onWarningFeedback(fbval));
            	node = om.convertValue(fbval, JsonNode.class);
            } else {
            	logger.warn("Cannot handle this BML feedback:\n"+feedback);
            }

        } catch (Exception e) {
        	logger.warn("Error handling feedback: ", e);
        }

		if (node != null && publishBmlFeedback) {
			enqueueMessage(node, node.get("bmlFeedbackType").asText());
		}
	}


	@Override
	/** ignores the charId because we assume that bml realisers read the charid from the request */
	public void performRealizationRequest(String bml, String charId) {
		logger.info("BMLEnvironment sending out BML: {} ", bml);
		this.bmlMiddleware.performBML(bml);
	}
	
}

// TODO: a nice interface to the openbmlparser:BehaviorBlockBuilder that can be used from javascript...
class MyBehaviourBlockBuilder extends BehaviourBlockBuilder {
	
    public MyBehaviourBlockBuilder(){
        super();
    }

    public BehaviourBlockBuilder addSpeechBehaviour(String behid, String content, String descriptionContent) {
        try {
            behaviours.add(new SpeechBehaviour(id,
            		new XMLTokenizer("<speech xmlns=\"" + BehaviourBlock.BMLNAMESPACE + "\" id=\"" + behid + "\">\n"+
            							"\t<text>" + content + "</text>\n"+
            							descriptionContent + "\n" +
            						 "</speech>")));
        } catch (IOException e) {
            throw new AssertionError();
        }
        return this;
    }
    
}
