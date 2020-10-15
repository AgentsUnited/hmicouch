package eu.couch.hmi.environments;

import java.io.IOException;
import java.util.*;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import asap.realizerport.BMLFeedbackListener;
import eu.couch.hmi.middleware.IDMiddlewareListener;
import hmi.flipper.ExperimentFileLogger;
import hmi.flipper.bmlfeedback.*;
import hmi.flipper.environment.BaseFlipperEnvironment;
import hmi.flipper.environment.MiddlewareEnvironment;
import hmi.flipper.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper.environment.IFlipperEnvironment;
import hmi.xml.XMLTokenizer;
import nl.utwente.hmi.middleware.Middleware;
import nl.utwente.hmi.middleware.MiddlewareListener;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;
import saiba.bml.builder.BehaviourBlockBuilder;
import saiba.bml.core.BehaviourBlock;
import saiba.bml.core.SpeechBehaviour;
import saiba.bml.feedback.*;
import asap.bml.ext.bmla.feedback.*;
import asap.bml.ext.bmlt.BMLTInfo;

public class FMLEnvironment extends BaseFlipperEnvironment implements IBMLFeedbackProvider {
    
	private static org.slf4j.Logger logger = LoggerFactory.getLogger(FMLEnvironment.class.getName());
	  
	List<IBMLFeedbackJSONListener> parsedFeedbackListeners;

	private boolean publishFmlFeedback = false;
    private ArrayList<String> characterIds = new ArrayList<String>();
	ObjectMapper om;

	private HashMap<String,Middleware> middlewareForChars = new HashMap<String,Middleware>();

	private HashMap<String,Middleware> middlewarebmlForChars = new HashMap<String,Middleware>();

	
	public FMLEnvironment() {
		super();
		parsedFeedbackListeners = new ArrayList<IBMLFeedbackJSONListener>();

		om = new ObjectMapper();
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	}


	@Override
	public void init(JsonNode params) throws Exception {
		
			if (!params.has("middleware")) throw new Exception("middleware object requied in params");
			Properties mwProperties = getGMLProperties(params.get("middleware"));
			String loaderClass = getGMLClass(params.get("middleware"));
			if (loaderClass == null || mwProperties == null) throw new Exception("Invalid middleware spec in params");

			if (params.has("publishBmlFeedback")) {
				publishFmlFeedback = params.get("publishBmlFeedback").asBoolean();
			}
			if (params.has("characterIds")) {
				Iterator<JsonNode> it = params.get("characterIds").elements();
				while (it.hasNext())
				{
					JsonNode next = it.next();
					characterIds.add(next.asText());
				}
			}
			
			List<String> subscribedFeedbackTopics = new ArrayList<String>();
			
			// TODO: check if middlewares are successfully loaded and throw exception if not?
			for (String charId: characterIds)
			{
				Properties mwFMLProps = new Properties(mwProperties);
				mwFMLProps.setProperty("iTopic",mwFMLProps.getProperty("iTopicPrefix")+charId);
				mwFMLProps.setProperty("oTopic",mwFMLProps.getProperty("oTopicPrefix")+charId);
				GenericMiddlewareLoader gml = new GenericMiddlewareLoader(loaderClass, mwFMLProps);
				Middleware mw = gml.load();
				mw.addListener(new MWListener(charId,this, "FML-Listener"));
				subscribedFeedbackTopics.add(mwFMLProps.getProperty("iTopic"));
				middlewareForChars.put(charId,mw);
			}
			
			if (params.has("middlewarebml")) {
				Properties mwPropertiesBml = getGMLProperties(params.get("middlewarebml"));
				String loaderClassBml = getGMLClass(params.get("middlewarebml"));
				Iterator<String> it = characterIds.iterator();
				while (it.hasNext())
				{
					String charId = it.next();
					Properties mwBMLProps = new Properties(mwPropertiesBml);
					mwBMLProps.setProperty("iTopic",mwBMLProps.getProperty("iTopicPrefix")+charId);
					mwBMLProps.setProperty("oTopic",mwBMLProps.getProperty("oTopicPrefix")+charId);
					GenericMiddlewareLoader gml = new GenericMiddlewareLoader(loaderClassBml, mwBMLProps);
					Middleware mw = gml.load();
					
					//apparently greta (sometimes..?) uses the same topic for FML feedback and BML feedback
					//so we have to try not to subscribe twice, or we risk sending duplicate move-complete updates to DAF somewhere further in the pipeline..!
					if(!subscribedFeedbackTopics.contains(mwBMLProps.getProperty("iTopic"))) {
						mw.addListener(new MWListener(charId,this, "BML-Listener"));
						subscribedFeedbackTopics.add(mwBMLProps.getProperty("iTopic"));
					} else {
						logger.warn("Ignoring duplicate feedback channel for FML and BML: {}", mwBMLProps.getProperty("iTopic"));
					}
					
					middlewarebmlForChars.put(charId,mw);
				}
			}
		
	}

