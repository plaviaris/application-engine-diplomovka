package com.netgrif.application.engine.petrinet.domain.service;

import com.netgrif.application.engine.petrinet.domain.Transition;

import java.util.*;

public class ProjectionInheritanceChecker {

    public static void printReachabilityGraphWithTau(String label,
                                                     Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> graph,
                                                     Set<String> parentTransitionIds) {

        System.out.println("\n=== " + label + " Reachability Graph ===");
        Set<Map<String, Integer>> tauTargets = new HashSet<>();

        for (Map.Entry<Map<String, Integer>, Map<Transition, Map<String, Integer>>> entry : graph.entrySet()) {
            Map<String, Integer> from = entry.getKey();
            Map<Transition, Map<String, Integer>> transitions = entry.getValue();
            for (Map.Entry<Transition, Map<String, Integer>> tEntry : transitions.entrySet()) {
                String id = tEntry.getKey().getStringId();
                boolean isTau = !parentTransitionIds.contains(id);
                String labelOut = isTau ? ("t(" + id + ")") : id;

                Map<String, Integer> to = tEntry.getValue();
                if (isTau) tauTargets.add(to);

                System.out.println(from + " --" + labelOut + "--> " + to);
            }
        }

        if (!tauTargets.isEmpty()) {
            System.out.println("\nStates reached by tau transitions:");
            for (Map<String, Integer> tauState : tauTargets) {
                System.out.println("τ-state: " + tauState);
            }
        }
    }

    public static boolean checkProjectionInheritanceUsingReachabilityGraph(
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> parentGraph,
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> childGraph,
            Set<String> parentTransitionIds) {

        printReachabilityGraphWithTau("Parent", parentGraph, parentTransitionIds);
        printReachabilityGraphWithTau("Child", childGraph, parentTransitionIds);

        System.out.println("\n=== Checking Projection Inheritance Simulation Path ===");

        for (Map.Entry<Map<String, Integer>, Map<Transition, Map<String, Integer>>> parentEntry : parentGraph.entrySet()) {
            Map<String, Integer> parentFrom = parentEntry.getKey();
            Map<Transition, Map<String, Integer>> transitions = parentEntry.getValue();

            for (Map.Entry<Transition, Map<String, Integer>> transitionEntry : transitions.entrySet()) {
                Transition parentTransition = transitionEntry.getKey();
                Map<String, Integer> parentTo = transitionEntry.getValue();

                boolean simulated = false;
                for (Map<String, Integer> childStart : childGraph.keySet()) {
                    if (!markingsMatchFiltered(parentFrom, childStart)) continue;

                    System.out.println("\n🔍 Simulating: " + parentFrom + " --" + parentTransition.getStringId() + "--> " + parentTo);
                    System.out.println("   Starting from child state: " + childStart);

                    if (canSimulateTransitionStrict(parentTransition, childStart, childGraph, parentTo, parentTransitionIds, parentFrom)) {
                        System.out.println("   ✅ Simulated successfully via tau.");
                        simulated = true;
                        break;
                    } else {
                        System.out.println("   ❌ Simulation failed.");
                    }
                }

                if (!simulated) {
                    System.out.println("❌ Transition " + parentTransition.getStringId() + " from " + parentFrom + " to " + parentTo + " cannot be simulated in child.");
                    return false;
                }
            }
        }

        System.out.println("\n✅ Projection inheritance simulation confirmed.");
        return true;
    }

    private static boolean canSimulateTransitionStrict(Transition t, Map<String, Integer> start,
                                                       Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> graph,
                                                       Map<String, Integer> expectedTarget,
                                                       Set<String> parentTransitionIds,
                                                       Map<String, Integer> parentRelevantPlaces) {

        Set<Map<String, Integer>> visited = new HashSet<>();
        Queue<Map<String, Integer>> queue = new LinkedList<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Map<String, Integer> state = queue.poll();
            Map<Transition, Map<String, Integer>> transitions = graph.getOrDefault(state, Collections.emptyMap());

            for (Map.Entry<Transition, Map<String, Integer>> entry : transitions.entrySet()) {
                Transition tr = entry.getKey();
                Map<String, Integer> next = entry.getValue();

                if (!parentTransitionIds.contains(tr.getStringId())) {
                    // check if this tau transition changes relevant places from parent
                    if (!relevantMarkingEqual(state, next, parentRelevantPlaces.keySet())) {
                        System.out.println("   ❌ Tau transition " + tr.getStringId() + " modifies parent-relevant places: " + state + " -> " + next);
                        return false;
                    }
                    if (!visited.contains(next)) {
                        System.out.println("   τ following: " + tr.getStringId() + " to " + next);
                        queue.add(next);
                        visited.add(next);
                    }
                }

                if (tr.getStringId().equals(t.getStringId())) {
                    System.out.println("   ➡ Trying direct transition: " + tr.getStringId() + " from " + state);
                    Set<Map<String, Integer>> closure = getTauClosureOnly(graph, next, parentTransitionIds);
                    for (Map<String, Integer> mark : closure) {
                        if (markingsMatchFiltered(expectedTarget, mark)) {
                            System.out.println("   ✔ Reached expected state via tau: " + mark);
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static Set<Map<String, Integer>> getTauClosureOnly(
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> graph,
            Map<String, Integer> initialState,
            Set<String> parentTransitionIds) {

        Set<Map<String, Integer>> closure = new HashSet<>();
        Queue<Map<String, Integer>> queue = new LinkedList<>();
        closure.add(initialState);
        queue.add(initialState);

        while (!queue.isEmpty()) {
            Map<String, Integer> state = queue.poll();
            Map<Transition, Map<String, Integer>> transitions = graph.getOrDefault(state, Collections.emptyMap());

            for (Map.Entry<Transition, Map<String, Integer>> entry : transitions.entrySet()) {
                if (!parentTransitionIds.contains(entry.getKey().getStringId())) {
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

    private static boolean markingsMatchFiltered(Map<String, Integer> parentMarking, Map<String, Integer> childMarking) {
        for (String place : parentMarking.keySet()) {
            if (!childMarking.containsKey(place)) return false;
            if (!Objects.equals(parentMarking.get(place), childMarking.get(place))) return false;
        }
        return true;
    }

    private static boolean relevantMarkingEqual(Map<String, Integer> a, Map<String, Integer> b, Set<String> relevantPlaces) {
        for (String place : relevantPlaces) {
            int av = a.getOrDefault(place, 0);
            int bv = b.getOrDefault(place, 0);
            if (av != bv) return false;
        }
        return true;
    }
}
