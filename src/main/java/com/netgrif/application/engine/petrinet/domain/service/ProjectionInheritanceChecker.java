package com.netgrif.application.engine.petrinet.domain.service;

import com.netgrif.application.engine.petrinet.domain.Transition;

import java.util.*;

public class ProjectionInheritanceChecker {

    private static final Set<String> parentTransitionIds = new HashSet<>();

    public ProjectionInheritanceChecker() {}

    public static boolean checkProjectionInheritanceUsingReachabilityGraph(
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> parentGraph,
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> childGraph,
            Set<String> parentTransitions) {

        System.out.println("\n=== Checking Projection Inheritance Using Reachability Graph ===");
        boolean inheritanceConfirmed = true;

        for (Map<String, Integer> parentState : parentGraph.keySet()) {
            boolean foundEquivalentState = false;
            System.out.println("Checking parent state: " + parentState);

            Set<Map<String, Integer>> childStates = getTauClosure(childGraph, parentState);
            if (childStates.isEmpty()) {
                System.out.println("\tERROR: No reachable states found in child for parent state: " + parentState);
                inheritanceConfirmed = false;
                continue;
            }

            for (Map<String, Integer> childState : childStates) {
                if (markingsMatch(parentState, childState)) {
                    System.out.println("\tFound equivalent child state (after tau transitions): " + childState);
                    foundEquivalentState = true;
                    Map<Transition, Map<String, Integer>> parentTransitionsMap = parentGraph.get(parentState);
                    Map<Transition, Map<String, Integer>> childTransitionsMap = childGraph.get(childState);

                    if (childTransitionsMap == null || childTransitionsMap.isEmpty()) {
                        System.out.println("\t\tWARNING: State " + childState + " exists but has no outgoing transitions");
                        continue;
                    }

                    for (Transition parentTransition : parentTransitionsMap.keySet()) {
                        if (!parentTransitions.contains(parentTransition.getStringId())) continue;

                        boolean transitionExists = false;
                        for (Map<String, Integer> reachableState : getTauClosure(childGraph, childState)) {
                            Map<Transition, Map<String, Integer>> reachableTransitions = childGraph.get(reachableState);
                            if (reachableTransitions == null) {
                                System.out.println("\t\tWARNING: No transitions found for state " + reachableState);
                                continue;
                            }

                            if (!reachableTransitions.containsKey(parentTransition)) {
                                System.out.println("\t\tERROR: Transition " + parentTransition.getStringId() + " is disabled after tau transitions!");
                                inheritanceConfirmed = false;
                                break;
                            }

                            for (Transition childTransition : reachableTransitions.keySet()) {
                                if (childTransition.getStringId().equals(parentTransition.getStringId()) &&
                                        markingsMatch(parentTransitionsMap.get(parentTransition), reachableTransitions.get(childTransition))) {
                                    transitionExists = true;
                                    System.out.println("\t\tMatching transition found: " + parentTransition.getStringId() + " in state " + reachableState);
                                    break;
                                }
                            }
                            if (transitionExists) break;
                        }
                        if (!transitionExists) {
                            System.out.println("\t\tERROR: Transition missing in child: " + parentTransition.getStringId());
                            inheritanceConfirmed = false;
                        }
                    }
                }
            }
            if (!foundEquivalentState) {
                System.out.println("\tERROR: No matching state found in child for parent state: " + parentState);
                inheritanceConfirmed = false;
            }
        }

        // Výstup verdiktu aj v prípade chyby
        if (inheritanceConfirmed) {
            System.out.println("\n✅ Projection Inheritance confirmed! ✅");
        } else {
            System.out.println("\n❌ Projection Inheritance NOT confirmed! ❌");
        }

        return inheritanceConfirmed;
    }

    private static Set<Map<String, Integer>> getTauClosure(Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> graph, Map<String, Integer> initialState) {
        Set<Map<String, Integer>> closure = new HashSet<>();
        Queue<Map<String, Integer>> queue = new LinkedList<>();
        closure.add(initialState);
        queue.add(initialState);

        while (!queue.isEmpty()) {
            Map<String, Integer> state = queue.poll();
            Map<Transition, Map<String, Integer>> transitions = graph.getOrDefault(state, Collections.emptyMap());

            if (transitions.isEmpty()) {
                System.out.println("\t\tWARNING: State " + state + " has no transitions, skipping tau closure expansion");
                continue;
            }

            for (Map.Entry<Transition, Map<String, Integer>> entry : transitions.entrySet()) {
                if (entry.getKey().getStringId().equals("tau") || !parentTransitionIds.contains(entry.getKey().getStringId())) {
                    Map<String, Integer> newState = entry.getValue();
                    if (!closure.contains(newState)) {
                        closure.add(newState);
                        queue.add(newState);
                    }
                }
            }
        }
        return closure;
    }

    static boolean markingsMatch(Map<String, Integer> parentMarking, Map<String, Integer> childMarking) {
        for (String place : parentMarking.keySet()) {
            if (!childMarking.containsKey(place) || !childMarking.get(place).equals(parentMarking.get(place))) {
                return false;
            }
        }
        return true;
    }
}