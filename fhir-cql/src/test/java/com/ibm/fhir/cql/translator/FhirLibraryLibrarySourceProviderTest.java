package com.ibm.fhir.cql.translator;

import static org.junit.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;

import java.io.InputStream;
import java.util.List;

import org.cqframework.cql.cql2elm.LibrarySourceProvider;
import org.hl7.elm.r1.VersionedIdentifier;
import org.junit.Test;

import com.ibm.fhir.model.resource.Bundle;
import com.ibm.fhir.model.resource.Library;

public class FhirLibraryLibrarySourceProviderTest {
    
    @Test
    public void testLibraryResolution() throws Exception {
        Bundle bundle = (Bundle) TestHelper.getTestResource("EXM74-10.2.000-request.json");
        List<Library> libraries = TestHelper.getBundleResources(bundle, Library.class);
        LibrarySourceProvider provider = new FhirLibraryLibrarySourceProvider(libraries);
        InputStream is = provider.getLibrarySource(new VersionedIdentifier().withId("EXM74").withSystem("10.2.000"));
        assertNotNull("Missing source for id with version", is);
        
        is = provider.getLibrarySource(new VersionedIdentifier().withId("EXM74"));
        assertNotNull("Missing source for id no version", is);
        
        assertThrows(IllegalArgumentException.class, () ->  provider.getLibrarySource(new VersionedIdentifier().withId("EXM74").withVersion("1.0.0")) );
    }
}
