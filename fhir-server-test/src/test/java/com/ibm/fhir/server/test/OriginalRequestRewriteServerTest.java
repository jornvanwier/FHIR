/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.server.test;

import static com.ibm.fhir.model.type.Code.code;
import static com.ibm.fhir.model.type.Uri.uri;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

import org.testng.annotations.Test;

import com.ibm.fhir.core.FHIRMediaType;
import com.ibm.fhir.model.resource.Bundle;
import com.ibm.fhir.model.resource.Patient;
import com.ibm.fhir.model.test.TestUtil;
import com.ibm.fhir.model.type.Coding;
import com.ibm.fhir.model.type.Meta;

/**
 * Basic sniff test of the FHIR Server.
 */
public class OriginalRequestRewriteServerTest extends FHIRServerTestBase {
    private static final String TAG = "OriginalRequestRewriteServerTest";
    private static final String ORIGINAL_URI = "https://frontend-url/base";

    /**
     * Create a Patient and ensure the Location header reflects the OriginalRequestUri
     */
    @Test
    public void testCreatePatient_LocationRewrite() throws Exception {
        WebTarget target = getWebTarget();

        // Build a new Patient and then call the 'create' API.
        Patient patient = TestUtil.readLocalResource("Patient_JohnDoe.json");
        Meta.Builder meta = patient.getMeta() == null ? Meta.builder() : patient.getMeta().toBuilder();
        meta.tag(Coding.builder().system(uri("http://ibm.com/fhir/tag")).code(code(TAG)).build());
        patient = patient.toBuilder()
                .meta(meta.build())
                .build();
        Entity<Patient> entity = Entity.entity(patient, FHIRMediaType.APPLICATION_FHIR_JSON);
        Response response = target.path("Patient").request()
                .header("X-FHIR-Forwarded-URL", ORIGINAL_URI + "/Patient")
                .post(entity, Response.class);
        assertResponse(response, Response.Status.CREATED.getStatusCode());
        assertTrue(response.getLocation().toString().startsWith(ORIGINAL_URI),
                response.getLocation().toString() + " should begin with " + ORIGINAL_URI);
    }

    /**
     * Search for the Patient and ensure that both the search links and the search response bundle entries
     * reflect the OriginalRequestUri
     */
    @Test(dependsOnMethods = "testCreatePatient_LocationRewrite")
    public void testPatientSearch_BaseUrlRewrite() throws Exception {
        WebTarget target = getWebTarget();

        Response response = target.path("Patient").queryParam("_tag", TAG).request()
                .header("X-FHIR-Forwarded-URL", ORIGINAL_URI + "/Patient?_tag=" + TAG)
                .get(Response.class);
        assertResponse(response, Response.Status.OK.getStatusCode());

        Bundle responseBundle = response.readEntity(Bundle.class);
        assertFalse(responseBundle.getLink().isEmpty());
        assertTrue(responseBundle.getLink().stream().allMatch(l -> l.getUrl().getValue().startsWith(ORIGINAL_URI)),
                "All search response bundle links start with the passed in uri");
        assertFalse(responseBundle.getEntry().isEmpty());
        assertTrue(responseBundle.getEntry().stream().allMatch(e -> e.getFullUrl().getValue().startsWith(ORIGINAL_URI)),
                "All search response bundle entry fullUrls start with the passed in uri");
    }

    /**
     * Search for the Patient via a whole-system search and ensure that both the search links
     * and the search response bundle entries reflect the OriginalRequestUri
     */
    @Test(dependsOnMethods = "testCreatePatient_LocationRewrite")
    public void testWholeSystemSearch_BaseUrlRewrite() throws Exception {
        WebTarget target = getWebTarget();

        Response response = target.queryParam("_tag", TAG).request()
                .header("X-FHIR-Forwarded-URL", ORIGINAL_URI + "?_tag=" + TAG)
                .get(Response.class);
        assertResponse(response, Response.Status.OK.getStatusCode());

        Bundle responseBundle = response.readEntity(Bundle.class);
        assertFalse(responseBundle.getLink().isEmpty());
        assertTrue(responseBundle.getLink().stream().allMatch(l -> l.getUrl().getValue().startsWith(ORIGINAL_URI)),
                "All search response bundle links start with the passed in uri");
        assertFalse(responseBundle.getEntry().isEmpty());
        assertTrue(responseBundle.getEntry().stream().allMatch(e -> e.getFullUrl().getValue().startsWith(ORIGINAL_URI + "/Patient")),
                "All search response bundle entry fullUrls start with the passed in uri");
    }
}
