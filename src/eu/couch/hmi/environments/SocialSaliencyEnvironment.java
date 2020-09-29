package eu.couch.hmi.environments;

import java.util.*;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.couch.hmi.moves.FilteredMove;
import eu.couch.hmi.moves.FilteredMoveSet;
import eu.couch.hmi.moves.IMoveDistributor;
import eu.couch.hmi.moves.IMoveFilter;
import eu.couch.hmi.moves.Move;
import eu.couch.hmi.moves.MoveSet;
import hmi.flipper.environment.BaseFlipperEnvironment;
import hmi.flipper.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper.environment.IFlipperEnvironment;
import saiba.bml.feedback.BMLSyncPointProgressFeedback;
import eu.couch.hmi.intent.planner.*;
import hmi.flipper.bmlfeedback.BMLBlockProgressFeedbackJSON;
import hmi.flipper.bmlfeedback.BMLPredictionFeedbackJSON;
import hmi.flipper.bmlfeedback.BMLSyncPointProgressFeedbackJSON;
import hmi.flipper.bmlfeedback.BMLWarningFeedbackJSON;
import hmi.flipper.bmlfeedback.IBMLFeedbackJSONListener;
import eu.couch.hmi.intent.realizer.*;

import eu.couch.hmi.intent.PlannedIntent;


public class SocialSaliencyEnvironment extends BaseFlipperEnvironment implements IBMLFeedbackJSONListener, IIntentRealizationJSONListener {
	
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(SocialSaliencyEnvironment.class.getName());

	//to convert stuff to JSON?
	ObjectMapper om;
		
		
	// ======= the other environments that we need to relate to (get bml feedback, hear about intents that wre planned, etc)
	BMLEnvironment bmlEnv = null;
	FMLEnvironment fmlEnv = null;
	DialogueLoaderEnvironment dialEnv = null;
	AsapIntentRealizer air = null;
	GretaIntentRealizer gir = null;
	
	
	// ======= core information needed to do the SSG
	
	//(intents are removed once we receive and processed the block end, to save speed)
    private HashMap<String,PlannedIntent> plannedIntentsByBlockId = new HashMap<String,PlannedIntent>();
    private HashSet<String> allCharacterIds = new HashSet<String>();
	private HashMap<String,IIntentRealizer> enginePerCharacterId = new HashMap<String,IIntentRealizer>();
    private HashSet<String> allMapIds = new HashSet<String>();
    //leaky: after a while, value for a target returns to default (so, a sort of "decay" functionailty)
    private HashMap<String,Boolean> mapIsLeaky = new HashMap<String,Boolean>();
    //for leaky maps, this is the value that the map returns to after a while
    private HashMap<String,Double> defaultValueForMap = new HashMap<String,Double>();
    //the actual saliency maps, keyed by charId, mapId, targetId (so, for character charid, targetid is salient in map mapid)
    private HashMap<String,HashMap<String,HashMap<String,Double>>> saliencyMaps = new HashMap<String,HashMap<String,HashMap<String,Double>>>();
    private HashMap<String,String> previousTargetPerCharacter = new HashMap<String,String>();	
	private HashMap<String,Integer> activeSpeakers = new HashMap<String,Integer>();
	private HashMap<String,Integer> activeAddressees = new HashMap<String,Integer>();
	
	// ======= some parameters that configure the behaviour of the saliency gaze
	
	//increased for every gazeShift request, to make sure BML blocks get unique IDs
	private int socialSaliencyBehaviourRequestCount = 0;

	//to compare which saliency value is highest ranking target, or whether a target is already "back at default"
	private double sameSaliencyEpsilon = 0.01;
	
	//how often are the saliencies recalculated?	i.e. speed of "ticks"
	private long msecPerUpdate = 20;
	
	//every tick (see above) all leaky saliencies are multiplied by this factor towards the "baseline value" for that type of saliency
	private double leakPerTick = 0.98;
	
    private Random random = new Random();
	// ======= initialisation methods	
	