	@Override
	public void setRequiredEnvironments(IFlipperEnvironment[] envs) {
		for (IFlipperEnvironment env : envs) {
			logger.warn("FMLEnvironment doesn't need environment: "+env.getId());
		}
	}

	@Override
	public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) {
		switch (fenvmsg.cmd) {
		case "send_fml":
		/*
			if (!fenvmsg.params.has("fml")) {
				logger.info("Got send_fml request, but params don't contain a fml property...");
				break;
			}

			if (fenvmsg.params.get("fml").isArray()) {
				String[] fmls = om.convertValue(fenvmsg.params.get("fml"), String[].class);
				for (String fml : fmls) {
					logger.debug("Performing fml: \n\n"+fml+"\n");
					//this.bmlMiddleware.performBML(fml);
					middleware.sendDataRaw(fml);
					ExperimentFileLogger.getInstance().log("fml", fml);
				}
			} else if (fenvmsg.params.get("fml").isTextual()) {
				String fml = fenvmsg.params.get("fml").asText();
				logger.debug("Performing fml: \n\n"+fml+"\n");
				//this.middleware.performFML(fml);
				ExperimentFileLogger.getInstance().log("fml", fml);
			} else {
				logger.info("Got send_fml request, but fml is neither array nor text...");
			}
			*/
			if(true)throw new RuntimeException("fmlEnvironment temporarily does not support eh send_fml message"); //because I'd nee to encode the character, to get the right mdidleware, and I do not even know that we even still use this function!
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
	public void performRealizationRequest(String fml, String charId) {
		logger.info("FMLEnvironment sending out FML: {} ", fml);
		this.middlewareForChars.get(charId).sendDataRaw(fml);
	}
	
	public void performBmlRealizationRequest(String bml, String charId) {
		logger.info("FMLEnvironment sending out BML via bypass: {} ", bml);
		if (this.middlewarebmlForChars.get(charId)!=null)this.middlewarebmlForChars.get(charId).sendDataRaw(bml);
		else logger.warn("no BML bypass for FMLenvironment available");
	}

}

class MWListener implements MiddlewareListener
{
	String charId = "greta";
	FMLEnvironment theEnv = null;
	private String mwListenerID;
	
	private static org.slf4j.Logger logger = LoggerFactory.getLogger(MWListener.class.getName());
	
	public MWListener(String newCharId, FMLEnvironment env, String mwListenerID)
	{
		this.mwListenerID = mwListenerID;
		charId = newCharId;
		theEnv = env;
	}
	@Override
	public void receiveData(JsonNode jn) {
		logger.info("Greta {} feedback for ID {}: {}", new String[] {charId, mwListenerID, jn.toString()});
		if (jn.get("fml_id").asText().equals(""))return;
        if (!jn.has("timeMarker_id"))
        {
			BMLBlockProgressFeedback fb = new BMLBlockProgressFeedback(jn.get("fml_id").asText(), jn.get("type").asText(), jn.get("time").asDouble(), charId);
			BMLBlockProgressFeedbackJSON bpf = new BMLBlockProgressFeedbackJSON(fb);
			logger.info("BMLBlockProgressFeedbackJSON for greta: {}",fb.toString());
			theEnv.parsedFeedbackListeners.forEach(listener -> listener.onBlockProgressFeedback(bpf));
        }
        else
        {
            BMLSyncPointProgressFeedback fb = new BMLSyncPointProgressFeedback(jn.get("fml_id").asText(), "s1", jn.get("timeMarker_id").asText(), jn.get("time").asDouble(), jn.get("time").asDouble());
            fb.setCharacterId(charId);
            BMLSyncPointProgressFeedbackJSON bsf = new BMLSyncPointProgressFeedbackJSON(fb);
            logger.info("BMLSyncPointProgressFeedbackJSON for greta: {}",fb.toString());
            theEnv.parsedFeedbackListeners.forEach(listener -> listener.onSyncPointProgressFeedback(bsf));
        }
	}
	
}