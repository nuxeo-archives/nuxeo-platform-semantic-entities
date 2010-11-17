NXP Semantic Entities
=====================

This project defines new document types such as Person, Place and Organization
and a service to link those entities to regular nuxeo document using Nuxeo Core
relations named Occurrences along with dedicated JSF views and operations to
automatically extract occurrences in the text content of documents.

A working internet connection with HTTP access to http://dbpedia.org and
http://fise.demo.nuxeo.com is required.

You can test if the plugin is working correctly by importing the HTML files from
the wikinews-stories/ folder.

WARNING: the default configuration will send the text content of your documents
to http://fise.demo.nuxeo.com for analysis: DO NOT USE WITH CONFIDENTIAL
DOCUMENTS!

For production setups, the package should be configured to use your own local
instance of the Apache Stanbol services (work in progress).
