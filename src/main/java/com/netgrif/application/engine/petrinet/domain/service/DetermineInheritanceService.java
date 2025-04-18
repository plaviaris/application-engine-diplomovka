package com.netgrif.application.engine.petrinet.domain.service;

import com.netgrif.application.engine.petrinet.domain.PetriNet;
import com.netgrif.application.engine.petrinet.domain.Transition;
import com.netgrif.application.engine.petrinet.domain.Place;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class DetermineInheritanceService {
    // Statické polia, ktoré chceme (ale nie je to odporúčané)
    public static boolean protocolChecker;
    public static boolean projectionChecker;

    @Value("${nae.inheritance.protocol}")
    private boolean protocolCheckerValue;

    @Value("${nae.inheritance.projection}")
    private boolean projectionCheckerValue;

    /**
     * Táto metóda sa zavolá po vytvorení bean-u (po injekcii).
     * Z bežných (inštančných) premenných priradí hodnoty do statických.
     */
    @PostConstruct
    public void initStaticFields() {
        protocolChecker = protocolCheckerValue;
        projectionChecker = projectionCheckerValue;
    }
    
    /**
     * Na základe nastavenia 'protocolChecker' a 'projectionChecker'
     * určíme (a hneď skontrolujeme) typ dedičnosti medzi parentNet a childNet.
     * - Ak protocolChecker == true, testuje protokolovú dedičnosť.
     *    -> pri neúspechu hneď vyhodí IllegalStateException
     *    -> pri úspechu vráti "Protocol Inheritance"
     * - Inak, ak projectionChecker == true, testuje projekčnú dedičnosť.
     *    -> pri neúspechu vyhodí výnimku
     *    -> pri úspechu vráti "Projection Inheritance"
     * - Ak ani jedno nie je zapnuté, vráti "No Inheritance".
     */
    public static String determineInheritanceType(PetriNet parentNet, PetriNet childNet) {

        if (!protocolChecker && Objects.equals(childNet.getType(), "protocol")) {
            throw new IllegalStateException(
                    "Protocol inheritance is forbidden"
            );
        } else if (!projectionChecker && Objects.equals(childNet.getType(), "projection")) {
            throw new IllegalStateException(
                    "Projection inheritance is forbidden"
            );
        }
        // 1) Vygenerujeme reachability grafy
        Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> parentGraph =
                PetriNetUtils.generateReachabilityGraph(parentNet);
        Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> childGraph =
                PetriNetUtils.generateReachabilityGraph(childNet);

        // 2) Výpis pre debug (nie je povinný, ale často sa hodí)
        System.out.println("=== PARENT NET STRUCTURE ===");
        PetriNetUtils.printPetriNetStructure(parentNet);
        PetriNetUtils.printGraph("Parent Graph", parentGraph);

        System.out.println("=== CHILD NET STRUCTURE ===");
        PetriNetUtils.printPetriNetStructure(childNet);
        PetriNetUtils.printGraph("Child Graph", childGraph);

        Set<String> parentPlaceIds = parentNet.getPlaces().entrySet().stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // Ak máme zapnutú kontrolu PROTOKOLOVEJ dedičnosti
        if (protocolChecker && Objects.equals(childNet.getType(), "protocol")) {
            Set<String> parentTransitionIds = getTransitionIds(parentNet);

            Map<Map<String, Integer>, Map<Transition, Map<String, Integer>>> protocolChildGraph =
                    PetriNetUtils.filterGraph(childGraph, parentTransitionIds);


            PetriNetUtils.CompareResult compareResult = PetriNetUtils.compareReachabilityGraphs(parentGraph, protocolChildGraph, parentPlaceIds);

            if (!compareResult.matches) {
                throw new IllegalStateException(
                        "Child PetriNet does not meet PROTOCOL inheritance requirements.\n"
                                + "Reason: " + compareResult.mismatchReason
                );
            }
            // Ak sme došli sem, protokolová dedičnosť je v poriadku
            return "Protocol Inheritance";
        }

        // Ak máme zapnutú kontrolu PROJEKČNEJ dedičnosti
        if (projectionChecker && Objects.equals(childNet.getType(), "projection")) {
            // Tu zavoláme (hypotetickú) metódu, ktorá zistí projekčnú dedičnosť
            boolean projectionOk = ProjectionInheritanceChecker
                    .checkProjectionInheritanceUsingReachabilityGraph(
                            parentGraph, childGraph, getTransitionIds(parentNet)
                    );

            if (!projectionOk) {
                throw new IllegalStateException(
                        "Child PetriNet does not meet PROJECTION inheritance requirements."
                );
            }
            // Ak projekčná dedičnosť prešla
            return "Projection Inheritance";
        }

        // Ani protocolChecker, ani projectionChecker nie je true => nerobíme kontrolu
        return "No Inheritance";
    }

    /**
     * Pomocná metóda – extrahuje množinu stringId prechodov z PetriNet.
     */
    private static Set<String> getTransitionIds(PetriNet net) {
        Set<String> result = new HashSet<>();
        if (net != null && net.getTransitions() != null) {
            for (Transition t : net.getTransitions().values()) {
                result.add(t.getStringId());
            }
        }
        return result;
    }
}
