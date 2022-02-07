/*
 * (C) Copyright IBM Corp. 2022
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ibm.fhir.core.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.ibm.fhir.core.FHIRVersionParam;
import com.ibm.fhir.core.ResourceType;

/**
 * Helper methods for working with FHIR Resource Type Strings
 */
public class ResourceTypeHelper {
    private static final Set<ResourceType> R4_ENUMS = collectResourceTypesFor(FHIRVersionParam.VERSION_40);
    private static final Set<ResourceType> R4B_ENUMS = collectResourceTypesFor(FHIRVersionParam.VERSION_43);
    private static final Set<ResourceType> R4B_ONLY_RESOURCE_ENUMS = collectR4bOnlyResourceTypes();
    private static final Set<ResourceType> ABSTRACT_TYPE_ENUMS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(
                ResourceType.RESOURCE,
                ResourceType.DOMAIN_RESOURCE
            )));
    /**
     * valid instances from 4.0 may not be valid in 4.3
     */
    private static final Set<ResourceType> BACKWARD_BREAKING_R4B_ENUMS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(
                ResourceType.DEVICE_DEFINITION,
                ResourceType.EVIDENCE,
                ResourceType.EVIDENCE_VARIABLE
            )));
    /**
     * valid instances from 4.3 may not be valid in 4.0
     */
    private static final Set<ResourceType> FORWARD_BREAKING_R4B_ENUMS = Collections.unmodifiableSet(new HashSet<>(
            Arrays.asList(
                ResourceType.DEVICE_DEFINITION,
                ResourceType.EVIDENCE,
                ResourceType.EVIDENCE_VARIABLE,
                ResourceType.ACTIVITY_DEFINITION,
                ResourceType.PLAN_DEFINITION
            )));

    private static final Set<String> R4_COMPATABLE_RESOURCES = Collections.unmodifiableSet(new LinkedHashSet<>(
            R4_ENUMS.stream()
                .filter(rtn -> !ABSTRACT_TYPE_ENUMS.contains(rtn))
                .map(ResourceType::value)
                .collect(Collectors.toList())));

    private static final Set<String> R4B_COMPATIBLE_RESOURCES = Collections.unmodifiableSet(new LinkedHashSet<>(
            R4B_ENUMS.stream()
                .filter(rtn -> !ABSTRACT_TYPE_ENUMS.contains(rtn))
                .map(ResourceType::value)
                .collect(Collectors.toList())));

    private static final Set<String> R4_AND_R4B_COMPATIBLE_RESOURCES = Collections.unmodifiableSet(new LinkedHashSet<>(
            R4B_ENUMS.stream()
                .filter(rtn -> !R4B_ONLY_RESOURCE_ENUMS.contains(rtn))
                .filter(rtn -> !BACKWARD_BREAKING_R4B_ENUMS.contains(rtn))
                .filter(rtn -> !ABSTRACT_TYPE_ENUMS.contains(rtn))
                .map(ResourceType::value)
                .collect(Collectors.toList())));

    private static final Set<String> ABSTRACT_RESOURCES = Collections.unmodifiableSet(
            ABSTRACT_TYPE_ENUMS.stream()
                .map(ResourceType::value)
                .collect(Collectors.toSet()));

    private static final Set<String> BACKWARD_BREAKING_R4B_RESOURCES = Collections.unmodifiableSet(
            BACKWARD_BREAKING_R4B_ENUMS.stream()
                .map(ResourceType::value)
                .collect(Collectors.toSet()));

    private static final Set<String> FORWARD_BREAKING_R4B_RESOURCES = Collections.unmodifiableSet(
            FORWARD_BREAKING_R4B_ENUMS.stream()
                .map(ResourceType::value)
                .collect(Collectors.toSet()));

    /**
     * @param fhirVersion The value of the MIME-type parameter 'fhirVersion' for the current interaction
     * @return a set of resource type names that corresponds to the requested fhirVersion
     */
    public static Set<String> getR4bResourceTypesFor(FHIRVersionParam fhirVersion) {
        switch (fhirVersion) {
        case VERSION_43:
            return R4B_COMPATIBLE_RESOURCES;
        case VERSION_40:
            return R4_AND_R4B_COMPATIBLE_RESOURCES;
        default:
            throw new IllegalArgumentException("unexpected fhirVersion " + fhirVersion.value());
        }
    }

    /**
     * @param fhirVersion The value of the MIME-type parameter 'fhirVersion' for the current interaction
     * @return the set of resource type names that were retired with, or before, the passed version of FHIR
     */
    public static Set<String> getRemovedResourceTypes(FHIRVersionParam fhirVersion) {
        return Arrays.stream(ResourceType.values())
            .filter(rt -> rt.getRetired() != null && rt.getRetired().compareTo(fhirVersion) <= 0)
            .map(ResourceType::value)
            .collect(Collectors.toSet());
    }

    /**
     * @param fhirVersion The value of the MIME-type parameter 'fhirVersion' for the current interaction
     * @return a set of resource type names that corresponds to the requested fhirVersion
     */
    public static Set<String> getCompatibleResourceTypes(FHIRVersionParam sourceFhirVersion, FHIRVersionParam targetFhirVersion) {
        if (sourceFhirVersion == FHIRVersionParam.VERSION_40) {
            switch(targetFhirVersion) {
            case VERSION_40:
                return R4_COMPATABLE_RESOURCES;
            case VERSION_43:
                return R4_AND_R4B_COMPATIBLE_RESOURCES;
            }
        }

        // sourceFhirVersion is 4.3
        if (targetFhirVersion == FHIRVersionParam.VERSION_40) {
            return new LinkedHashSet<>(R4B_ENUMS.stream()
                    .filter(rtn -> !R4B_ONLY_RESOURCE_ENUMS.contains(rtn))
                    .filter(rtn -> !FORWARD_BREAKING_R4B_ENUMS.contains(rtn))
                    .filter(rtn -> !ABSTRACT_TYPE_ENUMS.contains(rtn))
                    .map(ResourceType::value)
                    .collect(Collectors.toList()));
        }
        return R4B_COMPATIBLE_RESOURCES;
    }

    /**
     * @param fhirVersions A set of fhirVersions;
     *     if no fhirVerions are passed, the implementation will assume fhirVersion 4.3.0
     * @return a set of resource type names for resource types that are compatible across all of the passed fhirVerions
     */
    public static Set<String> getCompatibleResourceTypes(FHIRVersionParam... fhirVersions) {
        List<FHIRVersionParam> fhirVersionList = Arrays.stream(fhirVersions)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        if (fhirVersionList.isEmpty()) {
            return R4B_COMPATIBLE_RESOURCES;
        }

        // currently there's only 2 possible values for FHIRVersionParam, so that makes it easy
        if (fhirVersionList.size() == 2) {
            return R4_AND_R4B_COMPATIBLE_RESOURCES;
        }
        switch(fhirVersionList.get(0)) {
        case VERSION_40:
            return R4_COMPATABLE_RESOURCES;
        case VERSION_43:
        default:
            return R4B_COMPATIBLE_RESOURCES;
        }
    }

    /**
     * @return the set of resource type names that were either introduced in 4.3.0 (e.g. Ingredient) or changed
     *          in backwards-incompatible ways in the 4.3.0 release (e.g. Evidence and EvidenceVariable)
     */
    public static Set<String> getAbstractResourceTypeNames() {
        return ABSTRACT_RESOURCES;
    }

    /**
     * @param resourceType a valid resource type string
     * @param resourceVersion the source version for the compatibility check
     * @param fhirVersion the target version for the compatibility check
     * @return whether the resourceType is supported in this server for interactions with the passed fhirVersion
     */
    public static boolean isCompatible(String resourceType, FHIRVersionParam resourceVersion, FHIRVersionParam fhirVersion) {
        if (!getResourceTypesFor(fhirVersion).contains(resourceType)) {
            return false;
        }

        if (resourceVersion == fhirVersion) {
            return true;
        }

        switch (resourceVersion) {
        case VERSION_40:
            return !BACKWARD_BREAKING_R4B_RESOURCES.contains(resourceType);
        case VERSION_43:
        default:
            return !FORWARD_BREAKING_R4B_RESOURCES.contains(resourceType);
        }
    }

    /**
     * @param fhirVersion The value of the MIME-type parameter 'fhirVersion' for the current interaction
     * @return a set of resource type names that corresponds to the requested fhirVersion
     */
    private static Set<String> getResourceTypesFor(FHIRVersionParam fhirVersion) {
        switch (fhirVersion) {
        case VERSION_40:
            return R4_COMPATABLE_RESOURCES;
        case VERSION_43:
            return R4B_COMPATIBLE_RESOURCES;
        default:
            throw new IllegalArgumentException("unexpected fhirVersion " + fhirVersion.value());
        }
    }

    private static Set<ResourceType> collectResourceTypesFor(FHIRVersionParam fhirVersion) {
        Set<ResourceType> set = new LinkedHashSet<>();
        for(ResourceType r : ResourceType.values()) {
            if (r.getIntroduced().compareTo(fhirVersion) <= 0) {
                if (r.getRetired() == null || fhirVersion.compareTo(r.getRetired()) < 0) {
                    set.add(r);
                }
            }
        }
        return set;
    }

    private static Set<ResourceType> collectR4bOnlyResourceTypes() {
        Set<ResourceType> set = new HashSet<>();
        set.add(ResourceType.ADMINISTRABLE_PRODUCT_DEFINITION);
        set.add(ResourceType.CITATION);
        set.add(ResourceType.CLINICAL_USE_DEFINITION);
        set.add(ResourceType.EVIDENCE_REPORT);
        set.add(ResourceType.INGREDIENT);
        set.add(ResourceType.MANUFACTURED_ITEM_DEFINITION);
        set.add(ResourceType.MEDICINAL_PRODUCT_DEFINITION);
        set.add(ResourceType.NUTRITION_PRODUCT);
        set.add(ResourceType.PACKAGED_PRODUCT_DEFINITION);
        set.add(ResourceType.REGULATED_AUTHORIZATION);
        set.add(ResourceType.SUBSCRIPTION_STATUS);
        set.add(ResourceType.SUBSCRIPTION_TOPIC);
        set.add(ResourceType.SUBSTANCE_DEFINITION);
        return set;
    }
}
