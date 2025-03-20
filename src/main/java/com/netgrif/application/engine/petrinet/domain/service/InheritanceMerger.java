package com.netgrif.application.engine.petrinet.domain;

import com.netgrif.application.engine.petrinet.domain.roles.ProcessRole;
import com.netgrif.application.engine.petrinet.domain.dataset.Field;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Trieda InheritanceMerger obsahuje metódu, ktorá vytvorí novú PetriNet zlúčením informácií z rodičovskej (parent)
 * siete do detskej (child) siete. Výsledná sieť (merged) je založená na detskej sieti, ale doplnená o tie prvky,
 * ktoré sú prítomné v rodičovskej sieti a v detskej chýbajú.
 * Ak sa v detskej sieti nachádza prvok s rovnakým identifikátorom ako v parent, vyhodí sa IllegalStateException.
 */
public class InheritanceMerger {

    public static PetriNet mergeParentIntoChild(PetriNet parent, PetriNet child) {
        PetriNet merged = child.clone();

        if (parent.getTransitions() != null) {
            if (merged.getTransitions() == null) {
                merged.setTransitions(new HashMap<>());
            }
            for (Map.Entry<String, Transition> entry : parent.getTransitions().entrySet()) {
                String parentTransitionId = entry.getKey();
                if (merged.getTransitions().containsKey(parentTransitionId)) {
                    throw new IllegalStateException("Conflict: Child PetriNet already contains Transition with ID '"
                            + parentTransitionId + "' from parent.");
                }
                merged.getTransitions().put(parentTransitionId, entry.getValue());
            }
        }

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
                    throw new IllegalStateException("Conflict: Child PetriNet already contains Field with ID '"
                            + parentFieldId + "' from parent.");
                }
                merged.getDataSet().put(parentFieldId, entry.getValue());
            }
        }

        // Zlúčenie funkcií: pridáme funkcie z parent, ktoré v merged sieti ešte nie sú.
        if (parent.getFunctions() != null) {
            if (merged.getFunctions() == null) {
                merged.setFunctions(new LinkedList<>());
            }
            Set<String> existingFunctionIds = merged.getFunctions().stream()
                    .map(func -> func.getImportId())
                    .collect(Collectors.toSet());
            for (com.netgrif.application.engine.petrinet.domain.Function func : parent.getFunctions()) {
                if (existingFunctionIds.contains(func.getImportId())) {
                    throw new IllegalStateException("Conflict: Child PetriNet already contains Function with importId '"
                            + func.getImportId() + "' from parent.");
                }
                merged.getFunctions().add(func);
            }
        }


        return merged;
    }
}
