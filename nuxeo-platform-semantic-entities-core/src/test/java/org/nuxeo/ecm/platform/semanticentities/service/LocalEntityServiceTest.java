/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 */

package org.nuxeo.ecm.platform.semanticentities.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.opencmis.impl.server.NuxeoRepository;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.TransactionalFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.core.versioning.VersioningService;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.semanticentities.Constants;
import org.nuxeo.ecm.platform.semanticentities.EntitySuggestion;
import org.nuxeo.ecm.platform.semanticentities.LocalEntityService;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceInfo;
import org.nuxeo.ecm.platform.semanticentities.adapter.OccurrenceRelation;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.test.runner.RuntimeHarness;
import org.nuxeo.runtime.transaction.TransactionHelper;

@RunWith(FeaturesRunner.class)
@Features({ TransactionalFeature.class, CoreFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy({
// necessary for the fulltext indexer
        "org.nuxeo.ecm.core.convert.api", //
        "org.nuxeo.ecm.core.convert", //
        "org.nuxeo.ecm.core.convert.plugins", //
        // dublincore contributors are required to check whether non system users have edited an entity or not
        "org.nuxeo.ecm.platform.dublincore", //
        // semantic entities types
        "org.nuxeo.ecm.platform.semanticentities.core", //
        // CMIS query maker
        "org.nuxeo.ecm.core.opencmis.impl", //
})
// deploy off-line mock DBpedia source to override the default source that needs an internet connection:
// comment the following contrib to test again the real DBpedia server
@LocalDeploy("org.nuxeo.ecm.platform.semanticentities.core.tests:OSGI-INF/test-semantic-entities-dbpedia-entity-contrib.xml")
public class LocalEntityServiceTest {

    public static final Log log = LogFactory.getLog(LocalEntityServiceTest.class);

    @Inject
    protected RuntimeHarness runtimeHarness;

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected CoreSession session;

    @Inject
    protected EventService eventService;

    @Inject
    LocalEntityService service;

    protected DocumentModel john;

    protected DocumentModel johndoe;

    protected DocumentModel obama;

    protected DocumentModel beatles;

    protected DocumentModel liverpool;

    protected DocumentModel doc1;

    protected DocumentModel doc2;

    String obamaWithHamza = "\u0623\u0648\u0628\u0627\u0645\u0627";

    String obamaWithoutHamza = "\u0627\u0648\u0628\u0627\u0645\u0627";

    @Before
    public void setUp() throws Exception {
        assumeTrue("Needs multi-fulltext support",
                coreFeature.getStorageConfiguration().supportsMultipleFulltextIndexes());

        // set CMISQL JOINs allowed
        Framework.getProperties().setProperty(NuxeoRepository.SUPPORTS_JOINS_PROP, "true");

        // override remote entity service
        Framework.getProperties().put("org.nuxeo.ecm.platform.semanticentities.stanbolUrl", "http://localhost:9090/");

        DocumentModel domain = session.createDocumentModel("/", "default-domain", "Folder");
        session.createDocument(domain);
        session.save();

        makeSomeDocuments();
    }

    public void makeSomeDocuments() {
        doc1 = session.createDocumentModel("/", "doc1", "File");
        doc1.setPropertyValue("dc:title", "A short bio for John");
        doc1.setPropertyValue("dc:description", "John Lennon was born in Liverpool in 1940. John was a musician."
                + " This document about John Lennon has many occurrences"
                + " of the words 'John' and 'Lennon' hence should rank high" + " for suggestions on such keywords.");
        doc1 = session.createDocument(doc1);

        doc2 = session.createDocumentModel("/", "doc2", "File");
        doc2.setPropertyValue("dc:title", "John Lennon is not a lemon");
        doc2.setPropertyValue("dc:description", "John Lennon is not a lemon despite his yellow submarine.");
        doc2 = session.createDocument(doc2);

        DocumentModel doc3 = session.createDocumentModel("/", "doc3", "File");
        doc3.setPropertyValue("dc:title", "Another document with unrelated topic");
        doc3 = session.createDocument(doc3);

        DocumentModel doc4 = session.createDocumentModel("/", "doc4", "File");
        doc4.setPropertyValue("dc:title", "It is necessary to have many documents to...");
        doc4 = session.createDocument(doc4);

        DocumentModel doc5 = session.createDocumentModel("/", "doc5", "File");
        doc5.setPropertyValue("dc:title", "...ensure that the l*****n word will not...");
        doc5 = session.createDocument(doc5);

        DocumentModel doc6 = session.createDocumentModel("/", "doc6", "File");
        doc6.setPropertyValue("dc:title", "... have a too high document frequency ...");
        doc6 = session.createDocument(doc6);

        DocumentModel doc7 = session.createDocumentModel("/", "doc7", "File");
        doc7.setPropertyValue("dc:title", "... otherwise the MySQL tests would be unstable.");
        doc7 = session.createDocument(doc7);

        session.save(); // force write to SQL backend
        waitForAsyncCompletion();
    }

    public void makeSomeEntities() {
        DocumentModel container = service.getEntityContainer(session);
        assertNotNull(container);
        assertEquals(Constants.ENTITY_CONTAINER_TYPE, container.getType());

        john = session.createDocumentModel(container.getPathAsString(), null, "Person");
        john.setPropertyValue("dc:title", "John Lennon");
        john.setPropertyValue("entity:summary", "John Winston Ono Lennon, MBE (9 October 1940 – 8 December 1980)"
                + " was an English rock musician, singer-songwriter, author, and peace"
                + " activist who gained worldwide fame as one of the founding members of" + " The Beatles.");
        john.setPropertyValue("entity:sameas", (Serializable) Arrays.asList("http://dbpedia.org/resource/John_Lennon"));
        john.setPropertyValue("entity:sameasDisplayLabel", (Serializable) Arrays.asList("John Lennon"));
        john.setPropertyValue("entity:types", (Serializable) Arrays.asList("http://dbpedia.org/ontology/MusicalArtist"));
        john.setPropertyValue("entity:altnames", new String[] { "John Winston Lennon" });
        john.setPropertyValue("person:birthDate", new GregorianCalendar(1940, 10, 9));
        john.setPropertyValue("person:birthDate", new GregorianCalendar(1980, 12, 8));
        john = session.createDocument(john);

        // add another john
        johndoe = session.createDocumentModel(container.getPathAsString(), null, "Person");
        johndoe.setPropertyValue("dc:title", "John Doe");
        johndoe = session.createDocument(johndoe);

        // add a name with a diacritic in arabic
        obama = session.createDocumentModel(container.getPathAsString(), null, "Person");
        obama.setPropertyValue("dc:title", "Barack Obama");
        obama.setPropertyValue("entity:summary", "44th President of the United States of America.");
        obama.setPropertyValue("entity:sameas",
                (Serializable) Arrays.asList("http://dbpedia.org/resource/Barack_Obama"));
        obama.setPropertyValue("entity:sameasDisplayLabel", (Serializable) Arrays.asList("Barack Obama"));
        obama.setPropertyValue("entity:altnames", new String[] { "Obama", "Barack Hussein Obama", obamaWithHamza });
        obama = session.createDocument(obama);

        beatles = session.createDocumentModel(container.getPathAsString(), null, "Organization");
        beatles.setPropertyValue("dc:title", "The Beatles");
        beatles.setPropertyValue("entity:summary", "The Beatles were an English rock band, formed in Liverpool in 1960"
                + " and one of the most commercially successful and critically acclaimed"
                + " acts in the history of popular music.");

        beatles.setPropertyValue("entity:sameas",
                (Serializable) Arrays.asList("http://dbpedia.org/resource/The_Beatles"));
        beatles.setPropertyValue("entity:sameasDisplayLabel", (Serializable) Arrays.asList("The Beatles"));
        beatles.setPropertyValue("entity:types", (Serializable) Arrays.asList("http://dbpedia.org/ontology/Band"));
        beatles = session.createDocument(beatles);

        liverpool = session.createDocumentModel(container.getPathAsString(), null, "Place");
        liverpool.setPropertyValue("dc:title", "Liverpool");
        liverpool.setPropertyValue("entity:summary",
                "Liverpool is a city and metropolitan borough of Merseyside, England, along"
                        + " the eastern side of the Mersey Estuary. It was founded as a borough"
                        + " in 1207 and was granted city status in 1880.");

        liverpool.setPropertyValue("entity:sameas",
                (Serializable) Arrays.asList("http://dbpedia.org/resource/Liverpool"));
        liverpool.setPropertyValue("entity:sameasDisplayLabel", (Serializable) Arrays.asList("http://Liverpool"));
        liverpool.setPropertyValue("entity:types", (Serializable) Arrays.asList("http://dbpedia.org/ontology/City"));
        liverpool.setPropertyValue("place:latitude", 53.4);
        liverpool.setPropertyValue("place:longitude", -2.983);
        liverpool = session.createDocument(liverpool);
        session.save();
        waitForAsyncCompletion();
    }

    protected void waitForAsyncCompletion() {
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
        eventService.waitForAsyncCompletion();
    }

    @Test
    public void testCreateEntities() {
        makeSomeEntities();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAddOccurrences() throws Exception {
        makeSomeEntities();

        // fetch the initial john popularity for later comparison
        double pop0 = john.getProperty("entity:popularity").getValue(Double.class);

        OccurrenceRelation occ1 = service.addOccurrence(session, doc1.getRef(), john.getRef(),
                "John Lennon was born in Liverpool in 1940.", 0, 11);
        assertNotNull(occ1);
        john = session.getDocument(john.getRef());
        // John Lennon is the main entity name, hence not stored in the altnames
        // field
        assertFalse(john.getProperty("entity:altnames").getValue(List.class).contains("John Lennon"));

        // check the increase in popularity
        double pop1 = john.getProperty("entity:popularity").getValue(Double.class);
        assertTrue(pop1 > pop0);

        OccurrenceRelation occ2 = service.addOccurrence(session, doc1.getRef(), john.getRef(), "John was a musician.",
                0, 4);
        assertNotNull(occ2);
        john = session.getDocument(john.getRef());
        // John is not the exact main entity name, hence stored in the altnames
        // field of the entity to improve fulltext lookup quality of the entity
        // in the future
        assertEquals(new ArrayList<String>(john.getProperty("entity:altnames").getValue(List.class)),
                Arrays.asList("John Winston Lennon", "John"));
        // check the popularity is still the same since this is an occurrence
        // from the same document
        double pop2 = john.getProperty("entity:popularity").getValue(Double.class);
        assertTrue(pop2 == pop1);

        assertEquals(occ1.getOccurrenceDocument().getRef(), occ2.getOccurrenceDocument().getRef());
        List<OccurrenceInfo> occurrences = occ2.getOccurrences();
        assertEquals(2, occurrences.size());
        assertEquals("John Lennon", occurrences.get(0).mention);
        assertEquals(0, occurrences.get(0).startPosInContext);
        assertEquals(11, occurrences.get(0).endPosInContext);
        assertEquals("John", occurrences.get(1).mention);

        // add an occurrence from another document
        OccurrenceRelation occ3 = service.addOccurrence(session, doc2.getRef(), john.getRef(),
                "John Lennon is not a lemon.", 0, 11);

        // check the increase in popularity
        john = session.getDocument(john.getRef());
        double pop3 = john.getProperty("entity:popularity").getValue(Double.class);
        assertTrue(pop3 > pop2);

        assertNotNull(occ3);
        assertEquals(1, occ3.getOccurrences().size());
        assertEquals("John Lennon", occ3.getOccurrences().get(0).mention);
        assertEquals(0, occ3.getOccurrences().get(0).startPosInContext);
        assertEquals(11, occ3.getOccurrences().get(0).endPosInContext);
    }

    @Test
    public void testGetRelatedDocumentsAndEntities() throws Exception {
        // create some entities in the KB and an unrelated document
        makeSomeEntities();

        PageProvider<DocumentModel> johnDocs = service.getRelatedDocuments(session, john.getRef(), null);
        assertEquals(0L, johnDocs.getCurrentPage().size());

        PageProvider<DocumentModel> doc1Entities = service.getRelatedEntities(session, doc1.getRef(), null);
        assertEquals(0L, doc1Entities.getCurrentPage().size());

        // add a relation between John and his bio
        service.addOccurrence(session, doc1.getRef(), john.getRef(), "John Lennon was born in Liverpool in 1940.", 0,
                11);

        johnDocs = service.getRelatedDocuments(session, john.getRef(), null);
        assertEquals(1L, johnDocs.getCurrentPage().size());
        DocumentModel doc0 = johnDocs.getCurrentPage().get(0);
        assertEquals("A short bio for John", doc0.getTitle());

        doc1Entities = service.getRelatedEntities(session, doc1.getRef(), null);
        assertEquals(1L, doc1Entities.getCurrentPage().size());
        DocumentModel ent0 = doc1Entities.getCurrentPage().get(0);
        assertEquals("John Lennon", ent0.getPropertyValue("dc:title"));

        // add a relation between Liverpool and John's bio
        service.addOccurrence(session, doc1.getRef(), liverpool.getRef(), "John was born in Liverpool in 1940.", 17, 26);

        // John still has only one related document, and Liverpool too
        johnDocs = service.getRelatedDocuments(session, john.getRef(), null);
        assertEquals(1L, johnDocs.getCurrentPage().size());
        doc0 = johnDocs.getCurrentPage().get(0);
        assertEquals("A short bio for John", doc0.getTitle());

        PageProvider<DocumentModel> liverpoolDocs = service.getRelatedDocuments(session, liverpool.getRef(), null);
        assertEquals(1L, liverpoolDocs.getCurrentPage().size());
        doc0 = liverpoolDocs.getCurrentPage().get(0);
        assertEquals("A short bio for John", doc0.getTitle());

        // the bio is hence now related to two entities
        doc1Entities = service.getRelatedEntities(session, doc1.getRef(), null);
        assertEquals(2L, doc1Entities.getCurrentPage().size());
        ent0 = doc1Entities.getCurrentPage().get(0);
        assertEquals("John Lennon", ent0.getPropertyValue("dc:title"));
        DocumentModel ent1 = doc1Entities.getCurrentPage().get(1);
        assertEquals("Liverpool", ent1.getPropertyValue("dc:title"));

        // We can restrict the entities to lookup by type
        doc1Entities = service.getRelatedEntities(session, doc1.getRef(), "Person");
        assertEquals(1L, doc1Entities.getCurrentPage().size());
        ent0 = doc1Entities.getCurrentPage().get(0);
        assertEquals("John Lennon", ent0.getPropertyValue("dc:title"));

        doc1Entities = service.getRelatedEntities(session, doc1.getRef(), "Place");
        assertEquals(1L, doc1Entities.getCurrentPage().size());
        ent0 = doc1Entities.getCurrentPage().get(0);
        assertEquals("Liverpool", ent1.getPropertyValue("dc:title"));
    }

    @Test
    public void testSuggestLocalEntitiesEmptyKB() {
        List<EntitySuggestion> suggestions = service.suggestLocalEntity(session, "John", null, 3);
        assertTrue(suggestions.isEmpty());
    }

    @Test
    public void testSuggestLocalEntities() {
        makeSomeEntities();

        List<EntitySuggestion> suggestions = service.suggestLocalEntity(session, "John", "Person", 3);
        assertEquals(2, suggestions.size());
        // by default the popularities are identical hence the ordering is
        // undefined
        List<DocumentModel> expectedEntities = Arrays.asList(john, johndoe);
        assertTrue(expectedEntities.contains(suggestions.get(0).entity));
        assertTrue(expectedEntities.contains(suggestions.get(1).entity));

        suggestions = service.suggestLocalEntity(session, "Lennon John", "Person", 3);
        assertEquals(1, suggestions.size());
        assertEquals(john, suggestions.get(0).entity);

        // make Lennon more popular my adding occurrences pointing to him
        service.addOccurrence(session, doc1.getRef(), john.getRef(), "John Lennon was born in Liverpool in 1940.", 0,
                11);

        // Lennon is now the top person for the "John" query
        suggestions = service.suggestLocalEntity(session, "John", "Person", 3);
        assertEquals(2, suggestions.size());
        assertEquals(john, suggestions.get(0).entity);
        assertEquals(johndoe, suggestions.get(1).entity);

        // Suggest based on exact match on alternative names
        suggestions = service.suggestLocalEntity(session, "John Winston Lennon", "Person", 3);
        assertEquals(1, suggestions.size());
        assertEquals(john, suggestions.get(0).entity);

        // create a new version for Lennon
        john.putContextData(VersioningService.VERSIONING_OPTION, VersioningOption.MAJOR);
        john = session.saveDocument(john);
        session.save();

        // We should still get only two suggestions (the archived versions are
        // not suggested)
        suggestions = service.suggestLocalEntity(session, "John", "Person", 3);
        assertEquals(2, suggestions.size());
        assertEquals(john, suggestions.get(0).entity);
        assertEquals(johndoe, suggestions.get(1).entity);

        // delete the john entity (using the trash)
        session.followTransition(john.getRef(), "delete");
        session.save();

        // We only get non-deleted live entities as suggestion
        suggestions = service.suggestLocalEntity(session, "John", "Person", 3);
        assertEquals(1, suggestions.size());
        assertEquals(johndoe, suggestions.get(0).entity);

        session.followTransition(john.getRef(), "undelete");
        session.save();
        suggestions = service.suggestLocalEntity(session, "John", "Person", 3);
        assertEquals(2, suggestions.size());
        assertEquals(john, suggestions.get(0).entity);
        assertEquals(johndoe, suggestions.get(1).entity);
    }

    @Test
    public void testSuggestLocalEntitiesWithNormalizedNames() {
        makeSomeEntities();

        // Sanity check: using the fulltext index for the title
        List<EntitySuggestion> suggestions = service.suggestLocalEntity(session, "Obama", "Person", 3);
        assertEquals(1, suggestions.size());

        // Sanity check: negative lookups
        suggestions = service.suggestLocalEntity(session, "someone_that_does_not_exist", "Person", 3);
        assertEquals(0, suggestions.size());

        // Check alternative names that does not require much normalization
        suggestions = service.suggestLocalEntity(session, "Barack Hussein Obama", "Person", 3);
        assertEquals(1, suggestions.size());
        suggestions = service.suggestLocalEntity(session, obamaWithHamza, "Person", 3);
        assertEquals(1, suggestions.size());

        // Check alternative names that does require normalization
        suggestions = service.suggestLocalEntity(session, obamaWithoutHamza, "Person", 3);
        assertEquals(1, suggestions.size());
    }

    @Test
    public void testSuggestEntities() throws Exception {
        // deploy off-line mock DBpedia source to override the default source
        // that needs an internet connection: comment the following contrib to
        // test again the real DBpedia server
        runtimeHarness.deployContrib("org.nuxeo.ecm.platform.semanticentities.core.tests",
                "OSGI-INF/test-semantic-entities-dbpedia-entity-contrib.xml");
        try {
            // empty local KB, only remote source output
            List<EntitySuggestion> suggestions = service.suggestEntity(session, "Barack Obama", "Person", 3);
            assertNotNull(suggestions);
            assertEquals(suggestions.size(), 1);
            EntitySuggestion firstGuess = suggestions.get(0);
            assertEquals("Barack Obama", firstGuess.label);
            assertEquals("http://dbpedia.org/resource/Barack_Obama", firstGuess.getRemoteUri());
            assertEquals("Person", firstGuess.type);
            assertFalse(firstGuess.isLocal());

            // synchronize the remote entity as a local entity
            DocumentModel localEntity = service.asLocalEntity(session, firstGuess);
            assertEquals(localEntity.getTitle(), "Barack Obama");

            // perform the same suggestion query again: this time the result is
            // local
            suggestions = service.suggestEntity(session, "Barack Obama", "Person", 3);
            assertNotNull(suggestions);
            assertEquals(suggestions.size(), 1);
            firstGuess = suggestions.get(0);
            assertEquals("Barack Obama", firstGuess.label);
            assertEquals("http://dbpedia.org/resource/Barack_Obama", firstGuess.getRemoteUri());
            assertEquals("Person", firstGuess.type);
            assertTrue(firstGuess.isLocal());
        } finally {
            runtimeHarness.undeployContrib("org.nuxeo.ecm.platform.semanticentities.core.tests",
                    "OSGI-INF/test-semantic-entities-dbpedia-entity-contrib.xml");
        }
    }

    @Test
    public void testSuggestEntitiesWithoutTypeRestriction() throws Exception {
        // deploy off-line mock DBpedia source to override the default source
        // that needs an internet connection: comment the following contrib to
        // test again the real DBpedia server
        runtimeHarness.deployContrib("org.nuxeo.ecm.platform.semanticentities.core.tests",
                "OSGI-INF/test-semantic-entities-dbpedia-entity-contrib.xml");
        try {
            // empty local KB, only remote source output
            List<EntitySuggestion> suggestions = service.suggestEntity(session, "Barack Obama", null, 3);
            assertNotNull(suggestions);
            assertEquals(suggestions.size(), 1);
            EntitySuggestion firstGuess = suggestions.get(0);
            assertEquals("Barack Obama", firstGuess.label);
            assertEquals("http://dbpedia.org/resource/Barack_Obama", firstGuess.getRemoteUri());
            assertEquals("Person", firstGuess.type);
            assertFalse(firstGuess.isLocal());

            // synchronize the remote entity as a local entity
            DocumentModel localEntity = service.asLocalEntity(session, firstGuess);
            assertEquals(localEntity.getTitle(), "Barack Obama");

            // perform the same suggestion query again: this time the result is
            // local
            suggestions = service.suggestEntity(session, "Barack Obama", "Person", 3);
            assertNotNull(suggestions);
            assertEquals(suggestions.size(), 1);
            firstGuess = suggestions.get(0);
            assertEquals("Barack Obama", firstGuess.label);
            assertEquals("http://dbpedia.org/resource/Barack_Obama", firstGuess.getRemoteUri());
            assertEquals("Person", firstGuess.type);
            assertTrue(firstGuess.isLocal());
        } finally {
            runtimeHarness.undeployContrib("org.nuxeo.ecm.platform.semanticentities.core.tests",
                    "OSGI-INF/test-semantic-entities-dbpedia-entity-contrib.xml");
        }
    }

    @Test
    public void testGetOccurrenceRelation() throws Exception {
        makeSomeEntities();
        OccurrenceRelation relation = service.getOccurrenceRelation(session, doc1.getRef(), john.getRef());
        assertNull(relation);

        List<OccurrenceInfo> mentions = Arrays.asList(new OccurrenceInfo("John Lennon",
                "John Lennon was born in Liverpool in 1940."), new OccurrenceInfo("John", "John was a musician."));
        service.addOccurrences(session, doc1.getRef(), john.getRef(), mentions);

        relation = service.getOccurrenceRelation(session, doc1.getRef(), john.getRef());
        assertNotNull(relation);
        assertEquals(doc1.getRef(), relation.getSourceDocumentRef());
        assertEquals(john.getRef(), relation.getTargetEntityRef());
        assertEquals(mentions, relation.getOccurrences());
    }

    @Test
    public void testAddRemoveOccurrenceRelationWithEmptyOccurrenceData() throws Exception {
        makeSomeEntities();
        OccurrenceRelation relation = service.getOccurrenceRelation(session, doc1.getRef(), john.getRef());
        assertNull(relation);
        assertEquals(0.0, (double) john.getProperty("entity:popularity").getValue(Double.class), 1e-8);

        service.addOccurrences(session, doc1.getRef(), john.getRef(), null);

        // check the popularity of john
        john = session.getDocument(john.getRef());
        assertEquals(1.0, (double) john.getProperty("entity:popularity").getValue(Double.class), 1e-8);

        relation = service.getOccurrenceRelation(session, doc1.getRef(), john.getRef());
        assertNotNull(relation);
        assertEquals(doc1.getRef(), relation.getSourceDocumentRef());
        assertEquals(john.getRef(), relation.getTargetEntityRef());
        assertEquals(Arrays.asList(), relation.getOccurrences());

        // check removal of relation
        service.removeOccurrences(session, doc1.getRef(), john.getRef(), false);

        // check that the relation has been removed
        DocumentRef relationRef = relation.getOccurrenceDocument().getRef();
        if (session.exists(relationRef)) {
            assertEquals("deleted", session.getCurrentLifeCycleState(relationRef));
        }

        // check that john has not been deleted (john was not created by the
        // system user)
        assertTrue(session.exists(john.getRef()));
        assertFalse("Entity should not have been marked as deleted",
                "deleted".equals(session.getCurrentLifeCycleState(john.getRef())));

        // check the popularity of john
        john = session.getDocument(john.getRef());
        assertEquals(0.0, (double) john.getProperty("entity:popularity").getValue(Double.class), 1e-8);
    }

    @Test
    public void testSuggestDocument() throws Exception {
        List<DocumentModel> suggestions = service.suggestDocument(session, "lemon", null, 3);
        assertNotNull(suggestions);
        assertEquals(1, suggestions.size());
        assertEquals(doc2.getRef(), suggestions.get(0).getRef());

        suggestions = service.suggestDocument(session, "Lennon John", null, 3);
        assertNotNull(suggestions);
        assertEquals(2, suggestions.size());
        // order is not deterministic
        assertEquals(new HashSet<>(Arrays.asList(doc1.getId(), doc2.getId())),
                new HashSet<>(Arrays.asList(suggestions.get(0).getId(), suggestions.get(1).getId())));

        // check that entities don't show up in the results
        makeSomeEntities();
        suggestions = service.suggestDocument(session, "Lennon John", null, 3);
        assertNotNull(suggestions);
        assertEquals(2, suggestions.size());
        // order is not deterministic
        assertEquals(new HashSet<>(Arrays.asList(doc1.getId(), doc2.getId())),
                new HashSet<>(Arrays.asList(suggestions.get(0).getId(), suggestions.get(1).getId())));
    }

    @Test
    public void testSuggestEntityWithSpecialCharacters() throws Exception {
        DocumentModel container = service.getEntityContainer(session);
        assertNotNull(container);
        assertEquals(Constants.ENTITY_CONTAINER_TYPE, container.getType());
        DocumentModel canalplus = session.createDocumentModel(container.getPathAsString(), null, "Organization");
        canalplus.setPropertyValue("dc:title", "Canal +");
        canalplus = session.createDocument(canalplus);
        session.save();

        // fulltext query with a + sign should be escaped
        List<EntitySuggestion> suggestions = service.suggestLocalEntity(session, "Canal +", null, 3);
        assertNotNull(suggestions);
        assertEquals(1, suggestions.size());
        assertEquals(canalplus.getId(), suggestions.get(0).getLocalId());
    }

    @Test
    public void testGetLinkedLocalEntity() throws Exception {
        URI johnURI = URI.create("http://dbpedia.org/resource/John_Lennon");

        // empty KB will not yield any match
        DocumentModel linkedEntity = service.getLinkedLocalEntity(session, johnURI);
        assertNull(linkedEntity);

        makeSomeEntities();

        // the service can find john since it's properly linked
        linkedEntity = service.getLinkedLocalEntity(session, johnURI);
        assertNotNull(linkedEntity);
        assertEquals(john.getRef(), linkedEntity.getRef());

    }

    @Test
    public void testNameNormalization() {
        assertEquals("this is a test", service.normalizeName("This is. a\n test?"));
        assertEquals("a b", service.normalizeName("a'.;,<>b"));

        // check french accents normalization (french diacritics)
        assertEquals("youpi c est l ete", service.normalizeName("Youpi, c'est l'\u00e9t\u00e9!"));

        // check arabic diacritics normalization: hamza below
        String israelArabicWithoutHamza = "\u0627\u0633\u0631\u0627\u0626\u064a\u0644";
        String israelArabicWithHamza = "\u0625\u0633\u0631\u0627\u0626\u064a\u0644";
        assertEquals(service.normalizeName(israelArabicWithoutHamza), service.normalizeName(israelArabicWithHamza));

        // check arabic diacritics normalization: hamza above
        assertEquals(service.normalizeName(obamaWithoutHamza), service.normalizeName(obamaWithHamza));
    }

}
