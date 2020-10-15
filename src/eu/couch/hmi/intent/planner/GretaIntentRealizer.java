package eu.couch.hmi.intent.planner;

import java.io.*;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.jms.TextMessage;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
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
import lombok.Getter;
import saiba.bml.builder.BehaviourBlockBuilder;
import saiba.bml.core.BehaviourBlock;


public class GretaIntentRealizer extends IntentRealizationStatusWatcher implements IIntentRealizationObserver  {
	private static org.slf4j.Logger logger = LoggerFactory.getLogger(GretaIntentRealizer.class.getName());
	
	private int fmlIdCounter = 0;

	String fmlTemplateDir = "fmltemplates";
	private HashMap<String, String> fmlTemplates = new HashMap<>();

	private boolean enableMeaningMiner = false;


	private LinkedList<IntentSegment> segments;

	private String segmentedIntentId;
	private String currentIntentSegmentId;

	@Override
	public void init(JsonNode params) throws Exception {
        logger.info("init params");
		if (params.has("enableMeaningMiner")) {
			enableMeaningMiner = params.get("enableMeaningMiner").asBoolean();
		}
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
		if(enableMeaningMiner) {
			segmentedIntentId = "SI"+RandomStringUtils.randomAlphanumeric(11);
			segments = makeIntentSegments(intent, observer);
			
			//kick off the sequence of intent segments, the next one will trigger automatically when this finishes
			currentIntentSegmentId = realizeNextIntentSegment();
			
			//this is the intent ID that the external observer will get notified for when all intent segments are complete
			return segmentedIntentId;
		} else {
			return realizeCompleteIntent(intent, observer);
		}
	}
	
	/**
	 * This method uses Greta's meaningminer to generate nonverbal gestures.
	 * We need to split the intent in separate segments and send them individually in sequence.
	 * It requires that speech be sent in a very specific way:
	 * - per FML request there must be a maximum of 1 sentence of text
	 * - each word must have its own timing tag <tm id="xx"/> see: https://github.com/isir/greta/blob/gpl/bin/Examples/DemoEN/MeaningMiner_FML_Example.xml
	 */
	public LinkedList<IntentSegment> makeIntentSegments(Intent intent, IIntentRealizationObserver observer) {
		
		//store the various segmented intents that should be handled in sequence
		LinkedList<IntentSegment> intentSegments = new LinkedList<IntentSegment>();
		
		int segmentIdCounter = 0;

		//we need to send each of the sentences in the text separately
		Locale locale = Locale.UK;
		BreakIterator breakIterator = BreakIterator.getSentenceInstance(locale);

		breakIterator.setText(intent.text);

		int start = breakIterator.first();
		int boundary = breakIterator.next();
		
		while(boundary != BreakIterator.DONE) {
			String text = intent.text.substring(start,boundary).trim();
			
			//FIXME: this is a workaround, the meaningminer seems to fail on the word "a" so for now just replace with something that sounds similar...
			text = text.replaceAll(" a ", " ah ");
		
			fmlIdCounter++;
			String blockId = "gretaSegmentedIntentFML"+fmlIdCounter;
			
			segmentIdCounter++;
			String segmentId = segmentedIntentId + "-S" + segmentIdCounter;
			
            String fmlString="<?xml version=\"1.0\" encoding=\"ISO-8859-1\" ?>\n<fml-apml id=\""+blockId+"\">\n\t<bml characterId=\""+intent.charId+"\">\n";
            fmlString+="\t\t<speech start=\"0\" id=\"s1\">";
			
			
			//create a new timing for each of the words in the segment
			List<String> words = new ArrayList<String>(Arrays.asList(text.split("\\s+")));
			
			int wordId = 0;
			for(String word : words) {
				wordId++;
				fmlString += "\n\t\t\t<tm id=\"tm" + wordId + "\"/>" + word.trim() + " ";
			}

			wordId++;
			fmlString += "<tm id=\"tm" + wordId + "\"/>"; // we need to close with one final tm after the full stop (maybe so that greta goes back to rest pose...? I dunno..)

            fmlString+="\n\t\t</speech>";
            fmlString+="\n\t</bml>";
            fmlString+="\n\t<fml>";
            fmlString+="\n\t</fml>\n</fml-apml>";
            
			logger.debug("making new fml for segment: \n{}", fmlString);
			
			Intent newIntent = intent.copy();
			newIntent.text = text;

			//try to find the next sentence
			start = boundary;
			boundary = breakIterator.next();
			
			if(boundary != BreakIterator.DONE) {
				intentSegments.addLast(new IntentSegment(fmlString, newIntent, blockId, segmentId, this));
			} else {
				//the last segment should notify the original observer when it is complete, to signal that the whole original intent is done
				intentSegments.addLast(new IntentSegment(fmlString, newIntent, blockId, segmentedIntentId, observer));
			}
			
		}
		
		return intentSegments;
	}
	
	private String realizeNextIntentSegment() {
		if(segments.peekFirst() != null) {
			IntentSegment nextIntentSegment = segments.pollFirst();
			
			String syncRef_start = getSyncRef(nextIntentSegment.intent.charId, nextIntentSegment.blockId, "start");
			String syncRef_end = getSyncRef(nextIntentSegment.intent.charId, nextIntentSegment.blockId, "end");
			
			registerIntentWatch(nextIntentSegment.intentId, nextIntentSegment.observer);
			setIntentStatusRef(nextIntentSegment.intentId, syncRef_start, IntentStatus.INTENT_REALIZATION_STARTED);
			setIntentStatusRef(nextIntentSegment.intentId, syncRef_end, IntentStatus.INTENT_REALIZATION_COMPLETED);

			PlannedIntent pi = new PlannedIntent(nextIntentSegment.intent, this.getClass().getName(), nextIntentSegment.blockId);
			parsedFeedbackListeners.forEach(listener -> listener.onIntentPlanned(pi));

	        feedback.performRealizationRequest(nextIntentSegment.fml, nextIntentSegment.intent.charId);    
	        
	        return nextIntentSegment.intentId;
		} else {
			logger.warn("there are no more intent segments to realize");
			return "";
		}
	}
	
	/**
	 * Realizes a complete intent with a speech block in one go, either from a template (if specified) or by generating a basic FML
	 */
	private String realizeCompleteIntent(Intent intent, IIntentRealizationObserver observer) {
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
	

	public class IntentSegment {
		public String fml;
		public Intent intent;
		public String blockId;
		public String intentId;
		public IIntentRealizationObserver observer;

		public IntentSegment(String fml, Intent intent, String blockId, String intentId, IIntentRealizationObserver observer) {
			this.fml = fml;
			this.intent = intent;
			this.blockId = blockId;
			this.intentId = intentId;
			this.observer = observer;
		}
	}


	@Override
	public void onIntentStatus(String intentId, IntentStatus intentStatus) {
		logger.debug("got status {} for intent id {}", intentStatus, intentId);
		if(intentId.equals(currentIntentSegmentId)) {
			if(intentStatus == IntentStatus.INTENT_REALIZATION_COMPLETED) {
				currentIntentSegmentId = realizeNextIntentSegment();
			}
		} else {
			logger.warn("Got intent status update {} for unknown intent id {}", intentStatus, intentId);
		}
	}
	
}
