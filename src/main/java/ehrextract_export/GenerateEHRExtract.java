package ehrextract_export;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.regex.Pattern;

import mysql.DatabaseStatements;
import mysql.MySqlAccess;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.jdom.Document;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;
import org.jdom.transform.JDOMSource;
import org.jdom.xpath.XPath;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.xml.sax.SAXException;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

public class GenerateEHRExtract {
	
	Connection connect;
	DatabaseStatements dbStatements;
	int encounterId;

	public void generateEHRExtract(int encounterId) {
		this.encounterId=encounterId;
		this.getDBConnection();
		LinkedHashMap<String,String> extractData = new LinkedHashMap<String,String>();		
		extractData=dbStatements.getObsAndPathsFromEncounterId(connect, encounterId);
		Document ehrExtract=generateXML(extractData);
		cleanXML(ehrExtract, extractData);
		validateEHRExtract(ehrExtract);
		try {
			saveXMLToFile(ehrExtract);
		} catch (IOException ioex) {
			ioex.printStackTrace();
		}
		this.closeDBConnection();
	}
	
	
	private Document generateXML(LinkedHashMap<String, String> extractDataMap) {
		Namespace xsiNS = Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
		Namespace openEHRNS = Namespace.getNamespace("http://schemas.openehr.org/v1");

		Element root = new Element("extract", openEHRNS);
		root.addNamespaceDeclaration(xsiNS);
		root.addNamespaceDeclaration(openEHRNS);
		
		//TODO: adding node id of the AT root node???
		root.setAttribute(new Attribute("archetype_node_id", "openEHR-EHR-EXTRACT.encounterNo"+encounterId+".v1"));
		
		Element name=new Element("name", openEHRNS);
		name.addContent(new Element("value", openEHRNS).addContent("Encounter No. " +encounterId));
		root.addContent(name);
		
		Element uid=new Element("uid", openEHRNS);
		uid.setAttribute("type", "HIER_OBJECT_ID", xsiNS);
		uid.addContent(new Element("value", openEHRNS).addContent(UUID.randomUUID().toString()));
		root.addContent(uid);
		root.addContent(new Element("sequence_nr", openEHRNS).addContent("1")); //mandatory element, default value = 1
		
		//according to old extract specification (revision 2.0)
		Element chapter=new Element("chapters", openEHRNS);
		chapter.setAttribute(new Attribute("archetype_node_id", "at0000"));
		Element chapterName=new Element("name", openEHRNS);
		chapterName.addContent(new Element("value", openEHRNS).addContent("openEHR-EHR-EXTRACT_CHAPTER")); //TODO ???
		chapter.addContent(chapterName);
		chapter.addContent(new Element("entity_identifier", openEHRNS));	
		Element content=new Element("content", openEHRNS);		
		content.setAttribute(new Attribute("type", "GENERIC_EXTRACT_CONTENT", xsiNS));
		Element items=new Element("items", openEHRNS); //generic extract item
		items.setAttribute(new Attribute("type", "GENERIC_EXTRACT_ITEM", xsiNS));
		Element uid2=new Element("uid", openEHRNS);
		uid2.setAttribute("type", "HIER_OBJECT_ID", xsiNS);
		uid2.addContent(new Element("value", openEHRNS).addContent(UUID.randomUUID().toString()));
		items.addContent(uid2);
		items.addContent(new Element("is_primary", openEHRNS).addContent("true"));
		items.addContent(new Element("is_changed", openEHRNS).addContent("false"));
		items.addContent(new Element("is_masked", openEHRNS).addContent("false"));		
		content.addContent(items); 		
		chapter.addContent(content);
		root.addContent(chapter);
		
		//according to new extract specification (Rev 2.1)
		/*Element extractEntityChapter=new Element("extract_entity_chapter", openEHRNS);
		extractEntityChapter.setAttribute(new Attribute("archetype_node_id", "")); //TODO
		Element entityChapterName=new Element("name", openEHRNS);
		entityChapterName.addContent(""); //TODO
		extractEntityChapter.addContent(entityChapterName);
		Element entityId=new Element("entity_identifier", openEHRNS);
		Element extract_id_key=new Element("extract_id_key", openEHRNS); //Identifier by which this entity is known in the Extract
		extract_id_key.addContent(""); //TODO
		entityId.addContent(extract_id_key);
		extractEntityChapter.addContent(entityId);
		root.addContent(extractEntityChapter);
		//generic extract item:
		Element items=new Element("items", openEHRNS); //in Element items kommen die item-Elemente vom Typ Locatable hinein
		Element uid=new Element("uid", openEHRNS);
		Element isPrimary=new Element("is_primary", openEHRNS);
		isPrimary.addContent("true");
		items.addContent(uid);
		items.addContent(isPrimary);
		extractEntityChapter.addContent(items); */
							
		Document extractDoc = new Document(root);

		for (Map.Entry<String, String> entry : extractDataMap.entrySet()) {
			String path=entry.getKey();
			String value=entry.getValue();
			extractDoc = addXMLElementFromPath(path, value, extractDoc);
		}
		//Element itemType=new Element("item_type", openEHRNS);
		//items.addContent(itemType);
		return extractDoc;
	}
	
