package com.netgrif.application.engine.petrinet.domain.service;

import com.netgrif.application.engine.petrinet.domain.PetriNet;
import com.netgrif.application.engine.petrinet.domain.Place;
import com.netgrif.application.engine.petrinet.domain.Transition;
import com.netgrif.application.engine.petrinet.domain.arcs.Arc;
import com.netgrif.application.engine.petrinet.domain.arcs.InhibitorArc;
import com.netgrif.application.engine.petrinet.domain.arcs.ReadArc;
import com.netgrif.application.engine.petrinet.domain.arcs.ResetArc;

import java.util.*;

/**
 * PetriNetUtils poskytuje metódy na generovanie reachability grafu a kontrolu dedičnosti.
 */
public class PetriNetUtils {

    /**
     * Vygeneruje **reachability graph** (graf dosiahnuteľnosti) pre danú Petriho sieť.
     *
     * @param petriNet Daná PetriNet.
     * @return Reachability graph ako mapa (kľúč: marking, hodnota: mapované prechody na ďalšie markingy).
     */
    public static Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> generateReachabilityGraph(PetriNet petriNet) {
        Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> reachabilityGraph = new HashMap<>();
        Set<Map<String, Integer>> visitedMarkings = new HashSet<>();
        Queue<Map<String, Integer>> workQueue = new LinkedList<>();

        // Začiatočný marking
        Map<String, Integer> initialMarking = getInitialMarking(petriNet);
        workQueue.add(initialMarking);
        reachabilityGraph.put(initialMarking, new HashMap<>());

        while (!workQueue.isEmpty()) {
            Map<String, Integer> currentMarking = workQueue.poll();

            for (Transition transition : petriNet.getTransitions().values()) {
                if (isTransitionEnabled(petriNet, transition, currentMarking)) {
                    Map<String, Integer> newMarking = fireTransition(petriNet, transition, currentMarking);

                    if (!visitedMarkings.contains(newMarking)) {
                        visitedMarkings.add(newMarking);
                        workQueue.add(newMarking);
                        reachabilityGraph.putIfAbsent(newMarking, new HashMap<>());
                    }

                    reachabilityGraph.get(currentMarking).put(transition, newMarking);
                }
            }
        }

        return reachabilityGraph;
    }

    /**
     * Skontroluje, či je prechod umožnený pre dané označenie (marking).
     * Zohľadňuje aj read, reset a inhibičné hrany.
     */
    private static boolean isTransitionEnabled(PetriNet petriNet, Transition transition, Map<String, Integer> marking) {
        for (Arc arc : petriNet.getArcsOfTransition(transition)) {
            String placeId = arc.getPlace().getStringId();
            int tokens = marking.getOrDefault(placeId, 0);

            if (arc instanceof InhibitorArc) {
                if (tokens > 0) return false;
            } else if (arc instanceof ReadArc) {
                if (tokens < 1) return false;
            } else if (!(arc instanceof ResetArc)) {
                if (tokens < arc.getMultiplicity()) return false;
            }
        }
        return true;
    }

    /**
     * Simuluje vykonanie prechodu a vráti nové označenie (marking).
     */
    private static Map<String, Integer> fireTransition(PetriNet petriNet, Transition transition, Map<String, Integer> marking) {
        Map<String, Integer> newMarking = new HashMap<>(marking);

        // Spracovanie odoberania tokenov
        for (Arc arc : petriNet.getArcsOfTransition(transition)) {
            String placeId = arc.getPlace().getStringId();
            int tokens = newMarking.getOrDefault(placeId, 0);

            if (arc instanceof ResetArc) {
                newMarking.put(placeId, 0);
            } else if (!(arc instanceof ReadArc) && !(arc instanceof InhibitorArc)) {
                newMarking.put(placeId, tokens - arc.getMultiplicity());
            }
        }

        // Pridanie tokenov do výstupných miest
        for (Arc arc : petriNet.getArcsOfTransition(transition)) {
            if (!(arc instanceof InhibitorArc) && !(arc instanceof ReadArc)) {
                String placeId = arc.getPlace().getStringId();
                int tokens = newMarking.getOrDefault(placeId, 0);
                newMarking.put(placeId, tokens + arc.getMultiplicity());
            }
        }

        return newMarking;
    }

    /**
     * Získa počiatočné označenie PetriNet.
     */
    private static Map<String, Integer> getInitialMarking(PetriNet petriNet) {
        Map<String, Integer> marking = new HashMap<>();
        if (petriNet.getPlaces() != null) {
            for (Place place : petriNet.getPlaces().values()) {
                marking.put(place.getStringId(), place.getTokens());
            }
        }
        return marking;
    }

    /**
     * Porovnáva dva reachability grafy.
     */
    public static boolean compareReachabilityGraphs(
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> parentGraph,
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> childGraph) {
        for (Map.Entry<Map<String, Integer>, Map<Transition, Map<String, Integer>>> parentEntry : parentGraph.entrySet()) {
            Map<String, Integer> parentMarking = parentEntry.getKey();

            boolean matchingMarking = childGraph.keySet().stream().anyMatch(childMarking -> markingsMatch(parentMarking, childMarking));
            if (!matchingMarking) {
                return false;
            }

            Map<String, Integer> matchingChildMarking = childGraph.keySet().stream()
                    .filter(childMarking -> markingsMatch(parentMarking, childMarking)).findFirst().orElse(null);
            Map<Transition, Map<String, Integer>> childTransitions = childGraph.get(matchingChildMarking);
            Map<Transition, Map<String, Integer>> parentTransitions = parentEntry.getValue();

            for (Transition parentTransition : parentTransitions.keySet()) {
                if (!childTransitions.containsKey(parentTransition) ||
                        !childTransitions.get(parentTransition).equals(parentTransitions.get(parentTransition))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Pomocná metóda na porovnanie markingov.
     */
    private static boolean markingsMatch(Map<String, Integer> parentMarking, Map<String, Integer> childMarking) {
        for (String place : parentMarking.keySet()) {
            if (!childMarking.containsKey(place) || !childMarking.get(place).equals(parentMarking.get(place))) {
                return false;
            }
        }
        return true;
    }
    public static Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> filterGraph(
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> graph, Set<String> allowedTransitions) {

        Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> filteredGraph = new HashMap<>();

        for (Map.Entry<Map<String, Integer>, Map<Transition, Map<String, Integer>>> entry : graph.entrySet()) {
            Map<Transition, Map<String, Integer>> filteredTransitions = new HashMap<>();

            for (Map.Entry<Transition, Map<String, Integer>> transitionEntry : entry.getValue().entrySet()) {
                if (allowedTransitions.contains(transitionEntry.getKey().getStringId())) {
                    filteredTransitions.put(transitionEntry.getKey(), transitionEntry.getValue());
                }
            }

            filteredGraph.put(entry.getKey(), filteredTransitions);
        }

        return filteredGraph;
    }


}
