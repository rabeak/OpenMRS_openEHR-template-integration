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
		Document ehrExtract=createXML(extractData);
		cleanXML(ehrExtract, extractData);
		validateEHRExtract(ehrExtract);
		try {
			saveXMLToFile(ehrExtract);
		} catch (IOException ioex) {
			ioex.printStackTrace();
		}
		this.closeDBConnection();
	}
	
	
	private Document createXML(LinkedHashMap<String, String> extractDataMap) {
		Namespace XSI_NAMESPACE = Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
		Namespace OPENEHR_NAMESPACE = Namespace.getNamespace("http://schemas.openehr.org/v1");

		Element rootElement = new Element("extract", OPENEHR_NAMESPACE);
		rootElement.addNamespaceDeclaration(XSI_NAMESPACE);
		rootElement.addNamespaceDeclaration(OPENEHR_NAMESPACE);
		
/*		Attribute schemaLoc = new Attribute("schemaLocation", "http://schemas.openehr.org/v1 C:\\Users\\Rabea\\workspace1\\openmrs-template-integration-v2\\src\\main\\schemas\\extract.xsd",
			    XSI_NAMESPACE);
		rootElement.setAttribute(schemaLoc);*/
		
		//TODO: adding node id of the AT root node???
		rootElement.setAttribute(new Attribute("archetype_node_id", "openEHR-EHR-EXTRACT.encounterNo"+encounterId+".v1"));
		
		Element name=new Element("name", OPENEHR_NAMESPACE);
		name.addContent(new Element("value", OPENEHR_NAMESPACE).addContent("Encounter No. " +encounterId));
		rootElement.addContent(name);
		
		Element uid=new Element("uid", OPENEHR_NAMESPACE);
		uid.setAttribute("type", "HIER_OBJECT_ID", XSI_NAMESPACE);
		uid.addContent(new Element("value", OPENEHR_NAMESPACE).addContent(UUID.randomUUID().toString()));
		rootElement.addContent(uid);
		rootElement.addContent(new Element("sequence_nr", OPENEHR_NAMESPACE).addContent("1")); //mandatory element, default value = 1
		
		//according to old extract specification (revision 2.0)
		Element chapter=new Element("chapters", OPENEHR_NAMESPACE);
		chapter.setAttribute(new Attribute("archetype_node_id", "at0000"));
		Element chapterName=new Element("name", OPENEHR_NAMESPACE);
		chapterName.addContent(new Element("value", OPENEHR_NAMESPACE).addContent("openEHR-EHR-EXTRACT_CHAPTER")); //TODO ???
		chapter.addContent(chapterName);
		chapter.addContent(new Element("entity_identifier", OPENEHR_NAMESPACE));	
		Element content=new Element("content", OPENEHR_NAMESPACE);		
		content.setAttribute(new Attribute("type", "GENERIC_EXTRACT_CONTENT", XSI_NAMESPACE));
		Element items=new Element("items", OPENEHR_NAMESPACE); //generic extract item
		items.setAttribute(new Attribute("type", "GENERIC_EXTRACT_ITEM", XSI_NAMESPACE));
		Element uid2=new Element("uid", OPENEHR_NAMESPACE);
		uid2.setAttribute("type", "HIER_OBJECT_ID", XSI_NAMESPACE);
		uid2.addContent(new Element("value", OPENEHR_NAMESPACE).addContent(UUID.randomUUID().toString()));
		items.addContent(uid2);
		items.addContent(new Element("is_primary", OPENEHR_NAMESPACE).addContent("true"));
		items.addContent(new Element("is_changed", OPENEHR_NAMESPACE).addContent("false"));
		items.addContent(new Element("is_masked", OPENEHR_NAMESPACE).addContent("false"));		
		content.addContent(items); 		
		chapter.addContent(content);
		rootElement.addContent(chapter);
		
		//according to new extract specification (Rev 2.1)
/*		Element extractEntityChapter=new Element("extract_entity_chapter", OPENEHR_NAMESPACE);
		extractEntityChapter.setAttribute(new Attribute("archetype_node_id", "")); //TODO
		Element entityChapterName=new Element("name", OPENEHR_NAMESPACE);
		entityChapterName.addContent(""); //TODO
		extractEntityChapter.addContent(entityChapterName);
		Element entityId=new Element("entity_identifier", OPENEHR_NAMESPACE);
		Element extract_id_key=new Element("extract_id_key", OPENEHR_NAMESPACE); //Identifier by which this entity is known in the Extract
		extract_id_key.addContent(""); //TODO
		entityId.addContent(extract_id_key);
		extractEntityChapter.addContent(entityId);
		rootElement.addContent(extractEntityChapter);
		//generic extract item:
		Element items=new Element("items", OPENEHR_NAMESPACE); //in Element items kommen die item-Elemente vom Typ Locatable hinein
		Element uid=new Element("uid", OPENEHR_NAMESPACE);
		Element isPrimary=new Element("is_primary", OPENEHR_NAMESPACE);
		isPrimary.addContent("true");
		items.addContent(uid);
		items.addContent(isPrimary);
		extractEntityChapter.addContent(items); */
							
		Document doc = new Document(rootElement);

		for (Map.Entry<String, String> entry : extractDataMap.entrySet()) {
			String path=entry.getKey();
			String value=entry.getValue();
			doc = addElementFromMap(path, value, doc);
		}
		//Element itemType=new Element("item_type", OPENEHR_NAMESPACE);
		//items.addContent(itemType);
		return doc;
	}
	
	private Document addElementFromMap(String key, String value, Document doc) {
		Namespace XSI_NAMESPACE = Namespace.getNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
		Namespace OPENEHR_NAMESPACE = Namespace.getNamespace("http://schemas.openehr.org/v1");
		
		try {
			/*prefix oehr is assigned to the openehr namespace, as xpath supports no default namespace (xmlns)
			 * oehr is added to all paths too*/
			String temp11 = key.replace("/", "/oehr:");
			String temp33 = "/oehr:extract/oehr:chapters/oehr:content/oehr:items/oehr:item"+temp11.replace("/oehr:at", "/at");

			XPath xpath = XPath.newInstance(temp33);
			Namespace nsConfig = Namespace.getNamespace("oehr", "http://schemas.openehr.org/v1");
			xpath.addNamespace(nsConfig);
			if (xpath.selectSingleNode(doc) == null) {
				/*nodeInformation includes the name of the parent as well as elements, attribute values etc. parsed from the xpath*/
				HashMap<String, String> nodeInformation = getXpathParent("/extract/chapters/content/items/item"+key); 
				String temp1 = nodeInformation.get("parent").replace("/", "/oehr:");			
				String temp3 = temp1.replace("/oehr:at", "/at");

				XPath parentXPath = XPath.newInstance(temp3);
				Namespace nsConfig1 = Namespace.getNamespace("oehr", "http://schemas.openehr.org/v1");
				parentXPath.addNamespace(nsConfig1);

				if (parentXPath.selectSingleNode(doc) != null) {
					Element element=new Element(nodeInformation.get("element"), OPENEHR_NAMESPACE);
					if (nodeInformation.get("archetype_node_id") != null) {
						element.setAttribute(new Attribute("archetype_node_id", nodeInformation.get("archetype_node_id")));
					}
					String type=nodeInformation.get("xsi:type");
					if (type != null) {
						if(type.equalsIgnoreCase("Iso8601DateTime")) { /*changing format*/
							value=value.replaceAll("\\s+", "T"); /*replacing whitespaces with T*/
						}
						element.setAttribute("type", type, XSI_NAMESPACE);
					} 
					element.setText(value);
					Element node = (Element) parentXPath.selectSingleNode(doc);

					if (nodeInformation.get("element").equalsIgnoreCase("name") && element.getAttribute("type", XSI_NAMESPACE).getValue().equalsIgnoreCase("DV_TEXT")) {
						node.addContent(0, element);
					} else {
						node.addContent(element);
					}
					//System.out.println("Done! XPath: "+xpath.getXPath());
				} else {
					//System.out.println("ERROR! XPath: "+xpath.getXPath());
				}
			} 

		} catch (Exception e) {
			e.printStackTrace();
		}
		return doc;
	}
	
	private HashMap<String, String> getXpathParent(String key) {
		HashMap<String, String> newParent = new HashMap<String, String>();
		String newParents = "";

		if (key.lastIndexOf("]") < key.lastIndexOf("/")) {

			newParent.put("parent", key.substring(0, key.lastIndexOf("/")));
			newParent.put("element", key.substring(key.lastIndexOf("/") + 1, key.length()));
		} else {
			newParents = key.substring(key.lastIndexOf("[") + 1, key.lastIndexOf("]"));
			if (newParents.matches("\\d+")) { /*if the path includes a number at the end, e.g.[1]*/
				String temp = key.substring(0, key.lastIndexOf("["));
				String tempT = temp.substring(0, temp.lastIndexOf("["));
				newParent.put("element", tempT.substring(tempT.lastIndexOf("/") + 1, tempT.length()));

				String tempAusdruck = key.substring(temp.lastIndexOf("["), temp.lastIndexOf("]"));
				if (tempAusdruck.contains("@archetype_node_id")) {
					String tempat = tempAusdruck.substring(tempAusdruck.indexOf("@archetype_node_id") + 20, tempAusdruck.length());
					newParent.put("archetype_node_id", tempat.substring(0, tempat.indexOf("'")));
				}
				if (tempAusdruck.contains("@xsi:type")) {
					String tempat = tempAusdruck.substring(tempAusdruck.indexOf("@xsi:type") + 11, tempAusdruck.length());
					newParent.put("xsi:type", getXMLSimpleType(tempat.substring(0, tempat.indexOf("'"))));										
				}
				newParents = key.substring(0, temp.substring(0, temp.lastIndexOf("[") + 1).lastIndexOf("/"));
				newParent.put("parent", newParents);
			} else {
				String temp = key.substring(0, key.lastIndexOf("["));
				newParent.put("element", temp.substring(temp.lastIndexOf("/") + 1, temp.length()));

				String tempAusdruck = key.substring(key.lastIndexOf("[") + 1, key.lastIndexOf("]"));
				if (tempAusdruck.contains("@archetype_node_id")) {
					String tempat = tempAusdruck.substring(tempAusdruck.indexOf("@archetype_node_id") + 20, tempAusdruck.length());
					newParent.put("archetype_node_id", tempat.substring(0, tempat.indexOf("'")));
				}
				if (tempAusdruck.contains("@xsi:type")) {
					String tempat = tempAusdruck.substring(tempAusdruck.indexOf("@xsi:type") + 11, tempAusdruck.length());				
					newParent.put("xsi:type", getXMLSimpleType(tempat.substring(0, tempat.indexOf("'"))));					
				}
				newParents = key.substring(0, key.substring(0, key.lastIndexOf("[") + 1).lastIndexOf("/"));
				newParent.put("parent", newParents);
			}
		}
		return newParent;
	}
	
	public void cleanXML(Document ehrExtract, LinkedHashMap<String,String> xpathsMap) { /*empty nodes are deleted*/
		/*xpaths are processed backwards and empty XML elements are delted
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
