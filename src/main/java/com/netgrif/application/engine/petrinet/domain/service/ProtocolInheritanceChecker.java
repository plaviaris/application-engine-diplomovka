package com.netgrif.application.engine.petrinet.domain.service;

import com.netgrif.application.engine.petrinet.domain.PetriNet;
import com.netgrif.application.engine.petrinet.domain.Place;
import com.netgrif.application.engine.petrinet.domain.Transition;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * ProtocolInheritanceChecker kontroluje, či detská PetriNet vykazuje
 * protokolovú dedičnosť voči rodičovskej PetriNet.
 *
 * Logika:
 *  - Vygenerujeme reachability graf pre rodičovskú aj detskú sieť.
 *  - Z rodičovskej siete získame množinu prechodových identifikátorov (pomocou getStringId()).
 *  - Detský graf sa filtruje tak, aby obsahoval iba tie prechody, ktoré sú definované v rodičovskej sieti.
 *  - Porovnáme reachability grafy – ak sú kompatibilné, dedičnosť je protokolová.
 *  - Ak dedičnosť nie je protokolová, vyhodíme IllegalStateException.
 */
public class ProtocolInheritanceChecker {

    /**
     * Overí, či detská PetriNet vykazuje protokolovú dedičnosť voči rodičovskej.
     * Ak nie, vyhodí IllegalStateException.
     *
     * @param parentNet Rodičovská PetriNet.
     * @param childNet  Detská PetriNet.
     */
    public static void validateProtocolInheritance(PetriNet parentNet, PetriNet childNet) {
        Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> parentGraph =
                PetriNetUtils.generateReachabilityGraph(parentNet);
        Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> childGraph =
                PetriNetUtils.generateReachabilityGraph(childNet);

        Set<String> parentTransitionIds = new HashSet<>();
        if (parentNet.getTransitions() != null) {
            for (Transition t : parentNet.getTransitions().values()) {
                parentTransitionIds.add(t.getStringId());
            }
        }

        Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> protocolChildGraph =
                PetriNetUtils.filterGraph(childGraph, parentTransitionIds);

        boolean isProtocolInheritance = PetriNetUtils.compareReachabilityGraphs(parentGraph, protocolChildGraph);

        if (!isProtocolInheritance) {
            throw new IllegalStateException("Child PetriNet does not meet protocol inheritance requirements.");
        }
    }

    /**
     * Určí typ dedičnosti medzi rodičovskou a detskou PetriNet.
     * Ak detská sieť spĺňa protokolovú dedičnosť, vráti "Protocol Inheritance",
     * inak vyhodí IllegalStateException.
     *
     * @param parentNet Rodičovská PetriNet.
     * @param childNet  Detská PetriNet.
     * @return Typ dedičnosti.
     */
    public String determineInheritanceType(PetriNet parentNet, PetriNet childNet) {
        try {
            validateProtocolInheritance(parentNet, childNet);
            return "Protocol Inheritance";
        } catch (IllegalStateException e) {
            return "No Inheritance";
        }
    }
}