	private Document addXMLElementFromPath(String pPath, String value, Document xmlDoc) {
		Namespace xsiNS = Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
		Namespace openEHRNS = Namespace.getNamespace("http://schemas.openehr.org/v1");
		
		try {
			/*prefix oehr is assigned to the openehr namespace, as xpath supports no default namespace (xmlns)
			 * oehr is added to all paths too*/
			String pathNS = pPath.replace("/", "/oehr:");
			String fullPathNS = "/oehr:extract/oehr:chapters/oehr:content/oehr:items/oehr:item"+pathNS.replace("/oehr:at", "/at");

			XPath xpath = XPath.newInstance(fullPathNS);
			Namespace nsOEHR = Namespace.getNamespace("oehr", "http://schemas.openehr.org/v1");
			xpath.addNamespace(nsOEHR);
			if (xpath.selectSingleNode(xmlDoc) == null) {
				/*nodeInformation includes the name of the parent as well as elements, attribute values etc. parsed from the xpath*/
				HashMap<String, String> nodeInformation = getParentFromXPath("/extract/chapters/content/items/item"+pPath); 
				String parentPathNS = nodeInformation.get("parentXPath").replace("/", "/oehr:");			
				String parentPathWONS = parentPathNS.replace("/oehr:at", "/at");

				XPath parentXPath = XPath.newInstance(parentPathWONS);
				parentXPath.addNamespace(nsOEHR);

				if (parentXPath.selectSingleNode(xmlDoc) != null) {
					Element element=new Element(nodeInformation.get("elementName"), openEHRNS);
					if (nodeInformation.get("archetype_node_id") != null) {
						element.setAttribute(new Attribute("archetype_node_id", nodeInformation.get("archetype_node_id")));
					}
					String type=nodeInformation.get("xsi:type");
					if (type != null) {
						if(type.equalsIgnoreCase("Iso8601DateTime")) { /*changing format*/
							value=value.replaceAll("\\s+", "T"); /*replacing whitespaces with T*/
						}
						element.setAttribute("type", type, xsiNS);
					} 
					element.setText(value);
					Element parentNode = (Element) parentXPath.selectSingleNode(xmlDoc);

					if (nodeInformation.get("elementName").equalsIgnoreCase("name") && element.getAttribute("type", xsiNS).getValue().equalsIgnoreCase("DV_TEXT")) {
						parentNode.addContent(0, element);
					} else {
						parentNode.addContent(element);
					}
					//System.out.println("Done! XPath: "+xpath.getXPath());
				} else {
					//System.out.println("ERROR! XPath: "+xpath.getXPath());
				}
			} 

		} catch (Exception e) {
			e.printStackTrace();
		}
		return xmlDoc;
	}
	
