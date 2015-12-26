/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Olivier Grisel
 */
package org.nuxeo.ecm.platform.semanticentities.service;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.platform.semanticentities.EntitySuggestion;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.LocalDeploy;

// deploy off-line mock DBpedia source to override the default source that needs an internet connection:
// comment the following contrib to test again the real Stanbol server
@LocalDeploy("org.nuxeo.ecm.platform.semanticentities.core.tests:OSGI-INF/test-semantic-entities-stanbol-entity-contrib.xml")
public class StanbolEntityHubSourceTest extends RemoteEntityServiceTest {

    @Before
    public void setUpStanbol() throws Exception {
        Framework.getProperties().put("org.nuxeo.ecm.platform.semanticentities.stanbolUrl", "http://localhost:9090/");
    }

    @Override
    @SuppressWarnings("unchecked")
    @Test
    public void testDerefenceRemoteEntity() throws Exception {
        DocumentModel barackDoc = session.createDocumentModel("Person");
        service.dereferenceInto(barackDoc, DBPEDIA_BARACK_OBAMA_URI, true, false);

        // the title and birth date are fetched from the remote entity
        // description
        assertEquals("Barack Obama", barackDoc.getTitle());

        String summary = barackDoc.getProperty("entity:summary").getValue(String.class);
        // TODO: the default entityhub DBpedia index does not store the
        // summaries
        assertNull(summary);

        List<String> altnames = barackDoc.getProperty("entity:altnames").getValue(List.class);
        // TODO: the default entityhub DBpedia index does not store many
        // languages for the labels
        assertEquals(1, altnames.size());
        // Western spelling:
        assertTrue(altnames.contains("Barack Obama"));

        Calendar birthDate = barackDoc.getProperty("person:birthDate").getValue(Calendar.class);
        assertNotNull(birthDate);
        TimeZone tz = TimeZone.getTimeZone("ECT");
        DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG, Locale.US);
        formatter.setTimeZone(tz);
        assertEquals("August 4, 1961 12:00:00 AM CET", formatter.format(birthDate.getTime()));

        Blob depiction = barackDoc.getProperty("entity:depiction").getValue(Blob.class);
        assertNotNull(depiction);
        assertEquals("Official_portrait_of_Barack_Obama.jpg", depiction.getFilename());
        assertEquals(14748, depiction.getLength());

        List<String> sameas = barackDoc.getProperty("entity:sameas").getValue(List.class);
        assertTrue(sameas.contains(DBPEDIA_BARACK_OBAMA_URI.toString()));

        // check that further dereferencing with override == false does not
        // erase local changes
        barackDoc.setPropertyValue("dc:title", "B. Obama");
        barackDoc.setPropertyValue("person:birthDate", null);

        service.dereferenceInto(barackDoc, DBPEDIA_BARACK_OBAMA_URI, false, false);

        assertEquals("B. Obama", barackDoc.getTitle());
        birthDate = barackDoc.getProperty("person:birthDate").getValue(Calendar.class);
        assertEquals("August 4, 1961 12:00:00 AM CET", formatter.format(birthDate.getTime()));

        // existing names are not re-added
        altnames = barackDoc.getProperty("entity:altnames").getValue(List.class);
        assertEquals(1, altnames.size());

        // later dereferencing with override == true does not preserve local
        // changes
        service.dereferenceInto(barackDoc, DBPEDIA_BARACK_OBAMA_URI, true, false);
        assertEquals("Barack Obama", barackDoc.getTitle());
    }

    @Override
    @Test
    public void testSuggestRemoteEntity() throws IOException {
        assertTrue(service.canSuggestRemoteEntity());
        List<EntitySuggestion> suggestions = service.suggestRemoteEntity("Obama", "Person", 3);
        assertNotNull(suggestions);
        assertEquals(2, suggestions.size());

        EntitySuggestion suggested = suggestions.get(0);
        assertEquals("Barack Obama", suggested.label);
        assertEquals(DBPEDIA_BARACK_OBAMA_URI.toString(), suggested.getRemoteUri());
        assertEquals("Person", suggested.type);
        assertFalse(suggested.isLocal());

        suggested = suggestions.get(1);
        assertEquals("Michelle Obama", suggested.label);
        assertEquals(DBPEDIA_MICHELLE_OBAMA_URI.toString(), suggested.getRemoteUri());
        assertEquals("Person", suggested.type);
        assertFalse(suggested.isLocal());

        // this should also work for a null type
        suggestions = service.suggestRemoteEntity("Obama", null, 3);
        assertNotNull(suggestions);
        assertEquals(2, suggestions.size());

        suggested = suggestions.get(0);
        assertEquals("Barack Obama", suggested.label);
        assertEquals(DBPEDIA_BARACK_OBAMA_URI.toString(), suggested.getRemoteUri());
        assertEquals("Person", suggested.type);
        assertFalse(suggested.isLocal());

        suggested = suggestions.get(1);
        assertEquals("Michelle Obama", suggested.label);
        assertEquals(DBPEDIA_MICHELLE_OBAMA_URI.toString(), suggested.getRemoteUri());
        assertEquals("Person", suggested.type);
        assertFalse(suggested.isLocal());

        // however no place should match this name
        suggestions = service.suggestRemoteEntity("Obama", "Place", 3);
        assertNotNull(suggestions);
        assertEquals(0, suggestions.size());
    }

}
