var ActorState = (function() {
	
	var ActorState = function(identifier) {
		this.identifier = identifier;
		this.speaking = false;
	};
	
	ActorState.prototype.isSpeaking = function() {
		return this.speaking;
	}
	
	return ActorState;
	
})();