	private HashMap<String, String> getParentFromXPath(String path) {
		
		int posLastBracketOpen=path.lastIndexOf("[");
		int posLastBracketClose=path.lastIndexOf("]");
		int posLastSlash=path.lastIndexOf("/");
		
		HashMap<String, String> parentInfoMap = new HashMap<String, String>();

		if (posLastBracketClose > posLastSlash) {

			String pathWOBrackets = path.substring(0, posLastBracketOpen);
			String textInBrackets = path.substring(posLastBracketOpen + 1, posLastBracketClose);
			
			if (textInBrackets.matches("\\d+")) { /*if the path includes a number at the end, e.g.[1]*/
				
				String pathCorr = pathWOBrackets.substring(0, pathWOBrackets.lastIndexOf("["));
				parentInfoMap.put("elementName", pathCorr.substring(pathCorr.lastIndexOf("/") + 1, pathCorr.length()));
				
				String parentPath = pathWOBrackets.substring(0, pathCorr.lastIndexOf("/"));
				parentInfoMap.put("parentXPath", parentPath);
				
				String textInBrackets2 = pathWOBrackets.substring(pathWOBrackets.lastIndexOf("[")+1, pathWOBrackets.lastIndexOf("]"));
				
				if (textInBrackets2.contains("@xsi:type")) {
					String typeText = textInBrackets2.substring(textInBrackets2.indexOf("@xsi:type") + 11, textInBrackets2.length());
					parentInfoMap.put("xsi:type", getXMLSimpleType(typeText.substring(0, typeText.indexOf("'"))));										
				}
				
				if (textInBrackets2.contains("@archetype_node_id")) {
					String atNodeId = textInBrackets2.substring(textInBrackets2.indexOf("@archetype_node_id") + 20, textInBrackets2.length());
					parentInfoMap.put("archetype_node_id", atNodeId.substring(0, atNodeId.indexOf("'")));
				}

			} else {
				parentInfoMap.put("elementName", pathWOBrackets.substring(pathWOBrackets.lastIndexOf("/") + 1, pathWOBrackets.length()));
				String parentPath = path.substring(0, pathWOBrackets.lastIndexOf("/"));
				parentInfoMap.put("parentXPath", parentPath);

				if (textInBrackets.contains("@xsi:type")) {
					String typeText = textInBrackets.substring(textInBrackets.indexOf("@xsi:type") + 11, textInBrackets.length());				
					parentInfoMap.put("xsi:type", getXMLSimpleType(typeText.substring(0, typeText.indexOf("'"))));					
				}
				
				if (textInBrackets.contains("@archetype_node_id")) {
					String atNodeId = textInBrackets.substring(textInBrackets.indexOf("@archetype_node_id") + 20, textInBrackets.length());
					parentInfoMap.put("archetype_node_id", atNodeId.substring(0, atNodeId.indexOf("'")));
				}
			}			
		} else {
			parentInfoMap.put("parentXPath", path.substring(0, posLastSlash));
			parentInfoMap.put("elementName", path.substring(posLastSlash + 1, path.length()));
		}
		return parentInfoMap;
	}
	
