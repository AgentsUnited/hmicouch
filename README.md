# The Conversational Intent Planner module
This Conversational Intent Planner (CIP) module is responsible for generating appropriate conversational intents for the group of agents in the council. It sits between the [Topic Selection Engine (TSE)](https://github.com/AgentsUnited/topic-selection-engine) & [Dialogue and Argumentation Framework (DAF)](https://github.com/AgentsUnited/daf) and the behaviour realizers [ASAP](https://github.com/ArticulatedSocialAgentsPlatform) & [GRETA](https://github.com/isir/greta). It translates high-level dialogue moves from DAF to more low-level (group) behaviours for individual agents. Additionally, it is the main point where user moves are received and forwarded for processing. While the user and agents progress through a dialogue, it also stores certain information in the [Shared Knowledge Base (SKB)](https://github.com/woolplatform/wool/tree/master/java/WoolWebService)

The core of this module is built upon the [Flipper2.0](https://github.com/hmi-utwente/flipper-2.0) dialogue manager. Within this module we define several loosely-connected environments that focus on a specific task. 

Conceptually, these environments are responsible for the following:
- **Talking with SKB** - Handles user authentication and storing/retrieving certain vairables.
- **Talking with Topic Selection** - The CIP is the main starting point for kicking off a dialogue with a user. It asks the [TSE](https://github.com/AgentsUnited/topic-selection-engine) for a dialogue that is suitable for the current user at the current time.
- **Talking with DAF** - Loads the dialogue in [DAF](https://github.com/AgentsUnited/daf), making sure that the various actors are all represented by an agent or the user. Then, a series of move selections is orchestrated while actors go through the dialogue.
- **Controlling the user interface** - Moves received from DAF are presented to the user/woz who can make a final selection of which move to execute. The [Unity scene](https://github.com/AgentsUnited/unityproject) contains an overlay interface for each of the actors, showing the various available moves at that point in time. A user or WoZ may select a move, which is then forwarded to DAF, as described above.
- **Generating conversational intents** - Once a user/WoZ has selected a move, [BML](http://www.mindmakers.org/projects/bml-1-0/wiki/Wiki) for speech and gestures is generated for each of the affected ASAP and GRETA agents. The CIP offers basic floor management and nonverbal saliency gaze behaviour.
- **ASAP** - Although ASAP is maintained in a separate repository, we include some convenient starter classes and configuration in the CIP module.

For the communication with external modules we support a variety of common middlewares through a [Middleware Abstraction Layer](https://github.com/ArticulatedSocialAgentsPlatform/HmiCore/tree/master/HmiMiddlewareAbstractionLayer/src/nl/utwente/hmi/middleware). This allows us to easily (re)configure our module to use any combination of specific middlewares, without changing the underlying code that uses these middlewares.

We use the following specific middlewares:
- ActiveMQ for DAF, the user interface, ASAP, and GRETA
- HTTP (POST/GET) requests for the CCE and SKB (RESTful APIs)
- UDP for streaming audio and joint rotations for the various agents from ASAP to the Unity scene

# Build
To build this module you need Ant and the HMI Build Tool, available in [this repository](https://github.com/ArticulatedSocialAgentsPlatform/hmibuild/tree/master). It must be placed in the parent folder, next to the *intent-planner* folder. Then run the commands `ant clean`, `ant resolve` and `ant compile`. Take a look at how it was done in the [demonstrator repository](https://github.com/AgentsUnited/demonstrator) readme instructions.

# Configuring the system

## ASAP agents
Within the CIP module we include some helpful configuration and launchers for running the ASAPRealizer. The main configuration file is `resource/couchlaunch.json`. Here, you configure which agents (charId) should be loaded by ASAP, according to which specification files (spec).

The actual configuration for each agent is located in the spec files. By default, `multiAgentSpecs/uma/UMA1.xml` and `multiAgentSpecs/uma/UMA3.xml` are loaded for agent COUCH_M_1 and COUCH_M_2, respectively. These files specify exacyly the properties of each agent. Most options should be left at default values. Things you may want to change:

**Voice of the agent**: By default, the ASAP agents are configured to use Windows/Microsoft MSAPI voices. You can modify the property `<Voice factory="WAV_TTS" voicename="Microsoft Mark" />` to select any of the [installed MSAPI voices](https://github.com/hmi-utwente/HmiASAPWiki/wiki/MS-API-Voices) on your Windows machine.

Alternatively, you may switch to MaryTTS. This may be useful if you are using OSX or Linux. 
In the agent spec file you should modify the loaders `ttsbinding` and `speechengine` to load MaryTTS instead:
```
<Loader id="ttsbinding" loader="asap.marytts5binding.loader.MaryTTSBindingLoader">
	<PhonemeToVisemeMapping resources="Humanoids/shared/phoneme2viseme/" filename="sampaen2disney.xml"/>
</Loader>
<Loader id="speechengine" loader="asap.speechengine.loader.SpeechEngineLoader" requiredloaders="facelipsync,ttsbinding">
	<Voice factory="WAV_TTS" voicename="dfki-spike"/>
</Loader>
```
Then, add the selected voice as a dependency in the `ivy.xml` file (For example: `<dependency org="marytts"	name="voice-dfki-spike" rev="latest.release" />`) and run `ant resolve` and `ant compile` in a terminal at the root of the `intent-planner` directory.
Note that ASAP uses an internal embedded MaryTTS instance, which is separate from the MaryTTS standalone server required by Greta.

**Gesture bindings**: More advanced users may want to take a look at the gesturebinding, which specifies how generic BML is mapped onto specific supported behaviours/gestures/animations of the agent: `<GestureBinding basedir="" resources="" filename="Humanoids/uma/gesturebinding/gesturebinding_borg.xml"/>`

**Middleware communication channels**: BML commands and BML feedback for each ASAP agent are sent/received through a shared "BML port", which is configured to use a middleware. The parameters for the middleware are configured in `resource/multiAgentSpecs/shared_port.xml`. By default, these are configured as ActiveMQ topics.

A different shared middleware is used to stream agents' joint rotations to the Unity scene. These are configured in `resource/multiAgentSpecs/shared_middleware.xml`. Since streaming this data should happen realtime and is relatively high-volume, we use UDP by default.

## Flipper
Our CIP module is built on the [Flipper2.0](https://github.com/hmi-utwente/flipper-2.0) dialogue engine. The main configuration file for Flipper is `resource/couchflipper.properties`, which specifies the flipper rules (split in various "template" files) to load.

## CIP
The actual templates are located in `resource/couchtemplates`. Settings for the various environments are configured in `Environment.xml`, in the information state variable `"environmentSpec": {"environments": [.....]}`. For each environment you may specify `requiredLoaders` (i.e. other environments it depends on) and additional `params` that are specific to the environment. Environments that communicate with other external modules require a middleware specification as a parameter. 


# License
This module is licensed under the GNU Lesser General Public License v3.0 (LGPL 3.0).
