## The Conversational Intent Planner module
This Intent Planner module is responsible for generating appropriate conversational intents for the group of agents in the council. It sits between the [Topic Selection Engine](https://github.com/AgentsUnited/topic-selection-engine) & [Dialogue and Argumentation Framework (DAF)](https://github.com/AgentsUnited/daf) and the behaviour realizers [ASAP](https://github.com/ArticulatedSocialAgentsPlatform) & [GRETA](https://github.com/isir/greta). It translates high-level dialogue moves from DAF to more low-level (group) behaviours for individual agents. Additionally, it is the main point where user moves are received and forwarded for processing. While the user and agents progress through a dialogue, it also stores certain information in the Shared Knowledge Base (SKB)

The core of this module is built upon the [Flipper2.0](https://github.com/hmi-utwente/flipper-2.0) dialogue manager.

Conceptually, there are several loosely-connected parts:
- **Talking with SKB** - Handles user authentication and storing/retrieving certain vairables.
- **Talking with Topic Selection** - The Intent Planner is the main starting point for kicking off a dialogue with a user. It asks the Topic Selection engine for a dialogue that is suitable for the current user at the current time.
- **Talking with DAF** - Loads the dialogue in DAF, making sure that the various actors are all represented by an agent or the user. Then, a series of move selections is orchestrated while actors go through the dialogue.
- **Generating conversational intents** - Moves received from DAF are presented to the user/woz who can make a final selection of which move to execute. Once this selection has taken place, [BML](http://www.mindmakers.org/projects/bml-1-0/wiki/Wiki) is generated for each of the affected ASAP and GRETA agents. The Intent Planner offers basic floor management and nonverbal saliency gaze behaviour.
- **Showing the user interface** - The Unity scene contains an overlay interface for each of the actors, showing the various available moves at that point in time. A user or WoZ may select a move, which is then forwarded to DAF, as described above.
- **ASAP** - Although ASAP is maintained in a separate repository, we include some convenient starter classes and configuration in the CIP module.

## Build
To build this module you need Ant and the HMI Build Tool, available in [this repository](https://github.com/ArticulatedSocialAgentsPlatform/hmibuild/tree/master). It must be placed in the parent folder, next to the *hmicouch* folder. Then run the commands `ant clean`, `ant resolve` and `ant compile`. Take a look at how it was done in the [demonstrator repository](https://github.com/AgentsUnited/demonstrator) readme instructions.

## Run

## Troubleshooting

## License
This module is licensed under the GNU Lesser General Public License v3.0 (LGPL 3.0).
