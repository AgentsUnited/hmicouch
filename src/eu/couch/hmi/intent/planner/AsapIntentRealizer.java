package eu.couch.hmi.intent.planner;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;

import eu.couch.hmi.intent.*;
import eu.couch.hmi.intent.realizer.IIntentRealizationObserver;
import eu.couch.hmi.intent.realizer.IntentRealizationStatusWatcher;
import saiba.bml.builder.BehaviourBlockBuilder;
import saiba.bml.core.BehaviourBlock;
import hmi.util.*;
import java.util.*;

import org.slf4j.LoggerFactory;
public class AsapIntentRealizer extends IntentRealizationStatusWatcher {
	private static org.slf4j.Logger logger = LoggerFactory.getLogger(AsapIntentRealizer.class.getName());
	
	private int bmlIdCounter = 0;

    String bmlTemplateDir = "bmltemplates";
	private HashMap<String, String> bmlTemplates = new HashMap<>();

	@Override
	public void init(JsonNode params) throws Exception {
        logger.info("init params");
		if (params.has("bmlTemplateDir")) {
            logger.info("preloading bmltemplates ");
                    
            this.bmlTemplateDir = params.get("bmlTemplateDir").asText();
			try {
                Resources r = new Resources("");
                
				File bmlTemplateFolder = new File(r.getURL(bmlTemplateDir).toURI());
                if (!bmlTemplateFolder.isDirectory())
                {
                    logger.warn("bmlTemplateFolder \"{}\" is not a directory",bmlTemplateDir);
                    return;
                }
				File[] filesInFolder = bmlTemplateFolder.listFiles();
                Resources r2 = new Resources(bmlTemplateDir);
                
				for (File cur : filesInFolder) {
					logger.info("BML file: "+cur.getName());
                    if (cur.isFile() && cur.getName().toLowerCase().endsWith(".xml"))
                    {
                        logger.info("loading file {}",cur.getName());
				    	FileInputStream fis = new FileInputStream(cur.getPath());
				    	//String data = IOUtils.toString(fis, "UTF-8");
				    	String data = r2.read(cur.getName());
				        bmlTemplates.put(cur.getName().substring(0,cur.getName().length()-4), data);
				    }
				}
			} catch (Exception e) {
				logger.error("reading bmltemplate files failed");
				e.printStackTrace();
			}
        }
	}

	@Override
	public String realizeIntent(Intent intent, IIntentRealizationObserver observer) {
		System.out.println("INTENT REQUEST: "+intent.text);

		// Set up the sync refs for the main speech block:
		bmlIdCounter++;
		String blockId = "asapintentbml_"+bmlIdCounter;
		String syncRef_start = getSyncRef(intent.charId, blockId, "start");
		String syncRef_end = getSyncRef(intent.charId, blockId, "end");

		// Register these sync refs for a new intent watch:
		String intentId = registerNewIntentWatch(observer);
		setIntentStatusRef(intentId, syncRef_start, IntentStatus.INTENT_REALIZATION_STARTED);
		setIntentStatusRef(intentId, syncRef_end, IntentStatus.INTENT_REALIZATION_COMPLETED);
		
		String bmlString = null;
		//finding bmltemplate prefix, if any
		if (!intent.bml_template.equals("")) {			
			logger.info("ASAP intent contains possible bml-template: {}", intent.bml_template);
			bmlString = bmlTemplates.get(intent.bml_template);
            if (bmlString==null)
            {
                logger.warn("no bml template {} found, using fallback");
                bmlString = bmlTemplates.get("BML_Fallback1.xml");//using simple fallback bmltemplate 
            }
            if (bmlString==null)
            {
                logger.warn("no fallback bml template found either; will construct BFML from scratch");
            } else {
                bmlString = bmlString.replace("$bml_id$",blockId).replace("$char_id$",intent.charId).replace("$text$",intent.text);            
            }
		}
        if (bmlString==null) {
                
            // Directly build a simple BML:		

			String bmlStringHeader="<bml id=\""+blockId+"\" xmlns=\"http://www.bml-initiative.org/bml/bml-1.0\"  xmlns:mwe=\"http://hmi.ewi.utwente.nl/middlewareengine\"  xmlns:bmla=\"http://www.asap-project.org/bmla\" characterId=\""+intent.charId+"\">";
			String bmlSpeech = "";
			String bmlGaze = "";
			if (intent.text==null || intent.text.trim().equals("")) 
			{
				intent.text="eh";
			}
			bmlSpeech = "<speech id=\"s1\" start=\"0\"><text>"+intent.text+"</text></speech>";
			/*if (intent.addressee != null && !intent.addressee.equals("")&& !intent.addressee.equals("ALL"))
			{
				bmlGaze = "<gazeShift id=\"gaze1\" influence=\"NECK\" target=\"head_"+intent.addressee+"\" start=\"s1:start\" end=\"s1:start+0.7\"/>";
			} 
			else 
			{
				bmlGaze = "<gazeShift id=\"gaze1\" influence=\"NECK\" target=\"head_COUCH_USER\" start=\"s1:start\" end=\"s1:start+0.7\"/>";
			}*/
			String urlBml = "";
			if (!intent.url.equals("")) {			
				logger.info("ASAP intent contains possible url: {}", intent.url);
				urlBml = "<mwe:sendJsonMessage id=\"m1\" start=\"0\" end = \"1\" middlewareloaderclass=\"nl.utwente.hmi.middleware.activemq.ActiveMQMiddlewareLoader\" middlewareloaderproperties=\"oTopic:COUCH/UI/URL,iTopic:DUMMY,amqBrokerURI:tcp://localhost:61616\">{\"url\":\"http://"+intent.url+"\"}</mwe:sendJsonMessage>";
			}			
			String bmlDeictic = "";
			if (!intent.deictic.equals("")) {			
				logger.info("ASAP intent contains possible deictic: {}", intent.deictic);
				bmlDeictic = "<pointing id=\"p1\" target=\""+intent.deictic+"\" mode=\"LEFT_HAND\" start=\"0\" end=\"3\"/><gazeShift id=\"g1\" influence=\"NECK\" target=\""+intent.deictic+"\" start=\"0\" end=\"0.35\"/><gazeShift id=\"g2\" influence=\"NECK\" target=\"head_COUCH_USER\" start=\"s1:end-0.35\" end=\"s1:end\"/>";
			}			
			String bmlStringFooter = "</bml>";
			bmlString = bmlStringHeader+bmlSpeech+bmlGaze+urlBml+bmlDeictic+bmlStringFooter;
		}
/*		BehaviourBlockBuilder builder = new BehaviourBlockBuilder();
		BehaviourBlock bb = builder.id(blockId).characterId(intent.charId)
			.addSpeechBehaviour("s1", intent.text).build();
		String bmlBlock = bb.toBMLString();*/
		
		logger.info("INTENT BML: {} ", bmlString);
		PlannedIntent pi = new PlannedIntent(intent, this.getClass().getName(), blockId);
		parsedFeedbackListeners.forEach(listener -> listener.onIntentPlanned(pi));
		
		feedback.performRealizationRequest(bmlString,null);
		
		return intentId;
	}

}
