package com.netgrif.application.engine.petrinet.domain.service;

import com.netgrif.application.engine.petrinet.domain.PetriNet;
import com.netgrif.application.engine.petrinet.domain.Place;
import com.netgrif.application.engine.petrinet.domain.Transition;
import com.netgrif.application.engine.petrinet.domain.arcs.Arc;
import com.netgrif.application.engine.petrinet.domain.arcs.InhibitorArc;
import com.netgrif.application.engine.petrinet.domain.arcs.ReadArc;
import com.netgrif.application.engine.petrinet.domain.arcs.ResetArc;

import java.util.*;
import java.util.stream.Collectors;

import static com.netgrif.application.engine.petrinet.domain.service.ProjectionInheritanceChecker.markingsMatch;

/**
 * Utility metódy pre prácu s Petriho sieťami.
 */
public class PetriNetUtils {

    /**
     * Pomocná trieda uchovávajúca výsledok porovnania reachability grafov.
     * Ak {@code matches == false}, potom {@code mismatchReason} obsahuje
     * vysvetlenie konkrétnej nezhody.
     */
    public static class CompareResult {
        public final boolean matches;
        public final String mismatchReason; // prázdny, ak matches == true

        public CompareResult(boolean matches, String mismatchReason) {
            this.matches = matches;
            this.mismatchReason = mismatchReason;
        }
    }

    /**
     * Vygeneruje reachability graph pre danú Petriho sieť.
     *
     * Kľúč:
     *   - {@code Map<String, Integer>} je marking (mapa placeId -> počet tokenov).
     * Hodnota:
     *   - {@code Map<Transition, Map<String, Integer>>} hovorí,
     *     že z daného marking-u, ak vieme spustiť daný Transition, dostaneme sa do nového marking-u.
     */
    public static Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> generateReachabilityGraph(PetriNet petriNet) {
        Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> reachabilityGraph = new HashMap<>();
        Set<String> visitedMarkingKeys = new HashSet<>();
        Queue<Map<String, Integer>> workQueue = new LinkedList<>();

        // Počiatočný marking
        Map<String, Integer> initialMarking = getInitialMarking(petriNet);
        String serializedInitial = serializeMarking(initialMarking);
        workQueue.add(initialMarking);
        visitedMarkingKeys.add(serializedInitial);
        reachabilityGraph.put(initialMarking, new HashMap<>());

        while (!workQueue.isEmpty()) {
            Map<String, Integer> currentMarking = workQueue.poll();

            // Pre každý transition skúmame, či je spustiteľný v currentMarking
            for (Transition transition : petriNet.getTransitions().values()) {
                if (isTransitionEnabled(petriNet, transition, currentMarking)) {
                    Map<String, Integer> newMarking = fireTransition(petriNet, transition, currentMarking);
                    String newSerialized = serializeMarking(newMarking);

                    if (!visitedMarkingKeys.contains(newSerialized)) {
                        visitedMarkingKeys.add(newSerialized);
                        workQueue.add(newMarking);
                        reachabilityGraph.put(newMarking, new HashMap<>());
                    }

                    reachabilityGraph.get(currentMarking).put(transition, newMarking);
                }
            }
        }

        return reachabilityGraph;
    }

