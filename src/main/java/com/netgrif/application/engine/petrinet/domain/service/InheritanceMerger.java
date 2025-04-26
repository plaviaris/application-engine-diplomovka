package com.netgrif.application.engine.petrinet.domain;

import com.netgrif.application.engine.petrinet.domain.arcs.Arc;
import com.netgrif.application.engine.petrinet.domain.roles.ProcessRole;
import com.netgrif.application.engine.petrinet.domain.dataset.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Trieda InheritanceMerger obsahuje metódu, ktorá vytvorí novú PetriNet zlúčením informácií z rodičovskej (parent)
 * siete do detskej (child) siete. Výsledná sieť (merged) je založená na detskej sieti, ale doplnená o tie prvky,
 * ktoré sú prítomné v rodičovskej sieti a v detskej chýbajú.
 * Ak sa v detskej sieti nachádza prvok s rovnakým identifikátorom ako v parent, vyhodí sa IllegalStateException.
 */
public class InheritanceMerger {

    private static final Logger log = LoggerFactory.getLogger(InheritanceMerger.class);

    public static PetriNet mergeParentIntoChild(PetriNet parent, PetriNet child) {
        PetriNet merged = child.clone();

        if (parent.getPlaces() != null) {
            if (merged.getPlaces() == null) {
                merged.setPlaces(new HashMap<>());
            }
            for (Map.Entry<String, Place> entry : parent.getPlaces().entrySet()) {
                String parentPlaceId = entry.getKey();
                if (merged.getPlaces().containsKey(parentPlaceId)) {
                    throw new IllegalStateException("Conflict: Child PetriNet already contains Place with ID '"
                            + parentPlaceId + "' from parent.");
                }
                merged.getPlaces().put(parentPlaceId, entry.getValue());
            }
        }

        if (parent.getRoles() != null) {
            if (merged.getRoles() == null) {
                merged.setRoles(new HashMap<>());
            }
            for (Map.Entry<String, ProcessRole> entry : parent.getRoles().entrySet()) {
                String parentRoleId = entry.getKey();
                if (merged.getRoles().containsKey(parentRoleId)) {
                    throw new IllegalStateException("Conflict: Child PetriNet already contains Role with ID '"
                            + parentRoleId + "' from parent.");
                }
                merged.getRoles().put(parentRoleId, entry.getValue());
            }
        }

        if (parent.getDataSet() != null) {
            if (merged.getDataSet() == null) {
                merged.setDataSet(new HashMap<>());
            }
            for (Map.Entry<String, Field> entry : parent.getDataSet().entrySet()) {
                String parentFieldId = entry.getKey();
                if (merged.getDataSet().containsKey(parentFieldId)) {
                    throw new IllegalStateException("Conflict: Child PetriNet already contains Field with ID '"+ parentFieldId + "' from parent. parent will be overrided");
                }
                merged.getDataSet().put(parentFieldId, entry.getValue());
            }
        }
        return merged;
    }

    public static PetriNet mergeParentTransitionsIntoChild(PetriNet parent, PetriNet child) {
        if (parent.getTransitions() != null) {
            if (child.getTransitions() == null) {
                child.setTransitions(new HashMap<>());
            }
            for (Map.Entry<String, Transition> entry : parent.getTransitions().entrySet()) {
                String parentTransitionId = entry.getKey();
                if (child.getTransitions().containsKey(parentTransitionId)) {
                    throw new IllegalStateException("Conflict: Child PetriNet already contains Transition with ID '"
                            + parentTransitionId + "' from parent.");
                }
                child.getTransitions().put(parentTransitionId, entry.getValue());
            }
        }
        if (parent.getFunctions() != null) {
            if (child.getFunctions() == null) {
                child.setFunctions(new LinkedList<>());
            }
            Set<String> existingFunctionIds = child.getFunctions().stream()
                    .map(func -> func.getImportId())
                    .collect(Collectors.toSet());
            for (com.netgrif.application.engine.petrinet.domain.Function func : parent.getFunctions()) {
                if (existingFunctionIds.contains(func.getImportId())) {
                    throw new IllegalStateException("Conflict: Child PetriNet already contains Function with importId '"
                            + func.getImportId() + "' from parent.");
                }
                child.getFunctions().add(func);
            }
        }

        return child;
    }

    public static PetriNet mergeParentArcIntoChild(PetriNet parent, PetriNet child) {
        if (parent.getArcs() != null) {
            if (child.getArcs() == null) {
                child.setArcs(new HashMap<>());
            }
            for (Map.Entry<String, List<Arc>> parentArcsEntry : parent.getArcs().entrySet()) {
                String sourceId = parentArcsEntry.getKey();
                List<Arc> arcsFromParent = parentArcsEntry.getValue();

                child.getArcs().putIfAbsent(sourceId, new LinkedList<>());

                List<Arc> childArcList = child.getArcs().get(sourceId);

                for (Arc parentArc : arcsFromParent) {
                    if (childArcList.stream()
                            .anyMatch(arc -> arc.getStringId().equals(parentArc.getStringId()))) {
                        throw new IllegalStateException("Conflict: Child PetriNet already contains Arc with ID '"
                                + parentArc.getStringId() + "' from parent.");
                    }

                    childArcList.add(parentArc);
                }
            }
        }
        return child;
    }
}