	public SocialSaliencyEnvironment() {
		super();
		om = new ObjectMapper();	
		initNewMap("speakerSaliency", false, 0.0);
		initNewMap("cumulativeSaliency", false, 0.0);
		initNewMap("penaltiesAndBoosts", true, 0.0);
		initNewCharacter("COUCH_USER","NONE");
		activeAddressees.put("ALL",0);
	}
	
	
	@Override
	public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {
		//gather the environments
		for (IFlipperEnvironment env : envs) {
			if (env instanceof BMLEnvironment) bmlEnv = (BMLEnvironment) env;
			else if (env instanceof FMLEnvironment) fmlEnv = (FMLEnvironment) env;
			else if (env instanceof DialogueLoaderEnvironment) dialEnv = (DialogueLoaderEnvironment) env;
			else if (env instanceof AsapIntentRealizer) air = (AsapIntentRealizer) env;
			else if (env instanceof GretaIntentRealizer) gir = (GretaIntentRealizer) env;
			else logger.warn("Unknown required environment {} for the social saliency gaze",env);
		}
		//check which ones are missing
		if (dialEnv == null) throw new Exception("Required loader DialogueLoaderEnvironment missing for SocialSaliencyEnvironment");
		//register for some listeners at the various environments
		if (bmlEnv != null) bmlEnv.registerBMLFeedbackJSONListener(this); // to hear about start and stop of relevant speech
		if (fmlEnv != null) fmlEnv.registerBMLFeedbackJSONListener(this); // to hear about start and stop of relevant speech
		if (air != null)  air.registerIntentRealizationJSONListener(this); // to hear about intent that has just been planned, so we know details such as BMLID of request
		if (gir != null)  gir.registerIntentRealizationJSONListener(this); // to hear about intent that has just been planned, so we know details such as BMLID of request
	}
	
	/**
	Initialize from params provided by FLipper (generally in the environment.xml templates)
	*/
	@Override
	public void init(JsonNode params) throws Exception {
		//get parameters from flipper
		if (params.has("sameSaliencyEpsilon")) sameSaliencyEpsilon = params.get("sameSaliencyEpsilon").asDouble();
		if (params.has("msecPerUpdate")) msecPerUpdate = params.get("msecPerUpdate").asInt();
		if (params.has("leakPerTick")) leakPerTick = params.get("leakPerTick").asDouble();
		
	}
	
	// ======= messenging from flipper

	/** 
	This is the Social Saliency Environment receiving a request from FLIPPER (so not directly from the other environments).
	"updatesaliency requests" are triggered here, plus the update rate can be modified here.
	*/
	@Override
	public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws Exception {
		switch (fenvmsg.cmd) {
		case "setMsecPerUpdate":
			if (!fenvmsg.params.has("msecPerUpdate")) throw new Exception("setMsecPerUpdate requires integer parameter msecPerUpdate in social saliency environment");
			msecPerUpdate = fenvmsg.params.get("msecPerUpdate").asInt();
			logger.info("New update rate set to {}",msecPerUpdate);
			break;		
		case "updateSaliency":
			updateSaliency();
			break;		
		case "initNewCharacter":
			if (!fenvmsg.params.has("charId")) throw new Exception("initNewCharacter requires String parameter charId in social saliency environment");
			if (!fenvmsg.params.has("engine")) throw new Exception("initNewCharacter requires String parameter engine in social saliency environment");
			initNewCharacter(fenvmsg.params.get("charId").asText(), fenvmsg.params.get("engine").asText());
			logger.info("Adding character to SSE: {}, {}",fenvmsg.params.get("charId").asText(), fenvmsg.params.get("engine").asText());
			break;		
		default:
			logger.warn("Unhandled message in SSE: {} ", fenvmsg.cmd);
			break;
		}
		return null;
	}

	// ================================ BML feedback: we need this to react with social saliency on any speaker, from any source of BML (also BML that was generated outside of Flipper, and BML feedback that actually originated as FML feedback)
	
	@Override
	public void onBlockProgressFeedback(BMLBlockProgressFeedbackJSON fb) {
		PlannedIntent pi = plannedIntentsByBlockId.get(fb.bmlId);
		
		//FIXME: super ugly hack for greta, who doesn't seem to offer syncPointFeedback.. 
		//instead we simply create a fake syncpoint start and stop for behaviourId "s1" so that it triggers salient gaze behaviour
		//this will, obviously, fail miserably when greta starts doing non-speech behaviours
		if(pi != null && "greta".equalsIgnoreCase(pi.engine) && pi.text != null && !"".equals(pi.text)) {
			double time = ("start".equals(fb.syncId)) ? 0.0d : 1.0d;
			BMLSyncPointProgressFeedbackJSON spfb = new BMLSyncPointProgressFeedbackJSON(new BMLSyncPointProgressFeedback(fb.bmlId, "s1", fb.syncId, time, fb.globalTime));
			logger.warn("generating fake syncpointProgressFeedback for greta: {}", spfb.flipperSyncId);
			onSyncPointProgressFeedback(spfb);
		}
		
		if (pi!=null && fb.syncId.equals("end")) {
			plannedIntentsByBlockId.remove(fb.bmlId);
			logger.info("dropped plannedintent from SSE; intent was finalized");
		}
	}


