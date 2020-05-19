
var PLN = (function() {
	// PRIVATE
	var _syncPointASAP = " <sync id=\"{{id}}\"/> ";
	var _syncPointGRETA = " <tm id=\"{{id}}\"/> ";
	var _syncPointMSAPIXML = " <bookmark mark=\"{{id}}\" /> ";
	var _syncPointSSML = " <mark name=\"{{id}}\"/> ";
	
	var _speech = "\
<speech {{#each params}}{{@key}}=\"{{this}}\" {{/each}}>\n\
<text>{{ Text text 'ASAP' }}{{#if appendEndSync }}{{ Sync appendEndSyncId \"ASAP\" }}{{/if}}</text>\n\
{{ SpeechDescription this }}\n\
</speech>";
	
	var _speech_description = "\
<description priority=\"{{#if description.priority}}{{priority}}{{else}}2{{/if}}\" type=\"application/ssml+xml\">\n\
  <speak xmlns=\"http://www.w3.org/2001/10/synthesis\">\n\
{{#if description.voice }}\
    <voice required=\"Name={{description.voice}}\" />\n\
{{/if}}\
    {{ Text description.text 'MSAPI' }}\n\
{{#if appendEndSync }}\
	{{ Sync appendEndSyncId 'MSAPI' }}\
{{/if}}\
  </speak>\n\
</description>";
	
	var _gesture = "<gesture {{#each params}}{{@key}}=\"{{this}}\" {{/each}} />";
	
	var _generic = "<{{type}} {{#each params}}{{@key}}=\"{{this}}\" {{/each}} />";
    
    var _pointing = "<pointing {{#each params}}{{@key}}=\"{{this}}\" {{/each}} />";
    
    var _head = "<head {{#each params}}{{@key}}=\"{{this}}\" {{/each}} />";
	
	var _postureShift = "\
<postureShift {{#each params}}{{@key}}=\"{{this}}\" {{/each}}>\n\
	<stance type=\"{{stance}}\"/>\n\
 	<pose part=\"{{part}}\" lexeme=\"{{lexeme}}\"/>\n\
</postureShift>";
	
	var _faceLexeme = "<faceLexeme {{#each params}}{{@key}}=\"{{this}}\" {{/each}} />";
	
	var _gazeShift = "<gazeShift {{#each params}}{{@key}}=\"{{this}}\" {{/each}} />";
	
	var _gaze = "<gaze {{#each params}}{{@key}}=\"{{this}}\" {{/each}} />";
	
	var _bmlBlock = "\
<bml {{#each params}}{{@key}}=\"{{this}}\" {{/each}}>\n\
{{#each behaviors }}\n\
  {{ SpeechBehavior this }} \
  {{ GenericBehavior this }} \
  {{ GestureBehavior this }} \
  {{ PointingBehavior this }} \
  {{ HeadBehavior this }} \
  {{ PostureShiftBehavior this }} \
  {{ GazeShiftBehavior this }} \
  {{ GazeBehavior this }} \
  {{ FaceLexemeBehavior this }} \
{{/each}}\n\
</bml>";
	
	var bmlCounter = 100;
	
	var currentPlan = null;
	
	//DEPRICATED: we don't want to use lookuptable hacks anymore
	function Init(_voiceHackLUT, _addresseeTargetLUT) {
		Init();
	}
	
	// Initialize the BML Templating engine by precompiling
	// partials and registering helpers.
	function Init() {
		
		var syncPointASAP = Handlebars.compile(_syncPointASAP);
		var syncPointGRETA = Handlebars.compile(_syncPointGRETA);
		var syncPointMSAPIXML = Handlebars.compile(_syncPointMSAPIXML);
		var syncPointSSML = Handlebars.compile(_syncPointSSML);
		
		//var compositionBML = Handlebars.compile(_compositionBML);
		//var compositionBMLA = Handlebars.compile(_compositionBMLA);
		
		var speechDescription = Handlebars.compile(_speech_description);
		var speechBehavior = Handlebars.compile(_speech);
		var gestureBehavior = Handlebars.compile(_gesture);
		var genericBehavior = Handlebars.compile(_generic);
		var pointingBehavior = Handlebars.compile(_pointing);        
		var headBehavior = Handlebars.compile(_head);
		var postureShiftBehavior = Handlebars.compile(_postureShift);
		var faceLexemeBehavior = Handlebars.compile(_faceLexeme);
		var gazeShiftBehavior = Handlebars.compile(_gazeShift);
		var gazeBehavior = Handlebars.compile(_gaze);
		
		Handlebars.registerHelper('Text', function(text, scope) {
			var txt_tmpl = Handlebars.compile(text);
			var txt_iterp = txt_tmpl(scope);
			var txt_safe =  new Handlebars.SafeString(txt_iterp);
			return txt_safe;
			/*
			if (scope == "MSAPI") {
				return new Handlebars.SafeString(syncPointMSAPIXML({ id: id }));
			} else if (scope == "GRETA") {
				return new Handlebars.SafeString(syncPointGRETA({ id: id }));
			} else if (scope == "SSML") {
				return new Handlebars.SafeString(syncPointSSML({ id: id }));
			} else {
				return new Handlebars.SafeString(syncPointASAP({ id: id }));
			}*/
		});
		
		Handlebars.registerHelper('Sync', function(id, scope) {
			if (scope == "MSAPI" || this == "MSAPI") {
				return new Handlebars.SafeString(syncPointMSAPIXML({ id: id }));
			} else if (scope == "GRETA" || this == "GRETA") {
				return new Handlebars.SafeString(syncPointGRETA({ id: id }));
			} else if (scope == "SSML" || this == "SSML") {
				return new Handlebars.SafeString(syncPointSSML({ id: id }));
			} else if (scope == "ASAP" || this == "ASAP") {
				return new Handlebars.SafeString(syncPointASAP({ id: id }));
			}

			return "";
		});
		
		Handlebars.registerHelper('SpeechDescription', function(context, options) {
			return new Handlebars.SafeString(speechDescription(context));
		});
		
		Handlebars.registerHelper('SpeechBehavior', function(context, options) {
			if (context.type == "speech") {
				return new Handlebars.SafeString(speechBehavior(context));
			} else return "";
		});
		
		Handlebars.registerHelper('GenericBehavior', function(context, options) {
			if (context.isGeneric == true) {
				return new Handlebars.SafeString(genericBehavior(context));
			} else return "";
		});
		
		Handlebars.registerHelper('GestureBehavior', function(context, options) {
			if (context.type == "gesture") {
				return new Handlebars.SafeString(gestureBehavior(context));
			} else return "";
		});
        
        Handlebars.registerHelper('PointingBehavior', function(context, options) {
			if (context.type == "pointing") {
				return new Handlebars.SafeString(pointingBehavior(context));
			} else return "";
		});
		
        Handlebars.registerHelper('HeadBehavior', function(context, options) {
			if (context.type == "head") {
				return new Handlebars.SafeString(headBehavior(context));
			} else return "";
		});
		
		Handlebars.registerHelper('PostureShiftBehavior', function(context, options) {
			if (context.type == "postureShift") {
				return new Handlebars.SafeString(postureShiftBehavior(context));
			} else return "";
		});
		
		Handlebars.registerHelper('FaceLexemeBehavior', function(context, options) {
			if (context.type == "faceLexeme") {
				return new Handlebars.SafeString(faceLexemeBehavior(context));
			} else return "";
		});

		Handlebars.registerHelper('GazeShiftBehavior', function(context, options) {
			if (context.type == "gazeShift") {
				return new Handlebars.SafeString(gazeShiftBehavior(context));
			} else return "";
		});
		
		Handlebars.registerHelper('GazeBehavior', function(context, options) {
			if (context.type == "gaze") {
				return new Handlebars.SafeString(gazeBehavior(context));
			} else return "";
		});


		
		bmlBlock = Handlebars.compile(_bmlBlock);
		return true;
	}
	
	function fixReferences(params, prefix) {
		var referenceKeys = ["start", "end", "ready", "relax", "stroke", "strokeStart", "strokeEnd" ];
		
		return _.object(_.map(params, function(value, key){
			var _key = key;
			var _value = value.toString();
			if (_.contains(referenceKeys, _key) && (_value.match(/:/g) || []).length == 2) {
				_value = prefix+_value;
			}
	        return [_key, _value];
		}));
	}
	
	var PlanTree = function(intent) {
		var uniquePrefix = Math.floor(1000+Math.random()*1000)+"";
		this.intent = intent.intent || {};
		this.engine = intent.engine || "ASAP";
		this.bmlTopic = intent.bmlTopic || "ASAP";
		this.charId = intent.charId;
		this.moveId = intent.moveId;
		this.bmlPrefix = "bml_"+uniquePrefix+"_"+(++bmlCounter)+"_";
		this.bmlBlocks = [];
		return this;
	};

	
	PlanTree.prototype.Block = function(params) {
		if (!params.id) {
			print("WARN: NewBMLBlock: Need to set ID in parameter");
			return this;
		}
		
		if (_.find(this.bmlBlocks, function(bml) { return bml.id == params.id }) != undefined) {
			print("WARN: NewBMLBlock: bml with ID "+params.id+" already in plan.");
			return this;
		}

		// todo: set composition smarter...
		// todo: merge params object with defaults?
		
		var compositionKey = "composition";
		var compositionValue = params.composition || "MERGE";
		
		if (this.bmlBlocks.length > 0 && params.composition == "APPEND") {
			compositionKey = "bmla:appendAfter";
			compositionValue = this.bmlBlocks[this.bmlBlocks.length-1].params.id;
		}
		var block = {
			id: params.id,
			params: {
				characterId: this.charId,
				id: this.bmlPrefix+params.id,
				xmlns: "http://www.bml-initiative.org/bml/bml-1.0",
				"xmlns:bmlt": "http://hmi.ewi.utwente.nl/bmlt",
				"xmlns:bmla":"http://www.asap-project.org/bmla"
			},
			behaviors: []
		};
		
		block.params[compositionKey] = compositionValue;
		this.bmlBlocks.push(block);
		return this;
	}
	
	PlanTree.prototype.Speech = function(params, options) {
		params.id = params.id || "s1";
		//params.start = params.start || 0;
		var voice = options.voice;
		var plainText = options.text.replace(/<(.|\n)*?>/gi, " ");
		_.last(this.bmlBlocks).behaviors.push({
			type: "speech", id: params.id,
			params: fixReferences(params, this.bmlPrefix),
			text: plainText,
			description: { voice: voice, text: options.text }
		});
		return this;
	}
	
	PlanTree.prototype.Gesture = function(params, options) {
		var id = params.id || "g1";
		params.id = id;
		//params.start = params.start || 0;
		_.last(this.bmlBlocks).behaviors.push({
			type: "gesture", id: id,
			params: fixReferences(params, this.bmlPrefix)
		});
		return this;
	}
	
	PlanTree.prototype.Generic = function(tag, params, options) {
		var id = params.id || "o1";
		params.id = id;
		_.last(this.bmlBlocks).behaviors.push({
			type: tag, id: id,
			isGeneric: true,
			params: fixReferences(params, this.bmlPrefix)
		});
		return this;
	}
    
    PlanTree.prototype.Pointing = function(params, options) {
		var id = params.id || "pt1";
		params.id = id;
		//params.start = params.start || 0;
		_.last(this.bmlBlocks).behaviors.push({
			type: "pointing", id: id,
			params: fixReferences(params, this.bmlPrefix)
		});
		return this;
	}
    
    PlanTree.prototype.Head = function(params, options) {
		var id = params.id || "h1";
		params.id = id;
		//params.start = params.start || 0;
		_.last(this.bmlBlocks).behaviors.push({
			type: "head", id: id,
			params: fixReferences(params, this.bmlPrefix)
		});
		return this;
	}
	
	PlanTree.prototype.PostureShift = function(params, options) {
		params.id = params.id || "p1";
		_.last(this.bmlBlocks).behaviors.push({
			id: params.id,
			type: "postureShift",
			params: fixReferences(params, this.bmlPrefix),
			stance: options.stance,
			part: options.part,
			lexeme: options.lexeme
		});
		return this;
	}
	
	PlanTree.prototype.GazeShift = function(params, option) {
		params.id = params.id || "gs1";
		params.start = params.start || 0;
		params.influence = params.influence || "NECK";
		
		_.last(this.bmlBlocks).behaviors.push({
			id: params.id,
			type: "gazeShift",
			params: fixReferences(params, this.bmlPrefix)
		});
		return this;
	}
	
	PlanTree.prototype.Gaze = function(params, option) {
		params.id = params.id || "gg1";
		params.start = params.start || 0;
		params.influence = params.influence || "NECK";
		
		_.last(this.bmlBlocks).behaviors.push({
			id: params.id,
			type: "gaze",
			params: fixReferences(params, this.bmlPrefix)
		});
		return this;
	}
	
	PlanTree.prototype.FaceLexeme = function(params, option) {
		params.id = params.id || "f1";
		params.amount = params.amount || 1;
		params.start = params.start || 0;
		_.last(this.bmlBlocks).behaviors.push({
			id: params.id,
			type: "faceLexeme",
			params: fixReferences(params, this.bmlPrefix)
		});
		return this;
	}
	
	PlanTree.prototype.Finalize = function() {
		var firstSpeechBlockIdx = this.FirstBlockWithBehaviorIdx("speech");
		var firstSpeechBehaviorIdx = this.FirstBehaviorInBlockIdx(firstSpeechBlockIdx, "speech");
		
		var lastSpeechBlockIdx = this.LastBlockWithBehaviorIdx("speech");
		var lastSpeechBehaviorIdx = this.LastBehaviorInBlockIdx(lastSpeechBlockIdx, "speech");
		if (lastSpeechBehaviorIdx >= 0 && firstSpeechBehaviorIdx >= 0) {
		/* TODO: implement automatic minimal move completion sync?
			var endSync = this.intent.parameters.minimalMoveCompletionId || "minimal"
			this.bmlBlocks[lastSpeechBlockIdx].behaviors[lastSpeechBehaviorIdx].appendEndSync = true;
			this.bmlBlocks[lastSpeechBlockIdx].behaviors[lastSpeechBehaviorIdx].appendEndSyncId = endSync;
			this.fullMoveCompletionId = this.bmlBlocks[lastSpeechBlockIdx].params.id+":"+this.bmlBlocks[lastSpeechBlockIdx].behaviors[lastSpeechBehaviorIdx].params.id+":"+endSync;
		*/
			this.intentStartedId =  this.bmlBlocks[firstSpeechBlockIdx].params.characterId + "___" +
									this.bmlBlocks[firstSpeechBlockIdx].params.id + "___" + 
									//this.bmlBlocks[firstSpeechBlockIdx].behaviors[firstSpeechBehaviorIdx].params.id + "___" +
									"start";
			
			this.intentCompletionId = this.bmlBlocks[lastSpeechBlockIdx].params.characterId + "___" +
									  this.bmlBlocks[lastSpeechBlockIdx].params.id + "___" + 
									  //this.bmlBlocks[lastSpeechBlockIdx].behaviors[lastSpeechBehaviorIdx].params.id + "___" +
									  "end";
		} else {
			this.intentStartedId =  this.bmlBlocks[0].params.characterId + "___" +
									this.bmlBlocks[0].params.id + "___" + 
									"start";
						
			this.intentCompletionId = this.bmlBlocks[this.bmlBlocks.length-1].params.characterId + "___" +
									  this.bmlBlocks[this.bmlBlocks.length-1].params.id + "___" + 
									  "end";
		}
		var result = this.Render();
		ClearPlan();
		return result;
	}
	
	PlanTree.prototype.Render = function() {
		this.bml = [];
		for (var i = 0; i < this.bmlBlocks.length; i++) {
			var renderedBML = bmlBlock(this.bmlBlocks[i]);
			this.bml.push(renderedBML);
		}
		return this;
	}
	
	// QUERY HELPERS: THESE ARE NOT CHAINABLE
	PlanTree.prototype.LastBlockWithBehaviorIdx = function(b) {
		if (this.bmlBlocks.length == 0) return -1;
		return _.findLastIndex(this.bmlBlocks, function(block) {
			return _.findLastIndex(block.behaviors, { type: b }) >= 0;
		});
	}
	
	PlanTree.prototype.FirstBlockWithBehaviorIdx = function(b) {
		if (this.bmlBlocks.length == 0) return -1;
		return _.findIndex(this.bmlBlocks, function(block) {
			return _.findIndex(block.behaviors, { type: b }) >= 0;
		});
	}

	PlanTree.prototype.FirstBehaviorInBlockIdx = function(blockIdx, b) {
		if (blockIdx >= this.bmlBlocks.length || blockIdx < 0) return -1;
		return _.findIndex(this.bmlBlocks[blockIdx].behaviors, { type: b });
	}
	
	PlanTree.prototype.LastBehaviorInBlockIdx = function(blockIdx, b) {
		if (blockIdx >= this.bmlBlocks.length || blockIdx < 0) return -1;
		return _.findLastIndex(this.bmlBlocks[blockIdx].behaviors, { type: b });
	}
	
	
	
	
	
	
	
	
	
	
	
	PlanTree.prototype.HasDefaultSurfaceText = function() {
		return this.intent.parameters.text != null && this.intent.parameters.text.length > 0;
	}
	
	PlanTree.prototype.DefaultSurfaceText = function() {
		return this.intent.parameters.text;
	}
	
	PlanTree.prototype.HasAddressee = function() {
		return this.intent.parameters.addressee != null && this.intent.parameters.addressee.length > 0;
	}
	
	PlanTree.prototype.AddresseeIsAll = function() {
		return this.intent.parameters.addressee != null && this.intent.parameters.addressee == "ALL";
	}
	
	PlanTree.prototype.GetAddresseeTarget = function() {
		if (!this.HasAddressee()) return "NO_TARGET";
		return "head_"+this.intent.parameters.addressee;
	}
	
	
	
	
	
	
	
	
	// Returns a reference to the first speech with suffix, or returns "other"
	PlanTree.prototype.FirstRefOr = function(behavior, suffix, other) {
		var blockIdx = this.FirstBlockWithBehaviorIdx(behavior);
		var behaviorIdx = this.FirstBehaviorInBlockIdx(blockIdx, behavior);
		if (behaviorIdx < 0) return other;
		if (suffix.length > 0 && suffix.charAt(0) != ":") suffix = ":"+suffix; 
		return this.bmlBlocks[blockIdx].id+":"+this.bmlBlocks[blockIdx].behaviors[behaviorIdx].id+suffix;
	}
	
	PlanTree.prototype.GetBlockRef = function(blockId) {
		var block = _.find(this.bmlBlocks, function(block){ return block.id == blockId; });
		if (!block) return undefined;
		return block.params.id;
	}
	
	PlanTree.prototype.GetBehaviourRef = function(blockId, behaviorId) {
		var block = _.find(this.bmlBlocks, function(block){ return block.id == blockId; });
		if (!block) return undefined;
		var behavior = _.find(block.behaviors, function(behavior){ return behavior.id == behaviorId; });
		if (!behavior) return undefined;
		return {
			bmlId: block.params.id,
			behaviourId: behavior.params.id
		}
	}
	
	PlanTree.prototype.MoveIdEquals = function(id) {
		return id.equals(this.moveId);
	}
	
	function SetPlan(p) {
		currentPlan = p;
		return true;
	}
	
	function ClearPlan() {
		currentPlan = null;
		return true;
	}
	
	function CurrentPlan() {
		return currentPlan;
	}
	
	// EXPOSED API
    return {
        Init: Init,
        PlanTree: PlanTree,
        CP: CurrentPlan,
        CurrentPlan: CurrentPlan,
        SetPlan: SetPlan,
        ClearPlan: ClearPlan
    };
})();