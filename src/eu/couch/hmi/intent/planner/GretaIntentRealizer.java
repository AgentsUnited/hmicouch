package eu.couch.hmi.intent.planner;

import java.io.*;
import java.util.HashMap;

import javax.jms.TextMessage;

import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.JsonNode;

import eu.couch.hmi.actor.DialogueActor;
import eu.couch.hmi.intent.*;
import eu.couch.hmi.intent.realizer.IIntentRealizationObserver;
import eu.couch.hmi.intent.realizer.IntentRealizationStatusWatcher;
import hmi.flipper.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper.environment.IFlipperEnvironment;
import hmi.util.*;

import saiba.bml.builder.BehaviourBlockBuilder;
import saiba.bml.core.BehaviourBlock;


public class GretaIntentRealizer extends IntentRealizationStatusWatcher  {
	private static org.slf4j.Logger logger = LoggerFactory.getLogger(GretaIntentRealizer.class.getName());
	
	private int fmlIdCounter = 0;

	String fmlTemplateDir = "fmltemplates";
	private HashMap<String, String> fmlTemplates = new HashMap<>();

	@Override
	public void init(JsonNode params) throws Exception {
        logger.info("init params");
		if (params.has("fmlTemplateDir")) {
            logger.info("preloading fmltemplates ");
                    
            this.fmlTemplateDir = params.get("fmlTemplateDir").asText();
			try {
                Resources r = new Resources("");
                
				File fmlTemplateFolder = new File(r.getURL(fmlTemplateDir).toURI());
                if (!fmlTemplateFolder.isDirectory())
                {
                    logger.warn("fmlTemplateFolder \"{}\" is not a directory",fmlTemplateDir);
                    return;
                }
				File[] filesInFolder = fmlTemplateFolder.listFiles();
                Resources r2 = new Resources(fmlTemplateDir);
                
				for (File cur : filesInFolder) {
					logger.info("FML file: "+cur.getName());
                    if (cur.isFile() && cur.getName().toLowerCase().endsWith(".xml"))
                    {
                        logger.info("loading file {}",cur.getName());
				    	FileInputStream fis = new FileInputStream(cur.getPath());
				    	//String data = IOUtils.toString(fis, "UTF-8");
				    	String data = r2.read(cur.getName());
				        fmlTemplates.put(cur.getName().substring(0,cur.getName().length()-4), data);
				    }
				}
			} catch (Exception e) {
				logger.error("reading fmltemplate files failed");
				e.printStackTrace();
			}
        }
	}

	@Override
	public String realizeIntent(Intent intent, IIntentRealizationObserver observer) {
		logger.warn("INTENT REQUEST (for GRETA): "+intent.text);

		// Set up the sync refs for the main speech block:
		fmlIdCounter++;
		String blockId = "gretaIntentFML"+fmlIdCounter;
		String syncRef_start = getSyncRef(intent.charId, blockId, "start");
		String syncRef_end = getSyncRef(intent.charId, blockId, "end");

		// Register these sync refs for a new intent watch:
		String intentId = registerNewIntentWatch(observer);
		setIntentStatusRef(intentId, syncRef_start, IntentStatus.INTENT_REALIZATION_STARTED);
		setIntentStatusRef(intentId, syncRef_end, IntentStatus.INTENT_REALIZATION_COMPLETED);
		
		String fmlString = null;
		
		if (intent.text==null || intent.text.trim().equals("")) 
		{
			intent.text="eh";
		}

		//finding fmltemplate prefix, if any
		if (!intent.fml_template.equals("")) {			
			logger.info("GRETA intent contains possible fml-template: {}", intent.fml_template);
			fmlString = fmlTemplates.get(intent.fml_template);
            if (fmlString==null)
            {
                logger.warn("no fml template {} found, using fallback");
                fmlString = fmlTemplates.get("FML_Fallback1.xml");//using simple fallback fmltemplate 
            }
            if (fmlString==null)
            {
                logger.warn("no fallback fml template found either; will construct FML from scratch");
            } else {
                fmlString = fmlString.replace("$fml_id$",blockId).replace("$text$",intent.text);            
            }
		}
        if (fmlString==null) {
                
            // Directly build a simple FML:
            fmlString="<?xml version=\"1.0\" encoding=\"ISO-8859-1\" ?>\n<fml-apml id=\""+blockId+"\">\n\t<bml characterId=\""+intent.charId+"\">\n";
            fmlString+="\t\t<speech start=\"0\" id=\"s1\" ";
            fmlString+=">"+intent.text+"</speech>";
            fmlString+="\n\t</bml>\n";
            fmlString+="\n\t<fml>\n";
            if (intent.addressee != null)
            {
               // fmlString+="<deictic id=\"d1\" start=\"speech1:start\" end=\"speech1:end\" importance=\"1.0\" target=\"head_"+intent.addressee+"\" />";
            }
            fmlString+="\n\t</fml>\n</fml-apml>\n";
        }
        
        
		logger.info("INTENT FML: {} ", fmlString);
		PlannedIntent pi = new PlannedIntent(intent, this.getClass().getName(), blockId);
		parsedFeedbackListeners.forEach(listener -> listener.onIntentPlanned(pi));

        feedback.performRealizationRequest(fmlString,intent.charId);        
		
		return intentId;
	}
	
	private static Document convertXMLFileToXMLDocument(String filePath)
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        try
        {
            builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new File(filePath));
            return doc;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }
	
}