	@Override
	public void onSyncPointProgressFeedback(BMLSyncPointProgressFeedbackJSON fb) {
//TODO:		use the old asappredictioneavesdropper as new flipper environment to catch all bml:beh id's for SPEECH elements so we know tom watch for them rather than for the "s1" id (which is faking it a bit :( )
		if (!fb.behaviorId.equals("s1"))return; //we only wait for SPEECH syncs here!
		PlannedIntent pi = plannedIntentsByBlockId.get(fb.bmlId);
		//logger.info("spnjfkd    \"{}\"",fb.bmlId);
		if (pi==null) { 
			if (allCharacterIds.contains(fb.characterId)) {
				if (fb.syncId.equals("start")) {
					//this speaker now started speaking unknown speech. no addressee saliency therefore
					activeSpeakers.put(fb.characterId,activeSpeakers.get(fb.characterId).intValue()+1);
					logger.info("unknown intent; speaker {} started speaking block {}",fb.characterId,fb.bmlId);
				}
				else if (fb.syncId.equals("end")) {
					//this speaker now stopped speaking unknown speech. no addressee saliency therefore
					activeSpeakers.put(fb.characterId,activeSpeakers.get(fb.characterId).intValue()-1);
					logger.info("unknown intent; speaker {} stopped speaking block {}",fb.characterId,fb.bmlId);
				}				
			}
		}
		else {
			if (fb.syncId.equals("start")) {
				//this speaker now started carrying out that planned intent, addressed to the known addressee
				activeSpeakers.put(pi.charId,activeSpeakers.get(pi.charId).intValue()+1);
				if (activeAddressees.get(pi.addressee)!=null)
				{
					activeAddressees.put(pi.addressee,activeAddressees.get(pi.addressee).intValue()+1);
				}
				logger.info("speaker {} started speaking block {}",pi.charId,pi.requestId);
			}
			else if (fb.syncId.equals("end")) {
				//this speaker now stopped carrying out that planned intent, addressed to the known addressee
				activeSpeakers.put(pi.charId,activeSpeakers.get(pi.charId).intValue()-1);
				if (activeAddressees.get(pi.addressee)!=null)
				{
					activeAddressees.put(pi.addressee,activeAddressees.get(pi.addressee).intValue()-1);
				}
				logger.info("speaker {} stopped speaking block {}",pi.charId,pi.requestId);
			}
		}
	}


	@Override
	public void onPredictionFeedback(BMLPredictionFeedbackJSON fb) { }


	@Override
	public void onWarningFeedback(BMLWarningFeedbackJSON fb) { }
	
	// ================== remember addressee etc for the given planned bmlID
	@Override
	public void onIntentPlanned(PlannedIntent pi) {
		plannedIntentsByBlockId.put(pi.requestId,pi);
		//logger.info("***************************************************************");
		//logger.info("pi: \"{}\"",pi.requestId);
	}

	// ======== core saliency mapping code
	
    protected boolean initNewMap(String mapId, boolean isLeaky, double defaultValue)
    {
        allMapIds.add(mapId);
        mapIsLeaky.put(mapId,new Boolean(isLeaky));
        defaultValueForMap.put(mapId,new Double(defaultValue));
        //for all characters, add map to the maps
        for (String charId:allCharacterIds)
        {
            saliencyMaps.get(charId).put(mapId,new HashMap<String,Double>());
        }
        return true;
    }
    protected boolean initNewCharacter(String charId, String engine)
    {
        charId = charId.replace("\"","");
        logger.debug("new character {} in saliency maps",charId);
        //add new character
        allCharacterIds.add(charId);
		//add engine
		if (engine.equalsIgnoreCase("greta")) enginePerCharacterId.put(charId, gir);
		else if (engine.equalsIgnoreCase("asap")) enginePerCharacterId.put(charId, air);
		else enginePerCharacterId.put(charId, null);
        //for all maps, add one map for the new char
        HashMap<String,HashMap<String,Double>> newMaps = new HashMap<String,HashMap<String,Double>>();
        for (String mapId:allMapIds)
        {
            newMaps.put(mapId,new HashMap<String,Double>());
        }
        saliencyMaps.put(charId,newMaps);
        previousTargetPerCharacter.put(charId,"NONE");
		activeSpeakers.put(charId,0);
		activeAddressees.put(charId,0);
        return true;
    }
	

