// Adapted from nanoevents by Andrey Sitnik <andrey@sitnik.ru>
// https://github.com/ai/nanoevents (MIT)
// This is not the same as the EventEmitter class from node.js!
var EventEmitter = (function() {
	var EventEmitter = function(bml_name) {
		this.events = {}
	};
	
	EventEmitter.prototype.emit = function(event) {
	    var args = [].slice.call(arguments, 1)
		// Array.prototype.call() returns empty array if context is not array-like
		;[].slice.call(this.events[event] || []).filter(function (i) {
		  i.apply(this, args) // this === global or window
		})
	};
	
	EventEmitter.prototype.on = function(event, cb) {
		if (process.env.NODE_ENV !== 'production' && typeof cb !== 'function') {
		  throw new Error('Listener must be a function')
		}

		(this.events[event] = this.events[event] || []).push(cb)

		return function () {
		  this.events[event] = this.events[event].filter(function (i) {
			return i !== cb
		  })
		}.bind(this)
	};
	
	EventEmitter.prototype.once = function(event, cb) {
		var unbind = this.on(event, function () {
			unbind();
			cb.apply(this, arguments);
		})
		return unbind;
	}
	
	EventEmitter.prototype.unbindAll = function() {
		this.events = {};
	}
	
    return EventEmitter;
})();
