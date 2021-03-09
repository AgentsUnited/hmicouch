# The Conversational Intent Planner module
This Conversational Intent Planner (CIP) module is responsible for generating appropriate conversational intents for the group of agents in the council. It sits between the [Topic Selection Engine (TSE)](https://github.com/AgentsUnited/topic-selection-engine) & [Dialogue and Argumentation Framework (DAF)](https://github.com/AgentsUnited/daf) and the behaviour realizers [ASAP](https://github.com/ArticulatedSocialAgentsPlatform) & [GRETA](https://github.com/isir/greta). It translates high-level dialogue moves from DAF to more low-level (group) behaviours for individual agents. Additionally, it is the main point where user moves are received and forwarded for processing. While the user and agents progress through a dialogue, it also stores certain information in the [Shared Knowledge Base (SKB)](https://github.com/woolplatform/wool/tree/master/java/WoolWebService)

The core of this module is built upon [Flipper2.0](https://github.com/hmi-utwente/flipper-2.0). Flipper is a rule-based dialogue engine, consisting of a collection of "templates". Each individual template specifies effects when certain preconditions are true. Flipper uses a hierarchical internal data structure (JSON) to store an information state. Preconditions are checked against the information state, and effects update the information state. It supports embedded JavaScript for light scripting and data manipulation. Heavy data processing and communications with external modules is handed off to Java objects. Within the CIP module we define several loosely-connected environments that focus on a specific task. 

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
To build this module you need [Ant](https://ant.apache.org/manual/install.html). Furthermore, you need the [HMI Build Tool](https://github.com/ArticulatedSocialAgentsPlatform/hmibuild/tree/master), which must be placed in the parent project folder next to the *intent-planner* folder. Then run the commands `ant clean`, `ant resolve` and `ant compile` in a terminal from within the intent-planner folder. Take a look at how it was done in the [demonstrator repository](https://github.com/AgentsUnited/demonstrator) readme instructions.

## Optional: use Eclipse to develop, compile and run the CIP
Of course, you are free to use which ever editor you like for editing and extending the CIP module. Compiling and running the project can always be done through the provided ant commands.

If you prefer to use the Eclipse IDE, we have additional integration options in place, which enable you to compile and build directly within the IDE.

- Download and install the latest [Python 2.7](https://www.python.org/downloads/release/python-2718/). Also add it in your PATH environment variable. Make sure that the `python` command in your commandline/terminal refers to the python 2.7 version, **NOT python 3** (check with `python --version`, this should print something like `Python 2.7.18`).
- Download and install a recent version of [Eclipse](https://www.eclipse.org/downloads/) for Java developers. Make sure that the version you download matches with your installed java SDK (32bit or 64bit).
- In a commandline window, navigate to the main intent-planner folder and run `ant eclipseproject`. This generates the necessary Eclipse project files, making sure that all dependencies are set correctly.
- Start your Eclipse, and select `File->Import` from the main menu. This opens an Import popup window. Select `General->Existing Projects into Workspace` and press `Next`. Press `Select root directory` and click on `Browse...` to select the main `intent-planner` directory. The `IntentPlanner` project should now show up in the `Projects:` area. Make sure the project is selected. (None of the other options on this page should be selected.) Press the `Finish` button to import the project in Eclipse.
- The Java source files are now automatically compiled as you edit them. The flipper templates are interpreted at run-time. You can launch the various components of this module from the `src/eu.couch.hmi.starters` package. `AsapCouchStarter` starts the ASAP realizer, and `FlipperStarter` starts the main CIP flipper instance.

Whenever you make modifications to the dependencies in the `ivy.xml` file, you will need to re-run the `ant resolve`, `ant compile`, and `ant eclipseproject` commands. You can then right-click on the project in Eclipse and select `Refresh` to import the latest dependencies.

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

## Flipper and the Conversational Intent Planner
The main configuration file for Flipper is `resource/couchflipper.properties`, which specifies the collection of template files to load.

The actual templates are located in `resource/couchtemplates`. Settings for the various environments are configured in `Environment.xml`, in the information state variable `"environmentSpec": {"environments": [.....]}`. For each environment you may specify `requiredLoaders` (i.e. other environments it depends on) and additional `params` that are specific to the environment. Environments that communicate with other external modules require a middleware specification as a parameter. 

**authEnv**: The authentication environment is responsible for authenticating the user with the Wool Web Service, and subsequently sharing the authentication token with other modules to allow a single-sign-on (SSO) entry point to the system. It requires several middleware instances:
- `AuthServiceMiddleware`: The service that does the authentication and returns an authentication token used for SSO.
- `SSOMiddleware`: The middleware on which we publish the SSO token. Any external module may listen to this middleware for an authentication token if they wish to automatically log in the user.
- `SSOCCEMiddleware`: A special middleware for transmitting the SSO token to the topic selection engine, which cannot listen to the regular `SSOMiddleware`.

Additionally, a default `username` and `password` may be configured, which will be pre-filled in the login GUI.

**bml**: The environment for sending BML to the ASAP realizer and receiving feedback on the execution of the BML through the configured `middleware`. If `publishBmlFeedback` is set to true, feedback is also published to Flipper, enabling it to be used in template preconditions.

**fml**: The environment for sending FML and BML to the Greta realizer and receiving feedback through the specified `middleware` and `middlewarebml`. Greta uses a unique middleware channel for each agent. For example, Greta's `COUCH_CAMILLE` agent will listen to FML requests on topic `COUCH/FML/REQUESTS/COUCH_CAMILLE`. `characterIds` lists the agent IDs that are controlled by Greta, these IDs will be appended to the `iTopicPrefix` configured in each middleware. If `publishFmlFeedback` is set to true, feedback is also published to Flipper, enabling it to be used in template preconditions.

**asapIntent and gretaIntent**: These environments translate a high-level move from DAF into behavioural intents and actual specific BML and FML for each respective realizer. A move may specify a template file with BML or FML, located in `bmlTemplateDir` or `fmlTemplateDir`. If no such template file is specified, or it can not be found, default BML and FML are generated dynamically.

**skb**: The SKB environment connects to the Wool Web Service for storing and retrieving variables through the `set-variables` and `get-variables` middleware, respectively.

**dgep**: The DGEP environment connects to DAF and is responsible for initiating dialogues and handling the flow of moves during an interaction. The `dgepCtrlMiddleware` is used for initiating the dialogue with actors. The `dgepMovesMiddleware` is used for selecting specific moves and retrieving subsequent available moves throughout a running dialogue.

**simplefilter and flipperMoveProxy**: These are basic environments for filtering incoming moves. In the demonstrator, these default environments don't do any filtering. More advanced implementations may want to filter incoming moves based on the user's preferences, the interaction history, or other considerations.

**cce**: The cce engine talks through the `get-new-topic` middleware to the topic selection engine, to retrieve a high-level topic for the logged in user. Once a topic is retrieved, it is initated in DAF. The retrieved topic may contain additional parameters that are passed on to the DAF.

**ui**: As a dialogue progresses, the UI environment listens in on the incoming moves. Through the configured `middleware` it instructs any (graphical) user interfaces to display the moves to the user. It is up to the implementing user interface how these choices are represented exactly. For example, in the basic demonstrator, the Unity generates an overlay with buttons. Additionally, we have several [mobile interfaces](https://github.com/AgentsUnited/dialogue-gui) available, which emulate WhatsApp or chatbox style interactions.

**floormanagement**: When there are several agents that want to make a move at the same time, the floormanagement environment takes care of selecting which agent may take the floor. There are several built-in floor management styles available, which can be configured in the `style` field:
- FCFS: a very basic first-come-first-serve floor manager. The floor will be granted to which ever agent requests the floor first.
- RANDOM: a basic floor manager which waits for a while to collect moves from agents (autonomous, wizard-controlled, and user-controlled) and then hands the floor to one at random. 
- GBM: a more advanced external "Group Behaviour Module", implemented as part of Greta. This floor manager communicates through the configured `middleware`. Currently under construction. The GBM will offer more intelligent floor management and also (nonverbal) turntaking behaviours.

**dialogueloader**: Ties together various other environments for initiating a dialogue, handling moves, generating behaviours. Has no configuration options.

**sse**: The social saliency environment listens for BML and FML commands and feedback, and generates basic accompanying nonverbal social saliency behaviours. Currently it generates gaze, more advanced implementations may also generate gestures and backchanneling, for example.

# License
This module is licensed under the GNU Lesser General Public License v3.0 (LGPL 3.0).
