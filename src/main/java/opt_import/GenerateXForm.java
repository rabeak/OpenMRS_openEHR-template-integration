package opt_import;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import org.openehr.jaxb.*;

import nu.xom.*;

import mysql.*;

import org.openehr.rm.datatypes.text.CodePhrase;
import org.openehr.rm.support.terminology.CodeSetAccess;
import org.openehr.rm.support.terminology.TerminologyAccess;
import org.openehr.rm.support.terminology.TerminologyService;
import org.openehr.terminology.*;

public class GenerateXForm {
	
	private Connection connect;
	private DatabaseStatements dbStatements;
	private int formId;
	private UUID uuid;
	private String xformXml;
	private String layoutXml;
	private OPERATIONALTEMPLATE template;
	private Document xform;
	private Document layout;
	private int lastLayoutPositionTop;
	private int lastGroupPositionTop;
	private int lastRepeatPositionLeft;
	private int lastTabIndex;
	private int lastPositionLeft;
	private Element page;
	private Boolean listBoxHelp;
	private String labelInRepeat;
	private Map<String,String> xpathDefaultValuesMap;
	private int repeatMetadataId;
	
	public GenerateXForm(OPERATIONALTEMPLATE opt)
	{
		this.uuid=UUID.randomUUID();
		this.template=opt;
		this.xformXml="";
		this.layoutXml="";
		this.lastLayoutPositionTop=-20;
		this.lastGroupPositionTop=-30;
		this.lastRepeatPositionLeft=-190;
		this.lastTabIndex=0;
		this.lastPositionLeft=30;
		this.page=new Element("Page");
		this.listBoxHelp=false;
		this.labelInRepeat=""; /*needed to concatenate the labels of subnodes of repeating nodes*/	
		BasicXFormBuilder formBuilder=new BasicXFormBuilder();
		this.xform=formBuilder.createBasicXForm();
		this.layout=formBuilder.createBasicLayout();
		xpathDefaultValuesMap=this.getDefaultValues(opt);
	}
	