	public void cleanXML(Document ehrExtract, LinkedHashMap<String,String> xpathsMap) { 
		/*xpaths are processed backwards and empty XML elements are deleted
		 * afterwards elements which only include name or terminology_id are deleted*/
		
		List<String> xpaths = new ArrayList<String>(xpathsMap.keySet());
		ListIterator<String> itr = xpaths.listIterator(xpaths.size());
		while (itr.hasPrevious()) {		
			try {
				String temp1 = itr.previous().replace("/", "/oehr:");	
				String temp2=temp1.replace("'DATE_TIME'", "'Iso8601DateTime'");
				temp2=temp2.replace("'DATE'", "'Iso8601Date'");
				temp2=temp2.replace("'TIME'", "'Iso8601Time'");
				temp2=temp2.replace("'DURATION'", "'Iso8601Duration'");
				String temp3 = temp2.replace("/oehr:at", "/at");
				XPath xpath=XPath.newInstance("/oehr:extract/oehr:chapters/oehr:content/oehr:items/oehr:item"+temp3);
				
				Namespace nsConfig1 = Namespace.getNamespace("oehr", "http://schemas.openehr.org/v1");
				xpath.addNamespace(nsConfig1);
				
				Element node=(Element)xpath.selectSingleNode(ehrExtract);
				if(node!=null) {	
					if(node.getChildren().isEmpty()) {
						if(node.getText()==null || node.getText().equalsIgnoreCase(" ") || node.getText().equalsIgnoreCase("")) {
							node.getParent().removeContent(node);
							Element node2=(Element)node.getParent();
							while(node2!=null) {								
								if(node2.getChildren().isEmpty() && (node2.getText()==null || node2.getText().equalsIgnoreCase(" ") || node2.getText().equalsIgnoreCase(""))) {
									node2.getParent().removeContent(node2);
								} else {
									break;
								}
								node2=(Element)node2.getParent();
							}
						} 
					}
					else if((node.getChildren().size()==1 && ((Element)node.getChildren().get(0)).getName().equalsIgnoreCase("name"))
							|| (node.getChildren().size()==1 && ((Element)node.getChildren().get(0)).getName().equalsIgnoreCase("terminology_id"))){
						node.getParent().removeContent(node);
					}
				}
			} catch(JDOMException ex) {
				ex.printStackTrace();
			}			
		}
	}
	
	public void validateEHRExtract(Document ehrExtract) {
		
		SchemaFactory sf = SchemaFactory.newInstance( XMLConstants.W3C_XML_SCHEMA_NS_URI );
		try {
			File xsdFile=new File("src/main/schemas/extract.xsd");
			Schema schema = sf.newSchema(xsdFile);
			Validator validator = schema.newValidator();
			MyErrorHandler errorHandler=new MyErrorHandler(); 
			validator.setErrorHandler(errorHandler);
			JDOMSource source=new JDOMSource(ehrExtract);
			validator.validate(source);
		} catch (SAXException ex) {
			System.out.println("SAXException: "+ex.getMessage());
		} catch (IOException e) {
			System.out.println("IOException: "+e.getMessage());
		}	
	}
	
	static class MyErrorHandler implements ErrorHandler { //Error Handler for XML validation
				
		public void warning(SAXParseException ex) throws SAXException {				
			System.out.println("Validation warning: "+ex.toString());				
		}		
		public void fatalError(SAXParseException ex) throws SAXException {
			System.out.println("Validation fatal error: "+ex.toString());
			
		}		
		public void error(SAXParseException ex) throws SAXException {
			System.out.println("Validation error: "+ex.toString());
		}
	}
	
	/*EHR extract is saved to file*/
	public void saveXMLToFile(Document doc) throws IOException {		
		XMLOutputter xmlOutput = new XMLOutputter();
		Format format = Format.getPrettyFormat();
		format.setEncoding("iso-8859-1");
		xmlOutput.setFormat(format);		
		xmlOutput.output(doc, new FileWriter("src/main/resources/openEHR-EHR-EXTRACT.encounterNo"+encounterId+".v1.xml"));		
	}
		
	private void getDBConnection() {
		MySqlAccess dao=new MySqlAccess();
		try {
			this.connect=dao.getDatabaseConnection();
		} catch (SQLException e) {
			System.out.println("SQLException: " + e.getMessage());
		} catch (Exception ex) {
			System.out.println("Exception: " + ex.getMessage());
		} 
	    dbStatements=new DatabaseStatements();
	}
	
	private void closeDBConnection() {
		try {
			this.connect.close();
		} catch (SQLException ex) {
			System.out.println("SQLException: " + ex.getMessage());
		}
	}
	
	private static String getXMLSimpleType(String type) {
		Map<String, String> simpleTypes=new HashMap<String, String>();
		simpleTypes.put("DATE_TIME", "Iso8601DateTime");
		simpleTypes.put("DATE", "Iso8601Date");	
		simpleTypes.put("TIME", "Iso8601Time");
		simpleTypes.put("DURATION", "Iso8601Duration");
		
		if(simpleTypes.get(type)!=null) {		
			return simpleTypes.get(type);
		} else {
			return type;
		}
	}
}
