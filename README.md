Integration of openEHR Templates into OpenMRS
====================================

With this prototype implementation forms can be generated from openEHR operational templates in the open source EHR system OpenMRS and data entered in those forms can be exported as archetype-compliant EHR extracts.

<h3>Prerequisites</h3>

<ul>
  <li>This prototype implementation has been developed using Eclipse Juno with m2e – Maven Integration for Eclipse.</li>
  <li>The <a href="https://github.com/wware/openehr-java">openEHR Java Libs (1.0.2)</a> need to be build and imported as Maven projects into the workspace.</li>
  <li>OpenMRS version 1.9 and the XForms module version 4.2.1.0 have been used to test the implementation.</li>
  <li>The SQL statements provided in the file "src/main/resources/openehr-metadata-script.sql" must be applied to the OpenMRS mySQL database in order for the code to work.</li>
</ul>

<h3>Start</h3>

<ul>
<li>The generator can be started with the GenerateCompTemplate.java class, which can be found under src/main/java/opt_import/GenerateCompTemplate.java.</li>
<li>The openEHR operational templates used for testing can be found under "src/main/resources/Templates/". They have been created with the Ocean Template Designer version 2.6 Beta.</li>
</ul>

<h3>References</h3>

An existing approach for the plug-and-play integration of archetypes into legacy EHR systems has been applied in this prototype implementation using templates instead of archetypes:

It is described in G. Duftschmid, J. Chaloupka, and C. Rinner, “Towards plug-and-play integration of archetypes into legacy electronic health record systems: the ArchiMed experience,” BMC Medical Informatics and Decision Making, vol. 13, no. 1, p. 11, 2013.