	public void createOpenMRSXForm() {
		try {
			this.getDBConnection();
			createDBForm();			
		} catch (SQLException ex) {
			System.out.println("SQLException: " + ex.getMessage());
			System.out.println("SQLState: " + ex.getSQLState());
			System.out.println("VendorError: " + ex.getErrorCode());
			ex.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if(this.connect!=null) {
					this.connect.close();
				}
			} catch(SQLException se) {
					se.printStackTrace();
			}
		}
	}
		
	/*creation of OpenMRS form table entry*/
	private void createDBForm()
	{	
		int rs2_value;
		this.formId=dbStatements.createForm(this.connect, this.template.getTemplateId().getValue(), "0.1", "Form automatically generated from openEHR Operational Template", 5, 1, uuid);
		
		/*form fields for the standard patient fields are created (13 db entries)
		 * otherwise the created form would need to be opened first in the "Manage Forms" section of OpenMRS, which causes an automatic generation of these db entries
		 * field ids: 2,3,5,6,8,9,10,11,12,13,17,18,19
		 * the fields with no 2 and 3 are created first as they are parents of other form fields*/
		rs2_value=dbStatements.createFormField(this.connect, this.formId, 3, 2, null, 1);

		for(int i=17; i<20; i++){ //Felder mit Nummer 17,18,19 haben Feld 3 als Parent
			dbStatements.createFormField(this.connect, this.formId, i, null, rs2_value, 1);
		}
		/*fields no 2*/
		rs2_value=dbStatements.createFormField(this.connect, this.formId, 2, 1, null, 1);
		
		/*fields no 6,8,9,10,11,12,13 (parent: field no 2)*/
		dbStatements.createFormField(this.connect, this.formId, 6, null, rs2_value, 1);						
		for(int i=8; i<14; i++){ 
			dbStatements.createFormField(this.connect, this.formId, i, null, rs2_value, 1);
		}			
		/*field no 5*/
		dbStatements.createFormField(this.connect, this.formId, 5, 3, null, 1);	
			
		createForm();
	}
		
	private void createForm()
	{
		/*layout_xml id is set to form_id*/
		layout.getRootElement().addAttribute(new Attribute("id", String.valueOf(this.formId)));
		//setting ID, name and uuid of the form
		xform.getRootElement().getFirstChildElement("model", "http://www.w3.org/2002/xforms").getFirstChildElement("instance", "http://www.w3.org/2002/xforms").getFirstChildElement("form").addAttribute(new Attribute("id", String.valueOf(formId)));
		xform.getRootElement().getFirstChildElement("model", "http://www.w3.org/2002/xforms").getFirstChildElement("instance", "http://www.w3.org/2002/xforms").getFirstChildElement("form").addAttribute(new Attribute("name", this.template.getTemplateId().getValue()));
		xform.getRootElement().getFirstChildElement("model", "http://www.w3.org/2002/xforms").getFirstChildElement("instance", "http://www.w3.org/2002/xforms").getFirstChildElement("form").addAttribute(new Attribute("uuid", String.valueOf(uuid)));
				
		//Attribute zur Form-Page für das Template hinzufügen
		this.page.addAttribute(new Attribute("Text", getTermDefinition(template.getDefinition().getNodeId(), "text")));
		this.page.addAttribute(new Attribute("fontWeight", "normal"));
		this.page.addAttribute(new Attribute("fontSize", "16px"));
		this.page.addAttribute(new Attribute("fontFamily", "Verdana,'Lucida Grande','Trebuchet MS',Arial,Sans-Serif"));
		this.page.addAttribute(new Attribute("Binding", getTermDefinition(template.getDefinition().getNodeId(), "text")));
		this.page.addAttribute(new Attribute("Width", "4000px"));
		this.page.addAttribute(new Attribute("backgroundColor", ""));
		layout.getRootElement().appendChild(this.page);
		
		iterateTemplateNodes(this.template.getDefinition(), null, null, null, null, null, null);
				
		this.page.addAttribute(new Attribute("Height", String.valueOf(lastLayoutPositionTop+100)+"px"));
		
		/*submit and cancel button are added to the layout xml on the last created page
		 * position of the last element on this page is retrieved*/
		Elements pageElements=layout.getRootElement().getChildElements();
		Element lastPage=pageElements.get(pageElements.size()-1);
		int lastElementPosition=0;
		if(lastPage.getChildElements().size()!=0) {
			Element lastElement=lastPage.getChildElements().get(lastPage.getChildElements().size()-1);
			String lastElementTop=lastElement.getAttributeValue("Top");
			lastElementPosition=0;
			if(lastElement.getAttributeValue("WidgetType").equalsIgnoreCase("GroupBox")) {
				/*if a GroupBox exists at the end of the page, it is the last element and its TopPosition is retrieved
				 * --> therefore its height needs to be added*/
				String groupHeight=lastElement.getAttributeValue("Height");
				lastElementPosition=Integer.parseInt(lastElementTop.substring(0, lastElementTop.length()-2))+Integer.parseInt(groupHeight.substring(0, groupHeight.length()-2));
			} else {	
				lastElementPosition=Integer.parseInt(lastElementTop.substring(0, lastElementTop.length()-2));
			}
		}

		Element submit=new Element("Item");
		submit.addAttribute(new Attribute("WidgetType", "Button"));
		submit.addAttribute(new Attribute("Text", "Submit"));
		submit.addAttribute(new Attribute("HelpText", "submit"));
		submit.addAttribute(new Attribute("Binding", "submit"));
		submit.addAttribute(new Attribute("Left", "50px"));
		submit.addAttribute(new Attribute("Top", String.valueOf(lastElementPosition+40)+"px"));
		submit.addAttribute(new Attribute("Width", "70px"));
		submit.addAttribute(new Attribute("Height", "30px"));
		submit.addAttribute(new Attribute("TabIndex", String.valueOf(lastTabIndex+1)));
		submit.addAttribute(new Attribute("fontSize", "16px"));
		submit.addAttribute(new Attribute("fontFamily", "Verdana,'Lucida Grande','Trebuchet MS',Arial,Sans-Serif"));
		
		lastTabIndex=lastTabIndex+1;

		Element cancel=new Element("Item");
		cancel.addAttribute(new Attribute("WidgetType", "Button"));
		cancel.addAttribute(new Attribute("Text", "Cancel"));
		cancel.addAttribute(new Attribute("HelpText", "cancel"));
		cancel.addAttribute(new Attribute("Binding", "cancel"));
		cancel.addAttribute(new Attribute("Left", "220px"));
		cancel.addAttribute(new Attribute("Top", String.valueOf(lastElementPosition+40)+"px"));
		cancel.addAttribute(new Attribute("Width", "70px"));
		cancel.addAttribute(new Attribute("Height", "30px"));
		cancel.addAttribute(new Attribute("TabIndex", String.valueOf(lastTabIndex+1)));
		cancel.addAttribute(new Attribute("fontSize", "16px"));
		cancel.addAttribute(new Attribute("fontFamily", "Verdana,'Lucida Grande','Trebuchet MS',Arial,Sans-Serif"));
		
		lastPage.appendChild(submit);
		lastPage.appendChild(cancel);
		
		this.xformXml=this.xform.toXML();
		this.layoutXml=this.layout.toXML();
		dbStatements.createXForm(connect, this.formId, this.xformXml, this.layoutXml, 1, this.uuid);	
	}
	/*iterating through the elements of the template for the creation of the XForm*/
	private void iterateTemplateNodes(COBJECT cobj, Element iLayoutGroup, Element iOldLayoutGroup, Element iXFormUIRepeat, Element iXFormRepeatInstance, OpenMRSConcept iRepeatC, OpenMRSConcept iGroupC)
	/*
	 * in case of a group iLayoutGroup holds a layout element, where the following nodes should be inserted
	 * in case of an enclosing repeat element: 
	 * -iXFormUIRepeat includes the XForm element wherein the labels etc. are defined
	 * -xFormRepeatInstance includes the XForm element, that defines the hierarchy of the form elements 
	 * at the beginning of the form in the definition part (</xf:instance>)
	 */
	{
		boolean hideOnForm=checkIfHidden(cobj); /*checking if the "Hide on form and html" option has been activated for this node*/
		/*if yes, only the elements in the XForms are created, no elements in the Layout XML*/
		
		Boolean repeatHelp=false;
		Boolean newRepeat=false;
		Element repeatElement=null;
		Element repeatInstance=null;
		int oldPositionLeft;
		String name; /*is used as label if no nodeId is available*/
		if(cobj.getParent()!=null) {
			name=cobj.getParent().getRmAttributeName();
		} else {
			name="default";
		}
		Element repeatLayoutGroup=null;
		OpenMRSConcept repeatConcept=new OpenMRSConcept();
		/*a repeat element is necessary, except in case of a constraintref, archetypeslot or archetypeinternalref node*/
		if(!(cobj instanceof ARCHETYPEINTERNALREF) && !(cobj instanceof CONSTRAINTREF) && !(cobj instanceof ARCHETYPESLOT)) {
			if(cobj.getOccurrences().isUpperUnbounded()==true) {
				if(iRepeatC!=null) {
					System.out.println("Repeat in repeat nicht möglich!");
				} else {
					repeatConcept.setNodeId(cobj.getNodeId());
					createConcept(cobj.getParent().getRmAttributeName(), "N/A", repeatConcept, true, checkForAnnoation(cobj));
					repeatHelp=true;
					newRepeat=true;
					Element[] xFormRepeatElements=createRepeatXFormElement(repeatConcept);
					repeatElement=xFormRepeatElements[0];
					repeatInstance=xFormRepeatElements[1];
					repeatLayoutGroup=createLayoutGroup(repeatConcept, true, iLayoutGroup);
					lastRepeatPositionLeft=-195;
					iOldLayoutGroup=iLayoutGroup;
					iLayoutGroup=repeatLayoutGroup;				
					iRepeatC=repeatConcept;
					iXFormRepeatInstance=repeatInstance;
					iXFormUIRepeat=repeatElement;	
					createMetadataEntry(getFullXPath(cobj), "Repeat", repeatConcept.getFullName(), repeatConcept.getConceptId(), null, repeatHelp);
				}
			} 
		}
		
		Boolean required=false; /*element required or not --> this has only been implemented rudimentary*/
		if(cobj.getOccurrences().getLower()!=null && cobj.getOccurrences().getLower()==1) {
			required=true;
		}
		
		if(iRepeatC!=null) {
			repeatHelp=true;
		} else {
			repeatHelp=false;
		}

		
		if(cobj instanceof CPRIMITIVEOBJECT) {		
			/*here the form fields for C_PRIMITIVE_OBJECTs, for which no specific mapping has been defined, are created */	
			OpenMRSConcept newConcept=new OpenMRSConcept();
			if(cobj.getNodeId()!=null && !cobj.getNodeId().equalsIgnoreCase("") && !cobj.getNodeId().equalsIgnoreCase(" ")) {
				newConcept.setNodeId(cobj.getNodeId());
				repeatConcept.setNodeId(cobj.getNodeId());
			} else {
				newConcept.setNodeId(null);
				repeatConcept.setNodeId(null);
			}
			createConcept(name, ((CPRIMITIVEOBJECT) cobj).getItem().getClass().getSimpleName(), newConcept, false, checkForAnnoation(cobj));
			
			/*in case of a repeat element a ConceptSetEntry needs to be created*/
			if(cobj.getOccurrences().isUpperUnbounded()==true) {
				if(iRepeatC!=null) {
					System.out.println("Repeat in repeat nicht möglich!");
				} else {
					createConceptSetEntry(repeatConcept, newConcept);
				}
			} else {
				if(iRepeatC!=null) {
					createConceptSetEntry(iRepeatC, newConcept);
					repeatHelp=true;
				}
			}
			
			boolean cprimRequired=checkIfRequired(cobj, required);
 			
			/*WidgetType is picked dependent on the class (e.g. DatePicker widget for C_DATE)*/
			String textFieldType=getConceptDatatype(((CPRIMITIVEOBJECT)cobj).getItem().getClass().getSimpleName())[4];	 
			listBoxHelp=false;
			createStandardXFormField((CPRIMITIVEOBJECT)cobj, cprimRequired, newConcept, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);				

			if(listBoxHelp==true) {
				textFieldType="ListBox";
			}
			createLayoutTextField(newConcept, iLayoutGroup, iOldLayoutGroup, textFieldType, repeatHelp, false, hideOnForm);
			
			if(newRepeat==true){			
				createAdditionalRepeatParts(iLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
				if(iOldLayoutGroup==null) {
					this.page.appendChild(iLayoutGroup);
				} else {
					iOldLayoutGroup.appendChild(iLayoutGroup);
				}
			} 	
			listBoxHelp=false;
			
			createMetadataEntry(getFullXPath(cobj), textFieldType, newConcept.getFullName(), newConcept.getConceptId(), null, repeatHelp);
			
		} else if(cobj instanceof CCOMPLEXOBJECT) {
			
			/*in case of a C_AT_ROOT, which begins with a COMPOSITION node, a new page is created for the content attribute
			 * the remaining attributes are added to the first form page
			 * otherwise a label and a metadata entry are created*/
			if(cobj instanceof CARCHETYPEROOT)
			{
				if(!newRepeat && iRepeatC!=null) {
					System.out.println("Warning: CARCHETYPEROOT in Repeat!");
				}
				Element oldPage=this.page; 
				int oldLayoutPositionTop=0;
		
				/*a label is created if its the root node of a slot archetype and not the root archetype*/
				if(newRepeat==false && !cobj.getRmTypeName().equalsIgnoreCase("COMPOSITION") && cobj.getParent()!=null) { 
					if(cobj.getNodeId()!=null && !cobj.getNodeId().equalsIgnoreCase("") && !cobj.getNodeId().equalsIgnoreCase(" ")) {
						createLayoutLabel(getTermDefinition(cobj.getNodeId(), "text"), iLayoutGroup, iOldLayoutGroup, iXFormUIRepeat, false, hideOnForm);
					} else {
						createLayoutLabel(name, iLayoutGroup, iOldLayoutGroup, iXFormUIRepeat, false, hideOnForm);
					}
				}
				
				if(!newRepeat) {
					if(cobj.getNodeId()!=null && !cobj.getNodeId().equalsIgnoreCase("") && !cobj.getNodeId().equalsIgnoreCase(" ")) {			
						createMetadataEntry(getFullXPath(cobj), "Page", getTermDefinition(cobj.getNodeId(), "text"), 0, null, repeatHelp);
					} else {
						createMetadataEntry(getFullXPath(cobj), "Page", ((CARCHETYPEROOT)cobj).getArchetypeId().getValue(), 0, null, repeatHelp);
					}
				}
				
				for(CATTRIBUTE attr:((CCOMPLEXOBJECT)cobj).getAttributes())
				{
					if(attr.getRmAttributeName().equalsIgnoreCase("name")||attr.getRmAttributeName().equalsIgnoreCase("archetype_node_id"))
					{
						/*for the attributes name and archetype_node_id only a metadata entry, including the values of those two attributes, is created*/
						createMetadataEntryForATNodeName(attr, repeatHelp);
						continue;
					}
					if(cobj.getRmTypeName().equalsIgnoreCase("COMPOSITION") && attr.getRmAttributeName().equalsIgnoreCase("content")) {
						this.page.addAttribute(new Attribute("Text", getTermDefinition(cobj.getNodeId(), "text")+" Descriptive Data"));
						Element newPage=new Element("Page");		
						newPage.addAttribute(new Attribute("Text", getTermDefinition(cobj.getNodeId(), "text")+" Content"));
						newPage.addAttribute(new Attribute("fontWeight", "normal"));
						newPage.addAttribute(new Attribute("fontSize", "16px"));
						newPage.addAttribute(new Attribute("fontFamily", "Verdana,'Lucida Grande','Trebuchet MS',Arial,Sans-Serif"));
						newPage.addAttribute(new Attribute("Binding", getTermDefinition(template.getDefinition().getNodeId(), "text")));
						newPage.addAttribute(new Attribute("Width", "4000px"));
						newPage.addAttribute(new Attribute("backgroundColor", ""));
						this.page=newPage;
						oldLayoutPositionTop=lastLayoutPositionTop;
						lastLayoutPositionTop=-20;
						layout.getRootElement().appendChild(this.page);
					} 
					oldPositionLeft=lastPositionLeft;
					lastPositionLeft=lastPositionLeft+10;
					for(COBJECT child: attr.getChildren())
					{
						iterateTemplateNodes(child, iLayoutGroup, iOldLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC, iGroupC);
					}	
					if(cobj.getRmTypeName().equalsIgnoreCase("COMPOSITION") && attr.getRmAttributeName().equalsIgnoreCase("content")) {					
						this.page.addAttribute(new Attribute("Height", String.valueOf(lastLayoutPositionTop+100)+"px"));
						lastLayoutPositionTop=oldLayoutPositionTop;
						this.page=oldPage;
					}	
					lastPositionLeft=oldPositionLeft;
				}
				
				if(newRepeat==true){			
					createAdditionalRepeatParts(iLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
					if(iOldLayoutGroup==null) {
						this.page.appendChild(iLayoutGroup);
					} else {
						iOldLayoutGroup.appendChild(iLayoutGroup);
					}
				} 	
				
			} else if(cobj.getRmTypeName().equalsIgnoreCase("SECTION")) { /*new page is created for SECTION nodes*/
				Element newPage=new Element("Page");
				Element oldPage=this.page;		
				newPage.addAttribute(new Attribute("Text", getTermDefinition(cobj.getNodeId(), "text")));
				newPage.addAttribute(new Attribute("fontWeight", "normal"));
				newPage.addAttribute(new Attribute("fontSize", "16px"));
				newPage.addAttribute(new Attribute("fontFamily", "Verdana,'Lucida Grande','Trebuchet MS',Arial,Sans-Serif"));
				newPage.addAttribute(new Attribute("Binding", getTermDefinition(template.getDefinition().getNodeId(), "text")));
				newPage.addAttribute(new Attribute("Width", "4000px"));
				newPage.addAttribute(new Attribute("backgroundColor", ""));
				this.page=newPage;

				if(newRepeat) { /*position of the repeat element needs to be adapted as its going to appear on the newly created page*/
					iLayoutGroup.addAttribute(new Attribute("Top", String.valueOf(10)+"px"));
				}
											
				int oldLayoutPositionTop=lastLayoutPositionTop;
				this.lastLayoutPositionTop=-20;
				oldPositionLeft=this.lastPositionLeft;
				this.lastPositionLeft=30; /*lastPositionLeft is set back because of the new page*/
				
				if(!newRepeat) { /*in case of a repeat element the necessary metadata entry has been created at the beginning of the method*/
					if(cobj.getNodeId()!=null && !cobj.getNodeId().equalsIgnoreCase("") && !cobj.getNodeId().equalsIgnoreCase(" ")) {
						createMetadataEntry(getFullXPath(cobj), "Page", getTermDefinition(cobj.getNodeId(), "text"), 0, null, repeatHelp);
						//0 as concept_id because no concept is created
					} else {
						createMetadataEntry(getFullXPath(cobj), "Page", name, 0, null, repeatHelp);
					}
				}
				
				
				for(CATTRIBUTE attr:((CCOMPLEXOBJECT)cobj).getAttributes())
				{
					/*for the attributes name and archetype_node_id only a metadata entry, including the values of those two attributes, is created*/
					if(attr.getRmAttributeName().equalsIgnoreCase("archetype_node_id") || attr.getRmAttributeName().equalsIgnoreCase("name")) {
						createMetadataEntryForATNodeName(attr, repeatHelp);
						continue;
					}
					for(COBJECT child: attr.getChildren())
					{				
						if(newRepeat) {
							iterateTemplateNodes(child, iLayoutGroup, null, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC, null);
						} else {
							iterateTemplateNodes(child, null, null, null, null, null, null);
						}			
					}
				}
								
				if(newRepeat) {			
					createAdditionalRepeatParts(iLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
					this.page.appendChild(iLayoutGroup);
				}
				
				this.page.addAttribute(new Attribute("Height", String.valueOf(lastLayoutPositionTop+100)+"px"));
				layout.getRootElement().appendChild(this.page);
				this.page=oldPage;
				this.lastLayoutPositionTop=oldLayoutPositionTop;
				this.lastPositionLeft=oldPositionLeft;			
								
			} else if(cobj.getRmTypeName().equalsIgnoreCase("CLUSTER")) {
				/*if the C_OBJECT node is a repeating node, no GroupBox needs to be created*/
				Element layoutGroup=null;				
				OpenMRSConcept groupConcept=new OpenMRSConcept();
				groupConcept.setNodeId(cobj.getNodeId());
				int oldGroupPosition=0;
			
				if(!newRepeat && !hideOnForm){				
					if(iRepeatC!=null) {
						/*a GroupBox can't be created inside a repeat element --> error in case of a CLUSTER node inside a repeating node*/
						System.out.println("A GroupBox (CLUSTER node) can't be created inside a repeat element! No GroupBox will be created!");
					} else {
						if(iLayoutGroup!=null) {
							oldGroupPosition=lastGroupPositionTop+40;
						}
						createConcept(name, "Text", groupConcept, true, checkForAnnoation(cobj));
						layoutGroup=createLayoutGroup(groupConcept, false, iLayoutGroup);
						lastGroupPositionTop=-10; /*inside a GroupBox the TopPositions are starting from scratch*/
					}
				}
			
				if(!newRepeat) { /*in case of a repeat element the necessary metadata entry has already been created at the beginning of the method*/
					if(cobj.getNodeId()!=null && !cobj.getNodeId().equalsIgnoreCase("") && !cobj.getNodeId().equalsIgnoreCase(" ")) {
						createMetadataEntry(getFullXPath(cobj), "GroupBox", getTermDefinition(cobj.getNodeId(), "text"), groupConcept.getConceptId(), null, repeatHelp);
					} else {
						createMetadataEntry(getFullXPath(cobj), "GroupBox", name, groupConcept.getConceptId(), null, repeatHelp);
					}
				}
											
				oldPositionLeft=lastPositionLeft;
				lastPositionLeft=lastPositionLeft+10;
				for(CATTRIBUTE attr:((CCOMPLEXOBJECT)cobj).getAttributes())
				{					
					/*for the attributes name and archetype_node_id only a metadata entry, including the values of those two attributes, is created*/
					if(attr.getRmAttributeName().equalsIgnoreCase("archetype_node_id") || attr.getRmAttributeName().equalsIgnoreCase("name")) {
						createMetadataEntryForATNodeName(attr, repeatHelp);
						continue;
					}				
					for(COBJECT child: attr.getChildren())
					{					
						if(newRepeat || hideOnForm ||iRepeatC!=null) {
							iterateTemplateNodes(child, iLayoutGroup, iOldLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC, null);
						} else {
							iterateTemplateNodes(child, layoutGroup, iLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC, groupConcept);
						}						
					}
					if(!newRepeat) {
						lastGroupPositionTop=oldGroupPosition+lastGroupPositionTop;	
					}
				}	
				lastPositionLeft=oldPositionLeft;
				if(newRepeat) {			
					createAdditionalRepeatParts(iLayoutGroup, iXFormUIRepeat,iXFormRepeatInstance,iRepeatC);
				} else if(!newRepeat && !hideOnForm && iRepeatC==null){
					/*the height is defined after the subelements have been added to the GroupBox */
					layoutGroup.addAttribute(new Attribute("Height", String.valueOf(lastGroupPositionTop-oldGroupPosition+32)+"px"));
				}	
				if(newRepeat) {
					if(iOldLayoutGroup==null) {
						this.page.appendChild(iLayoutGroup);
					} else {
						iOldLayoutGroup.appendChild(iLayoutGroup);
					}

				} else if(!hideOnForm && layoutGroup!=null) {
					if(iLayoutGroup==null) {
						this.page.appendChild(layoutGroup);
					} else {
						iLayoutGroup.appendChild(layoutGroup);
					}
				}

								
			} else if(cobj.getRmTypeName().equalsIgnoreCase("ELEMENT")) {
				OpenMRSConcept elementConcept=new OpenMRSConcept();
				elementConcept.setNodeId(cobj.getNodeId());
				
				if(!newRepeat) {
					if(cobj.getNodeId()!=null && !cobj.getNodeId().equalsIgnoreCase("") && !cobj.getNodeId().equalsIgnoreCase(" ")) {			
						createMetadataEntry(getFullXPath(cobj), null, getTermDefinition(cobj.getNodeId(), "text"), 0, null, repeatHelp);
					} else {
						createMetadataEntry(getFullXPath(cobj), null, name, 0, null, repeatHelp);
					}
				}
				
				for(CATTRIBUTE elementAttr:((CCOMPLEXOBJECT)cobj).getAttributes())
				{
					if(elementAttr.getRmAttributeName().equalsIgnoreCase("name")||elementAttr.getRmAttributeName().equalsIgnoreCase("archetype_node_id"))
					{
						/*for the attributes name and archetype_node_id only a metadata entry, including the values of those two attributes, is created*/
						createMetadataEntryForATNodeName(elementAttr, repeatHelp);
						continue;
					}					
					for(COBJECT elementChild: elementAttr.getChildren())
					{
						
						if(elementChild.getParent().getRmAttributeName().equalsIgnoreCase("value")) {
							if(elementChild.getRmTypeName().equalsIgnoreCase("DV_CODED_TEXT") || elementChild.getRmTypeName().equalsIgnoreCase("DvCodedText")) {
								
								if(elementChild.getNodeId()!=null && !elementChild.getNodeId().equalsIgnoreCase("") && !elementChild.getNodeId().equalsIgnoreCase(" ")) {
									createMetadataEntry(getFullXPath(elementChild), null, getTermDefinition(elementChild.getNodeId(), "text"), 0, null, repeatHelp);
								} else {
									createMetadataEntry(getFullXPath(elementChild), null, elementChild.getParent().getRmAttributeName(), 0, null, repeatHelp);
								}
								repeatHelp=mapDvCodedText(elementChild, required, elementConcept, iRepeatC, iGroupC, hideOnForm, iXFormUIRepeat, iXFormRepeatInstance, iLayoutGroup, iOldLayoutGroup, repeatHelp, checkForAnnoation(cobj));
								
							} else if(elementChild.getRmTypeName().equalsIgnoreCase("DV_TEXT") || elementChild.getRmTypeName().equalsIgnoreCase("DvText")
									||elementChild.getRmTypeName().equalsIgnoreCase("DV_DATE_TIME") ||elementChild.getRmTypeName().equalsIgnoreCase("DvDateTime")
									||elementChild.getRmTypeName().equalsIgnoreCase("DV_DATE") || elementChild.getRmTypeName().equalsIgnoreCase("DvDate")
									||elementChild.getRmTypeName().equalsIgnoreCase("DV_TIME") || elementChild.getRmTypeName().equalsIgnoreCase("DvTime")
									||elementChild.getRmTypeName().equalsIgnoreCase("DV_EHR_URI") || elementChild.getRmTypeName().equalsIgnoreCase("DvEhrUri")
									||elementChild.getRmTypeName().equalsIgnoreCase("DV_URI") || elementChild.getRmTypeName().equalsIgnoreCase("DvUri")
									||elementChild.getRmTypeName().equalsIgnoreCase("DV_PERIODIC_TIME_SPECIFICATION") || elementChild.getRmTypeName().equalsIgnoreCase("DvPeriodicTimeSpecification")
									||elementChild.getRmTypeName().equalsIgnoreCase("DV_GENERAL_TIME_SPECIFICATION") || elementChild.getRmTypeName().equalsIgnoreCase("DvGeneralTimeSpecification")){

								if(elementChild.getNodeId()!=null && !elementChild.getNodeId().equalsIgnoreCase("") && !elementChild.getNodeId().equalsIgnoreCase(" ")) {
									createMetadataEntry(getFullXPath(elementChild), null, getTermDefinition(elementChild.getNodeId(), "text"), 0, null, repeatHelp);
								} else {
									createMetadataEntry(getFullXPath(elementChild), null, elementChild.getParent().getRmAttributeName(), 0, null, repeatHelp);
								}
								repeatHelp=mapDvText(elementChild, elementConcept, iRepeatC, iGroupC, iXFormUIRepeat, iXFormRepeatInstance, iLayoutGroup, iOldLayoutGroup, required, repeatHelp, hideOnForm, checkForAnnoation(cobj));
								
							} else if(elementChild.getRmTypeName().equalsIgnoreCase("DV_QUANTITY") || elementChild.getRmTypeName().equalsIgnoreCase("DvQuantity")) {
								if(elementChild.getNodeId()!=null && !elementChild.getNodeId().equalsIgnoreCase("") && !elementChild.getNodeId().equalsIgnoreCase(" ")) {
									createMetadataEntry(getFullXPath(elementChild), null, getTermDefinition(elementChild.getNodeId(), "text"), 0, null, repeatHelp);
								} else {
									createMetadataEntry(getFullXPath(elementChild), null, elementChild.getParent().getRmAttributeName(), 0, null, repeatHelp);
								}
								repeatHelp=mapDvQuantity(elementChild, elementConcept, iRepeatC, iGroupC, iXFormUIRepeat, iXFormRepeatInstance, iLayoutGroup, iOldLayoutGroup, required, repeatHelp, hideOnForm, checkForAnnoation(cobj));
								
							} else if(elementChild.getRmTypeName().equalsIgnoreCase("DV_BOOLEAN")||elementChild.getRmTypeName().equalsIgnoreCase("DvBoolean")) {
								if(elementChild.getNodeId()!=null && !elementChild.getNodeId().equalsIgnoreCase("") && !elementChild.getNodeId().equalsIgnoreCase(" ")) {
									createMetadataEntry(getFullXPath(elementChild), null, getTermDefinition(elementChild.getNodeId(), "text"), 0, null, repeatHelp);
								} else {
									createMetadataEntry(getFullXPath(elementChild), null, elementChild.getParent().getRmAttributeName(), 0, null, repeatHelp);
								}
								repeatHelp=mapDvBoolean(elementChild, elementConcept, iRepeatC, iXFormUIRepeat, iXFormRepeatInstance, iLayoutGroup, iOldLayoutGroup, required, repeatHelp, hideOnForm, checkForAnnoation(cobj));				
							
							} else {
								/*all other subtypes of DATE_VALUE are mapped in the generic way*/
								createLayoutLabel(getTermDefinition(elementConcept.getNodeId(), "text"), iLayoutGroup, iOldLayoutGroup, iXFormUIRepeat, true, hideOnForm);
								oldPositionLeft=lastPositionLeft;
								lastPositionLeft=lastPositionLeft+10;

								iterateTemplateNodes(elementChild, iLayoutGroup, iOldLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC, iGroupC);
								
								lastPositionLeft=oldPositionLeft;
							}							
						
						} 
						/*if the ELEMENT has other attributes except "value"*/
						else { 
							oldPositionLeft=lastPositionLeft;
							lastPositionLeft=lastPositionLeft+10;
							iterateTemplateNodes(elementChild, iLayoutGroup, iOldLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC, iGroupC);
							lastPositionLeft=oldPositionLeft;
						}
						

					}		
				}	
				
				if(newRepeat==true){			
					createAdditionalRepeatParts(iLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
					if(iOldLayoutGroup==null) {
						this.page.appendChild(iLayoutGroup);
					} else {
						iOldLayoutGroup.appendChild(iLayoutGroup);
					}
				} 	
				
			} else {	/*C_COMPLEX_OBJECT, but no ELEMENT, CLUSTER or SECTION*/			
				OpenMRSConcept concept=new OpenMRSConcept();
				concept.setNodeId(cobj.getNodeId());
				boolean newRequired=checkIfRequired(cobj, required);
				if(!newRepeat) {
					if(cobj.getNodeId()!=null && !cobj.getNodeId().equalsIgnoreCase("") && !cobj.getNodeId().equalsIgnoreCase(" ")) {			
						createMetadataEntry(getFullXPath(cobj), null, getTermDefinition(cobj.getNodeId(), "text"), 0, null, repeatHelp);
					} else {
						createMetadataEntry(getFullXPath(cobj), null, name, 0, null, repeatHelp);
					}
				}

				if(cobj.getRmTypeName().equalsIgnoreCase("DV_CODED_TEXT") || cobj.getRmTypeName().equalsIgnoreCase("DvCodedText")) {
					
					repeatHelp=mapDvCodedText(cobj, newRequired, concept, iRepeatC, iGroupC, hideOnForm, iXFormUIRepeat, iXFormRepeatInstance, iLayoutGroup, iOldLayoutGroup, repeatHelp, checkForAnnoation(cobj));
					
				} else if(cobj.getRmTypeName().equalsIgnoreCase("DV_TEXT") || cobj.getRmTypeName().equalsIgnoreCase("DvText")
						||cobj.getRmTypeName().equalsIgnoreCase("DV_DATE_TIME") ||cobj.getRmTypeName().equalsIgnoreCase("DvDateTime")
						||cobj.getRmTypeName().equalsIgnoreCase("DV_DATE") ||cobj.getRmTypeName().equalsIgnoreCase("DvDate")
						||cobj.getRmTypeName().equalsIgnoreCase("DV_TIME") || cobj.getRmTypeName().equalsIgnoreCase("DvTime")
						||cobj.getRmTypeName().equalsIgnoreCase("DV_EHR_URI") || cobj.getRmTypeName().equalsIgnoreCase("DvEhrUri")
						||cobj.getRmTypeName().equalsIgnoreCase("DV_URI") || cobj.getRmTypeName().equalsIgnoreCase("DvUri")
						||cobj.getRmTypeName().equalsIgnoreCase("DV_PERIODIC_TIME_SPECIFICATION") || cobj.getRmTypeName().equalsIgnoreCase("DvPeriodicTimeSpecification")
						||cobj.getRmTypeName().equalsIgnoreCase("DV_GENERAL_TIME_SPECIFICATION") ||cobj.getRmTypeName().equalsIgnoreCase("DvGeneralTimeSpecification")){

					repeatHelp=mapDvText(cobj, concept, iRepeatC, iGroupC, iXFormUIRepeat, iXFormRepeatInstance, iLayoutGroup, iOldLayoutGroup, newRequired, repeatHelp, hideOnForm, checkForAnnoation(cobj));
					
				} else if(cobj.getRmTypeName().equalsIgnoreCase("DV_QUANTITY") || cobj.getRmTypeName().equalsIgnoreCase("DvQuantity")) {
					repeatHelp=mapDvQuantity(cobj, concept, iRepeatC, iGroupC, iXFormUIRepeat, iXFormRepeatInstance, iLayoutGroup, iOldLayoutGroup, newRequired, repeatHelp, hideOnForm, checkForAnnoation(cobj));
					
				} else if(cobj.getRmTypeName().equalsIgnoreCase("DV_BOOLEAN")||cobj.getRmTypeName().equalsIgnoreCase("DvBoolean")) {
					repeatHelp=mapDvBoolean(cobj, concept, iRepeatC, iXFormUIRepeat, iXFormRepeatInstance, iLayoutGroup, iOldLayoutGroup, newRequired, repeatHelp, hideOnForm, checkForAnnoation(cobj));				
				
				} else {				
					/*a label shall only be created if its no repeat element, otherwise the label would occur twice*/
					if(newRepeat==false) {
						if(cobj.getNodeId()!=null && !cobj.getNodeId().equalsIgnoreCase("") && !cobj.getNodeId().equalsIgnoreCase(" ")) {
							createLayoutLabel(getTermDefinition(cobj.getNodeId(), "text"), iLayoutGroup, iOldLayoutGroup, iXFormUIRepeat, false, hideOnForm);
						} else {
							createLayoutLabel(name, iLayoutGroup, iOldLayoutGroup, iXFormUIRepeat, false, hideOnForm);
						}
					}
					oldPositionLeft=lastPositionLeft;
					lastPositionLeft=lastPositionLeft+10;
					for(CATTRIBUTE cattr:((CCOMPLEXOBJECT)cobj).getAttributes())
					{
						if(cattr.getRmAttributeName().equalsIgnoreCase("name")||cattr.getRmAttributeName().equalsIgnoreCase("archetype_node_id"))
						{
							/*those two classes include a name-attribute, for which a normal form field should be created*/
							if(!cobj.getRmTypeName().equalsIgnoreCase("PARTY_IDENTIFIED") && !cobj.getRmTypeName().equalsIgnoreCase("TERMINOLOGY_ID")) {
								createMetadataEntryForATNodeName(cattr, repeatHelp);
								continue;
							}
						}
						for(COBJECT cchild: cattr.getChildren())
						{
							iterateTemplateNodes(cchild, iLayoutGroup, iOldLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC, iGroupC);
						}			
					}
					lastPositionLeft=oldPositionLeft;
					
					if(newRepeat==true){			
						createAdditionalRepeatParts(iLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
						if(iOldLayoutGroup==null) {
							this.page.appendChild(iLayoutGroup);
						} else {
							iOldLayoutGroup.appendChild(iLayoutGroup);
						}
					} 	
				}

			}
		} else if(cobj instanceof CDVSTATE)	{	//TODO: not tested yet!						
			boolean newRequired=checkIfRequired(cobj, required);			
			OpenMRSConcept stateConcept=new OpenMRSConcept();
			if(cobj.getNodeId()!=null || !cobj.getNodeId().equalsIgnoreCase("")||!cobj.getNodeId().equalsIgnoreCase(" ")) {
				stateConcept.setNodeId(cobj.getNodeId());
			};	
			createConcept(name, "Coded", stateConcept, false, checkForAnnoation(cobj));	
			
			if(iRepeatC!=null) {
				createConceptSetEntry(iRepeatC, stateConcept);
				repeatHelp=true;
			}			
			
			List<String[]> options=new ArrayList<String[]>();
			for(int i=0; i<((CDVSTATE)cobj).getValue().getStates().size(); i++) {
				options.add(new String[]{((CDVSTATE)cobj).getValue().getStates().get(i).getName(), ((CDVSTATE)cobj).getValue().getStates().get(i).getName()});
			}

			createListBoxXFormField(stateConcept, newRequired, options, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC, false);	
			createLayoutTextField(stateConcept, iLayoutGroup, iOldLayoutGroup, "ListBox", repeatHelp, false, hideOnForm);

			if(newRepeat==true){			
				createAdditionalRepeatParts(iLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
				if(iOldLayoutGroup==null) {
					this.page.appendChild(iLayoutGroup);
				} else {
					iOldLayoutGroup.appendChild(iLayoutGroup);
				}
			} 
			
			if(!newRepeat) { 
				createMetadataEntry(getFullXPath(cobj), null, null, 0, null, repeatHelp);
			}
			createMetadataEntry(getFullXPath(cobj)+"/value", null, null, 0, null, repeatHelp);
			createMetadataEntry(getFullXPath(cobj)+"/value/value", "ListBox", stateConcept.getFullName(), stateConcept.getConceptId(), null, repeatHelp);
			createMetadataEntry(getFullXPath(cobj)+"/value/defining_code", null, null, 0, null, repeatHelp);
			createMetadataEntry(getFullXPath(cobj)+"/value/defining_code/code_string", "ListBox", stateConcept.getFullName(), stateConcept.getConceptId(), null, repeatHelp);
			createMetadataEntry(getFullXPath(cobj)+"/is_terminal", null, null, 0, null, repeatHelp);


			
		} else if(cobj instanceof CDVORDINAL) {			
			boolean newRequired=checkIfRequired(cobj, required);			
			OpenMRSConcept ordinalConcept=new OpenMRSConcept();
			if(cobj.getNodeId()!=null || !cobj.getNodeId().equalsIgnoreCase("")||!cobj.getNodeId().equalsIgnoreCase(" ")) {
				ordinalConcept.setNodeId(cobj.getNodeId());
			} else { /*if no nodeId is available, the one of the parent node is used*/
				ordinalConcept.setNodeId(cobj.getParent().getParent().getNodeId());
			}
			createConcept(name, "Coded", ordinalConcept, false, checkForAnnoation(cobj));
			
			if(iRepeatC!=null) {
				createConceptSetEntry(iRepeatC, ordinalConcept);
				repeatHelp=true;
			}
			
			String terminologyId=null;
			List<String[]> options=new ArrayList<String[]>();
			Boolean openEHRCode=false; /*needed for the generation of ListBoxes, 
			* in case of a code from the openEHR terminology existing concepts should be reused instead of generating new ones (not implemented yet!)*/ 
			for(int i=0; i<((CDVORDINAL)cobj).getList().size(); i++) {
				if(((CDVORDINAL)cobj).getList().get(i).getSymbol()!=null && ((CDVORDINAL)cobj).getList().get(i).getSymbol().getDefiningCode()!=null
						&& ((CDVORDINAL)cobj).getList().get(i).getSymbol().getDefiningCode().getTerminologyId()!=null) {
					terminologyId=((CDVORDINAL)cobj).getList().get(i).getSymbol().getDefiningCode().getTerminologyId().getValue();
					if(terminologyId.equalsIgnoreCase("local"))
					{
						options.add(new String[]{String.valueOf(((CDVORDINAL)cobj).getList().get(i).getValue()), getTermDefinition(((CDVORDINAL)cobj).getList().get(i).getSymbol().getDefiningCode().getCodeString(), "text")});
					} else if(terminologyId.equalsIgnoreCase("openehr")) { 
						/*CDVOrdinal includes DV_ORDINAL elements where value and symbol are defined, the according description needs to be retrieved from the openEHR terminology if necessary*/
						for(String[] element:getOpenEHRRubricForCode(((CDVORDINAL)cobj).getList().get(i).getSymbol().getDefiningCode().getTerminologyId().getValue(), Arrays.asList(((CDVORDINAL)cobj).getList().get(i).getSymbol().getDefiningCode().getCodeString())))
						{ 
							options.add(new String[]{String.valueOf(((CDVORDINAL)cobj).getList().get(i).getValue()), element[1]});
							openEHRCode=true;
						}
					}
				} else { /*probably a code set id*/
					//TODO
				}	
			}

			createListBoxXFormField(ordinalConcept, newRequired, options, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC, openEHRCode);
			createLayoutTextField(ordinalConcept, iLayoutGroup, iOldLayoutGroup, "ListBox", repeatHelp, false, hideOnForm);

			if(newRepeat==true){			
				createAdditionalRepeatParts(iLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
				if(iOldLayoutGroup==null) {
					this.page.appendChild(iLayoutGroup);
				} else {
					iOldLayoutGroup.appendChild(iLayoutGroup);
				}
			} 	
			
			/*no default value can be defined for the terminology_id attribute, as codes from different terminologies could be specified*/

			if(!newRepeat) {
				createMetadataEntry(getFullXPath(cobj), null, null, 0, null, repeatHelp);
			}
			
			createMetadataEntry(getFullXPath(cobj)+"/value", "", null, 0, null, repeatHelp);
			createMetadataEntry(getFullXPath(cobj)+"/symbol", "", null, 0, null, repeatHelp);
			createMetadataEntry(getFullXPath(cobj)+"/symbol/value", "ListBox", ordinalConcept.getFullName(), ordinalConcept.getConceptId(), null, repeatHelp);
			createMetadataEntry(getFullXPath(cobj)+"/symbol/defining_code", "", null, 0, null, repeatHelp);
			createMetadataEntry(getFullXPath(cobj)+"/symbol/defining_code/terminology_id", "", null, 0, null, repeatHelp);
			createMetadataEntry(getFullXPath(cobj)+"/symbol/defining_code/terminology_id/value", "", null, 0, null, repeatHelp);
			createMetadataEntry(getFullXPath(cobj)+"/symbol/defining_code/code_string", "", null, 0, null, repeatHelp);

			
		} else if(cobj instanceof CCODEPHRASE) {
			
			boolean newRequired=checkIfRequired(cobj, required);		
			OpenMRSConcept codePhraseConcept=new OpenMRSConcept();
			if(cobj.getNodeId()!=null && !cobj.getNodeId().equalsIgnoreCase("")&&!cobj.getNodeId().equalsIgnoreCase(" ")) {
				codePhraseConcept.setNodeId(cobj.getNodeId());
			}	
			createConcept(name, "Coded", codePhraseConcept, false, checkForAnnoation(cobj));	
			
			if(iRepeatC!=null) {
				createConceptSetEntry(iRepeatC, codePhraseConcept);
				repeatHelp=true;
			}
			
			//TODO: if no code list is defined, all codes from the terminology should be used
			List<String[]> options=new ArrayList<String[]>();  /*includes the IDs and labels which should be included in the ListBox*/
			//nur im Falle von locale sind die codes bei den term def zu finden
			Boolean openEHRCode=false; /*needed for the generation of ListBoxes, 
			* in case of a code from the openEHR terminology existing concepts should be reused instead of generating new ones (not implemented yet!)*/ 
			if(((CCODEPHRASE) cobj).getTerminologyId().getValue().equalsIgnoreCase("locale")
					|| ((CCODEPHRASE) cobj).getTerminologyId().getValue().equalsIgnoreCase("local")) {
				for(int i=0; i<((CCODEPHRASE)cobj).getCodeList().size(); i++) {
					options.add(new String[] {((CCODEPHRASE)cobj).getCodeList().get(i), getTermDefinition(((CCODEPHRASE)cobj).getCodeList().get(i), "text")});
				}
			} else if (((CCODEPHRASE) cobj).getTerminologyId().getValue().equalsIgnoreCase("openEHR")){
				options=getOpenEHRRubricForCode(((CCODEPHRASE) cobj).getTerminologyId().getValue(), ((CCODEPHRASE) cobj).getCodeList());				
				openEHRCode=true;
			} else { 
				
				if(((CCODEPHRASE) cobj).getCodeList().isEmpty()) { /*then its probably a CodeSet*/
					options=getOpenEHRCodeSet(((CCODEPHRASE) cobj).getTerminologyId().getValue());
				} else { /*some other terminology --> codes of the code list will be shown in the ListBox*/
					for(int i=0; i<((CCODEPHRASE)cobj).getCodeList().size(); i++) {
						options.add(new String[] {((CCODEPHRASE)cobj).getCodeList().get(i), ((CCODEPHRASE)cobj).getCodeList().get(i)});
					}
				}
				openEHRCode=true;
			}
			
			createListBoxXFormField(codePhraseConcept, newRequired, options, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC, openEHRCode);			
			createLayoutTextField(codePhraseConcept, iLayoutGroup, iOldLayoutGroup, "ListBox", repeatHelp, false, hideOnForm);
	
			if(newRepeat==true){			
				createAdditionalRepeatParts(iLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
				if(iOldLayoutGroup==null) {
					this.page.appendChild(iLayoutGroup);
				} else {
					iOldLayoutGroup.appendChild(iLayoutGroup);
				}
			} 	
			if(!newRepeat) {				
				createMetadataEntry(getFullXPath(cobj), "", null, 0, null, repeatHelp);
			}
			createMetadataEntry(getFullXPath(cobj)+"/terminology_id", "", null, 0, null, repeatHelp);	
			createMetadataEntry(getFullXPath(cobj)+"/terminology_id/value", "", null, 0, ((CCODEPHRASE) cobj).getTerminologyId().getValue(), repeatHelp);
			createMetadataEntry(getFullXPath(cobj)+"/code_string", "ListBox-Code", codePhraseConcept.getFullName(), codePhraseConcept.getConceptId(), null, repeatHelp);
			
					
 			
		} else if(cobj instanceof CDVQUANTITY) {
			/*the value range for the magnitude attribute depends on the chosen unit --> couldn't be implemented in OpenMRS XForms*/
			/*the property attribute describes the unit (e.g. TerminologyID=130=Work for unit=J/min) and is not included on the form*/
			
			List<String[]> units=new ArrayList<String[]>(); /*collecting the units from all CQuantityItems*/
			for(int i=0; i<((CDVQUANTITY)cobj).getList().size(); i++) {
				units.add(new String[] {((CDVQUANTITY)cobj).getList().get(i).getUnits(), ((CDVQUANTITY)cobj).getList().get(i).getUnits()});
			}
			
			boolean newRequired=checkIfRequired(cobj, required);			
			OpenMRSConcept magnitudeConcept=new OpenMRSConcept();
			magnitudeConcept.setNodeId(cobj.getNodeId());
			createConcept(cobj.getParent().getRmAttributeName(), "Numeric", magnitudeConcept, false, checkForAnnoation(cobj));			
			/*the precision attribute is optional*/
			OpenMRSConcept precisionConcept=new OpenMRSConcept();
			createConcept("Precision", "Numeric", precisionConcept, false, "Precision");
			OpenMRSConcept unitConcept=new OpenMRSConcept();
			String unitWidgetType="ListBox";
			if(units.isEmpty()) { /*if no units are defined, a TextBox should be created instead of a ListBox*/
				unitWidgetType="TextBox";
				createConcept("Units", "Text", unitConcept, false, "Units");
			} else {
				createConcept("Units", "Coded", unitConcept, false, "Units");
			}
						
			if(iRepeatC!=null) {
				createConceptSetEntry(iRepeatC, magnitudeConcept);
				createConceptSetEntry(iRepeatC, unitConcept);
				createConceptSetEntry(iRepeatC, precisionConcept);
				repeatHelp=true;
			}

			createLayoutTextField(magnitudeConcept, iLayoutGroup, iOldLayoutGroup, "TextBox", repeatHelp, false, hideOnForm);
			createLayoutTextField(unitConcept, iLayoutGroup, iOldLayoutGroup, unitWidgetType, repeatHelp, true, hideOnForm);
			createLayoutTextField(precisionConcept, iLayoutGroup, iOldLayoutGroup, "TextBox", repeatHelp, true, hideOnForm);
			
			CPRIMITIVEOBJECT cprim=new CPRIMITIVEOBJECT(); 

			listBoxHelp=false;
			createStandardXFormField(cprim, newRequired, magnitudeConcept, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
			if(unitWidgetType.equalsIgnoreCase("ListBox")) {
				createListBoxXFormField(unitConcept, newRequired, units, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC, false);
			} else {
				createStandardXFormField(cprim, newRequired, unitConcept, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
			}
			/*precision is an optional attribute */
			createStandardXFormField(cprim, false, precisionConcept, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
			
			if(newRepeat==true){			
				createAdditionalRepeatParts(iLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
				if(iOldLayoutGroup==null) {
					this.page.appendChild(iLayoutGroup);
				} else {
					iOldLayoutGroup.appendChild(iLayoutGroup);
				}
			} 	
			
			if(!newRepeat) {				
				createMetadataEntry(getFullXPath(cobj), "", null, 0, null, repeatHelp);
			}		
			createMetadataEntry(getFullXPath(cobj)+"/magnitude", "TextBox", magnitudeConcept.getFullName(), magnitudeConcept.getConceptId(), null, repeatHelp);
			createMetadataEntry(getFullXPath(cobj)+"/units", unitWidgetType, "Units", unitConcept.getConceptId(), null, repeatHelp);
			createMetadataEntry(getFullXPath(cobj)+"/precision", "ListBox", "Precision", precisionConcept.getConceptId(), null, repeatHelp);
			

		} else if(cobj instanceof ARCHETYPEINTERNALREF) {
			/*should already have been replaced by the referenced node during the generation of the comprehensive template*/		
			System.out.println("ARCHETYPEINTERNALREF!");
		} else if(cobj instanceof CONSTRAINTREF) {
			System.out.println("CONSTRAINTREF!");
		} else if(cobj instanceof ARCHETYPESLOT) {
			System.out.println("ARCHETYPESLOT!");
		}	

	}
	
	/*creation of add-Button and Group-Box for Repeat element*/
	private void createAdditionalRepeatParts(Element layoutRepeatGroup, Element repeatElement, Element repeatInstance, OpenMRSConcept groupConcept/*, Element groupInstance*/) {
		Element item=new Element("Item");
		item.addAttribute(new Attribute("WidgetType", "Button"));		
		item.addAttribute(new Attribute("Text", "Add new"));
		item.addAttribute(new Attribute("HelpText", "addnew"));
		item.addAttribute(new Attribute("Binding", "addnew"));
		item.addAttribute(new Attribute("Left", "10px"));
		item.addAttribute(new Attribute("Top", "55px"));
		item.addAttribute(new Attribute("Width", "120px"));	
		item.addAttribute(new Attribute("Height", "30px"));
		item.addAttribute(new Attribute("TabIndex", "0"));
		item.addAttribute(new Attribute("fontSize", "16px"));
		item.addAttribute(new Attribute("fontFamily", "Verdana,'Lucida Grande','Trebuchet MS',Arial,Sans-Serif"));				
		layoutRepeatGroup.appendChild(item);		
		layoutRepeatGroup.addAttribute(new Attribute("Height", "100px"));
		
		if(repeatElement!=null) {
			Element repeatGroup=new Element("xf:group", "http://www.w3.org/2002/xforms");
			Element repeatGroupLabel=new Element("xf:label", "http://www.w3.org/2002/xforms");
			repeatGroupLabel.appendChild(groupConcept.getFullName());	
			repeatGroup.appendChild(repeatGroupLabel);
			repeatGroup.appendChild(repeatElement);	
			repeatGroup.addAttribute(new Attribute("id", "obs/"+repeatElement.getAttributeValue("bind")));
			Element group1=xform.getRootElement().getFirstChildElement("group", "http://www.w3.org/2002/xforms");
			group1.appendChild(repeatGroup);
			lastLayoutPositionTop=lastLayoutPositionTop+120;
			lastGroupPositionTop=lastGroupPositionTop+120;
		}
		if(repeatInstance!=null) {
			Element root = xform.getRootElement();
			Element model=root.getFirstChildElement("model", "http://www.w3.org/2002/xforms");
			Element obs=model.getFirstChildElement("instance", "http://www.w3.org/2002/xforms").getFirstChildElement("form").getFirstChildElement("obs");
			obs.appendChild(repeatInstance);		
		}
	}
		
	/*creates a text field, DateTimeWidget or CheckBox depending on the widgetType*/
	private void createLayoutTextField(OpenMRSConcept concept, Element layoutGroup, Element oldLayoutGroup, String widgetType, boolean repeat, boolean noLabel, boolean hideOnForm)
	{	
		if(!hideOnForm) {
			/*if noLabel is true, the fields should be created without a label and placed next to the previous form field*/
			Element labelElement=new Element("Item");
			labelElement.addAttribute(new Attribute("WidgetType", "Label"));		
			labelElement.addAttribute(new Attribute("Text", concept.getFullName()));
			labelElement.addAttribute(new Attribute("HelpText", concept.getFullName()));							
			labelElement.addAttribute(new Attribute("TabIndex", "0"));
			labelElement.addAttribute(new Attribute("fontSize", "16px"));
			labelElement.addAttribute(new Attribute("fontFamily", "Verdana,'Lucida Grande','Trebuchet MS',Arial,Sans-Serif"));
			labelElement.addAttribute(new Attribute("Visible", "true"));
			
			Element textBox=new Element("Item");
			textBox.addAttribute(new Attribute("WidgetType", widgetType));
			textBox.addAttribute(new Attribute("HelpText", concept.getDescription()));	
			textBox.addAttribute(new Attribute("TabIndex", String.valueOf(lastTabIndex+1)));
			textBox.addAttribute(new Attribute("fontSize", "16px"));
			textBox.addAttribute(new Attribute("fontFamily", "Verdana,'Lucida Grande','Trebuchet MS',Arial,Sans-Serif"));
			if(widgetType.equalsIgnoreCase("CheckBox")) { 
				textBox.addAttribute(new Attribute("Text", "true"));
			} else {
				textBox.addAttribute(new Attribute("Width", "200px"));
				textBox.addAttribute(new Attribute("Height", "25px"));
			}
			
			labelElement.addAttribute(new Attribute("Binding", "obs/"+concept.getNodeId()+"_"+concept.getConceptId()));
			textBox.addAttribute(new Attribute("Binding", "obs/"+concept.getNodeId()+"_"+concept.getConceptId()+"/value"));
			
			if(layoutGroup!=null && !repeat) 
			{
				if(!noLabel) {
					labelElement.addAttribute(new Attribute("Left", String.valueOf(lastPositionLeft)+"px"));
					labelElement.addAttribute(new Attribute("Top", String.valueOf(lastGroupPositionTop+40)+"px"));
					textBox.addAttribute(new Attribute("Top", String.valueOf(lastGroupPositionTop+40)+"px"));
					textBox.addAttribute(new Attribute("Left", "300px"));
					lastGroupPositionTop=lastGroupPositionTop+40;
					lastLayoutPositionTop=lastLayoutPositionTop+40;
					layoutGroup.appendChild(labelElement);
					layoutGroup.appendChild(textBox);	
					lastRepeatPositionLeft=300;
				} else {
					textBox.addAttribute(new Attribute("Top", String.valueOf(lastGroupPositionTop)+"px"));
					textBox.addAttribute(new Attribute("Left", String.valueOf(lastRepeatPositionLeft+230)+"px"));
					layoutGroup.appendChild(textBox);	
					lastRepeatPositionLeft=lastRepeatPositionLeft+230;
				}
			} else if(repeat && layoutGroup!=null) {
				if(!labelInRepeat.equalsIgnoreCase("")) { /*labels from intermediate nodes are concatenated*/
					labelElement.addAttribute(new Attribute("Text", labelInRepeat+concept.getFullName()));
					labelInRepeat="";
				} 
				
				labelElement.addAttribute(new Attribute("Left", String.valueOf(lastRepeatPositionLeft+204)+"px"));
				if(oldLayoutGroup==null) {
					labelElement.addAttribute(new Attribute("Top", String.valueOf(lastLayoutPositionTop)+"px"));
				} else {
					labelElement.addAttribute(new Attribute("Top", String.valueOf(lastGroupPositionTop)+"px"));
				}
				labelElement.addAttribute(new Attribute("Binding", layoutGroup.getAttributeValue("Binding")+"/"+concept.getNodeId()+"_"+concept.getConceptId()+"/value"));	
				/*top and left attributes are not necessary in case of a TextBox as these form fields are 
				 * automatically positioned when the form is opened for the first time*/
				textBox.addAttribute(new Attribute("Binding", layoutGroup.getAttributeValue("Binding")+"/"+concept.getNodeId()+"_"+concept.getConceptId()+"/value"));
				layoutGroup.appendChild(textBox);	
				if(oldLayoutGroup==null) {
					this.page.appendChild(labelElement);
				} else {
					oldLayoutGroup.appendChild(labelElement);
				}
				lastRepeatPositionLeft=lastRepeatPositionLeft+204;			
			} else {	/*normal text field*/		
				if(!noLabel) {
					labelElement.addAttribute(new Attribute("Left", String.valueOf(lastPositionLeft)+"px"));
					labelElement.addAttribute(new Attribute("Top", String.valueOf(lastLayoutPositionTop+40)+"px"));	
					textBox.addAttribute(new Attribute("Top", String.valueOf(lastLayoutPositionTop+40)+"px"));			
					textBox.addAttribute(new Attribute("Left", "300px"));
					this.page.appendChild(labelElement);
					this.page.appendChild(textBox);	
					lastLayoutPositionTop=lastLayoutPositionTop+40;
					lastRepeatPositionLeft=300;
				} else { /*noLabel=true, no label should be created and the element should be positioned next to the previous one */
					textBox.addAttribute(new Attribute("Top", String.valueOf(lastLayoutPositionTop)+"px"));
					textBox.addAttribute(new Attribute("Left", String.valueOf(lastRepeatPositionLeft+230)+"px"));
					this.page.appendChild(textBox);	
					lastRepeatPositionLeft=lastRepeatPositionLeft+230;
				}			
			}
			lastTabIndex=lastTabIndex+1;
		}
	}
		
	private Element createLayoutGroup(OpenMRSConcept groupConcept, Boolean repeat, Element layoutGroup)
	{
		//GroupBoxes are not indented
		Element groupElement=new Element("Item");
		groupElement.addAttribute(new Attribute("WidgetType", "GroupBox"));	
		
		if(layoutGroup==null) {
			groupElement.addAttribute(new Attribute("Top", String.valueOf(lastLayoutPositionTop+40)+"px"));
			groupElement.addAttribute(new Attribute("Width", "3990px"));
			groupElement.addAttribute(new Attribute("Left", "0px"));
			groupElement.addAttribute(new Attribute("Binding", "LEFT0pxTOP"+String.valueOf(lastLayoutPositionTop+40)+"px-"+groupConcept.getNodeId()+"_"+groupConcept.getConceptId()));
		} else {
			groupElement.addAttribute(new Attribute("Top", String.valueOf(lastGroupPositionTop+40)+"px"));
			groupElement.addAttribute(new Attribute("Width", "3980px"));
			groupElement.addAttribute(new Attribute("Left", "10px"));
			groupElement.addAttribute(new Attribute("Binding", "LEFT10pxTOP"+String.valueOf(lastGroupPositionTop+40)+"px-"+groupConcept.getNodeId()+"_"+groupConcept.getConceptId()));
		}		
		groupElement.addAttribute(new Attribute("Height", "500px")); //will be adapted later
		groupElement.addAttribute(new Attribute("TabIndex", String.valueOf(lastTabIndex+1)));
		groupElement.addAttribute(new Attribute("fontSize", "16px"));
		groupElement.addAttribute(new Attribute("fontFamily", "Verdana,'Lucida Grande','Trebuchet MS',Arial,Sans-Serif"));
		groupElement.addAttribute(new Attribute("borderStyle", "dashed"));
		groupElement.addAttribute(new Attribute("borderColor", "rgb(143, 171, 199)"));
				
		if(repeat) { /*if the GroupBox is used for a repeat element*/
			groupElement.addAttribute(new Attribute("Repeated", "1"));
			//Binding muss geändert werden
			groupElement.addAttribute(new Attribute("Binding", "obs/"+groupConcept.getNodeId()+"_"+groupConcept.getConceptId()));
			groupElement.addAttribute(new Attribute("Width", "100px")); /*the width of repeat elements is adapted automatically in the form*/
			//Label for the heading of the repeat element
			Element repeatLabel=new Element("Item");
			repeatLabel.addAttribute(new Attribute("WidgetType", "Label"));		
			repeatLabel.addAttribute(new Attribute("Text", groupConcept.getFullName()));
			repeatLabel.addAttribute(new Attribute("HelpText", groupConcept.getFullName()));			
			repeatLabel.addAttribute(new Attribute("Left", "20px"));			
			repeatLabel.addAttribute(new Attribute("TabIndex", "0"));
			repeatLabel.addAttribute(new Attribute("fontWeight", "bold"));
			repeatLabel.addAttribute(new Attribute("fontStyle", "italic"));
			repeatLabel.addAttribute(new Attribute("fontSize", "16px"));
			repeatLabel.addAttribute(new Attribute("fontFamily", "Verdana,'Lucida Grande','Trebuchet MS',Arial,Sans-Serif"));
			
			if(layoutGroup==null) {
				/*the GroupBox is moved downwards as the labels for the fields in the GroupBox need to be placed above it*/
				repeatLabel.addAttribute(new Attribute("Binding", "obs/"+groupConcept.getNodeId()+"_"+groupConcept.getConceptId()));
				groupElement.addAttribute(new Attribute("Top", String.valueOf(lastLayoutPositionTop+120)+"px"));
				repeatLabel.addAttribute(new Attribute("Top", String.valueOf(lastLayoutPositionTop+40)+"px"));
				this.page.appendChild(repeatLabel);
			} else {
				repeatLabel.addAttribute(new Attribute("Binding", "obs/"+groupConcept.getNodeId()+"_"+groupConcept.getConceptId()));
				groupElement.addAttribute(new Attribute("Top", String.valueOf(lastGroupPositionTop+120)+"px"));
				repeatLabel.addAttribute(new Attribute("Top", String.valueOf(lastGroupPositionTop+40)+"px"));
				lastGroupPositionTop=lastGroupPositionTop+80; 
				layoutGroup.appendChild(repeatLabel);
			}				
			lastLayoutPositionTop=lastLayoutPositionTop+80; 
		} else { /*HeaderLabel is only necessary in case of a normal GroupBox (not a repeat element)*/
			Element groupLabel=new Element("Item");
			groupLabel.addAttribute(new Attribute("WidgetType", "Label"));		
			groupLabel.addAttribute(new Attribute("Text", groupConcept.getFullName()));
			groupLabel.addAttribute(new Attribute("HelpText", groupConcept.getFullName()));
			
			if(layoutGroup==null) {
				groupLabel.addAttribute(new Attribute("Binding", "LEFT0pxTOP"+String.valueOf(lastLayoutPositionTop+40)+"px-"+groupConcept.getNodeId()+"_"+groupConcept.getConceptId()));
			} else {
				groupLabel.addAttribute(new Attribute("Binding", "LEFT10pxTOP"+String.valueOf(lastGroupPositionTop+40)+"px-"+groupConcept.getNodeId()+"_"+groupConcept.getConceptId()));
			}
			groupLabel.addAttribute(new Attribute("Left", "null"));
			groupLabel.addAttribute(new Attribute("Top", "null"));
			groupLabel.addAttribute(new Attribute("Width", "100%"));
			groupLabel.addAttribute(new Attribute("Height", "20px"));
			groupLabel.addAttribute(new Attribute("TabIndex", "0"));
			groupLabel.addAttribute(new Attribute("color", "white"));
			groupLabel.addAttribute(new Attribute("fontWeight", "bold"));
			groupLabel.addAttribute(new Attribute("fontSize", "16px"));
			groupLabel.addAttribute(new Attribute("fontFamily", "Verdana,'Lucida Grande','Trebuchet MS',Arial,Sans-Serif"));
			groupLabel.addAttribute(new Attribute("backgroundColor", "rgb(143, 171, 199)"));
			groupLabel.addAttribute(new Attribute("textAlign", "left")); 
			groupLabel.addAttribute(new Attribute("HeaderLabel", "true"));
			groupElement.appendChild(groupLabel);
			lastGroupPositionTop=lastGroupPositionTop+40;
			lastLayoutPositionTop=lastLayoutPositionTop+40;
		}				
		lastTabIndex=lastTabIndex+1;
		return groupElement;
	}
	
	private void createLayoutLabel(String label, Element layoutGroup, Element oldLayoutGroup, Element repeat, boolean important, boolean hideOnForm)
	{
		if(!hideOnForm) {
			if(repeat==null) {
				Element labelElement=new Element("Item");
				labelElement.addAttribute(new Attribute("WidgetType", "Label"));		
				labelElement.addAttribute(new Attribute("Text", label));
				labelElement.addAttribute(new Attribute("HelpText", label));		
				labelElement.addAttribute(new Attribute("Left", String.valueOf(lastPositionLeft)+"px"));
				labelElement.addAttribute(new Attribute("Top", String.valueOf(lastLayoutPositionTop+40)+"px"));
				labelElement.addAttribute(new Attribute("TabIndex", "0"));
				labelElement.addAttribute(new Attribute("fontSize", "16px"));
				labelElement.addAttribute(new Attribute("fontFamily", "Verdana,'Lucida Grande','Trebuchet MS',Arial,Sans-Serif"));
				
				if(layoutGroup!=null)
				{
					labelElement.addAttribute(new Attribute("Top", String.valueOf(lastGroupPositionTop+40)+"px"));
					layoutGroup.appendChild(labelElement);
					lastGroupPositionTop=lastGroupPositionTop+40;
				} else {
					this.page.appendChild(labelElement);				
				}	
				lastLayoutPositionTop=lastLayoutPositionTop+40;

			} else { 	
				labelInRepeat+=label+".";
			}
		}
	}
	
	private void createStandardXFormField(CPRIMITIVEOBJECT cobj, boolean required, OpenMRSConcept newConcept, Element repeatElement, Element repeatInstance, /*Element groupBinding, Element groupInstance,*/ OpenMRSConcept repeatConcept)
	{		
		Element root = xform.getRootElement();
		Element model=root.getFirstChildElement("model", "http://www.w3.org/2002/xforms");
		Element obs=model.getFirstChildElement("instance", "http://www.w3.org/2002/xforms").getFirstChildElement("form").getFirstChildElement("obs");
		
		Element newObs=new Element(newConcept.getNodeId()+"_"+newConcept.getConceptId());
		newObs.addAttribute(new Attribute("openmrs_concept", 
				newConcept.getConceptId()+"^"+newConcept.getFullName()+"^99DCT"));
		newObs.addAttribute(new Attribute("openmrs_datatype", newConcept.getConceptDatatype())); /*will be changed to "coded" afterwards in case of a ListBox*/
		Element date=new Element("date");
		date.addAttribute(new Attribute("xsi:nil", "http://www.w3.org/2001/XMLSchema-instance", "true"));
		newObs.appendChild(date);
		Element time=new Element("time");
		time.addAttribute(new Attribute("xsi:nil", "http://www.w3.org/2001/XMLSchema-instance", "true"));
		newObs.appendChild(time);
		Element value=new Element("value");
		value.addAttribute(new Attribute("xsi:nil", "http://www.w3.org/2001/XMLSchema-instance", "true"));
		newObs.appendChild(value);
		/*newObs is added to obs at the end*/
		
		Element bind=new Element("xf:bind", "http://www.w3.org/2002/xforms");
		bind.addAttribute(new Attribute("id", newConcept.getNodeId()+"_"+newConcept.getConceptId()+"_value"));
		if(repeatConcept!=null) { 	/*the path is different if the element is inside a repeat element*/
			bind.addAttribute(new Attribute("nodeset", 
					repeatConcept.getXFormNodeset()+"/"+newConcept.getNodeId()+"_"+newConcept.getConceptId()+"/value"));
		} else {
			bind.addAttribute(new Attribute("nodeset", 
					"/form/obs/"+newConcept.getNodeId()+"_"+newConcept.getConceptId()+"/value"));
		}
		bind.addAttribute(new Attribute("type", "xsd:"+newConcept.getXmlDatatype())); 
		
		if(required==true)
		{
			bind.addAttribute(new Attribute("required", "true()"));
		}
		
		/*if available additional constraints are added depending on the datatype*/
		/*constraints defined in C_BOOLEAN are not retrieved, as only assumedValue is available, which is not representable in XForms*/
		if(cobj.getItem()!=null) {
			/*the attribute "range" is only used in case of CREAL and CINTEGER, as this information can only be used for numbers in OpenMRS*/
			String upperPattern=".<="; 
			String lowerPattern=".>="; //default=inclusive
			if(cobj.getItem().getClass().getSimpleName().equalsIgnoreCase("CSTRING"))
			{	
				if(!((CSTRING)cobj.getItem()).getList().isEmpty())
				{
					listBoxHelp=true;
				} 
			}							
			if(cobj.getItem().getClass().getSimpleName().equalsIgnoreCase("CREAL")) {	
				if(((CREAL)cobj.getItem()).getRange()!=null 
						&& ((CREAL)cobj.getItem()).getRange().getLower()!=null
						&& ((CREAL)cobj.getItem()).getRange().getUpper()!=null) {
					if(((CREAL)cobj.getItem()).getRange().isLowerIncluded()!=null) {
						if(((CREAL)cobj.getItem()).getRange().isLowerIncluded()==true) {
							lowerPattern=".>=";
						} else {
							lowerPattern=".>";
						}
					}
					if(((CREAL)cobj.getItem()).getRange().isUpperIncluded()!=null) {
						if(((CREAL)cobj.getItem()).getRange().isUpperIncluded()==true) {
							upperPattern=".<=";
						} else {
							upperPattern=".<";
						}
					}
					bind.addAttribute(new Attribute("constraint", lowerPattern+((CREAL)cobj.getItem()).getRange().getLower()
							+ " and "+upperPattern+((CREAL)cobj.getItem()).getRange().getUpper()));
				}
				if(!((CREAL)cobj.getItem()).getList().isEmpty())
				{
					listBoxHelp=true;
				} 
			}
			if(cobj.getItem().getClass().getSimpleName().equalsIgnoreCase("CINTEGER")) {	
				if(((CINTEGER)cobj.getItem()).getRange()!=null 
						&& ((CINTEGER)cobj.getItem()).getRange().getLower()!=null
						&& ((CINTEGER)cobj.getItem()).getRange().getUpper()!=null) {
					if(((CINTEGER)cobj.getItem()).getRange().isLowerIncluded()!=null) {
						if(((CINTEGER)cobj.getItem()).getRange().isLowerIncluded()==true) {
							lowerPattern=".>=";
						} else {
							lowerPattern=".>";
						}
					}
					if(((CINTEGER)cobj.getItem()).getRange().isUpperIncluded()!=null) {
						if(((CINTEGER)cobj.getItem()).getRange().isUpperIncluded()==true) {
							upperPattern=".<=";
						} else {
							upperPattern=".<";
						}
					}
					bind.addAttribute(new Attribute("constraint", lowerPattern+((CINTEGER)cobj.getItem()).getRange().getLower()
							+ " and "+upperPattern+((CINTEGER)cobj.getItem()).getRange().getUpper()));
				}
				if(!((CINTEGER)cobj.getItem()).getList().isEmpty())
				{
					listBoxHelp=true;
				} 
			}
		}			
		model.appendChild(bind);
				
		Element group1=root.getFirstChildElement("group", "http://www.w3.org/2002/xforms");
		Element input=null;
		
		if(listBoxHelp==true)	
		{	//the datatype of the concept needs to be changed to "coded"/"CWE" in the database and in the XForm
			dbStatements.updateConceptDatatype(connect, 2, newConcept.getConceptId());						
			newObs.addAttribute(new Attribute("openmrs_datatype", "CWE")); 
	
			input=new Element("xf:select1", "http://www.w3.org/2002/xforms");
			input.addAttribute(new Attribute("bind", newConcept.getNodeId()+"_"+newConcept.getConceptId()+"_value"));
			Element label=new Element("xf:label", "http://www.w3.org/2002/xforms");
			label.appendChild(newConcept.getFullName());
			input.appendChild(label);
			
			if(cobj.getItem() instanceof CSTRING) {
				for(int i=0; i<((CSTRING)cobj.getItem()).getList().size(); i++)
				{ 		
					OpenMRSConcept answerConcept=new OpenMRSConcept();
					answerConcept=createConceptAnswer(newConcept, ((CSTRING)cobj.getItem()).getList().get(i), ((CSTRING)cobj.getItem()).getList().get(i));
					Element item=new Element("xf:item", "http://www.w3.org/2002/xforms");
					item.addAttribute(new Attribute("concept_id", String.valueOf(answerConcept.getConceptId())));
					item.addAttribute(new Attribute("id", String.valueOf(answerConcept.getConceptId())+"^"+answerConcept.getFullName()+"^99DCT"));
					Element label1=new Element("xf:label", "http://www.w3.org/2002/xforms");
					label1.appendChild(((CSTRING)cobj.getItem()).getList().get(i));
					Element value2=new Element("xf:value", "http://www.w3.org/2002/xforms");
					value2.appendChild(String.valueOf(answerConcept.getConceptId())+"^"+answerConcept.getFullName()+"^99DCT");
					item.appendChild(label1);
					item.appendChild(value2);
					input.appendChild(item);
				}
			} else if(cobj.getItem() instanceof CINTEGER) {
				for(int i=0; i<((CINTEGER)cobj.getItem()).getList().size(); i++)
				{ 			
					OpenMRSConcept answerConcept=new OpenMRSConcept();
					answerConcept=createConceptAnswer(newConcept, ((CINTEGER)cobj.getItem()).getList().get(i).toString(), ((CINTEGER)cobj.getItem()).getList().get(i).toString());
					Element item=new Element("xf:item", "http://www.w3.org/2002/xforms");
					item.addAttribute(new Attribute("concept_id", String.valueOf(answerConcept.getConceptId())));
					item.addAttribute(new Attribute("id", String.valueOf(answerConcept.getConceptId())+"^"+answerConcept.getFullName()+"^99DCT"));
					Element label1=new Element("xf:label", "http://www.w3.org/2002/xforms");
					label1.appendChild(((CINTEGER)cobj.getItem()).getList().get(i).toString());
					Element value2=new Element("xf:value", "http://www.w3.org/2002/xforms");
					value2.appendChild(String.valueOf(answerConcept.getConceptId())+"^"+answerConcept.getFullName()+"^99DCT");
					item.appendChild(label1);
					item.appendChild(value2);
					input.appendChild(item);
				}
			} else if(cobj.getItem() instanceof CREAL) {
				for(int i=0; i<((CREAL)cobj.getItem()).getList().size(); i++)
				{ 			
					OpenMRSConcept answerConcept=new OpenMRSConcept();
					answerConcept=createConceptAnswer(newConcept, ((CREAL)cobj.getItem()).getList().get(i).toString(), ((CREAL)cobj.getItem()).getList().get(i).toString());
					Element item=new Element("xf:item", "http://www.w3.org/2002/xforms");
					item.addAttribute(new Attribute("concept_id", String.valueOf(answerConcept.getConceptId())));
					item.addAttribute(new Attribute("id", String.valueOf(answerConcept.getConceptId())+"^"+answerConcept.getFullName()+"^99DCT"));
					Element label1=new Element("xf:label", "http://www.w3.org/2002/xforms");
					label1.appendChild(((CREAL)cobj.getItem()).getList().get(i).toString());
					Element value2=new Element("xf:value", "http://www.w3.org/2002/xforms");
					value2.appendChild(String.valueOf(answerConcept.getConceptId())+"^"+answerConcept.getFullName()+"^99DCT");
					item.appendChild(label1);
					item.appendChild(value2);
					input.appendChild(item);
				}
			}
		}		
		else if(listBoxHelp==false) {
			input=new Element("xf:input", "http://www.w3.org/2002/xforms");
			input.addAttribute(new Attribute("bind", newConcept.getNodeId()+"_"+newConcept.getConceptId()+"_value"));
			Element label=new Element("xf:label", "http://www.w3.org/2002/xforms");
			label.appendChild(newConcept.getFullName());
			Element hint=new Element("xf:hint", "http://www.w3.org/2002/xforms");
			hint.appendChild(newConcept.getDescription());
			input.appendChild(label);
			input.appendChild(hint);
		}
		if(repeatElement!=null) {
			repeatElement.appendChild(input);	
		} else { 
			if(input!=null) {
				group1.appendChild(input);
			} else {
				System.out.println("Fehler passiert: input ist null");
			}
		}
		
		if(repeatInstance!=null) {
			repeatInstance.appendChild(newObs);
		} else {
			obs.appendChild(newObs);
		}		
	}
	
	private Element[] createRepeatXFormElement(OpenMRSConcept repeatConcept) 
	{
		Element root = xform.getRootElement();
		Element model=root.getFirstChildElement("model", "http://www.w3.org/2002/xforms");
		
		Element newObs=new Element(repeatConcept.getNodeId()+"_"+repeatConcept.getConceptId());
		newObs.addAttribute(new Attribute("openmrs_concept", 
				repeatConcept.getConceptId()+"^"+repeatConcept.getFullName()+"^99DCT"));
		newObs.addAttribute(new Attribute("openmrs_datatype", "ZZ"));
		
		Element bind=new Element("xf:bind", "http://www.w3.org/2002/xforms");
		bind.addAttribute(new Attribute("id", repeatConcept.getNodeId()+"_"+repeatConcept.getConceptId()));
		Element repeatElement=new Element("xf:repeat", "http://www.w3.org/2002/xforms");
		repeatElement.addAttribute(new Attribute("bind", repeatConcept.getNodeId()+"_"+repeatConcept.getConceptId()));
		repeatConcept.setXFormBinding(repeatConcept.getNodeId()+"_"+repeatConcept.getConceptId());
		bind.addAttribute(new Attribute("nodeset", 
			"/form/obs/"+repeatConcept.getNodeId()+"_"+repeatConcept.getConceptId()));
		repeatConcept.setXFormNodeset("/form/obs/"+repeatConcept.getNodeId()+"_"+repeatConcept.getConceptId());		
	
		model.appendChild(bind);
		return new Element[] {repeatElement, newObs};
	}
	
	private void createListBoxXFormField(OpenMRSConcept newConcept, boolean required, List<String[]> options, Element repeatElement, Element repeatInstance, OpenMRSConcept repeatConcept, Boolean openEHRCode)
	{ 
		Element root = xform.getRootElement();
		Element model=root.getFirstChildElement("model", "http://www.w3.org/2002/xforms");
		Element obs=model.getFirstChildElement("instance", "http://www.w3.org/2002/xforms").getFirstChildElement("form").getFirstChildElement("obs");
		
		Element newObs=new Element(newConcept.getNodeId()+"_"+newConcept.getConceptId());
		newObs.addAttribute(new Attribute("openmrs_concept", 
				newConcept.getConceptId()+"^"+newConcept.getFullName()+"^99DCT"));
		newObs.addAttribute(new Attribute("openmrs_datatype", "CWE")); 
		Element date=new Element("date");
		date.addAttribute(new Attribute("xsi:nil", "http://www.w3.org/2001/XMLSchema-instance", "true"));
		newObs.appendChild(date);
		Element time=new Element("time");
		time.addAttribute(new Attribute("xsi:nil", "http://www.w3.org/2001/XMLSchema-instance", "true"));
		newObs.appendChild(time);
		Element value=new Element("value");
		value.addAttribute(new Attribute("xsi:nil", "http://www.w3.org/2001/XMLSchema-instance", "true"));
		newObs.appendChild(value);
		
		if(repeatConcept!=null) {
			newConcept.setXFormNodeset(repeatConcept.getXFormNodeset()+"/"+newConcept.getNodeId()+"_"+newConcept.getConceptId()+"/value");
		} else {
			newConcept.setXFormNodeset("/form/obs/"+newConcept.getNodeId()+"_"+newConcept.getConceptId()+"/value");
		}
		newConcept.setXFormBinding(newConcept.getNodeId()+"_"+newConcept.getConceptId()+"_value"); 
		
		Element bind=new Element("xf:bind", "http://www.w3.org/2002/xforms");
		bind.addAttribute(new Attribute("id", newConcept.getNodeId()+"_"+newConcept.getConceptId()+"_value"));
		bind.addAttribute(new Attribute("nodeset", newConcept.getXFormNodeset()));
		bind.addAttribute(new Attribute("type", "xsd:"+newConcept.getXmlDatatype())); 
		
		if(required==true) {	
			bind.addAttribute(new Attribute("required", "true()"));
		}			
		model.appendChild(bind);
				
		Element group1=root.getFirstChildElement("group", "http://www.w3.org/2002/xforms");
		Element input=new Element("xf:select1", "http://www.w3.org/2002/xforms");
		input.addAttribute(new Attribute("bind", newConcept.getNodeId()+"_"+newConcept.getConceptId()+"_value"));
		Element label=new Element("xf:label", "http://www.w3.org/2002/xforms");
		label.appendChild(newConcept.getFullName());
		input.appendChild(label);
			
		//if(!openEHRCode) { //TODO
			for(int i=0; i<options.size(); i++) {
				OpenMRSConcept answerConcept=new OpenMRSConcept();
				answerConcept=createConceptAnswer(newConcept, options.get(i)[1], options.get(i)[0]);
				
				Element item=new Element("xf:item", "http://www.w3.org/2002/xforms");
				item.addAttribute(new Attribute("concept_id", String.valueOf(answerConcept.getConceptId())));
				item.addAttribute(new Attribute("id", String.valueOf(answerConcept.getConceptId())+"^"+answerConcept.getFullName()+"^99DCT"));
				Element label1=new Element("xf:label", "http://www.w3.org/2002/xforms");
				label1.appendChild(options.get(i)[1]);
				Element value2=new Element("xf:value", "http://www.w3.org/2002/xforms");
				value2.appendChild(String.valueOf(answerConcept.getConceptId())+"^"+answerConcept.getFullName()+"^99DCT");
				item.appendChild(label1);
				item.appendChild(value2);
				input.appendChild(item);
			}
		//} else { 
			//TODO
			/*term is from an openEHR code set (e.g. ISO language) and the concept is already available, 
			 * in such a case the concept is retrieved in a previous step and saved in newConcept*/
		//}
				
		if(repeatElement!=null) {
			repeatElement.appendChild(input);			
		} else { 	
			if(input!=null) {
				group1.appendChild(input);
			} 
		}
		if(repeatInstance!=null) {
			repeatInstance.appendChild(newObs);
		} else {
			obs.appendChild(newObs);
		}
	}
		
	private void getDBConnection() throws Exception, SQLException
	{
		MySqlAccess dao=new MySqlAccess();
		this.connect=dao.getDatabaseConnection();	
		dbStatements=new DatabaseStatements();	    
	}
			
	private void createConcept(String name, String conceptDatatype, OpenMRSConcept newConcept, boolean isSet, String description)
	/*if a ListBox should be created, conceptDatatype holds the value "coded" instead of an openEHR datatype
	 * the defined "name" is used if not nodeId is defined in the concept*/
	
	{
		if((newConcept!=null) && (newConcept.getNodeId()!=null) && !(newConcept.getNodeId().equalsIgnoreCase("")) && !(newConcept.getNodeId().equalsIgnoreCase(" "))) {	
			String nodeIdText=getTermDefinition(newConcept.getNodeId(), "text");
			String nodeIdDescription=getTermDefinition(newConcept.getNodeId(), "description");
			if(nodeIdText!=null && !nodeIdText.equalsIgnoreCase("not found")) {
				newConcept.setFullName(nodeIdText);	
			} else {
				newConcept.setFullName(name);
			}
			if(nodeIdDescription!=null){					
				newConcept.setDescription(nodeIdDescription);
			} else {
				newConcept.setDescription(name);
			}				
		} else {
			newConcept.setFullName(name); /*will be used as name if no nodeId is available*/
			newConcept.setDescription(name); /*the name is used as description per default*/
		}
		
		if(conceptDatatype.equalsIgnoreCase("noNewName")) { 
			/*"name" must be used instead of the according ontology entry for the nodeId
			 * (this is needed in case the number, which marks embedded archetypes, has already been removed)*/
			newConcept.setFullName(name);
			conceptDatatype="Text";
		}
			
		if(description!=null){ /*if a description has been defined, it must be used (important for C_DV_ORDINAL)*/
			newConcept.setDescription(description);
		}
									
		String[] conceptDt=getConceptDatatype(conceptDatatype);
		int datatypeId=3; /*default: Concept Datatype=Text*/
		/*if the datatype can't be found, it already is the desired OpenMRS datatype */
		if(!conceptDt[0].equalsIgnoreCase("not found")) {
			newConcept.setConceptDatatype(conceptDt[1]);			
			newConcept.setXmlDatatype(conceptDt[3]);	
			datatypeId=Integer.parseInt(conceptDt[2]);
		} else {
			String[] conceptDtFromName=getConceptIdFromDTName(conceptDatatype);
			if(!conceptDtFromName[0].equalsIgnoreCase("not found"))
			{
				newConcept.setConceptDatatype(conceptDtFromName[0]);			
				newConcept.setXmlDatatype(conceptDtFromName[2]);	
				datatypeId=Integer.parseInt(conceptDtFromName[1]);
			} else {	
				datatypeId=3; //default: Concept Datatype=Text 
				newConcept.setConceptDatatype("ST");
				newConcept.setXmlDatatype("string");
			}
		}
				
		int conceptId=dbStatements.createConcept(this.connect, newConcept.getDescription(), datatypeId, 16, 1, isSet);
		newConcept.setConceptId(conceptId);			
		dbStatements.createConceptName(this.connect, conceptId, newConcept.getFullName(), 1);					
	}
	
	private OpenMRSConcept createConceptAnswer(OpenMRSConcept concept, String option, String answerId)
	/*for ListBoxes concepts for all options need to be created + entries in the concept-answers table
	 * answerId includes an additional Id for the option (e.g. in case of a DV_ORDINAL node)*/
	{
		OpenMRSConcept answerConcept=new OpenMRSConcept();
		if(answerId!=null && answerId.startsWith("at")) 
		{			
			/*in case of an embedded archetype the number which has been added during the 
			 * creation of the comprehensive archetype needs to be removed, e.g. from at2.0005 to at0005*/
			String pattern = "(at)(\\d*)([\\.])"; 		
			answerConcept.setNodeId(answerId.replaceAll(pattern, "$1"));
		}
		//"noNewName" is used instead of "Text", so that the defined option name is used and not the name from the ontology is looked up
		createConcept(option, "noNewName", answerConcept, false, answerId); 
		/*if additional ids are available for the options, they are added as description*/
		dbStatements.createConceptAnswer(connect, concept.getConceptId(), answerConcept.getConceptId(), 1);
		return answerConcept;
	}
	
	private void createConceptSetEntry(OpenMRSConcept setConcept, OpenMRSConcept otherConcept) {
		dbStatements.createConceptSet(connect, otherConcept.getConceptId(), setConcept.getConceptId(), 1);
	}
	
	private String[] getConceptDatatype(String dt)
	{
		HashMap<String, String[]> dtmapping = new HashMap<String, String[]>();
		//openEHR constraint data type, OpenMRS Concept data type name, hl7 abbreviation, db id, XML data type, WidgetType
		dtmapping.put("cboolean", new String[]{"Boolean", "BIT", "10", "boolean", "ListBox"});
		dtmapping.put("cstring", new String[]{"Text", "ST", "3", "string", "TextBox"});
		dtmapping.put("cinteger", new String[]{"Numeric", "NM", "1", "int", "TextBox"});
		dtmapping.put("creal", new String[]{"Numeric", "NM", "1", "decimal", "TextBox"});
		dtmapping.put("cdate", new String[]{"Date", "DT", "6", "date", "DatePicker"});
		dtmapping.put("ctime", new String[]{"Time", "TM", "7", "time", "TimeWidget"});
		dtmapping.put("cdatetime", new String[]{"DateTime", "TS", "8", "dateTime", "DateTimeWidget"});
		dtmapping.put("cduration", new String[]{"Text", "ST", "3", "duration", "TextBox"}); 
		
		for(String datatype:dtmapping.keySet())
		{
			if(datatype.equalsIgnoreCase(dt))
			{			
				return dtmapping.get(datatype);
			}
		}
		return new String[] {"not found", ""};    
	}
	
	private String[] getConceptIdFromDTName(String dtname)
	{
		HashMap<String, String[]> dtnamemapping = new HashMap<String, String[]>();
		//OpenMRS Concept data type name, hl7 abbreviation, db id, XML data type
		dtnamemapping.put("Numeric", new String[]{"NM", "1", "decimal"});
		dtnamemapping.put("Coded", new String[]{"CWE", "2", "string"});
		dtnamemapping.put("Text", new String[]{"ST", "3", "string"});
		dtnamemapping.put("N/A", new String[]{"ZZ", "4", "string"});
		dtnamemapping.put("Document", new String[]{"RP", "5", "string"});
		dtnamemapping.put("Date", new String[]{"DT", "6", "date"});
		dtnamemapping.put("Time", new String[]{"TM", "7", "time"});
		dtnamemapping.put("DateTime", new String[]{"TS", "8", "dateTime"});		
		dtnamemapping.put("Boolean", new String[]{"BIT", "10", "boolean"});
		dtnamemapping.put("Rule", new String[]{"ZZ", "11", "string"});
		dtnamemapping.put("Structured Numeric", new String[]{"SN", "12", "string"});
		dtnamemapping.put("Complex", new String[]{"ED", "13", "string"});
		
		for(String datatype:dtnamemapping.keySet())
		{
			if(datatype.equalsIgnoreCase(dtname))
			{			
				return dtnamemapping.get(datatype);
			}
		}
		return new String[] {"not found", ""};    
	}
	
	private String getWidgetType(String templateElement) {
		/*returns the suitable XForm Widget type for a given RM data type*/
		HashMap<String, String> widgetMapping = new HashMap<String, String>();
		widgetMapping.put("DV_TEXT", "TextBox");
		widgetMapping.put("DV_DATE_TIME", "DateTimeWidget");
		widgetMapping.put("DV_DATE", "DatePicker");
		widgetMapping.put("DV_TIME", "TimeWidget");
		widgetMapping.put("DV_CODED_TEXT", "ListBox");
		widgetMapping.put("DV_URI", "TextBox");
		widgetMapping.put("DV_DURATION", "TextBox");
		
		for(String te:widgetMapping.keySet())
		{
			if(te.equalsIgnoreCase(templateElement))
			{			
				return widgetMapping.get(te);
			}
		}
		return "TextBox";  
	}

	private String getTermDefinition(String nodeId, String type) 
	/*retrieves text or description for a given node id from the term definitions*/
	{
		List<StringDictionaryItem> items=new ArrayList<StringDictionaryItem>();
		for (ARCHETYPETERM term : template.getDefinition().getTermDefinitions()) {
			if (term.getCode().equalsIgnoreCase(nodeId)) {
				items=term.getItems();
				break;
			}			
		}
		for (StringDictionaryItem dict_item:items)
		{
			if(dict_item.getId().equalsIgnoreCase(type))
			{
				return dict_item.getValue();
			}
		}	
		return "not found";
	}
	
	private List<String[]> getOpenEHRRubricForCode(String terminologyId, List<String> codeList)
	/*labels for a list of codes are retrieved from a certain openEHR terminology*/
	{
		List<String[]> codes=new ArrayList<String[]>();
		try {
			TerminologyService termService=SimpleTerminologyService.getInstance();
			TerminologyAccess termAccess=termService.terminology(terminologyId);
			for(String code:codeList) {
				String codeString=termAccess.rubricForCode(code, "en"); /*only grouped terminologies are retrieved here, no code sets*/
				if(codeString!=null) {
					codes.add(new String[]{code, codeString});
				} else {
					codes.add(new String[]{code, code});
				}
			}
		} catch(Exception exc) {
			System.out.println("Exception: " + exc.getMessage());
			exc.printStackTrace();
		}
		return codes;
	}
	
	private List<String[]> getOpenEHRCodeSet(String codeSetId) {	
		/*codes for a given code set are retrieved*/
		List<String[]> codes=new ArrayList<String[]>();
		if(codeSetId!=null) {
			try {
				TerminologyService termService=SimpleTerminologyService.getInstance();
				CodeSetAccess codeAccess=termService.codeSet(codeSetId);
				Set<CodePhrase> allCodes=codeAccess.allCodes();
				for(CodePhrase code:allCodes) {
					//TODO: Möglichkeit finden, dass Description zum Code auch ausgegeben wird??
					codes.add(new String[]{code.getCodeString(), code.getCodeString()});
				}
			} catch(Exception exc) {
				System.out.println("Exception: " + exc.getMessage());
				exc.printStackTrace();
			}
		}
		return codes;
	}
	
	public static String getFullXPath(COBJECT cobj) {			
		StringBuilder sb=new StringBuilder(cobj.getXPath());
		if(cobj.getParent()!=null && cobj.getParent().getParent()!=null) {
			while(true) {
				sb.insert(0, cobj.getParent().getParent().getXPath()+cobj.getParent().getXPath());
				cobj=cobj.getParent().getParent();
				if(cobj.getParent()!=null && cobj.getParent().getParent()!=null) {
					continue;
				} else {
					break;
				}
			}
		}
		String xpath=sb.toString();
		return xpath;
	}
	
	public static String getFullPath(COBJECT cobj) {
		StringBuilder sb=new StringBuilder(cobj.getPath());		
		if(cobj.getParent()!=null && cobj.getParent().getParent()!=null) {
			while(true) {
				sb.insert(0, cobj.getParent().getParent().getPath()+cobj.getParent().getPath());
				cobj=cobj.getParent().getParent();
				if(cobj.getParent()!=null && cobj.getParent().getParent()!=null) {
					continue;
				} else {
					break;
				}
			}
		}
		String path=sb.toString();
		return path;
	}
	
	private void createMetadataEntry(String xPath, String widgetType, String label, int concept_id, String defaultValue, boolean inRepeat) {		
		if(this.xpathDefaultValuesMap.get(xPath)!=null 
				&& !this.xpathDefaultValuesMap.get(xPath).equalsIgnoreCase("") 
				&& !this.xpathDefaultValuesMap.get(xPath).equalsIgnoreCase(" ")
				&& defaultValue==null) {
			defaultValue=this.xpathDefaultValuesMap.get(xPath);
		}	
		/*for elements in a repeat element the metadata id of the repeat element is added (needed for the ehr extract generation)*/
		if(inRepeat==true && (widgetType==null || (widgetType!=null && !widgetType.equalsIgnoreCase("Repeat")))) {
			dbStatements.createOpenEHRFormMetadata(this.connect, this.formId, widgetType, label, xPath, concept_id, defaultValue, repeatMetadataId);
		} else {
			if(inRepeat) {
		repeatMetadataId=dbStatements.createOpenEHRFormMetadata(this.connect, this.formId, widgetType, label, xPath, concept_id, defaultValue, 1);
			} else {
				dbStatements.createOpenEHRFormMetadata(this.connect, this.formId, widgetType, label, xPath, concept_id, defaultValue, 0);
			}
		}
	}
	
	private void createMetadataEntryForATNodeName(CATTRIBUTE cattr, boolean inRepeat) {
	/*creates a metadata entry for the attribute "name" (no form field is needed for this attribute */
		if(cattr.getRmAttributeName().equalsIgnoreCase("name")) 	{
			for(CATTRIBUTE attr1:((CCOMPLEXOBJECT)cattr.getChildren().get(0)).getAttributes()) {
				if(attr1.getRmAttributeName().equalsIgnoreCase("value")) {
					createMetadataEntry(getFullXPath((CCOMPLEXOBJECT)cattr.getChildren().get(0)), null, null, 0, null, inRepeat);
					createMetadataEntry(getFullXPath((CPRIMITIVEOBJECT)attr1.getChildren().get(0)), null, "name", 0, getTermDefinition(cattr.getParent().getNodeId(), "text"), inRepeat);
					
				}
			}
		} else if(cattr.getRmAttributeName().equalsIgnoreCase("archetype_node_id")) {
			/*not necessary as the at_node_id is already included in the XPath*/
			//createMetadataEntry(getFullXPath((CPRIMITIVEOBJECT)cattr.getChildren().get(0)), null, "archetype_node_id", 0, ((CSTRING)((CPRIMITIVEOBJECT)cattr.getChildren().get(0)).getItem()).getList().get(0));
		}
	}
	
	private Map<String, String> getDefaultValues(OPERATIONALTEMPLATE opt) {
		Map<String, String> xpathValueMap=new HashMap<String, String>();
		if(template.getConstraints()!=null && template.getConstraints().getAttributes()!=null) {
		for(TATTRIBUTE tattr:template.getConstraints().getAttributes()) {
			String path=opt.getDefinition().getPath()+tattr.getDifferentialPath();
			COBJECT node=template.node(path);
			String xpath=getFullXPath(node);
			xpath=xpath+"/"+tattr.getRmAttributeName();
			for(TCOMPLEXOBJECT tobj:tattr.getChildren()) {
				xpath=xpath+"[@xsi:type='"+tobj.getRmTypeName()+"']";
				
				if(tobj.getDefaultValue() instanceof DVBOOLEAN) {
					if(((DVBOOLEAN)tobj.getDefaultValue()).isValue()) {
						xpathValueMap.put(xpath+"/value", "true");
					} else {
						xpathValueMap.put(xpath+"/value", "false");
					}	
					
				} else if(tobj.getDefaultValue() instanceof DVPARAGRAPH) {
					//TODO
/*					for(DVTEXT item:((DVPARAGRAPH)tobj.getDefaultValue()).getItems()) {
						xpathValueMap.put(xpath+"/value/items[@xsi:type='DV_TEXT']/value", item.getValue());
					}*/
					
				} else if(tobj.getDefaultValue() instanceof DVINTERVAL) {
					//TODO
					
				} else if(tobj.getDefaultValue() instanceof DVTEXT) {
					xpathValueMap.put(xpath+"/value", ((DVTEXT)tobj.getDefaultValue()).getValue());
					
				} else if(tobj.getDefaultValue() instanceof DVURI) {
					xpathValueMap.put(xpath+"/value", ((DVURI)tobj.getDefaultValue()).getValue());
				} 
				
				else if(tobj.getDefaultValue() instanceof DVSTATE) {
					//TODO
					/*xpathValueMap.put(xpath+"/value", ((DVSTATE)tobj.getDefaultValue()).getValue());*/
					
				} else if(tobj.getDefaultValue() instanceof DVQUANTITY) {
					xpathValueMap.put(xpath+"/precision", String.valueOf(((DVQUANTITY)tobj.getDefaultValue()).getPrecision()));
					xpathValueMap.put(xpath+"/units", String.valueOf(((DVQUANTITY)tobj.getDefaultValue()).getUnits()));
					xpathValueMap.put(xpath+"/magnitude", String.valueOf(((DVQUANTITY)tobj.getDefaultValue()).getMagnitude()));					
					
				} else if(tobj.getDefaultValue() instanceof DVPROPORTION) {
					xpathValueMap.put(xpath+"/numerator", String.valueOf(((DVPROPORTION)tobj.getDefaultValue()).getNumerator()));
					xpathValueMap.put(xpath+"/denominator", String.valueOf(((DVPROPORTION)tobj.getDefaultValue()).getDenominator()));
					xpathValueMap.put(xpath+"/type", String.valueOf(((DVPROPORTION)tobj.getDefaultValue()).getType()));
					
				} else if(tobj.getDefaultValue() instanceof DVPARSABLE) {
					xpathValueMap.put(xpath+"/value", String.valueOf(((DVPARSABLE)tobj.getDefaultValue()).getValue()));
					xpathValueMap.put(xpath+"/value", String.valueOf(((DVPARSABLE)tobj.getDefaultValue()).getFormalism()));
					
				} else if(tobj.getDefaultValue() instanceof DVMULTIMEDIA) {
					//TODO
					
				} else if(tobj.getDefaultValue() instanceof DVIDENTIFIER) {
					//TODO
					
				} else if(tobj.getDefaultValue() instanceof DVTIMESPECIFICATION) {
					xpathValueMap.put(xpath+"/value[@xsi:type='DV_PARSABLE']/value", ((DVTIMESPECIFICATION)tobj.getDefaultValue()).getValue().getValue());
				}
				else if(tobj.getDefaultValue() instanceof DVORDINAL) {
					//??
					xpathValueMap.put(xpath+"/value", String.valueOf(((DVORDINAL)tobj.getDefaultValue()).getValue()));
					xpathValueMap.put(xpath+"/symbol/value", String.valueOf(((DVORDINAL)tobj.getDefaultValue()).getSymbol().getValue()));
					xpathValueMap.put(xpath+"/symbol/terminology_id/value", String.valueOf(((DVORDINAL)tobj.getDefaultValue()).getSymbol().getDefiningCode().getTerminologyId().getValue()));
					xpathValueMap.put(xpath+"/symbol/code_string", String.valueOf(((DVORDINAL)tobj.getDefaultValue()).getSymbol().getDefiningCode().getCodeString()));
				
				} else if(tobj.getDefaultValue() instanceof DVTIME) {
					xpathValueMap.put(xpath+"/value", ((DVTIME)tobj.getDefaultValue()).getValue());
				
				} else if(tobj.getDefaultValue() instanceof DVDATETIME) {
					xpathValueMap.put(xpath+"/value", ((DVDATETIME)tobj.getDefaultValue()).getValue());
				
				} else if(tobj.getDefaultValue() instanceof DVDATE) {
					xpathValueMap.put(xpath+"/value", ((DVDATE)tobj.getDefaultValue()).getValue());
					
				} else if(tobj.getDefaultValue() instanceof DVDURATION) {
					xpathValueMap.put(xpath+"/value", ((DVDURATION)tobj.getDefaultValue()).getValue());
					
				} else if(tobj.getDefaultValue() instanceof DVCOUNT) {
					xpathValueMap.put(xpath+"/magnitude", String.valueOf(((DVCOUNT)tobj.getDefaultValue()).getMagnitude()));
					
				} else if(tobj.getDefaultValue() instanceof DVCODEDTEXT) {
					xpathValueMap.put(xpath+"/value", String.valueOf(((DVCODEDTEXT)tobj.getDefaultValue()).getValue()));
					xpathValueMap.put(xpath+"/terminology_id/value", String.valueOf(((DVCODEDTEXT)tobj.getDefaultValue()).getDefiningCode().getTerminologyId().getValue()));
					xpathValueMap.put(xpath+"/code_string", String.valueOf(((DVCODEDTEXT)tobj.getDefaultValue()).getDefiningCode().getCodeString()));
				}	
			}
		}
		}
		return xpathValueMap;
	}
	
	private boolean checkIfHidden(COBJECT cobj) {
		if(template.getView()!=null && template.getView().getConstraints()!=null) {
			String path=getFullPath(cobj);
			if (path.lastIndexOf("/")!=-1) {
				path=path.substring(path.indexOf("/"));
				for(TVIEW.Constraints constraint:template.getView().getConstraints()) {
					if(path.equalsIgnoreCase(constraint.getPath()) 
						&& constraint.getItems().get(0).getId().equalsIgnoreCase("pass_through")) {					
						System.out.println("Node hidden: "+path);
						return true;
					}
				}
			}
		}
		return false;
	}
	
	private String checkForAnnoation(COBJECT cobj) {
		String annotation=null;
		if(template.getAnnotations()!=null) {
			for(ANNOTATION text:template.getAnnotations()) {
				String path=getFullPath(cobj);
				if (path.lastIndexOf("/")!=-1) {
					path=path.substring(path.indexOf("/"));
					if(path.equalsIgnoreCase(text.getPath())) {
						for(StringDictionaryItem item: text.getItems()) {
							if(item.getId().equalsIgnoreCase("default")) {
								annotation=item.getValue();
							}
						}
					}	
				}
			}
		}
		return annotation;
	}
	
	private boolean checkIfRequired(COBJECT cobj, boolean required) {
		/*the next LOCATABLE node in the nodes above the current one is selected, if it is required too, the current node is set to required
		 * this method is used for all nodes which are not of type CCOMPLEXOBJECT, e.g. CPRIMITIVE, CCODEPHRASE...*/
		if(required==true) {
			COBJECT cobj2=cobj.getParent().getParent();
			while(cobj2!=null) {
				if(GenerateCompTemplate.checkIfChildOfLocatable(cobj2.getRmTypeName())) {
					if(cobj2.getOccurrences().getLower()!=null && cobj2.getOccurrences().getLower()==1 && required) {
						return true;					
					} else {
						return false;
					}
				}				
				cobj2=cobj2.getParent().getParent();
			}
		}
		return false;
	}
	
	private boolean mapDvCodedText(COBJECT elementChild, boolean required, OpenMRSConcept elementConcept, 
			OpenMRSConcept iRepeatC, OpenMRSConcept iGroupC, boolean hideOnForm, Element iXFormUIRepeat, 
			Element iXFormRepeatInstance, Element iLayoutGroup, Element iOldLayoutGroup, boolean repeatHelp, String description) {
		
		CPRIMITIVEOBJECT elementCprim=new CPRIMITIVEOBJECT();
		for(CATTRIBUTE codedAttr1:((CCOMPLEXOBJECT)elementChild).getAttributes()) {
			if(codedAttr1.getRmAttributeName().equalsIgnoreCase("defining_code")) {
				/*the attributes defining_code and value are mapped together for a better representation on the form
				 * all other attributes are mapped in the generic way*/

				if(codedAttr1.getChildren().get(0) instanceof CCODEPHRASE) {
					CCODEPHRASE elementCCodePhrase=(CCODEPHRASE)codedAttr1.getChildren().get(0);
					List<String[]> options=new ArrayList<String[]>(); /*includes the Ids and labels which should be used in the ListBox*/
					Boolean openEHRCode=false; 
					if(elementCCodePhrase.getTerminologyId()!=null 
							&& elementCCodePhrase.getTerminologyId().getValue() !=null
							&& (elementCCodePhrase.getTerminologyId().getValue().equalsIgnoreCase("locale")
							||elementCCodePhrase.getTerminologyId().getValue().equalsIgnoreCase("local")))
					{
						for(int i=0; i<elementCCodePhrase.getCodeList().size(); i++) {
							String codeId=elementCCodePhrase.getCodeList().get(i).replaceFirst("(at)(\\d*)(\\.)", "at");
							options.add(new String[]{codeId, getTermDefinition(elementCCodePhrase.getCodeList().get(i), "text")});	
						}
					} else if(elementCCodePhrase.getTerminologyId()!=null && elementCCodePhrase.getTerminologyId().getValue().equalsIgnoreCase("openEHR")) {
						options=getOpenEHRRubricForCode(elementCCodePhrase.getTerminologyId().getValue(), elementCCodePhrase.getCodeList());	
					} else {
						if(elementCCodePhrase.getCodeList()==null) { /*probably a code set*/
							options=getOpenEHRCodeSet(elementCCodePhrase.getTerminologyId().getValue());
							openEHRCode=true;
							
						} else { /*unkown terminology --> the codes themselve are used as ListBox options*/								
							for(int i=0; i<elementCCodePhrase.getCodeList().size(); i++) {
								options.add(new String[] {elementCCodePhrase.getCodeList().get(i), elementCCodePhrase.getCodeList().get(i)});
							}
						}	
					}
					if(options.isEmpty()) { /*in case no options can be found a TextBox is created instead of a ListBox*/
															
						createConcept(elementChild.getParent().getRmAttributeName(), "Text", elementConcept, false, description);	
						if(iRepeatC!=null) {
							createConceptSetEntry(iRepeatC, elementConcept);
							repeatHelp=true;
						}
						
						elementCprim.setOccurrences(codedAttr1.getExistence());
						createStandardXFormField(elementCprim, required, elementConcept, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
						createLayoutTextField(elementConcept, iLayoutGroup, iOldLayoutGroup, "TextBox", repeatHelp, false, hideOnForm);						
						//TODO? paths are not correct!
						createMetadataEntry(getFullXPath(codedAttr1.getChildren().get(0)), "TextBox", elementConcept.getFullName(), elementConcept.getConceptId(), null, repeatHelp);
					} else {
						createConcept(elementChild.getParent().getRmAttributeName(), "Coded", elementConcept, false, description);									
						if(iRepeatC!=null) {
							createConceptSetEntry(iRepeatC, elementConcept);
							repeatHelp=true;
						}				
						createListBoxXFormField(elementConcept, required, options, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC, openEHRCode);												
						createLayoutTextField(elementConcept, iLayoutGroup, iOldLayoutGroup, "ListBox", repeatHelp, false, hideOnForm);		

						createMetadataEntry(getFullXPath(elementChild)+"/value", "ListBox", elementConcept.getFullName(), elementConcept.getConceptId(), null, repeatHelp);
						createMetadataEntry(getFullXPath(elementCCodePhrase), "", null, 0, null, repeatHelp);
						createMetadataEntry(getFullXPath(elementCCodePhrase)+"/terminology_id", "", null, 0, null, repeatHelp);
						createMetadataEntry(getFullXPath(elementCCodePhrase)+"/terminology_id/value", "", null, 0, elementCCodePhrase.getTerminologyId().getValue(), repeatHelp);
						createMetadataEntry(getFullXPath(elementCCodePhrase)+"/code_string", "ListBox-Code", elementConcept.getFullName(), elementConcept.getConceptId(), null, repeatHelp);
					}										
				} else if(codedAttr1.getChildren().get(0) instanceof CCOMPLEXOBJECT) {
					//TODO: 
					
				} else {/* no CCODEPHRASE or CCOMPLEXOBJECT, but e.g. a CONSTRAINT_REF*/
					System.out.println("CONSTRAINT_REF: normales Textfeld erzeugt");
					/*normal text field will be created*/
					createConcept(codedAttr1.getRmAttributeName(), "cstring", elementConcept, false, description);
					if(iRepeatC!=null) {
						createConceptSetEntry(iRepeatC, elementConcept);
						repeatHelp=true;
					}
					elementCprim.setOccurrences(codedAttr1.getExistence());
					createStandardXFormField(elementCprim, required, elementConcept, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
					createLayoutTextField(elementConcept, iLayoutGroup, iOldLayoutGroup, "TextBox", repeatHelp, false, hideOnForm);	
					createMetadataEntry(getFullXPath(codedAttr1.getChildren().get(0)), "TextBox", elementConcept.getFullName(), elementConcept.getConceptId(), null, repeatHelp);
				}
			} else if(codedAttr1.getRmAttributeName().equalsIgnoreCase("value")) { /*will be mapped together with the defining_code attribute*/
				continue;
			} else { /*creation of additional fields if more attributes are available*/
				int oldPositionLeft=lastPositionLeft;
				lastPositionLeft=lastPositionLeft+10;
				for(COBJECT child: codedAttr1.getChildren()) {
					iterateTemplateNodes(child, iLayoutGroup, iOldLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC, iGroupC);
				}
				lastPositionLeft=oldPositionLeft;
			}
		}
		return repeatHelp;
	}	
	
	//Mapping for DV_TEXT, DV_DATE_TIME, DV_DATE, DV_TIME, DV_EHR_URI, DV_PERIODIC_TIME_SPECIFICATION, DV_GENERAL_TIME_SPECIFICATION
	private boolean mapDvText(COBJECT elementChild, OpenMRSConcept elementConcept, OpenMRSConcept iRepeatC, OpenMRSConcept iGroupC, Element iXFormUIRepeat, Element iXFormRepeatInstance,
			Element iLayoutGroup, Element iOldLayoutGroup, boolean required, boolean repeatHelp, boolean hideOnForm, String description) {
		
		CPRIMITIVEOBJECT elementCprim=new CPRIMITIVEOBJECT();
		for(CATTRIBUTE elementAttr1:((CCOMPLEXOBJECT)elementChild).getAttributes()) {		
			if(elementAttr1.getRmAttributeName().equalsIgnoreCase("value")) {
				elementCprim=(CPRIMITIVEOBJECT)elementAttr1.getChildren().get(0);
				createConcept(elementChild.getParent().getRmAttributeName(), elementCprim.getItem().getClass().getSimpleName(), elementConcept, false, description);
				if(iRepeatC!=null) {
					createConceptSetEntry(iRepeatC, elementConcept);
					repeatHelp=true;
				}
				listBoxHelp=false;
				
				createStandardXFormField(elementCprim, required, elementConcept, iXFormUIRepeat, iXFormRepeatInstance, /*iXFormGroupBind, iXFormGroupInstance,*/ iRepeatC);
				if(listBoxHelp==true) {
					createLayoutTextField(elementConcept, iLayoutGroup, iOldLayoutGroup, "ListBox", repeatHelp, false, hideOnForm);
					createMetadataEntry(getFullXPath(elementCprim), "ListBox", elementConcept.getFullName(), elementConcept.getConceptId(), null, repeatHelp);
				} else {
					createLayoutTextField(elementConcept, iLayoutGroup, iOldLayoutGroup, getWidgetType(elementChild.getRmTypeName()), repeatHelp, false, hideOnForm);
					createMetadataEntry(getFullXPath(elementCprim), getWidgetType(elementChild.getRmTypeName()), elementConcept.getFullName(), elementConcept.getConceptId(), null, repeatHelp);
				}
				listBoxHelp=false;
				
			}
			/*if additional attributes except "value" are present*/
			else { 
				int oldPositionLeft=lastPositionLeft;
				lastPositionLeft=lastPositionLeft+10;
				for(COBJECT child: elementAttr1.getChildren()) {
					iterateTemplateNodes(child, iLayoutGroup, iOldLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC, iGroupC);
				}
				lastPositionLeft=oldPositionLeft;
			}
		}
		return repeatHelp;
	}
	
	private boolean mapDvBoolean(COBJECT elementChild, OpenMRSConcept elementConcept, OpenMRSConcept iRepeatC,
			Element iXFormUIRepeat, Element iXFormRepeatInstance, Element iLayoutGroup, Element iOldLayoutGroup,
			boolean required, boolean repeatHelp, boolean hideOnForm, String description) {
		/*mapped to a ListBox with the options "true" and "false"*/
		/*DV_BOOLEAN has only one attribute "value"*/
		CPRIMITIVEOBJECT elementCprim=(CPRIMITIVEOBJECT)((CCOMPLEXOBJECT)elementChild).getAttributes().get(0).getChildren().get(0);
		createConcept(elementChild.getParent().getRmAttributeName(), "Boolean", elementConcept, false, description);
		listBoxHelp=false;
		if(iRepeatC!=null) {
			createConceptSetEntry(iRepeatC, elementConcept);
			repeatHelp=true;
		}
		createStandardXFormField(elementCprim, required, elementConcept, iXFormUIRepeat, iXFormRepeatInstance, /*iXFormGroupBind, iXFormGroupInstance,*/ iRepeatC);
		createLayoutTextField(elementConcept, iLayoutGroup, iOldLayoutGroup, "ListBox", repeatHelp, false, hideOnForm);
		listBoxHelp=false;		
		createMetadataEntry(getFullXPath(elementCprim), "ListBox", elementConcept.getFullName(), elementConcept.getConceptId(), null, repeatHelp);
		return repeatHelp;
	}
	
	private boolean mapDvQuantity(COBJECT elementChild, OpenMRSConcept magnitudeConcept, OpenMRSConcept iRepeatC, OpenMRSConcept iGroupC,
			Element iXFormUIRepeat, Element iXFormRepeatInstance, Element iLayoutGroup, Element iOldLayoutGroup,
			boolean required, boolean repeatHelp, boolean hideOnForm, String description) {
		
		createConcept(elementChild.getParent().getRmAttributeName(), "Numeric", magnitudeConcept, false, description);
		OpenMRSConcept unitConcept=new OpenMRSConcept(); 
		/*unitConcept will be generated afterwards in case a Textbox should be created instead of a ListBox*/
		OpenMRSConcept precisionConcept=new OpenMRSConcept(); 
		createConcept("Precision", "Numeric", precisionConcept, false, "Precision");
		
		List<String[]> units=new ArrayList<String[]>();
		Boolean precisionHelp=false;
		
		//elementChild could be a CCOMPLEXOBJECT or a CDVQUANTITY 
		if(elementChild.getClass().getSimpleName().equalsIgnoreCase("CCOMPLEXOBJECT")) {
			for(CATTRIBUTE cattr:((CCOMPLEXOBJECT)elementChild).getAttributes()) {
				if(cattr.getRmAttributeName().equalsIgnoreCase("magnitude")) {
					magnitudeConcept.setXPath(getFullXPath(cattr.getChildren().get(0)));
					
				} else if(cattr.getRmAttributeName().equalsIgnoreCase("units")) {
					unitConcept.setXPath(getFullXPath(cattr.getChildren().get(0)));
					
				} else if (cattr.getRmAttributeName().equalsIgnoreCase("precision")) {
					precisionHelp=true;
					precisionConcept.setXPath(getFullXPath(cattr.getChildren().get(0)));
				}
			}
			
			/*if elementChild is a CCOMPLEXOBJECT it could have other attributes in addition to magnitude,
			 * units & precision*/
			for(CATTRIBUTE elementAttr:((CCOMPLEXOBJECT)elementChild).getAttributes()) {		
				if(!elementAttr.getRmAttributeName().equalsIgnoreCase("magnitude")
						&& !elementAttr.getRmAttributeName().equalsIgnoreCase("units")
						&& !elementAttr.getRmAttributeName().equalsIgnoreCase("precision")) {
					int oldPositionLeft=lastPositionLeft;
					lastPositionLeft=lastPositionLeft+10;
					for(COBJECT child: elementAttr.getChildren()) {
						iterateTemplateNodes(child, iLayoutGroup, iOldLayoutGroup, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC, iGroupC);
					}
					lastPositionLeft=oldPositionLeft;
				}	
			}
			
			
		} else if(elementChild.getClass().getSimpleName().equalsIgnoreCase("CDVQUANTITY")
				|| elementChild.getClass().getSimpleName().equalsIgnoreCase("CDvQuantity")) { 
			/*the optional property attribute of CDVQUANTITY can include constraints on the units attribute of DV_QUANTITY 
			 * --> won't be considered here*/
			precisionHelp=true;
			magnitudeConcept.setXPath(getFullXPath(elementChild)+"/magnitude");
			unitConcept.setXPath(getFullXPath(elementChild)+"/units");
			precisionConcept.setXPath(getFullXPath(elementChild)+"/precision");
			for(int i=0; i<((CDVQUANTITY)elementChild).getList().size(); i++) {
				units.add(new String[]{((CDVQUANTITY)elementChild).getList().get(i).getUnits(), ((CDVQUANTITY)elementChild).getList().get(i).getUnits()});
			}
		} else {
			System.out.println("ERROR: kein XPath gespeichert");
		}
		
		String unitWidgetType="ListBox";
		if(units.isEmpty()) { /*if no units are defined a TextBox should be created instead of a ListBox*/
			unitWidgetType="TextBox";
			createConcept("Units", "Text", unitConcept, false, "Units");
		} else {
			createConcept("Units", "Coded", unitConcept, false, "Units");
		}
		if(iXFormUIRepeat==null) {
			lastRepeatPositionLeft=0;
		}	
		if(iRepeatC!=null) {
			createConceptSetEntry(iRepeatC, magnitudeConcept);
			createConceptSetEntry(iRepeatC, unitConcept);
			repeatHelp=true;
		}
		
		createLayoutTextField(magnitudeConcept, iLayoutGroup, iOldLayoutGroup, "TextBox", repeatHelp, false, hideOnForm);
		createLayoutTextField(unitConcept, iLayoutGroup, iOldLayoutGroup, unitWidgetType, repeatHelp, true, hideOnForm);
		
		CPRIMITIVEOBJECT cprim=new CPRIMITIVEOBJECT();

		listBoxHelp=false;
		createStandardXFormField(cprim, required, magnitudeConcept, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
		
		if(unitWidgetType.equalsIgnoreCase("ListBox")) { /*if units are defined a ListBox should be created*/
			createListBoxXFormField(unitConcept, required, units, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC, false);	
		} else {
			createStandardXFormField(cprim, required, unitConcept, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
		}
		createMetadataEntry(magnitudeConcept.getXPath(), "TextBox", "Magnitude", magnitudeConcept.getConceptId(), null, repeatHelp);
		createMetadataEntry(unitConcept.getXPath(), unitWidgetType, "Units", unitConcept.getConceptId(), null, repeatHelp);
		if(precisionHelp){
			precisionHelp=true;
			//precision is optional
			createStandardXFormField(cprim, false, precisionConcept, iXFormUIRepeat, iXFormRepeatInstance, iRepeatC);
			createLayoutTextField(precisionConcept, iLayoutGroup, iOldLayoutGroup, "TextBox", repeatHelp, true, hideOnForm);
			if(iRepeatC!=null) {
				createConceptSetEntry(iRepeatC, precisionConcept);
			}
			createMetadataEntry(precisionConcept.getXPath(), "TextBox", "Precision", precisionConcept.getConceptId(), null, repeatHelp);
		}
				
		return repeatHelp;
	}
}