    protected void updateSaliency()
    {
        //for all characters: a little saliency? or at least a 0.0 so we know they exist?
        
        //for all characters, 
        for (String charId:allCharacterIds)
        {
            //update their "speaker saliency"
            HashMap<String,Double> sps = saliencyMaps.get(charId).get("speakerSaliency");
            sps.clear();
            // potential: user is always a little bit salient regarding speaker saliency. 
            //sps.put("head_COUCH_USER", new Double(0.15));
			//if (activeSpeakers.size()>0) logger.warn("================== SPEAKERS");
            for (String keySp:activeSpeakers.keySet())
            {                
                if (!keySp.equals(charId)) //speakers are salient but not if it is self...
                {
                    if (activeSpeakers.get(keySp).intValue()>0) {
						sps.put("head_"+keySp, new Double(0.2));
						//logger.warn("putting in target here {} to {}",charId,keySp);
					}
                }
				else if (activeSpeakers.get(keySp).intValue()>0) //if self is speaking, all active addressee is salient
//TODO: that is not quite right. Only MY addressees should be salient				
				{
					//also: great should not gaze at addressees
					if (!charId.equals("COUCH_CAMILLE") && !charId.equals("COUCH_LAURA")) {
						boolean specificAddressee = false;
						for (String keyAddr:activeAddressees.keySet())
						{                
							if (!keyAddr.equals("ALL") && activeAddressees.get(keyAddr).intValue()>0) 
							{
								sps.put("head_"+keyAddr, new Double(0.2));
								specificAddressee = true;
							}
						}
						if (!specificAddressee) 
						{
							for (String targetId:allCharacterIds)
							{
								if (!targetId.equals(charId)) sps.put("head_"+targetId, new Double(0.2));
							}
						}
					}
				}                
            }   
            
            //update to this map is done below when determining the new gaze target... but we need it here already to determine the cumulative saliency at this moment
            HashMap<String,Double> pbs = saliencyMaps.get(charId).get("penaltiesAndBoosts");
            
            //in future: also do other saliency updates 
            
            //calculate cumulative saliency as the sum of all other saliency maps. 
            HashMap<String,Double> cs = saliencyMaps.get(charId).get("cumulativeSaliency");
            cs.clear();
            for (String targetCharId:allCharacterIds)
            {   
                //first: all characters have saliency of at least 0
                double finalSaliencyForTarget = 0d;
                //add speaker saliency for target
                if (sps.containsKey("head_"+targetCharId)) finalSaliencyForTarget += sps.get("head_"+targetCharId).doubleValue();
                //add penalty/boost for target
                if (pbs.containsKey("head_"+targetCharId)) finalSaliencyForTarget += pbs.get("head_"+targetCharId).doubleValue();
                //store as cumulative saliency
                cs.put("head_"+targetCharId, new Double(finalSaliencyForTarget));
                //if (finalSaliencyForTarget>0.01)logger.warn("saliency for "+charId+": {}, {}", targetCharId, finalSaliencyForTarget);
            }

            //now start calculating most salient target(s) based on cumulative saliency...
            
            //first determine maximum saliency
            double max = 0;
            for (Double value:cs.values())
            {
                if (value.doubleValue()>max) max=value.doubleValue();
            }
            
            ArrayList<String> maxSalientTargets = new ArrayList<String>();
            //get all targets that are "max" salient +- epsilon
            if (max>0)
            {
                for (Map.Entry<String,Double> e:cs.entrySet())
                {
                    if (max-e.getValue().doubleValue() <= sameSaliencyEpsilon)
                    {
                        //  logger.warn("max {}",e.getKey());
                        maxSalientTargets.add(e.getKey());
                    }
                }
            }
            
            //now figure out whether we will do a gaze shift to new max salient target if it is different from previous one
            if (maxSalientTargets.size()>0)
            {
                //pick a random new target from the set
                String newTarget = maxSalientTargets.get(random.nextInt(maxSalientTargets.size()));
                String prevTarget = previousTargetPerCharacter.get(charId);
                if (!newTarget.equals(prevTarget))
                {
                    sendGazeShift(newTarget,charId);
                    //logger.debug("new target {} for {}",newTarget,charId);
                    double boostValueNT = 0;
                    double boostValuePT = 0;
                    //no prefixing of head_ here as we already take the targets out of the other maps
                    if (pbs.containsKey(newTarget)) boostValueNT = pbs.get(newTarget).doubleValue();
                    if (pbs.containsKey(prevTarget)) boostValuePT = pbs.get(prevTarget).doubleValue();
                    //boost new target
                    boostValueNT += .15;
                    //penalty for old target to prevent too quick return
                    boostValuePT -= .15;
                    //store new values
                    pbs.put(newTarget,new Double(boostValueNT));
                    pbs.put(prevTarget,new Double(boostValuePT));
                    previousTargetPerCharacter.put(charId,newTarget);
                    //logger.warn("shifting gaze from {} to {}",prevTarget,newTarget);
                }
                
            }
            
            //do all the leaky stuff for the leaky maps... exponentially back to default value for every saliency value in every leaky map!
            
            for (String mapId:allMapIds)
            {
                if (mapIsLeaky.get(mapId).booleanValue())
                {
                    //logger.warn("leak {}",mapId);
                    double defaultValue = defaultValueForMap.get(mapId).doubleValue();
                    for (String ci:allCharacterIds)
                    {   
                        HashMap<String,Double> nextMap = saliencyMaps.get(ci).get(mapId);
                        for (Map.Entry<String,Double> e:nextMap.entrySet())
                        {
                            double v = e.getValue().doubleValue();
                            v= (v-defaultValue)*leakPerTick+defaultValue;
                            if (Math.abs(v-defaultValue)<(sameSaliencyEpsilon))
                            {
                                v = defaultValue;
                                //logger.warn("dropped {} in map {}",e.getKey(),mapId);
                            }
                            nextMap.put(e.getKey(),new Double(v));
                        }
                    }
                }
            }
            
            
        }
        
        
        
    }

