# Mineogenesis — Project Brief

**Project:** Mineogenesis  
**Subtitle:** Neuroevolution in Minecraft  
**Status:** Planning  
**Initial milestone:** Phase A — Block Acquisition  
**Long-term goal:** Phase B — Autonomous Survival

## 1. Overview

Mineogenesis is an experimental platform for investigating evolutionary learning within Minecraft.

Populations of autonomous, player-like agents are placed into a Minecraft environment and given an objective, initially to locate and collect a specified block.

Each agent is controlled by an artificial neural network. Rather than using predefined navigation or mining strategies, agents develop behaviours through evolutionary selection, mutation and reproduction.

The project aims to explore whether useful behaviours can emerge from simple sensory inputs, actions and fitness functions.

## 2. Initial Objectives

The first version should allow a user to:

- Select a target Minecraft block.
- Spawn a configurable population of autonomous agents.
- Observe agents moving and interacting with the environment.
- Fly freely around the world while experiments run.
- Evaluate agents based on their performance.
- Select successful agents and produce mutated offspring.
- Repeat the process across multiple generations.
- Visualise evolutionary progress through statistics.
- Save and reload experiments.

## 3. Agent Design

Each agent possesses an independently initialised neural network.

### Sensory inputs

Possible observations include:

- Nearby block types and collision information.
- Direction of movement and velocity.
- Whether the agent is grounded or airborne.
- Information about blocks within its field of view.
- Mining progress and inventory contents.
- Previous actions and their outcomes.

Agents should not initially receive exact target coordinates or predefined paths.

### Available actions

Agents can:

- Move forwards, backwards and sideways.
- Rotate their view.
- Jump.
- Mine blocks.
- Interact with blocks.

Future versions may introduce inventory management, crafting, combat and tool selection.

## 4. Evolutionary System

Each generation begins with a population of neural networks.

Agents are evaluated over a fixed time or tick budget. Their performance determines a fitness score.

The evolutionary algorithm then:

1. Evaluates each agent's performance.
2. Selects agents for reproduction based on fitness.
3. Preserves a configurable number of successful agents.
4. Creates offspring through mutation and, optionally, crossover.
5. Replaces the previous population and begins a new generation.

Initial experiments will use fixed-topology neural networks, with more advanced neuroevolution techniques considered later.

## 5. Fitness and Experimental Design

The initial objective is to collect a selected block.

Potential fitness components include:

- Successful collection of the target.
- Progress towards locating the target.
- Successful interaction with relevant blocks.
- Exploration of previously unseen areas.
- Penalties for inactivity or repeated unproductive actions.

Different fitness functions should be compared experimentally.

Agents must be evaluated under comparable conditions, with controlled environments and repeatable random seeds.

## 6. Minecraft Integration

Agents should appear as player-like characters within the Minecraft world.

The observer should be able to use Creative or Spectator mode to watch experiments without interfering with them.

The system should provide:

- Agent names or identifiers.
- Optional generation and fitness indicators.
- Commands for starting, pausing and resetting experiments.
- A configurable experimental arena.
- Statistics and experiment logging.

The initial implementation should prioritise reliable simulated agents rather than requiring many separate Minecraft clients.

## 7. Research Questions

The project will initially investigate:

- Can neural networks evolved through genetic algorithms learn basic Minecraft navigation and block acquisition?
- How do population size and mutation rate affect evolutionary performance?
- How do different fitness functions influence emerging behaviours?
- Can agents trained in one environment generalise to unfamiliar environments?
- What behaviours emerge without explicitly programmed navigation strategies?

## 8. Development Roadmap

**Phase A1 — Foundations**

Create a minimal Minecraft environment with controllable simulated agents and basic observations/actions.

**Phase A2 — Evolution**

Implement neural networks, fitness evaluation, selection, mutation and generational resets.

**Phase A3 — Block Acquisition**

Train agents to locate and collect simple targets, beginning with oak logs.

**Phase A4 — Experimental Research**

Introduce multiple environments, reproducible benchmarks, fitness comparisons and performance visualisations.

**Phase B — Autonomous Survival**

Extend agents to gathering resources, crafting, tool usage, survival and increasingly complex objectives.

## 9. Intended Deliverables

- Open-source Minecraft integration.
- Evolutionary computation engine.
- Configurable experiment system.
- Visual observation tools.
- Reproducible datasets and benchmark results.
- Technical documentation.
- An independent research report describing the methodology, experiments and findings.

## 10. Project Philosophy

Mineogenesis is intended to be both an enjoyable simulation and a rigorous experimental platform.

The aim is not to script intelligent behaviour, but to investigate the conditions under which useful behaviour can emerge through evolutionary processes.