    /**
     * Overí, či je daný {@code transition} spustiteľný v rámci daného {@code marking}.
     */
    private static boolean isTransitionEnabled(
            PetriNet petriNet,
            Transition transition,
            Map<String, Integer> marking
    ) {
        // Ak je getArcs() typu Map<String, List<Arc>>, potrebujeme dvojitý for-cyklus
        for (List<Arc> arcList : petriNet.getArcs().values()) {
            for (Arc arc : arcList) {
                if (!arc.getDestinationId().equals(transition.getStringId())) {
                    continue;
                }

                String placeId = arc.getSourceId();
                int tokens = marking.getOrDefault(placeId, 0);

                if (arc instanceof InhibitorArc) {
                    // InhibitorArc vyžaduje, aby v mieste neboli žiadne tokeny
                    if (tokens > 0) {
                        return false;
                    }
                } else if (arc instanceof ReadArc) {
                    // ReadArc vyžaduje aspoň 1 token
                    if (tokens < 1) {
                        return false;
                    }
                } else if (!(arc instanceof ResetArc)) {
                    // Bežný vstupný oblúk vyžaduje aspoň arc.getMultiplicity() tokenov
                    if (tokens < arc.getMultiplicity()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Aplikuje spustenie (fire) daného prechodu {@code transition} na {@code marking} a vráti nový marking.
     */
    private static Map<String, Integer> fireTransition(
            PetriNet petriNet,
            Transition transition,
            Map<String, Integer> marking
    ) {
        Map<String, Integer> newMarking = new HashMap<>(marking);

        // Vstupné oblúky (place -> transition)
        for (List<Arc> arcList : petriNet.getArcs().values()) {
            for (Arc arc : arcList) {
                if (arc.getDestinationId().equals(transition.getStringId())) {
                    String placeId = arc.getSourceId();
                    int tokens = newMarking.getOrDefault(placeId, 0);

                    if (arc instanceof ResetArc) {
                        newMarking.put(placeId, 0);
                    } else if (!(arc instanceof ReadArc || arc instanceof InhibitorArc)) {
                        newMarking.put(placeId, tokens - arc.getMultiplicity());
                    }
                }
            }
        }

        // Výstupné oblúky (transition -> place)
        for (List<Arc> arcList : petriNet.getArcs().values()) {
            for (Arc arc : arcList) {
                if (arc.getSourceId().equals(transition.getStringId())) {
                    // ReadArc alebo InhibitorArc nepridávajú žiadne tokeny
                    if (arc instanceof ReadArc || arc instanceof InhibitorArc) {
                        continue;
                    }

                    String placeId = arc.getDestinationId();
                    int tokens = newMarking.getOrDefault(placeId, 0);
                    newMarking.put(placeId, tokens + arc.getMultiplicity());
                }
            }
        }

        return newMarking;
    }

    /**
     * Získa počiatočný marking Petriho siete (mapa placeId -> počet tokenov).
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
     * Serializuje marking do reťazca pre porovnávacie účely.
     * Napríklad: "p1:2,p2:0,p3:1"
     */
    public static String serializeMarking(Map<String, Integer> marking) {
        return marking.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
    }

    /**
     * Serializuje marking tak, že berie do úvahy IBA miesta z {@code allowedPlaces}.
     * Ostatné kľúče v mapke (miesta v child nete navyše) sa odignorujú.
     *
     * Ak nejaké miesto v 'allowedPlaces' v marking-u chýba, berie sa 0 tokenov.
     */
    private static String serializeMarkingFiltering(
            Map<String, Integer> marking,
            Set<String> allowedPlaces
    ) {
        // vyrobíme novú mapu len pre tie miesta, ktoré existujú v allowedPlaces
        Map<String, Integer> filtered = new TreeMap<>();
        for (String placeId : allowedPlaces) {
            filtered.put(placeId, marking.getOrDefault(placeId, 0));
        }

        // teraz to môžeme klasicky “serializovať”
        return filtered.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
    }

    /**
     * Porovná reachability graf rodiča (parentGraph) a dieťaťa (childGraph).
     * Ak nájde nezhodu, vráti CompareResult(false, popisDovodu).
     * Ak všetko sedí, vráti CompareResult(true, "").
     */
    static CompareResult compareReachabilityGraphs(
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> parentGraph,
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> childGraph
    ) {
        for (Map.Entry<Map<String, Integer>, Map<Transition, Map<String, Integer>>> parentEntry : parentGraph.entrySet()) {
            Map<String, Integer> parentMarking = parentEntry.getKey();

            // Skúsime nájsť zodpovedajúci (matching) marking v detskom grafe
            Optional<Map<String, Integer>> matchingChildMarkingOpt = childGraph.keySet().stream()
                    .filter(childMarking -> markingsMatch(parentMarking, childMarking))
                    .findFirst();

            if (!matchingChildMarkingOpt.isPresent()) {
                // Nevieme nájsť zhodný marking
                String reason = "No matching marking found in child for parent marking: " + parentMarking;
                System.out.println("❌ " + reason);
                return new CompareResult(false, reason);
            }

            Map<String, Integer> matchingChildMarking = matchingChildMarkingOpt.get();

            // Porovnanie prechodov
            Map<Transition, Map<String, Integer>> parentTransitions = parentEntry.getValue();
            Map<Transition, Map<String, Integer>> childTransitions = childGraph.get(matchingChildMarking);

            for (Transition parentTransition : parentTransitions.keySet()) {
                // Ak detský graf neobsahuje tento prechod, je to nezhoda
                if (!childTransitions.containsKey(parentTransition)) {
                    String reason = "Child marking missing parent transition '"
                            + parentTransition.getStringId() + "' at marking: " + parentMarking;
                    System.out.println("❌ " + reason);
                    return new CompareResult(false, reason);
                }

                // Porovnáme cieľové markingy
                Map<String, Integer> parentTargetMarking = parentTransitions.get(parentTransition);
                Map<String, Integer> childTargetMarking = childTransitions.get(parentTransition);

                if (!markingsMatch(parentTargetMarking, childTargetMarking)) {
                    String reason = "Transition '" + parentTransition.getStringId()
                            + "' leads to different target markings:\n"
                            + "   Parent: " + parentTargetMarking + "\n"
                            + "   Child : " + childTargetMarking;
                    System.out.println("❌ " + reason);
                    return new CompareResult(false, reason);
                }
            }
        }

        // Ak sme prešli všetky markingy a nenašli žiadnu nezhodu:
        System.out.println("✅ All parent markings and transitions matched in the child reachability graph.");
        return new CompareResult(true, "");
    }


    /**
     * Ponechá v grafe len tie prechody, ktorých ID je v allowedTransitions.
     */
    public static Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> filterGraph(
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> graph,
            Set<String> allowedTransitions
    ) {
        Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> filteredGraph = new HashMap<>();

        for (Map.Entry<Map<String, Integer>, Map<Transition, Map<String, Integer>>> entry : graph.entrySet()) {
            // Pôvodná hodnota (prehody -> ciele)
            Map<Transition, Map<String, Integer>> originalTransitions = entry.getValue();
            Map<Transition, Map<String, Integer>> filteredTransitions = new HashMap<>();

            for (Map.Entry<Transition, Map<String, Integer>> transitionEntry : originalTransitions.entrySet()) {
                if (allowedTransitions.contains(transitionEntry.getKey().getStringId())) {
                    filteredTransitions.put(transitionEntry.getKey(), transitionEntry.getValue());
                }
            }
            filteredGraph.put(entry.getKey(), filteredTransitions);
        }

        return filteredGraph;
    }

    /**
     * Pomocná metóda pre debug – vypíše do konzoly všetky markingy a prechody daného grafu.
     */
    public static void printGraph(
            String graphName,
            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> graph
    ) {
        System.out.println("=== Reachability Graph: " + graphName + " ===");
        for (Map<String, Integer> marking : graph.keySet()) {
            String markingString = serializeMarking(marking);
            System.out.println("Marking: " + markingString);

            Map<Transition, Map<String, Integer>> transitions = graph.get(marking);
            for (Map.Entry<Transition, Map<String, Integer>> edge : transitions.entrySet()) {
                Transition t = edge.getKey();
                Map<String, Integer> targetMarking = edge.getValue();
                String targetString = serializeMarking(targetMarking);
                System.out.println("  --" + t.getStringId() + "--> " + targetString);
            }
        }
        System.out.println("==============================================\n");
    }

    public static void printPetriNetStructure(PetriNet net) {
        if (net == null) {
            System.out.println("PetriNet is null");
            return;
        }

        System.out.println("=== PetriNet Structure: " + net.getStringId() + " ===");

        // 1) Miesta (Places)
        if (net.getPlaces() != null) {
            System.out.println("Places:");
            for (Map.Entry<String, Place> placeEntry : net.getPlaces().entrySet()) {
                Place place = placeEntry.getValue();
                System.out.println("  Place ID: " + place.getStringId()
                        + ", Tokens: " + place.getTokens());
            }
        } else {
            System.out.println("No places defined (net.getPlaces() == null).");
        }

        // 2) Prechody (Transitions)
        if (net.getTransitions() != null) {
            System.out.println("Transitions:");
            for (Map.Entry<String, Transition> transitionEntry : net.getTransitions().entrySet()) {
                Transition transition = transitionEntry.getValue();
                System.out.println("  Transition ID: " + transition.getStringId()
                        + ", Title: " + transition.getTitle());
            }
        } else {
            System.out.println("No transitions defined (net.getTransitions() == null).");
        }

        // 3) Oblúky (Arcs)
        // getArcs() je zvyčajne Map<String, List<Arc>>, kde kľúč je 'sourceId' (alebo iný identifikátor).
        if (net.getArcs() != null) {
            System.out.println("Arcs:");
            for (Map.Entry<String, List<Arc>> arcListEntry : net.getArcs().entrySet()) {
                String arcSource = arcListEntry.getKey();
                List<Arc> arcsFromSource = arcListEntry.getValue();
                for (Arc arc : arcsFromSource) {
                    // typ oblúka (regular, read, inhibitor, reset)
                    String arcType = arc.getClass().getSimpleName();
                    // multiplicita
                    int multiplicity = arc.getMultiplicity();
                    // kam (destination)
                    String destId = arc.getDestinationId();
                    System.out.println("  " + arcSource + " --(" + arcType + ", multiplicity="
                            + multiplicity + ")--> " + destId);
                }
            }
        } else {
            System.out.println("No arcs defined (net.getArcs() == null).");
        }

        System.out.println("==============================================\n");
    }

}
