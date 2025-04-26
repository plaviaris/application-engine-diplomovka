package com.netgrif.application.engine.petrinet.domain.service;

import com.netgrif.application.engine.petrinet.domain.Transition;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * ProjectionInheritanceChecker: overuje projekčné dedenie medzi rodičovským a detským dosiahnuteľnostným grafom.
 * Oprava: teraz prehľadáva aj všetky vetvy tichých prechodov (tau),
 * a kontroluje, že v žiadnej z týchto vetiev nemôže dieťa „zabuchnúť“ zdedený prechod rodiča.
 */
public class ProjectionInheritanceChecker {

    /**
     * Vytlačí dosiahnuteľnostný graf, označí tiché (tau) prechody.
     */
    public static void printReachabilityGraphWithTau(
            String label,
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> graph,
            Set<String> parentTransitionIds) {

        System.out.println("\n=== " + label + " Reachability Graph ===");
        for (Map.Entry<Map<String, Integer>, Map<Transition, Map<String, Integer>>> entry : graph.entrySet()) {
            Map<String, Integer> fromMarking = entry.getKey();
            Map<Transition, Map<String, Integer>> outgoing = entry.getValue();

            for (Map.Entry<Transition, Map<String, Integer>> transitionEntry : outgoing.entrySet()) {
                Transition transition = transitionEntry.getKey();
                Map<String, Integer> toMarking = transitionEntry.getValue();

                String transitionId = transition.getStringId()  ;
                boolean isTau = !parentTransitionIds.contains(transitionId);
                String arrowLabel = isTau ? "τ(" + transitionId + ")" : transitionId;

                System.out.println(fromMarking + " --" + arrowLabel + "--> " + toMarking);
            }
        }
    }

    /**
     * Kontroluje, či každé správanie (prechod) rodiča dokáže dieťa simulovať,
     * a to aj cez tiché prechody (tau), pričom v žiadnej vetve nesmie
     * dieťa zablokovať pôvodný prechod rodiča.
     */
    public static boolean checkProjectionInheritanceUsingReachabilityGraph(
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> parentGraph,
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> childGraph,
            Set<String> parentTransitionIds) {

        printReachabilityGraphWithTau("Parent", parentGraph, parentTransitionIds);
        printReachabilityGraphWithTau("Child" , childGraph , parentTransitionIds);

        System.out.println("\n=== Checking Projection Inheritance ===");

        // Pre každý stav a prechod v rodičovskom grafe
        for (Map.Entry<Map<String, Integer>, Map<Transition, Map<String, Integer>>> parentEntry : parentGraph.entrySet()) {
            Map<String, Integer> parentState      = parentEntry.getKey();
            Map<Transition, Map<String, Integer>> parentEdges = parentEntry.getValue();

            for (Map.Entry<Transition, Map<String, Integer>> parentTransitionEntry : parentEdges.entrySet()) {
                Transition parentTransition    = parentTransitionEntry.getKey();
                Map<String, Integer> expectedParentNext = parentTransitionEntry.getValue();

                boolean simulatedSuccessfully = false;

                // Skúšame každý možný štartovací stav v detskom grafe
                for (Map<String, Integer> childStartState : childGraph.keySet()) {
                    // 1) musí sa zhodovať rodičovské miesta
                    if (!matchParentPlaces(parentState, childStartState)) {
                        continue;
                    }

                    System.out.println("\n🔍 Simulating: " +
                            parentState + " --" + parentTransition.getStringId() + "--> " + expectedParentNext);
                    System.out.println("   Start child state: " + childStartState);

                    // 2) overíme, či dieťa dokáže simulovať parentTransition z tohto stavu
                    if (canSimulate(
                            childStartState,
                            parentTransition,
                            expectedParentNext,
                            childGraph,
                            parentTransitionIds,
                            parentState)) {

                        System.out.println("   ✅ Simulated successfully from: " + childStartState);
                        simulatedSuccessfully = true;
                        break;
                    } else {
                        System.out.println("   ❌ Failed from child state: " + childStartState);
                    }
                }

                if (!simulatedSuccessfully) {
                    System.out.println("❌ Cannot simulate transition '"
                            + parentTransition.getStringId() + "' from parent state " + parentState);
                    return false;
                }
            }
        }

        System.out.println("\n✅ Projection inheritance confirmed.");
        return true;
    }

