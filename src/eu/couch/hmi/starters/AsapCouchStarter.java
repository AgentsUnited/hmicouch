package eu.couch.hmi.starters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.stream.Collectors;

import asap.bml.ext.bmlt.BMLTInfo;
import asap.environment.AsapEnvironment;
import asap.realizerembodiments.SharedPortLoader;
import hmi.audioenvironment.AudioEnvironment;
import hmi.environmentbase.ClockDrivenCopyEnvironment;
import hmi.environmentbase.Environment;
import hmi.audioenvironment.middleware.MiddlewareStreamingSoundManager;
import hmi.mixedanimationenvironment.MixedAnimationEnvironment;
import hmi.physicsenvironment.OdePhysicsEnvironment;
import hmi.unityembodiments.loader.SharedMiddlewareLoader;
import hmi.worldobjectenvironment.WorldObjectEnvironment;
import nl.utwente.hmi.middleware.Middleware;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;
import saiba.bml.BMLInfo;
import saiba.bml.core.FaceLexemeBehaviour;
import saiba.bml.core.HeadBehaviour;
import saiba.bml.core.PostureShiftBehaviour;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jknack.handlebars.*;

public class AsapCouchStarter {

	public static String ReadFile(String path) {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        InputStream is = classloader.getResourceAsStream(path);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
    		return br.lines().collect(Collectors.joining(System.lineSeparator()));
    	} catch (IOException e) {
			return null;
		}
	}
	
    public static void main(String[] args) throws IOException {
        String launchSpecFile = "couchlaunch.json";
        String audioPort="6669";
        String middlewarePropFile = "defaultmiddleware.properties";
        
        String help = "Commandline arguments: \n\tOptional: multiple \"-<argname> <arg>\".\n\n\tAccepting the following argnames: \n\t-launchspecfile <filepath to the file containing the launch specifications (default: "+launchSpecFile+")>\n\t-middlewareprops <filepath to file containing the default middleware properties (default: "+middlewarePropFile+")>\n\t-audioport <port number of audiostreamer port for this instance of ASAP (default: "+audioPort+")>\n";

		for (String arg : args) {
			System.out.println("Arg: "+arg);
		}
        
        if(args.length > 0 && args.length % 2 != 0){
        	System.err.println("Invalid number of arguments: "+args.length);
          System.err.println(help);
          System.exit(0);
        }
        
        
        
        for(int i = 0; i < args.length-1; i = i + 2){
        	if(args[i].equals("-launchspecfile")){
				launchSpecFile = args[i+1];
			} else if(args[i].equals("-audioport")){
				audioPort = args[i+1];
			} else if(args[i].equals("-middlewareprops")){
			    middlewarePropFile = args[i+1];
			} else {
      System.err.println("Unknown commandline argument: \""+args[i]+" "+args[i+1]+"\".\n"+help);
      System.exit(0);
  }
        }

        GenericMiddlewareLoader.setGlobalPropertiesFile(middlewarePropFile);

        AsapCouchStarter asap = new AsapCouchStarter();
    	String launch = ReadFile(launchSpecFile);
    	
    	if (launch != null) {
    		ObjectMapper mapper = new ObjectMapper();
    		asap.init(mapper.readTree(launch), audioPort);
    	} else {
    		asap.init(null, audioPort);
    	}
    }

    public AsapCouchStarter() {
    	
    }

    public void init(JsonNode jsonNode, String audioport) throws IOException {
        String shared_port = "multiAgentSpecs/shared_port.xml";
        String shared_middleware = "multiAgentSpecs/shared_middleware.xml";
        String resources = "";
        
    	String loaderClass = "nl.utwente.hmi.middleware.udp.UDPMultiClientMiddlewareLoader";
    	Properties loaderProperties = new Properties();
    	loaderProperties.put("port", audioport);
    	GenericMiddlewareLoader gml = new GenericMiddlewareLoader(loaderClass, loaderProperties);
    	Middleware audioMiddleware = gml.load();
    	
    	
        MixedAnimationEnvironment mae = new MixedAnimationEnvironment();
        final OdePhysicsEnvironment ope = new OdePhysicsEnvironment();
        WorldObjectEnvironment we = new WorldObjectEnvironment();
        MiddlewareStreamingSoundManager mssm = new MiddlewareStreamingSoundManager(audioMiddleware);
        AudioEnvironment aue = new AudioEnvironment();

        BMLTInfo.init();
        BMLInfo.addCustomFloatAttribute(FaceLexemeBehaviour.class, "http://asap-project.org/convanim", "repetition");
        BMLInfo.addCustomStringAttribute(HeadBehaviour.class, "http://asap-project.org/convanim", "spindirection");
        BMLInfo.addCustomFloatAttribute(PostureShiftBehaviour.class, "http://asap-project.org/convanim", "amount");

        ArrayList<Environment> environments = new ArrayList<Environment>();
        final AsapEnvironment ee = new AsapEnvironment();
        
        ClockDrivenCopyEnvironment ce = new ClockDrivenCopyEnvironment(1000 / 30);

        ce.init();
        ope.init();
        mae.init(ope, 0.002f);
        we.init();
        aue.init(mssm);
        environments.add(ee);
        environments.add(ope);
        environments.add(mae);
        environments.add(we);

        environments.add(ce);
        environments.add(aue);

        SharedMiddlewareLoader sml = new SharedMiddlewareLoader();
        sml.load(resources, shared_middleware);
        environments.add(sml);
        
        SharedPortLoader spl = new SharedPortLoader();
        spl.load(resources, shared_port, ope.getPhysicsClock());
        environments.add(spl);

        ee.init(environments, spl.getSchedulingClock());
        ope.addPrePhysicsCopyListener(ee);
    	
        
        if (!jsonNode.has("agents")) {
        	throw new IOException("launch spec json needs to contain an 'agents' object.");
        }

        ObjectMapper om = new ObjectMapper();
        JsonNode agents = jsonNode.get("agents");
		JsonNode sharedContext = om.createObjectNode();
        if (jsonNode.has("sharedContext")) sharedContext = jsonNode.get("sharedContext");
        
        String instance = "ASAP";
        if (jsonNode.has("instance")) instance = jsonNode.get("instance").asText();
    	for (JsonNode agent : agents) {
    		if (!agent.has("charId")) throw new IOException("an entry in the agents array must contain a 'charId' string property.");
    		String charId = agent.get("charId").asText();
    		String title = String.format("%s: %s", instance, charId);
    		
            if (!agent.has("spec")) {
            	String templateFile = "";
            	if (jsonNode.has("template"))
            		templateFile = jsonNode.get("template").asText();
            	if (agent.has("template"))
            		templateFile = agent.get("template").asText();
            	if (templateFile.length() == 0) {
                	throw new IOException("no 'template' or 'spec' defined");
            	}

                System.out.println("Template: "+templateFile);
                
        		JsonNode agentContext = om.createObjectNode();
        		if (agent.has("context")) {
            		agentContext = agent.get("context");
        		}
                
            	JsonNode contextNode = merge(sharedContext.deepCopy(), agentContext);
            	System.out.println("=====\n"+contextNode.toString()+"\n=====");
            	
                Handlebars handlebars = new Handlebars();
            	Template template = handlebars.compile(templateFile);
            	Context context = Context
            			  .newBuilder(contextNode)
            			  .resolver(JsonNodeValueResolver.INSTANCE)
            			  .build();
            	String specString = template.apply(context);
            	System.out.println("\n\n"+charId+":\n"+specString+"\n\n");
                ee.loadVirtualHumanFromSpecString(charId, specString, title);
            } else if (agent.has("spec")) {
                ee.loadVirtualHuman(agent.get("charId").asText(), resources, agent.get("spec").asText(), title);
            } else {
            	throw new IOException("an entry in the agents array must at least contain either a 'template' (if not defined in root) or a 'spec' entry");
            }
    	}
        ope.startPhysicsClock();
    }
    
    private JsonNode merge(JsonNode mainNode, JsonNode updateNode) {

        Iterator<String> fieldNames = updateNode.fieldNames();

        while (fieldNames.hasNext()) {
            String updatedFieldName = fieldNames.next();
            JsonNode valueToBeUpdated = mainNode.get(updatedFieldName);
            JsonNode updatedValue = updateNode.get(updatedFieldName);

            // If the node is an @ArrayNode
            if (valueToBeUpdated != null && valueToBeUpdated.isArray() && 
                updatedValue.isArray()) {
                // running a loop for all elements of the updated ArrayNode
                for (int i = 0; i < updatedValue.size(); i++) {
                    JsonNode updatedChildNode = updatedValue.get(i);
                    // Create a new Node in the node that should be updated, if there was no corresponding node in it
                    // Use-case - where the updateNode will have a new element in its Array
                    if (valueToBeUpdated.size() <= i) {
                        ((ArrayNode) valueToBeUpdated).add(updatedChildNode);
                    }
                    // getting reference for the node to be updated
                    JsonNode childNodeToBeUpdated = valueToBeUpdated.get(i);
                    merge(childNodeToBeUpdated, updatedChildNode);
                }
            // if the Node is an @ObjectNode
            } else if (valueToBeUpdated != null && valueToBeUpdated.isObject()) {
                merge(valueToBeUpdated, updatedValue);
            } else {
                if (mainNode instanceof ObjectNode) {
                    ((ObjectNode) mainNode).replace(updatedFieldName, updatedValue);
                }
            }
        }
        return mainNode;
    }

}

