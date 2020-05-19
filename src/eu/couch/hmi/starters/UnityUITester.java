package eu.couch.hmi.starters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.couch.hmi.actor.Actor;
import eu.couch.hmi.actor.DialogueActor;
import eu.couch.hmi.actor.GenericDialogueActor;
import eu.couch.hmi.actor.UIControllableActor;
import eu.couch.hmi.environments.UIEnvironment;
import eu.couch.hmi.environments.ui.UIProtocolActor;
import eu.couch.hmi.environments.ui.UIProtocolCustomAction;
import eu.couch.hmi.environments.ui.UIProtocolStatusResponse;
import eu.couch.hmi.floormanagement.FCFSFloorManager;
import eu.couch.hmi.floormanagement.IFloorManager;
import eu.couch.hmi.intent.Intent;
import eu.couch.hmi.intent.planner.IIntentPlanner;
import eu.couch.hmi.middleware.MultiMiddlewareManager;
import eu.couch.hmi.moves.FilteredMove;
import eu.couch.hmi.moves.IMoveCollector;
import eu.couch.hmi.moves.IMoveDistributor;
import eu.couch.hmi.moves.IMoveFilter;
import eu.couch.hmi.moves.IMoveListener;
import eu.couch.hmi.moves.Move;
import eu.couch.hmi.moves.MoveSet;
import eu.couch.hmi.moves.selector.IMoveSelector;
import hmi.flipper.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper.environment.IFlipperEnvironment;
import hmi.flipper.environment.MiddlewareEnvironment;
import nl.utwente.hmi.middleware.Middleware;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;

public class UnityUITester implements IMoveDistributor, IMoveSelector, IIntentPlanner, IMoveCollector {


	private ObjectMapper om;
	private Middleware mw;

	public UnityUITester() {
		this.om = new ObjectMapper();

        String propFile = "defaultmiddleware.properties";
        GenericMiddlewareLoader.setGlobalPropertiesFile(propFile);
        
		Properties props = new Properties();
		props.put("iTopic","COUCH/UI/REQUESTS");
		props.put("oTopic","COUCH/UI/STATE");
		
        String loaderclass = "nl.utwente.hmi.middleware.activemq.ActiveMQMiddlewareLoader";

		GenericMiddlewareLoader gml = new GenericMiddlewareLoader(loaderclass, props);
		this.mw = gml.load();
		

	}
	
	
	public void sendUIRequest() {
		List<UIProtocolActor> _actors = new ArrayList<UIProtocolActor>();
		
		int dialogueID = 1;
		
		IFloorManager fm = new FCFSFloorManager();
		
		Actor a = new Actor();
		a.identifier = "aid_1";
		a.name = "Bob";
		a.player = "User";
		a.bml_name = "";
		
		DialogueActor da = new GenericDialogueActor(a, fm, this, this, this, this);
		
		_actors.add(new UIProtocolActor(da, new String[] {"unityTest"}, "", "", new UIProtocolCustomAction[] {}) );

		FilteredMove fm1 = new FilteredMove();
		fm1.score = 1.0f;
		fm1.actorIdentifier = a.identifier;
		fm1.dialogueID = dialogueID;
		fm1.moveID = "move_1";
		fm1.opener = "enter text here";
		fm1.requestUserInput = true;

		FilteredMove fm2 = new FilteredMove();
		fm2.score = 1.0f;
		fm2.actorIdentifier = a.identifier;
		fm2.dialogueID = dialogueID;
		fm2.moveID = "move_2";
		fm2.opener = "this is a button";

		FilteredMove fm3 = new FilteredMove();
		fm3.score = 1.0f;
		fm3.actorIdentifier = a.identifier;
		fm3.dialogueID = dialogueID;
		fm3.moveID = "move_3";
		fm3.opener = "put more text here";
		fm3.requestUserInput = true;
		
		MoveSet[] moveSets = new MoveSet[] {new MoveSet("aid_1", "bob", dialogueID, new FilteredMove[] {fm1,fm2,fm3})};
		
		UIProtocolStatusResponse res = new UIProtocolStatusResponse(
				_actors.toArray(new UIProtocolActor[0]), moveSets,
				new UIProtocolCustomAction[] {});
		

		JsonNode jn = om.convertValue(res, JsonNode.class);

		mw.sendData(jn);
	}
	
	
	public static void main(String[] args) {
		UnityUITester t = new UnityUITester();
		t.sendUIRequest();
	}


	@Override
	public void registerMoveListener(IMoveListener listener) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void registerMoveFilter(IMoveFilter filter) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void selectMove(DialogueActor actor, MoveSet[] moveSets) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public String planIntent(DialogueActor actor, Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public void cancelIntentPlan(String intentId) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void onMoveCompleted(IMoveListener actor, Move move) {
		// TODO Auto-generated method stub
		
	}


}
