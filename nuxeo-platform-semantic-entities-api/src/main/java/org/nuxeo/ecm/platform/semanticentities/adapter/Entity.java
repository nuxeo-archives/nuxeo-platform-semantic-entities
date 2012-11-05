package org.nuxeo.ecm.platform.semanticentities.adapter;

public interface Entity {

    Entity withType(String facetName);

    Entity withName(String text, String lang);

    Entity withTransliteratedName(String text, String lang, String transliterationText, String transliterationLanguage);

    Entity withSummary(String text, String lang);

}
