var FloorBehaviourManager = (function() {
	
	var FloorBehaviourManager = function(watcher, restGazeTarget) {
		this.notIdleSince = new Date().getTime();
		this.restGazeTarget = restGazeTarget;
		this.watcher = watcher;
		this.currentGaze = undefined;
		this.currentHead = undefined;
		
	};

	FloorBehaviourManager.prototype.update = function() {
		//var headBehaviours = this.watcher.GetActiveBehavioursOfType("head");
		if (this.currentGaze) {
			var gazeBlock = this.watcher.GetBlock(this.currentGaze.bmlId);
			if (!gazeBlock && this.currentGaze.state != "WAIT_FOR_PLAN") {
				this.currentGaze = undefined;
			} else if (gazeBlock && this.currentGaze.state != "WAIT_FOR_INTERRUPT") {
				this.currentGaze.state = gazeBlock.state;
			}
		}

		//var gazeBehaviours = watcher.GetActiveBehavioursOfType("gaze");
		if (this.currentHead) {
			var headBlock = this.watcher.GetBlock(this.currentHead.bmlId);
			if (!headBlock && this.currentHead.state != "WAIT_FOR_PLAN") {
				this.currentHead = undefined;
			} else if (headBlock && this.currentHead.state != "WAIT_FOR_INTERRUPT") {
				this.currentHead.state = headBlock.state;
			}
		}
	};
	
	FloorBehaviourManager.prototype.ensureGazeAt = function(target) {
		if (!this.currentGaze || this.currentGaze.target != target) {
			var randomStart = 0.1+Math.random()*0.5;
			var randomReady = randomStart + 0.5+Math.random()*0.5;
			var gazePlan = new PLN.PlanTree({ charId: this.watcher.bml_name })
				.Block({ id: "FLOOR_GAZE_SHIFT", composition: "MERGE" })
				.Gaze({ id: "g1", "bmlt:dynamic":"true", target:target,  influence: "WAIST",
					start: randomStart, ready: randomReady, relax: 179, end: 180,
				});
			var _gaze = gazePlan.GetBehaviourRef("FLOOR_GAZE_SHIFT", "g1");
			_gaze.target = target;
			_gaze.state = "WAIT_FOR_PLAN";
			this.currentGaze = _gaze;

			var res = gazePlan.Render();
			print("GAZE BML "+this.watcher.bml_name+": "+JSON.stringify(res.bml, null, 2));
			ENV.queueMessage("bml", "send_bml", { bml: res.bml });
		}
	}
	
	FloorBehaviourManager.prototype.ensureNodPlanned = function() {
		if (!this.currentHead) {
			var randomRepetition = (1+Math.floor(Math.random() * 3))*2;
			var randomStart = 1+Math.random()*4;
			var randomDuration = (randomRepetition * 0.4) * (1+Math.random()*0.4);
			var randomAmount = 0.01 + Math.random() * 0.04;
			
			var headPlan = new PLN.PlanTree({ charId: this.watcher.bml_name })
				.Block({ id: "FLOOR_HEAD", composition: "MERGE" })
			headPlan
				.Head({ id: "h1", lexeme:"NOD", 
					start: randomStart, 
					end:   randomStart+randomDuration, 
					amount: randomAmount,  
					repetition: randomRepetition
				});
			var _head = headPlan.GetBehaviourRef("FLOOR_HEAD", "h1");
			_head.state = "WAIT_FOR_PLAN";
			this.currentHead = _head;

			var res = headPlan.Render();
			print("HEAD BML "+this.watcher.bml_name+": "+JSON.stringify(res.bml, null, 2));
			ENV.queueMessage("bml", "send_bml", { bml: res.bml });
		}
	}
	

	FloorBehaviourManager.prototype.listen = function(target) {
		this.notIdleSince = new Date().getTime();
		this.ensureGazeAt(target);
		this.ensureNodPlanned();
	}
	

	FloorBehaviourManager.prototype.idle = function() {
		var now = new Date().getTime();
		if (now - this.notIdleSince > 2500) {
			this.ensureGazeAt(this.restGazeTarget);
		}
	}

	FloorBehaviourManager.prototype.cancel = function() {
		this.notIdleSince = new Date().getTime();
		this.stopAll();
	}
	
	FloorBehaviourManager.prototype.stopAll = function() {
		if (!this.currentGaze && !this.currentHead) return;
		var cancelPlan = new PLN.PlanTree({ charId: this.watcher.bml_name })
			.Block({ id: "FLOOR_INTERRUPT_CANCEL", composition: "MERGE" });
		
		var haveCancel = false;
		if (this.currentGaze && this.currentGaze.state != "WAIT_FOR_PLAN" && this.currentGaze.state != "WAIT_FOR_INTERRUPT") {
			this.interrupt(cancelPlan, this.currentGaze.bmlId, this.currentGaze.behaviourId);
			this.currentGaze.state = "WAIT_FOR_INTERRUPT";
			haveCancel = true;
		}
		
		if (this.currentHead && this.currentHead.state != "WAIT_FOR_PLAN"&& this.currentHead.state != "WAIT_FOR_INTERRUPT") {
			this.interrupt(cancelPlan, this.currentHead.bmlId, this.currentHead.behaviourId);
			this.currentHead.state = "WAIT_FOR_INTERRUPT";
			haveCancel = true;
		}
		
		if (haveCancel) {
			cancelPlan.Render();
			ENV.queueMessage("bml", "send_bml", { bml: cancelPlan.bml });
		}
	}
	
	
	
	FloorBehaviourManager.prototype.interrupt = function(plan, bmlId, behaviourId) {
		plan.Generic("bmla:interrupt", { id:"intrpt"+bmlId+behaviourId, target:bmlId, start: 0 });
	};
	
	return FloorBehaviourManager;
	
})();