    /**
     * Skontroluje, či z daného štartovacieho stavu vie dieťa simulovať
     * prechod parentTransition do očakávaného stavu expectedParentNext.
     * Preskúma všetky vetvy tichých prechodov a nesmie v žiadnej vetve
     * zablokovať parentTransition.
     */
    private static boolean canSimulate(
            Map<String, Integer> startState,
            Transition parentTransition,
            Map<String, Integer> expectedParentNext,
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> childGraph,
            Set<String> parentTransitionIds,
            Map<String, Integer> parentRelevantPlaces) {

        Queue<Map<String, Integer>> queue = new LinkedList<>();
        Set<Map<String, Integer>> visited = new HashSet<>();

        queue.add(startState);
        visited.add(startState);

        while (!queue.isEmpty()) {
            Map<String, Integer> currentState = queue.poll();
            Map<Transition, Map<String, Integer>> outgoing =
                    childGraph.getOrDefault(currentState, Collections.emptyMap());

            // (1) Skúmame všetky tiché (tau) prechody najprv
            for (Map.Entry<Transition, Map<String, Integer>> entry : outgoing.entrySet()) {
                Transition transition = entry.getKey();
                Map<String, Integer> nextState = entry.getValue();

                if (!parentTransitionIds.contains(transition.getStringId())) {
                    // tau prechod nesmie zmeniť rodičovské miesta
                    if (!matchParentPlaces(currentState, nextState, parentRelevantPlaces.keySet())) {
                        System.out.println("   ❌ Tau '" + transition.getStringId()
                                + "' zmenil rodičovské miesta: " + currentState + " -> " + nextState);
                        return false;
                    }
                    if (visited.add(nextState)) {
                        System.out.println("   τ→ " + transition.getStringId() + " leads to " + nextState);
                        queue.add(nextState);
                    }
                }
            }

            // (2) Skúšame priamy parentTransition
            if (outgoing.containsKey(parentTransition)) {
                Map<String, Integer> afterParent = outgoing.get(parentTransition);
                Set<Map<String, Integer>> closure = tauClosure(childGraph, afterParent, parentTransitionIds);

                // hľadáme v tau-uzávere stav zhodný s expectedParentNext
                for (Map<String, Integer> candidate : closure) {
                    if (matchParentPlaces(expectedParentNext, candidate)) {
                        System.out.println("   ✔ '" + parentTransition.getStringId()
                                + "' simulated, reached " + candidate);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Vráti množinu stavov dosiahnuteľných len cez tiché prechody (tau-uzáver).
     */
    private static Set<Map<String, Integer>> tauClosure(
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> graph,
            Map<String, Integer> startState,
            Set<String> parentTransitionIds) {

        Set<Map<String, Integer>> closure = new HashSet<>();
        Queue<Map<String, Integer>> queue = new LinkedList<>();

        closure.add(startState);
        queue.add(startState);

        while (!queue.isEmpty()) {
            Map<String, Integer> state = queue.poll();
            Map<Transition, Map<String, Integer>> outgoing =
                    graph.getOrDefault(state, Collections.emptyMap());

            for (Map.Entry<Transition, Map<String, Integer>> entry : outgoing.entrySet()) {
                Transition transition = entry.getKey();
                Map<String, Integer> nextState = entry.getValue();

                if (!parentTransitionIds.contains(transition.getStringId()) && closure.add(nextState)) {
                    queue.add(nextState);
                }
            }
        }

        return closure;
    }

    /** Porovná, že na všetkých rodičovských miestach sú tokeny rovnaké. */
    private static boolean matchParentPlaces(
            Map<String, Integer> parentMarking,
            Map<String, Integer> childMarking,
            Set<String> parentPlaces) {

        for (String placeId : parentPlaces) {
            Integer parentTokens = parentMarking.getOrDefault(placeId, 0);
            Integer childTokens  = childMarking.getOrDefault(placeId, 0);
            if (!Objects.equals(parentTokens, childTokens)) {
                return false;
            }
        }
        return true;
    }

    /** Overí rodičovské miesta (predvolená verzia: porovná podľa všetkých miest rodiča). */
    private static boolean matchParentPlaces(
            Map<String, Integer> parentMarking,
            Map<String, Integer> childMarking) {
        return matchParentPlaces(parentMarking, childMarking, parentMarking.keySet());
    }
}
