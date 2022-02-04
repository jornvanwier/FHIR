/*
 * (C) Copyright IBM Corp. 2022
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.core.util.test;

import static org.testng.Assert.assertEquals;

import java.util.Set;

import org.testng.annotations.Test;

import com.ibm.fhir.core.FHIRVersionParam;
import com.ibm.fhir.core.util.ResourceTypeHelper;

/**
 * Tests for the ResourceTypeHelper class
 */
public class ResourceTypeHelperTest {
    @Test
    public void testGetResourceTypesFor() {
        Set<String> r4Types = ResourceTypeHelper.getCompatibleResourceTypes(FHIRVersionParam.VERSION_40, FHIRVersionParam.VERSION_40);
        assertEquals(r4Types.size(), 146, "number of r4 resource types");

        Set<String> r4bTypes = ResourceTypeHelper.getCompatibleResourceTypes(FHIRVersionParam.VERSION_43, FHIRVersionParam.VERSION_43);
        assertEquals(r4bTypes.size(), 141, "number of r4b resource types");

        Set<String> backwardCompatibleTypes = ResourceTypeHelper.getCompatibleResourceTypes(FHIRVersionParam.VERSION_40, FHIRVersionParam.VERSION_43);
        assertEquals(backwardCompatibleTypes.size(), 125, "number of r4b resource types that are backwards-scompatible with r4");

        Set<String> forwardCompatibleTypes = ResourceTypeHelper.getCompatibleResourceTypes(FHIRVersionParam.VERSION_43, FHIRVersionParam.VERSION_40);
        assertEquals(forwardCompatibleTypes.size(), 123, "number of r4 resource types that are forwards-compatible with r4b");
    }
}