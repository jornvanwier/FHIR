package com.ibm.fhir.operation.cpg;

import static com.ibm.fhir.cql.helpers.ModelHelper.fhirboolean;
import static com.ibm.fhir.cql.helpers.ModelHelper.fhircode;
import static com.ibm.fhir.cql.helpers.ModelHelper.fhirstring;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.junit.Test;
import org.opencds.cqf.cql.engine.data.DataProvider;
import org.opencds.cqf.cql.engine.execution.Context;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;

import com.ibm.fhir.cql.engine.model.FhirModelResolver;
import com.ibm.fhir.exception.FHIROperationException;
import com.ibm.fhir.model.resource.OperationDefinition;
import com.ibm.fhir.model.resource.Parameters;
import com.ibm.fhir.model.resource.Resource;
import com.ibm.fhir.model.type.code.OperationKind;
import com.ibm.fhir.model.type.code.PublicationStatus;
import com.ibm.fhir.server.operation.spi.FHIROperationContext;
import com.ibm.fhir.server.operation.spi.FHIRResourceHelpers;

public class AbstractCqlOperationTest extends BaseCqlOperationTest<AbstractCqlOperation> {

    @Test
    public void testGetCqlContext() {
        Parameters.Parameter p = Parameters.Parameter.builder().name(fhirstring("subject")).value(fhirstring("Patient/123")).build();
        Parameters parameters = Parameters.builder().parameter(p).build();

        ParameterMap paramMap = new ParameterMap(parameters);
        
        Pair<String, Object> context = op.getCqlContext(paramMap);
        assertEquals("Patient", context.getKey());
        assertEquals("123", context.getValue());
    }

    @Test
    public void testDataProviderResolveResourcePackage() {
        RetrieveProvider retrieveProvider = mock(RetrieveProvider.class);
        Map<String, DataProvider> dataProviders = op.createDataProviders(retrieveProvider);
        assertTrue(dataProviders.size() > 0);

        org.cqframework.cql.elm.execution.Library library = mock(org.cqframework.cql.elm.execution.Library.class);
        when(library.getIdentifier()).thenReturn(new VersionedIdentifier().withId("test"));

        Context context = new Context(library);
        for (Map.Entry<String, DataProvider> entry : dataProviders.entrySet()) {
            context.registerDataProvider(entry.getKey(), entry.getValue());
        }

        DataProvider resolved = context.resolveDataProvider(FhirModelResolver.BASE_PACKAGE_NAME + ".resource");
        assertNotNull(resolved);
    }

    @Override
    protected AbstractCqlOperation getOperation() {
        return new AbstractCqlOperation() {

            @Override
            protected OperationDefinition buildOperationDefinition() {
                return OperationDefinition.builder()
                        .name(fhirstring("dummy"))
                        .status(PublicationStatus.ACTIVE)
                        .kind(OperationKind.OPERATION).system(fhirboolean(true))
                        .type(fhirboolean(false))
                        .instance(fhirboolean(false))
                        .code(fhircode("dummy"))
                        .build();
            }

            @Override
            protected Parameters doInvoke(FHIROperationContext operationContext, Class<? extends Resource> resourceType, String logicalId, String versionId,
                Parameters parameters, FHIRResourceHelpers resourceHelper) throws FHIROperationException {
                throw new FHIROperationException("You should not have reached this point");
            }
        };
    }
}
