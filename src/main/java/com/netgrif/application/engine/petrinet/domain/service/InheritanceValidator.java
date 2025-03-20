package com.netgrif.application.engine.petrinet.domain;

import com.netgrif.application.engine.petrinet.domain.arcs.Arc;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.types.ObjectId;
import java.util.UUID;

/**
 * Trieda InheritanceValidator zabezpečuje, že pri dedičnosti Petri siete (PetriNet)
 * budú všetky prechody a miesta mať jedinečné identifikátory a zároveň kontroluje,
 * či importovaná (child) sieť neobsahuje explicitné ID, ktoré už existujú v rodičovskej (parent) sieti.
 * Ak sa nájdu duplicity, vyhodí IllegalStateException.
 */
public class InheritanceValidator {

    /**
     * Klonuje importovanú (child) PetriNet, generuje nové ObjectId pre prechody a miesta a aktualizuje referencie v oblúkoch.
     * Túto metódu voláme, ak nepotrebujeme spájať child a parent, ale len chceme child klonovať s jedinečnými ID.
     *
     * @param original Importovaná (child) PetriNet.
     * @return Klonovaná PetriNet s unikátnymi identifikátormi.
     */
    public static PetriNet validateAndClone(PetriNet original) {
        PetriNet clone = original.clone();
        clone.setObjectId(new ObjectId());
        clone.setCreationDate(LocalDateTime.now());

        if (clone.getTransitions() != null) {
            Map<String, Transition> oldTransitions = clone.getTransitions();
            Map<String, Transition> updatedTransitions = new HashMap<>();
            Map<String, String> transitionIdMap = new HashMap<>();
            for (Map.Entry<String, Transition> entry : oldTransitions.entrySet()) {
                String oldId = entry.getKey();
                Transition transition = entry.getValue();
                ObjectId newObjId = new ObjectId();
                transition.setObjectId(newObjId);
                String newId = transition.getStringId();
                if (transitionIdMap.containsValue(newId)) {
                    throw new IllegalStateException("Duplicate Transition ID detected during cloning: " + newId);
                }
                transitionIdMap.put(oldId, newId);
                updatedTransitions.put(newId, transition);
            }
            if (updatedTransitions.size() != oldTransitions.size()) {
                throw new IllegalStateException("Duplicate Transition IDs detected after cloning.");
            }
            clone.setTransitions(updatedTransitions);

            if (clone.getArcs() != null) {
                for (List<Arc> arcList : clone.getArcs().values()) {
                    for (Arc arc : arcList) {
                        if (transitionIdMap.containsKey(arc.getSourceId())) {
                            arc.setSourceId(transitionIdMap.get(arc.getSourceId()));
                        }
                        if (transitionIdMap.containsKey(arc.getDestinationId())) {
                            arc.setDestinationId(transitionIdMap.get(arc.getDestinationId()));
                        }
                    }
                }
            }
        }

        // Aktualizácia miest
        if (clone.getPlaces() != null) {
            Map<String, Place> oldPlaces = clone.getPlaces();
            Map<String, Place> updatedPlaces = new HashMap<>();
            Map<String, String> placeIdMap = new HashMap<>();
            for (Map.Entry<String, Place> entry : oldPlaces.entrySet()) {
                String oldId = entry.getKey();
                Place place = entry.getValue();
                ObjectId newObjId = new ObjectId();
                place.setObjectId(newObjId);
                String newId = place.getStringId();
                if (placeIdMap.containsValue(newId)) {
                    throw new IllegalStateException("Duplicate Place ID detected during cloning: " + newId);
                }
                placeIdMap.put(oldId, newId);
                updatedPlaces.put(newId, place);
            }
            if (updatedPlaces.size() != oldPlaces.size()) {
                throw new IllegalStateException("Duplicate Place IDs detected after cloning.");
            }
            clone.setPlaces(updatedPlaces);

            // Aktualizácia referencií v oblúkoch pre miesta
            if (clone.getArcs() != null) {
                for (List<Arc> arcList : clone.getArcs().values()) {
                    for (Arc arc : arcList) {
                        if (placeIdMap.containsKey(arc.getSourceId())) {
                            arc.setSourceId(placeIdMap.get(arc.getSourceId()));
                        }
                        if (placeIdMap.containsKey(arc.getDestinationId())) {
                            arc.setDestinationId(placeIdMap.get(arc.getDestinationId()));
                        }
                    }
                }
            }
        }

        clone.initializeArcs();
        return clone;
    }

    /**
     * Overí, či importovaná (child) PetriNet obsahuje ID prechodov alebo miest, ktoré už existujú
     * v rodičovskej PetriNet. Ak áno, vyhodí IllegalStateException. Ak kontrola prejde,
     * potom sa volá validateAndClone(child) a vráti klon.
     *
     * @param child Importovaná (child) PetriNet.
     * @param parent Rodičovská PetriNet.
     * @return Klonovaná PetriNet s unikátnymi identifikátormi.
     */
    public static PetriNet validateAndClone(PetriNet child, PetriNet parent) {
        // Overenie prechodov
        if (child.getTransitions() != null && parent.getTransitions() != null) {
            for (String childId : child.getTransitions().keySet()) {
                if (parent.getTransitions().containsKey(childId)) {
                    throw new IllegalStateException("Transition ID '" + childId + "' already exists in parent PetriNet.");
                }
            }
        }
        // Overenie miest
        if (child.getPlaces() != null && parent.getPlaces() != null) {
            for (String childId : child.getPlaces().keySet()) {
                if (parent.getPlaces().containsKey(childId)) {
                    throw new IllegalStateException("Place ID '" + childId + "' already exists in parent PetriNet.");
                }
            }
        }
        return validateAndClone(child);
    }

    private static String generateUniqueId() {
        return UUID.randomUUID().toString();
    }
}
