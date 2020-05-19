
var AgentBehaviourWatcher = (function() {
	
	var AgentBehaviourWatcher = function(identifier, bml_name) {
		this.emitter = new EventEmitter();
		this.ignored = [];
		this.bml_name = bml_name;
		this.blocks = {};
		ActorState.call(this, identifier);
	};
	
	AgentBehaviourWatcher.prototype = Object.create(ActorState.prototype);
	
	/* events:
	 * 	- speakStart
	 *  - speakPause (emitted between bml blocks & pauses (TODO: ))
	 *  - speakEnd ( ~= yield turn, no other speech planned)
	 *  ... todo...
	 */
	AgentBehaviourWatcher.prototype.on = function() {
		return this.emitter.on.apply(this.emitter, arguments);
	}
	
	AgentBehaviourWatcher.prototype.UpdateState = function() {
		this.RemoveCompleted();
    	var active = this.GetActiveBehavioursOfType("speak");
    	//print(this.bml_name+": "+JSON.stringify(this.blocks, null, 2));
    	this.speaking = active.length > 0;
	}
	
	AgentBehaviourWatcher.prototype.GetActiveBehavioursInBlock = function(bmlId, behaviourType) {
		var res = [];
	    if (this.blocks.hasOwnProperty(bmlId)) {
			for (var behaviourId in this.blocks[bmlId].behaviours) {
			    if (!this.blocks[bmlId].behaviours.hasOwnProperty(behaviourId)) continue;
		    	if (behaviourType && this.blocks[bmlId].behaviours[behaviourId].behaviourType != behaviourType) continue;
		    	
		    	if (this.blocks[bmlId].behaviours[behaviourId].syncs.hasOwnProperty('start') 
		    	 && !this.blocks[bmlId].behaviours[behaviourId].syncs.hasOwnProperty('end')) {
		    		res.push(this.blocks[bmlId].behaviours[behaviourId]);
		    	}
			}
	    } else {
		    print("WARN: bml block with ID does not exist (anymore): "+bmlId);
	    }
    	return res;
	}
	
	AgentBehaviourWatcher.prototype.GetBlock = function(bmlId) {
	    if (this.blocks.hasOwnProperty(bmlId)) {
	    	return this.blocks[bmlId];
	    } 
    	return undefined;
	}
	
	
	AgentBehaviourWatcher.prototype.GetActiveBehavioursOfType = function(behaviourType) {
		var res = [];
		for (var bmlId in this.blocks) {
		    if (this.blocks.hasOwnProperty(bmlId)) {
		    	var behavioursInBlock = this.GetActiveBehavioursInBlock(bmlId, behaviourType);
		    	res = res.concat(behavioursInBlock);
		    }
		}
    	return res;
	}
	
	AgentBehaviourWatcher.prototype.RemoveCompleted = function() {
		for (var bmlId in this.blocks) {
		    if (this.blocks.hasOwnProperty(bmlId)) {
	        	var active = this.GetActiveBehavioursInBlock(bmlId);
	        	if (active.length == 0 && this.blocks[bmlId].state == "DONE") {
	        		//print("Block ended cleanly: "+bmlId);
	        		delete this.blocks[bmlId];
	        	} else if (active.length > 0  && this.blocks[bmlId].state == "DONE") {
	        		// Behaviours in this block were interrupted(?)
	        		// ... notify behaviour listeners
	        		//print("Block ended, some behaviours unfinished...: "+bmlId);
	        		delete this.blocks[bmlId];
	        	}
		    }
		}
	}
	
	// behaviourType can be null....
	AgentBehaviourWatcher.prototype.SetBehaviourState = function(behaviourType, bmlId, behaviourId, state, sync) {
		if (!this.blocks.hasOwnProperty(bmlId)) {
			//print("New block: "+bmlId+" state: "+state);
			this.blocks[bmlId] = { bmlId: bmlId, state: state, behaviours: {} };
		} else if (state) {
			this.blocks[bmlId].state = state;
		}
		
		if (behaviourId && behaviourType) {
			if (!this.blocks[bmlId].behaviours.hasOwnProperty(behaviourId)) {
				//print("\tNew Behaviour: "+bmlId+":"+behaviourId+" ("+behaviourType+")");
				this.blocks[bmlId].behaviours[behaviourId] = {
					bmlId: bmlId,
					behaviourId: behaviourId,
					behaviourType: behaviourType,
					syncs: {}
				};
				// TODO: behaviour state
			} else {
				// TODO: behaviour state
			}
		}
		
		//print("BML "+bmlId+" state: "+this.blocks[bmlId].state);
		
		if (sync != null) {
			if (!this.blocks.hasOwnProperty(bmlId) || !this.blocks[bmlId].behaviours.hasOwnProperty(behaviourId)) {
				//print("WARN: sync for unknown behaviour "+bmlId+":"+behaviourId+":"+sync.syncId);
				return true;
			}
			
			if (this.blocks[bmlId].behaviours[behaviourId].syncs.hasOwnProperty(sync.syncId)) {
				//print("Sync already received: "+bmlId+":"+behaviourId+":"+sync.syncId);
				this.blocks[bmlId].behaviours[behaviourId].syncs[sync.syncId].globalTime = sync.globalTime;
			} else {
				this.blocks[bmlId].behaviours[behaviourId].syncs[sync.syncId] = {
					syncId: sync.syncId,
					globalTime: sync.globalTime
				};
				
				//print("\t "+bmlId+":"+behaviourId+":"+sync.syncId+" @ "+sync.globalTime);
			}
			
		}
		
	}
	
	/*
	Before scheduling: predictionFeedback for block, takes into account composition/appendAfter attributes, provides block start prediction.
	After scheduling: predictionFeedback for block+behaviors provides block start and end time prediction, internal timing of behaviors in the block, timing based on e.g. rest posture.
	At block start: blockProgress, block start feedback
	Directly after block start (unless the block ends instantly (the block is empty or all containing behavior fails to be scheduled)): predictionFeedback for block+behaviors, provides block start and end time prediction, internal timing of behaviors in the block, timing based on e.g. start posture.
	During execution:
	syncPointProgress feedback for syncpoints of all behaviors in the block
	predictionFeedback whenever timing predictions change (e.g. through anticipator time changes, bottom-up adaptations, parametervaluechanges that affect timing) [TO IMPLEMENT!]
	At end: blockProgress, block end feedback
	*/
	AgentBehaviourWatcher.prototype.ParseFeedback = function(fb) {

		//print(JSON.stringify(fb, null, 2));
		
		if (fb.bmlFeedbackType == "predictionFeedback") {
			for (var i = 0; i < fb.bmlBlockPredictions.length; i++) {
				var haveBehaviour = false;
				
				// TODO: when "repeating", ProcAnim engine produces two behaviors with the same behaviour ID
				// - I think there is some timing information missing in the JSON, as both predictions have the same
				//  timing in their sync points... 
				//  ..there is an "iteration" parameter in sync points that isn't set, perhaps that helps?
				for (var j = 0; j < fb.bmlBehaviourPredictions.length; j++) {
			    	haveBehaviour = true;
			    	this.SetBehaviourState(
						fb.bmlBehaviourPredictions[j].behaviourType,
						fb.bmlBlockPredictions[i].id,
						fb.bmlBehaviourPredictions[j].behaviourId,
						fb.bmlBlockPredictions[i].status,
						null
					);
				}
				
				if (!haveBehaviour) {
					this.SetBehaviourState(
						null,
						fb.bmlBlockPredictions[i].id,
						null,
						fb.bmlBlockPredictions[i].status,
						null
					);
				}
			}
		} else if (fb.bmlFeedbackType == "blockProgress") {
			// TODO: could also handle fb.globalTime here...
			if (fb.syncId == "end") {
				this.SetBehaviourState(
					null,
					fb.bmlId,
					null,
					"DONE",
					null
				);
			} else if (fb.syncId == "start") {
				this.SetBehaviourState(
					null,
					fb.bmlId,
					null,
					"START",
					null
				);
			}
		} else if (fb.bmlFeedbackType == "syncPointProgress") {
			this.SetBehaviourState(
				null,
				fb.bmlId,
				fb.behaviorId,
				null,
				fb
			);
		} else {
			print("WARN: UNHANDLED FEEDBACK... "+JSON.stringify(fb))
		}
	}
	
	return AgentBehaviourWatcher;
	
})();