    protected void sendGazeShift(String target, String charId)
    {
		if (enginePerCharacterId.get(charId) == air) sendGazeShiftAIR(target, charId);
		else if (enginePerCharacterId.get(charId) == gir) sendGazeShiftGIR(target, charId);
	}

    protected void sendGazeShiftAIR(String target, String charId) {
		String bmlStringHeader="<bml id=\"SSE_"+(socialSaliencyBehaviourRequestCount++)+"\" xmlns=\"http://www.bml-initiative.org/bml/bml-1.0\"  xmlns:mwe=\"http://hmi.ewi.utwente.nl/middlewareengine\"  xmlns:bmla=\"http://www.asap-project.org/bmla\" characterId=\""+charId+"\">";
		String bmlGaze = "<gazeShift id=\"gaze1\" influence=\"NECK\" target=\""+target+"\" start=\"0\" end=\"0.7\"/>";
		String bmlStringFooter = "</bml>";
		String bmlString = bmlStringHeader+bmlGaze+bmlStringFooter;
		
		bmlEnv.performRealizationRequest(bmlString,charId);
	}
    protected void sendGazeShiftGIR(String target, String charId) {
		String bmlStringHeader="<?xml version=\"1.0\" encoding=\"ISO-8859-1\" ?>\n<bml id=\"SSE_"+(socialSaliencyBehaviourRequestCount++)+"\" xmlns=\"http://www.bml-initiative.org/bml/bml-1.0\" characterId=\""+charId+"\">";
		String bmlGaze = "<gazeShift id=\"gaze1\" influence=\"NECK\" target=\""+target+"\" start=\"0\" end=\"0.7\"/>";
		String bmlStringFooter = "</bml>";
		String bmlString = bmlStringHeader+bmlGaze+bmlStringFooter;
		
		fmlEnv.performBmlRealizationRequest(bmlString,charId);
	}	

	//somewhere, upon receiving stuff or modifying stuff, we want to send a message to Flipper. This goes like this:
	//lastRequestId = enqueueMessage(om.convertValue(new FlipperMoveSetMessage(moveSets), JsonNode.class), "movesets");
	//or see how fmlenvironment handles lists of fml that are to be sent on...
	
	//bmlenv has performRealizationRequest(String bml)
	//fmlenv has performRealizationRequest(String fml)
}
