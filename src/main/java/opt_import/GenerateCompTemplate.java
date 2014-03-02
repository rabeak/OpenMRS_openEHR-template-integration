package opt_import;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.openehr.am.archetype.Archetype;
import org.openehr.jaxb.*;

import se.acode.openehr.parser.*;
import org.openehr.am.serialize.*;

import org.apache.commons.io.filefilter.RegexFileFilter;

import com.rits.cloning.Cloner;

import ehrextract_export.GenerateEHRExtract;

public class GenerateCompTemplate {
	
	private OPERATIONALTEMPLATE template;
	private boolean rootNode = true;
	private boolean inSlot;
	private String goid = ""; /*currently only a sequential number (1,2,3,...), every AT in a slot gets its own number*/
	int goidCounter=1;
	private String atName;	
	private List<ARCHETYPEINTERNALREF> atIntRefNodes=new ArrayList<ARCHETYPEINTERNALREF>();
	
	public static void main(String[] args) throws Exception{
		
		System.out.println("Please enter 'a' if you want to create a form from an operational template, or 'b' if you want to generate an EHR extract for an encounter!");
		
		BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
		String chosen_option = "";
		try {
			chosen_option = console.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (!chosen_option.equalsIgnoreCase("") && chosen_option.equalsIgnoreCase("a"))
		{
			System.out.println("Please enter the path of the operational template or 'respiration' for the Respiration test template!");
			String opt_file_path = "";
			try {
				opt_file_path = console.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (!opt_file_path.equalsIgnoreCase("")) {
				/*Package including the classes created with JAXB from the openEHR release 1.0.2 XML schemas*/
			    JAXBContext jc = JAXBContext.newInstance("org.openehr.jaxb"); 
			    Unmarshaller unmarshaller = jc.createUnmarshaller();
			    Source source;
			    if (opt_file_path.equalsIgnoreCase("respiration"))
			    {
			    	source = new StreamSource("src/main/resources/Templates/openEHR-EHR-OBSERVATION.respiration.v1.template.opt");
			    } else {
			    source = new StreamSource(opt_file_path);
			    }
			    JAXBElement<OPERATIONALTEMPLATE> root = unmarshaller.unmarshal(source, OPERATIONALTEMPLATE.class);
			    
			    OPERATIONALTEMPLATE optemplate = root.getValue();
			    	    
			    GenerateCompTemplate gCT=new GenerateCompTemplate();	    	    
			    gCT.getComprehensiveTemplate(optemplate);
			    
			    GenerateXForm gXF=new GenerateXForm(gCT.template);
			    gXF.createOpenMRSXForm();
			} else {
				System.out.println("No path entered!");
			}
			
		} else if (!chosen_option.equalsIgnoreCase("") && chosen_option.equalsIgnoreCase("b")) {
			System.out.println("Please enter the OpenMRS encounter ID (encounter data must be entered via a form generated from an openEHR operational template)!");
			String encounter_id = "";
			try {
				encounter_id = console.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (!encounter_id.equalsIgnoreCase("")) {
			GenerateEHRExtract gEHRExtract=new GenerateEHRExtract();
		    gEHRExtract.generateEHRExtract(Integer.parseInt(encounter_id)); // OpenMRS EncounterId
			} else {
				System.out.println("No encounter ID entered!");
			}
		} else {
			System.out.println("No valid option was entered!");
		}
	}
	
	public OPERATIONALTEMPLATE getComprehensiveTemplate(OPERATIONALTEMPLATE opt) {
		this.template=opt;
		this.atName=template.getDefinition().getArchetypeId().getValue();
		/*additional mandatory RM elements are added*/
		template.setDefinition((CARCHETYPEROOT)addRmClasses(template.getDefinition()));
		template.loadPathNodeMap(template.getDefinition()); /*all paths and according nodes (COBJECTs) are saved in this map*/
		loadATIntRefNodes(); /*possible AT_INTERNAL_REF nodes are resolved*/
		
		return template;
	}
	
	private CCOMPLEXOBJECT addRmClasses(CCOMPLEXOBJECT ccomplexobj) {
		 
		if (this.inSlot) { /*the node id needs to be adapted if the node occurs in an embedded template*/

			if (ccomplexobj.getNodeId() != null && !ccomplexobj.getNodeId().equalsIgnoreCase("") && !ccomplexobj.getNodeId().equalsIgnoreCase(" ") && !ccomplexobj.getNodeId().contains("at"+this.goid+".")) {
				ccomplexobj.setNodeId(ccomplexobj.getNodeId().substring(0, 2) + this.goid + "." + ccomplexobj.getNodeId().substring(2));
			}
		}
		
		List<CATTRIBUTE> cattributes = new ArrayList<CATTRIBUTE>();

		for (String[] RMElement : getRMElements(ccomplexobj.getRmTypeName())) {
			
			/* all mandatory RM elements are included in the RMElement array
			* if element doesn't exist, its created*/
			boolean elementExists = false;

			if (ccomplexobj.getAttributes() != null) {

				for (CATTRIBUTE cattribute : ccomplexobj.getAttributes()) {
					if (cattribute.getRmAttributeName().equalsIgnoreCase(RMElement[0])) {
						cattribute.setParent(ccomplexobj);
						/*if a new name has been defined for this node in the template, its added to the term definitions of the according COBJECT
						 * --> no good solution*/
						if(cattribute.getRmAttributeName().equalsIgnoreCase("name")) {
							checkForNewName(cattribute);						
						}
						cattributes.add(addRMattributes(cattribute));
						elementExists = true;

					}
				}
			}
			/*element doesn't exist and is created
			 * if the element is abstract or a subclass of LOCATABLE, a default object can't be created*/
			boolean abstractElement=checkIfAbstract(RMElement[1]);
			boolean childOfLocatable=checkIfChildOfLocatable(RMElement[1]);
								
			if (!abstractElement && !childOfLocatable && !elementExists && Integer.parseInt(RMElement[2]) > 0 ) {				
				IntervalOfInteger required=new IntervalOfInteger();
				required.setLower(1);
				required.setUpper(1);
				IntervalOfInteger optional=new IntervalOfInteger();
				optional.setLower(0);
				optional.setUpper(1);
				IntervalOfInteger existence=new IntervalOfInteger();
				
				if (RMElement[2].equalsIgnoreCase("1")) {
					existence = required;
				} else {
					existence = optional;
				}

				try {
					Integer upper;
					if (RMElement[3].equalsIgnoreCase("*")) {
						upper = null;
					} else {
						upper = Integer.parseInt(RMElement[3]);
					}
					
					/*simple data types*/
					if (RMElement[1].equalsIgnoreCase("string") || RMElement[1].equalsIgnoreCase("boolean") || RMElement[1].equalsIgnoreCase("real") || RMElement[1].equalsIgnoreCase("integer") || RMElement[1].equalsIgnoreCase("character")) {
						
						CATTRIBUTE newCattribute = new CSINGLEATTRIBUTE();
						newCattribute.setExistence(existence);
						newCattribute.setRmAttributeName(RMElement[0]);
						newCattribute.setParent(ccomplexobj);
						newCattribute.setXPath("/" + newCattribute.getRmAttributeName());
						newCattribute.setPath("/" + newCattribute.getRmAttributeName());
						
						CPRIMITIVEOBJECT newCprim = new CPRIMITIVEOBJECT();
						newCprim.setOccurrences(required);
						newCprim.setParent(newCattribute);
						newCprim.setNodeId(null);

						CPRIMITIVE newVal=new CSTRING();
						if(RMElement[0].equalsIgnoreCase("value")) { 
							/*in case of a value attribute the data type is adapted to the DV-type
							 * e.g. in case of a DV_DATE a C_DATE is created*/
							if(ccomplexobj.getRmTypeName().equalsIgnoreCase("DV_TEXT")) {
								newVal=new CSTRING();
								newCprim.setPath("");
								newCprim.setXPath("");
							} else if(ccomplexobj.getRmTypeName().equalsIgnoreCase("DV_DATE")) {
								newVal=new CDATE();
								newCprim.setPath("");
								/*needed for the generation of the EHR extract (will be replaced by "Iso8601Date" later)*/
								newCprim.setXPath("[@xsi:type='DATE']"); 
							} else if(ccomplexobj.getRmTypeName().equalsIgnoreCase("DV_DATE_TIME")) {
								newVal=new CDATETIME();
								newCprim.setPath("");
								newCprim.setXPath("[@xsi:type='DATE_TIME']");
							} else if(ccomplexobj.getRmTypeName().equalsIgnoreCase("DV_TIME")) {
								newVal=new CTIME();
								newCprim.setPath("");
								newCprim.setXPath("[@xsi:type='TIME']");
							} else if(ccomplexobj.getRmTypeName().equalsIgnoreCase("DV_BOOLEAN")) {
								newVal=new CBOOLEAN();
								newCprim.setPath("");
								newCprim.setXPath("");
							}else if(ccomplexobj.getRmTypeName().equalsIgnoreCase("DV_DURATION")) {
								newVal=new CDURATION();
								newCprim.setPath("");
								newCprim.setXPath("");
							} else {
								newVal=new CSTRING();
								newCprim.setPath("");
								newCprim.setXPath("");
							}
						} else { /*else type depends on the specified data type*/
							if(RMElement[1].equalsIgnoreCase("string") || RMElement[1].equalsIgnoreCase("character")) {
								newVal=new CSTRING();
								newCprim.setPath("");
								newCprim.setXPath("");
							} else if(RMElement[1].equalsIgnoreCase("boolean")) {
								newVal=new CBOOLEAN();
								newCprim.setPath("");
								newCprim.setXPath("");
							} else if(RMElement[1].equalsIgnoreCase("real")) {
								newVal=new CREAL();
								newCprim.setPath("");
								newCprim.setXPath("");
							} else if(RMElement[1].equalsIgnoreCase("integer")) {
								newVal=new CINTEGER();
								newCprim.setPath("");
								newCprim.setXPath("");
							} else {
								newVal=new CSTRING();
								newCprim.setPath("");
								newCprim.setXPath("");
							}
						}
						
						if (RMElement[0].equalsIgnoreCase("archetype_node_id")) {	
							newVal = new CSTRING();

							/*if the node has a node id, its added to this attribute*/
							if (ccomplexobj.getNodeId() != null) {						
								((CSTRING)newVal).getList().add(ccomplexobj.getNodeId().replace(this.goid + ".", ""));
								((CSTRING)newVal).setPattern(null);
							} 
							/*in case of a root node the archetype id is added (see common IM)
							 * if a node id is avaiable too, both are used*/
							if (this.rootNode == true) { 
								String newNodeId="";
								if(ccomplexobj.getNodeId()!=null) {
									newNodeId=atName+"/"+ccomplexobj.getNodeId().replace(this.goid + ".", "");
								} else {
									newNodeId=atName;
								}
								this.rootNode = false;
								((CSTRING)newVal).getList().add(newNodeId);
								((CSTRING)newVal).setPattern(null);
							}
						}

						newCprim.setItem(newVal);
						newCattribute.getChildren().add(newCprim);
						cattributes.add(newCattribute);
					}
					/*complex data types*/
					else {
						CATTRIBUTE newCattribute = new CSINGLEATTRIBUTE();
						newCattribute.setExistence(existence);
						newCattribute.setRmAttributeName(RMElement[0]);
						newCattribute.setParent(ccomplexobj);

						IntervalOfInteger interval=new IntervalOfInteger();
						interval.setLower(Integer.parseInt(RMElement[2]));
						optional.setUpper(upper);
						
						/*if the attribute is of type CODE_PHRASE code sets need to be included where necessary
						 * if the attribute belongs to a node of type DV_CODED_TEXT, a C_COMPLEX_OBJECT is created*/
						if(RMElement[1].equalsIgnoreCase("CODE_PHRASE") && !ccomplexobj.getRmTypeName().equalsIgnoreCase("DV_CODED_TEXT")) { 
							CCODEPHRASE newCcode=new CCODEPHRASE();
							TERMINOLOGYID termId=new TERMINOLOGYID();
							termId.setValue(getCodeSetFromAttributeName(RMElement[0]));
							newCcode.setTerminologyId(termId); /*the name of the code set is used as terminologyID*/
							/*the codes of the code set are selected in the method GenerateXForm*/
							newCcode.setOccurrences(interval);
							newCcode.setParent(newCattribute);
							newCcode.setRmTypeName(RMElement[1]);
							newCcode.setXPath("[@xsi:type='"+RMElement[1]+"']");
							newCcode.setPath("");
							newCattribute.getChildren().add(newCcode); 
						} else {
							CCOMPLEXOBJECT newCcomplex = new CCOMPLEXOBJECT();
							newCcomplex.setXPath("[@xsi:type='"+RMElement[1]+"']");
							newCcomplex.setPath("");
							newCcomplex.setRmTypeName(RMElement[1]);
							newCcomplex.setParent(newCattribute);
							newCcomplex.setOccurrences(interval);
							newCattribute.getChildren().add(addRmClasses(newCcomplex));	
						}				
						
						newCattribute.setXPath("/" + newCattribute.getRmAttributeName());
						newCattribute.setPath("/" + newCattribute.getRmAttributeName());
						cattributes.add(newCattribute);
					}
				} catch (Exception e) {
					System.out.println("Error when trying to create element " + RMElement[0]);
					e.printStackTrace();
				}
			}
		}
		/*all attributes from cattributes are added to ccomplexobj as the attributes have to be available in the right order*/
		ccomplexobj.getAttributes().clear();
		for(CATTRIBUTE cattr: cattributes) {
			ccomplexobj.getAttributes().add(cattr);
		}
			
		/*running through all attributes and checking if a parent object is defined
		 * if not, the attribute couldn't be found in the getRMElements method
		 * the parent is added then and getRMattributes called so that the tree can be processed here too*/
		if (ccomplexobj.getAttributes() != null) {

			for (CATTRIBUTE cattribute : ccomplexobj.getAttributes()) {
				if (cattribute.getParent()==null) {
					cattribute.setParent(ccomplexobj);
					cattribute=addRMattributes(cattribute);
				}
			}
		}
	
		CATTRIBUTE at_id=new CSINGLEATTRIBUTE();
		at_id=null;
        for (CATTRIBUTE attribute : ccomplexobj.getAttributes()) {
        	if (attribute.getRmAttributeName().equals("archetype_node_id")) {
        		at_id=attribute;
        	}
        }
		String xpath = "";
		String path="";
		if(ccomplexobj instanceof CARCHETYPEROOT && ccomplexobj.getNodeId() != null && at_id != null) {
			xpath = "[@archetype_node_id='" +atName + "' and @xsi:type='" + ccomplexobj.getRmTypeName() + "']";
			path="["+atName+"]";
		} else if (ccomplexobj.getNodeId() != null && at_id != null) {
			String atId = ((CSTRING) ((CPRIMITIVEOBJECT) at_id.getChildren().get(0)).getItem()).getList().get(0);
			xpath = "[@archetype_node_id='" + atId + "' and @xsi:type='" + ccomplexobj.getRmTypeName() + "']";
			path="["+atId+"]";
		} else if(ccomplexobj.getRmTypeName()!=null && !ccomplexobj.getRmTypeName().equalsIgnoreCase("") && !ccomplexobj.getRmTypeName().equalsIgnoreCase(" ")) {
			xpath = "[@xsi:type='" + ccomplexobj.getRmTypeName() + "']";
		} 

		ccomplexobj.setXPath(xpath);
		ccomplexobj.setPath(path);
		return ccomplexobj;
	}
	
	private CATTRIBUTE addRMattributes(CATTRIBUTE cattribute) {
		cattribute.setXPath("/" + cattribute.getRmAttributeName());
		cattribute.setPath("/" + cattribute.getRmAttributeName());
		List<COBJECT> children=new ArrayList<COBJECT>();
		if (cattribute.getChildren() != null) {
			for (COBJECT cobject : cattribute.getChildren()) {
				cobject.setParent(cattribute);
				
				if (cobject instanceof CARCHETYPEROOT) {
					/*adapting the paths and setting inSlot to true*/
					Boolean slotHelper=false;
					Boolean helperRootNode=this.rootNode; 
					String goidHelper = this.goid;
					String atNameHelper = this.atName;
					this.rootNode=true; /*because its a root node of an AT*/
					if (this.inSlot == true) {
						slotHelper = true;
					}
					this.inSlot = true;
					this.goid=String.valueOf(goidCounter+1);
					this.goidCounter=this.goidCounter+1;
					this.atName=((CARCHETYPEROOT)cobject).getArchetypeId().getValue();
					/*the term definitions are added to the term definitions of the template and the node ids are changed*/
					for(ARCHETYPETERM term:((CARCHETYPEROOT)cobject).getTermDefinitions()) {
						term.setCode(term.getCode().substring(0,2)+this.goid+"."+term.getCode().substring(2));
						template.getDefinition().getTermDefinitions().add(term);
					}
					
					children.add(addRmClasses((CCOMPLEXOBJECT) cobject));
					if (slotHelper == false) {
						this.inSlot = false;
					}
					this.rootNode = helperRootNode;
					this.goid = goidHelper;
					this.atName = atNameHelper;
					
				} else if (cobject instanceof CCOMPLEXOBJECT) {
					children.add(addRmClasses((CCOMPLEXOBJECT) cobject));

				} else if (cobject instanceof ARCHETYPESLOT) {
					
					System.out.println("An archetype slot has been found!");
					String chosen_at = "j";
					/*if the slot is optional, the user is asked whether the slot should be filled*/
					if(((ARCHETYPESLOT)cobject).getOccurrences().getLower()==0) {
						System.out.println("Do you want to add an archetype? y/n");					
						BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
						try {
							chosen_at = console.readLine();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					if (chosen_at.equalsIgnoreCase("y")) {
						CARCHETYPEROOT at_root=fillATSlot((ARCHETYPESLOT)cobject);
						
						if(at_root!=null) {
							at_root.setParent(cattribute);
							/*the paths are adapted and inSlot is set to true*/
							Boolean slotHelper=false;
							Boolean helperFirst=this.rootNode; 
							String goidHelper = this.goid;
							String atNameHelper = this.atName;
							this.rootNode=true; 
							if (this.inSlot == true) {
								slotHelper = true;
							}
							this.inSlot = true;
							this.goid=String.valueOf(goidCounter+1);
							this.goidCounter=this.goidCounter+1;
							this.atName=at_root.getArchetypeId().getValue();
							/*the term definitions are added to the term definitions of the template and the node ids are changed*/
							for(ARCHETYPETERM term:(at_root).getTermDefinitions()) {
								term.setCode(term.getCode().substring(0,2)+this.goid+"."+term.getCode().substring(2));
								template.getDefinition().getTermDefinitions().add(term);
							}
							at_root=(CARCHETYPEROOT)addRmClasses((CCOMPLEXOBJECT)at_root);
							children.add(at_root);
							if (slotHelper == false) {
								this.inSlot = false;
							}
							this.rootNode = helperFirst;
							this.goid = goidHelper;
							this.atName = atNameHelper;
							
						} else { 
							/*no suitable archetype could be found for the slot
							 * slot is deleted as it is not added to children*/
						}
					} 
													
				} else if (cobject instanceof CPRIMITIVEOBJECT) {
					if(cobject.getRmTypeName().equalsIgnoreCase("DATE_TIME") 
							|| cobject.getRmTypeName().equalsIgnoreCase("DATE")
							|| cobject.getRmTypeName().equalsIgnoreCase("TIME")
							|| cobject.getRmTypeName().equalsIgnoreCase("DURATION")) {					
						cobject.setXPath("[@xsi:type='"+cobject.getRmTypeName()+"']");
					} else {
						cobject.setXPath("");
					}
					cobject.setPath("");
					children.add((CPRIMITIVEOBJECT)cobject);
					
				} else if (cobject instanceof CCODEPHRASE) {
					cobject.setXPath("[@xsi:type='"+cobject.getRmTypeName()+"']");
					cobject.setPath("");
					
					/*in case of an embedded archetype the goid is added to the codes
					 * (only in case of at-codes (terminology-id=local))
					 * this is necessary as also in the term definitions the goid is added to the codes in order to know 
					 * to which archetype the codes belong*/
					if (this.inSlot && ((CCODEPHRASE) cobject).getTerminologyId().getValue().equalsIgnoreCase("local")) {
						for (int i = 0; i < ((CCODEPHRASE) cobject).getCodeList().size(); i++) {
							String code = ((CCODEPHRASE) cobject).getCodeList().get(i);
							code = code.substring(0, 2) + this.goid + "." + code.substring(2);
							((CCODEPHRASE) cobject).getCodeList().set(i, code);
						}
					}
					children.add((CCODEPHRASE)cobject);
					
				} else if (cobject instanceof CDVORDINAL) {
					cobject.setXPath("[@xsi:type='"+cobject.getRmTypeName()+"']");
					cobject.setPath("");
					children.add((CDVORDINAL)cobject);
					
				} else if (cobject instanceof CDVQUANTITY) {
					cobject.setXPath("[@xsi:type='"+cobject.getRmTypeName()+"']");
					cobject.setPath("");
					children.add((CDVQUANTITY)cobject);
					
				} else if (cobject instanceof CDVSTATE) {
					cobject.setXPath("[@xsi:type='"+cobject.getRmTypeName()+"']");
					cobject.setPath("");
					children.add((CDVSTATE)cobject);
				}
				
				else if (cobject instanceof CONSTRAINTREF) {
					/*shouldn't occur in OPTs, but can occur in slot archetypes which have been added after the generation of the OPT*/
					cobject.setXPath("[@xsi:type='"+cobject.getRmTypeName()+"']");	
					cobject.setPath("");
					children.add((CONSTRAINTREF)cobject);
				}

				else if (cobject instanceof ARCHETYPEINTERNALREF) {
					/*not necessary for OPTs as all AT_INTERNAL_REFs are replaced by the according referenced nodes 
					 * during the creation of the OPT 
					 * only necessary in case of ATs which are added to slots afterwards
					 * the COBJECT is added to the atIntRefNodes list and replaced by the right node later
					 * when for all nodes the missing attributes have been added, as only then all paths are known*/
					cobject.setXPath("[@xsi:type='"+cobject.getRmTypeName()+"']");
					cobject.setPath("");
					String targetPath=((ARCHETYPEINTERNALREF) cobject).getTargetPath();
					((ARCHETYPEINTERNALREF) cobject).setTargetPath("["+atName+"]"+targetPath);
					children.add((ARCHETYPEINTERNALREF)cobject);
					System.out.println("Archetypeinternalref");
					atIntRefNodes.add((ARCHETYPEINTERNALREF)cobject);

				} else {
					cobject.setXPath("[@xsi:type='"+cobject.getRmTypeName()+"']");
					cobject.setPath("");
					children.add(cobject);
					System.out.println(cobject.getClass().getName());
				}
			}	
			cattribute.getChildren().clear();
			for(COBJECT child:children) {
				cattribute.getChildren().add(child);
			}
		}		
		else { /*node has no children*/
			IntervalOfInteger required=new IntervalOfInteger();
			required.setLower(1);
			required.setUpper(1);
			/*type of the attribute is selected in order to be able to add children and mandatory attributes to it*/
			for (String[] RMElement : getRMElements(cattribute.getParent().getRmTypeName())) {
				if (RMElement[0].equalsIgnoreCase(cattribute.getRmAttributeName())) {
					if (getRMElements(RMElement[1]).isEmpty()) {
						/*if no type is defined for the attribute*/					
						cattribute.setXPath("/" + cattribute.getRmAttributeName());
						cattribute.setPath("/" + cattribute.getRmAttributeName());
						CPRIMITIVEOBJECT cPrim = new CPRIMITIVEOBJECT();
						cPrim.setOccurrences(required);
						cPrim.setNodeId(null);
						cPrim.setParent(cattribute);
						cPrim.setXPath("");
						cPrim.setPath("");
						CSTRING item=new CSTRING();
						item.setPattern(".+"); //TODO
						cPrim.setItem(item);
						cattribute.getChildren().add(cPrim);
						break;
					} else {
						COBJECT cobject1 = new CCOMPLEXOBJECT();
						cobject1.setRmTypeName(RMElement[1]);
						cobject1.setPath("");
						cobject1.setXPath("[@xsi:type='"+cobject1.getRmTypeName()+"']");
						cobject1.setOccurrences(required);
						cobject1.setNodeId(null);
						cobject1.setParent(cattribute);
						cattribute.setXPath("/" + cattribute.getRmAttributeName());
						cattribute.setPath("/" + cattribute.getRmAttributeName());
						cattribute.getChildren().add(addRmClasses((CCOMPLEXOBJECT) cobject1));
					}
				}
			}
		}		
		return cattribute;
	}
	
	private CARCHETYPEROOT fillATSlot(ARCHETYPESLOT slot_cobject) {	
		
		List<ASSERTION> assertions = slot_cobject.getIncludes();
		CARCHETYPEROOT inclAT = new CARCHETYPEROOT(); /*instead of the slot a CARCHETYPEROOT element is added*/
		
		for (ASSERTION assertion : assertions) {
			if (assertion.getExpression() instanceof EXPRBINARYOPERATOR) {
				EXPRBINARYOPERATOR expBinaryOperator = (EXPRBINARYOPERATOR) assertion.getExpression();

				if (expBinaryOperator.getRightOperand() instanceof EXPRLEAF) {
					String slotPattern = ((CSTRING) ((EXPRLEAF) expBinaryOperator.getRightOperand()).getItem()).getPattern().toString();
					
					String pattern = "(\\S)(\\\\)([\\.])";
					slotPattern=slotPattern.replaceAll(pattern, "$1$3");
					String[] splitAtIds = slotPattern.split("\\|"); /*multiple AT ids are separated by a pipe*/
					
					List<String> splitAtNames=new ArrayList<String>();
					for(String atId:splitAtIds) {
						/*here the RM type name of the slot defines which ATs can be chosen*/
						if(atId.equalsIgnoreCase(".*")) { 
							splitAtNames.add("openEHR-EHR-"+slot_cobject.getRmTypeName()+".*.adl");
						} else {
							splitAtNames.add(atId+".adl");
						}
					}
					
					ARCHETYPE at = getArchetype(splitAtNames);

					if(at==null) {
						inclAT=null;
						System.out.println("No Archetype found for Slot!");
					} else {
						/*definition section of the AT, which is added to the slot, is converted into an AT_ROOT*/
						CCOMPLEXOBJECT cobject_AT=at.getDefinition();
						inclAT.setNodeId(cobject_AT.getNodeId());
						inclAT.setOccurrences(cobject_AT.getOccurrences());
						inclAT.setParent(cobject_AT.getParent());
						inclAT.setRmTypeName(cobject_AT.getRmTypeName());
						for(CATTRIBUTE cattr_AT:cobject_AT.getAttributes()) {
							inclAT.getAttributes().add(cattr_AT);
						}
						inclAT.setArchetypeId(at.getArchetypeId());
						
						/*term definitions of the slot AT are added to the CARCHETYPEROOT*/
						List<CodeDefinitionSet> at_terms = at.getOntology().getTermDefinitions();
						for (CodeDefinitionSet codeDefSet : at_terms) {
							for(ARCHETYPETERM term:codeDefSet.getItems()) {
								inclAT.getTermDefinitions().add(term);
							}
						}
					}
				} else if (expBinaryOperator.getRightOperand() instanceof EXPROPERATOR) {
					System.out.println("ExpressionOperator");
				} else {
					// unknown ExpressionItem
					System.out.println("unknown ExpressionItem");
				}
			}
		}
		return inclAT;
	}
	
	
	public static List<String[]> getRMElements(String rmClassNameUnparsed) {
		List<String[]> rmElements = new ArrayList<String[]>();
		/*rmClassName is parsed in case of a generic type, e.g. DV_INTERVAL<DV_COUNT>*/
		String pattern = "(\\S+)(<(\\S*)>)";
		String type=rmClassNameUnparsed.replaceAll(pattern, "$3");
		String rmClassName=rmClassNameUnparsed.replaceAll(pattern, "$1");

		if (rmClassName.equalsIgnoreCase("FOLDER")) {			
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "folders", "FOLDER", "0", "1" });
			rmElements.add(new String[] { "items", "OBJECT_REF", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("COMPOSITION")) {						
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "context", "EVENT_CONTEXT", "0", "1" });
			rmElements.add(new String[] { "composer", "PARTY_PROXY", "1", "1" });
			rmElements.add(new String[] { "category", "DV_CODED_TEXT", "1", "1" });		
			rmElements.add(new String[] { "language", "CODE_PHRASE", "1", "1" });			
			rmElements.add(new String[] { "territory", "CODE_PHRASE", "1", "1" });
			rmElements.add(new String[] { "content", "CONTENT_ITEM", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("ADMIN_ENTRY")) {			
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "language", "CODE_PHRASE", "1", "1" });
			rmElements.add(new String[] { "encoding", "CODE_PHRASE", "1", "1" });
			rmElements.add(new String[] { "subject", "PARTY_PROXY", "1", "1" });		
			rmElements.add(new String[] { "provider", "PARTY_PROXY", "0", "1" });
			rmElements.add(new String[] { "other_participations", "PARTICIPATION", "0", "1" });
			rmElements.add(new String[] { "workflow_id", "OBJECT_REF", "0", "1" });
			
			rmElements.add(new String[] { "data", "ITEM_STRUCTURE", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("ACTION")) {
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "language", "CODE_PHRASE", "1", "1" });
			rmElements.add(new String[] { "encoding", "CODE_PHRASE", "1", "1" });
			rmElements.add(new String[] { "subject", "PARTY_PROXY", "1", "1" });		
			rmElements.add(new String[] { "provider", "PARTY_PROXY", "0", "1" });
			rmElements.add(new String[] { "other_participations", "PARTICIPATION", "0", "1" });
			rmElements.add(new String[] { "workflow_id", "OBJECT_REF", "0", "1" });
			
			rmElements.add(new String[] { "protocol", "ITEM_STRUCTURE", "0", "1" });
			rmElements.add(new String[] { "guideline_id", "OBJECT_REF", "0", "1" });
			
			rmElements.add(new String[] { "time", "DV_DATE_TIME", "1", "1" });
			rmElements.add(new String[] { "description", "ITEM_STRUCTURE", "1", "1" });
			rmElements.add(new String[] { "ism_transition", "ISM_TRANSITION", "1", "1" });
			rmElements.add(new String[] { "instruction_details", "INSTRUCTION_DETAILS", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("EVALUATION")) {
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "language", "CODE_PHRASE", "1", "1" });
			rmElements.add(new String[] { "encoding", "CODE_PHRASE", "1", "1" });
			rmElements.add(new String[] { "subject", "PARTY_PROXY", "1", "1" });		
			rmElements.add(new String[] { "provider", "PARTY_PROXY", "0", "1" });
			rmElements.add(new String[] { "other_participations", "PARTICIPATION", "0", "1" });
			rmElements.add(new String[] { "workflow_id", "OBJECT_REF", "0", "1" });
			
			rmElements.add(new String[] { "protocol", "ITEM_STRUCTURE", "0", "1" });
			rmElements.add(new String[] { "guideline_id", "OBJECT_REF", "0", "1" });
			
			rmElements.add(new String[] { "data", "ITEM_STRUCTURE", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("INSTRUCTION")) {
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "language", "CODE_PHRASE", "1", "1" });
			rmElements.add(new String[] { "encoding", "CODE_PHRASE", "1", "1" });
			rmElements.add(new String[] { "subject", "PARTY_PROXY", "1", "1" });		
			rmElements.add(new String[] { "provider", "PARTY_PROXY", "0", "1" });
			rmElements.add(new String[] { "other_participations", "PARTICIPATION", "0", "1" });
			rmElements.add(new String[] { "workflow_id", "OBJECT_REF", "0", "1" });
			
			rmElements.add(new String[] { "protocol", "ITEM_STRUCTURE", "0", "1" });
			rmElements.add(new String[] { "guideline_id", "OBJECT_REF", "0", "1" });
			
			rmElements.add(new String[] { "narrative", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "activities", "ACTIVITY", "0", "1" });
			rmElements.add(new String[] { "expiry_time", "DV_DATE_TIME", "0", "1" });
			rmElements.add(new String[] { "wf_definition", "DV_PARSABLE", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("OBSERVATION")) {
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "language", "CODE_PHRASE", "1", "1" });
			rmElements.add(new String[] { "encoding", "CODE_PHRASE", "1", "1" });
			rmElements.add(new String[] { "subject", "PARTY_PROXY", "1", "1" });		
			rmElements.add(new String[] { "provider", "PARTY_PROXY", "0", "1" });
			rmElements.add(new String[] { "other_participations", "PARTICIPATION", "0", "1" });
			rmElements.add(new String[] { "workflow_id", "OBJECT_REF", "0", "1" });
			
			rmElements.add(new String[] { "protocol", "ITEM_STRUCTURE", "0", "1" });
			rmElements.add(new String[] { "guideline_id", "OBJECT_REF", "0", "1" });
			
			rmElements.add(new String[] { "data", "HISTORY<ITEM_STRUCTURE>", "1", "1" });
			rmElements.add(new String[] { "state", "HISTORY<ITEM_STRUCTURE>", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("SECTION")) {
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "items", "CONTENT_ITEM", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("GENERIC_ENTRY")) {						
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "data", "ITEM_TREE", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("ACTIVITY")) {						
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "description", "ITEM_STRUCTURE", "1", "1" });
			rmElements.add(new String[] { "timing", "DV_PARSABLE", "1", "1" });
			rmElements.add(new String[] { "action_archetype_id", "String", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("HISTORY")) {						
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "origin", "DV_DATE_TIME", "1", "1" });
			rmElements.add(new String[] { "events", "EVENT<"+type+">", "0", "1" });
			//rmElements.add(new String[] { "events", "ITEM_STRUCTURE", "0", "1" });
			rmElements.add(new String[] { "period", "DV_DURATION", "0", "1" });
			rmElements.add(new String[] { "duration", "DV_DURATION", "0", "1" });
			rmElements.add(new String[] { "summary", "ITEM_STRUCTURE", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("ITEM_LIST")) {		
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "items", "ELEMENT", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("ITEM_SINGLE")) {		
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "item", "ELEMENT", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("ITEM_TABLE")) {					
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "rows", "CLUSTER", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("ITEM_TREE")) {		
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "items", "ITEM", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("CLUSTER")) {
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "items", "ITEM", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("ELEMENT")) {		
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "value", "DATA_VALUE", "1", "1" });
			rmElements.add(new String[] { "null_flavor", "DV_CODED_TEXT", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("ADDRESS")) {		
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "details", "ITEM_STRUCTURE", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("CAPABILITY")) {	
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "time_validity", "DV_INTERVAL", "0", "1" });
			rmElements.add(new String[] { "credentials", "ITEM_STRUCTURE", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("CONTACT")) {
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "time_validity", "DV_INTERVAL", "0", "1" });
			rmElements.add(new String[] { "addresses", "ADDRESS", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("AGENT")) {				
			rmElements.add(new String[] { "uid", "HIER_OBJECT_ID", "1", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "identities", "PARTY_IDENTITY", "1", "1" });
			rmElements.add(new String[] { "contacts", "CONTACT", "0", "1" });
			rmElements.add(new String[] { "relationships", "PARTY_RELATIONSHIP", "0", "1" });
			rmElements.add(new String[] { "reverse_relationships", "LOCATABLE_REF", "0", "1" });
			rmElements.add(new String[] { "details", "ITEM_STRUCTURE", "0", "1" });
			
			rmElements.add(new String[] { "roles", "PARTY_REF", "0", "1" });
			rmElements.add(new String[] { "languages", "DV_TEXT", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("GROUP")) {
			rmElements.add(new String[] { "uid", "HIER_OBJECT_ID", "1", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "identities", "PARTY_IDENTITY", "1", "1" });
			rmElements.add(new String[] { "contacts", "CONTACT", "0", "1" });
			rmElements.add(new String[] { "relationships", "PARTY_RELATIONSHIP", "0", "1" });
			rmElements.add(new String[] { "reverse_relationships", "LOCATABLE_REF", "0", "1" });
			rmElements.add(new String[] { "details", "ITEM_STRUCTURE", "0", "1" });
			
			rmElements.add(new String[] { "roles", "PARTY_REF", "0", "1" });
			rmElements.add(new String[] { "languages", "DV_TEXT", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("ORGANISATION")) {		
			rmElements.add(new String[] { "uid", "HIER_OBJECT_ID", "1", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "identities", "PARTY_IDENTITY", "1", "1" });
			rmElements.add(new String[] { "contacts", "CONTACT", "0", "1" });
			rmElements.add(new String[] { "relationships", "PARTY_RELATIONSHIP", "0", "1" });
			rmElements.add(new String[] { "reverse_relationships", "LOCATABLE_REF", "0", "1" });
			rmElements.add(new String[] { "details", "ITEM_STRUCTURE", "0", "1" });
			
			rmElements.add(new String[] { "roles", "PARTY_REF", "0", "1" });
			rmElements.add(new String[] { "languages", "DV_TEXT", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("PERSON")) {	
			rmElements.add(new String[] { "uid", "HIER_OBJECT_ID", "1", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "identities", "PARTY_IDENTITY", "1", "1" });
			rmElements.add(new String[] { "contacts", "CONTACT", "0", "1" });
			rmElements.add(new String[] { "relationships", "PARTY_RELATIONSHIP", "0", "1" });
			rmElements.add(new String[] { "reverse_relationships", "LOCATABLE_REF", "0", "1" });
			rmElements.add(new String[] { "details", "ITEM_STRUCTURE", "0", "1" });
					
			rmElements.add(new String[] { "roles", "PARTY_REF", "0", "1" });
			rmElements.add(new String[] { "languages", "DV_TEXT", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("ROLE")) {			
			rmElements.add(new String[] { "uid", "HIER_OBJECT_ID", "1", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "identities", "PARTY_IDENTITY", "1", "1" });
			rmElements.add(new String[] { "contacts", "CONTACT", "0", "1" });
			rmElements.add(new String[] { "relationships", "PARTY_RELATIONSHIP", "0", "1" });
			rmElements.add(new String[] { "reverse_relationships", "LOCATABLE_REF", "0", "1" });
			rmElements.add(new String[] { "details", "ITEM_STRUCTURE", "0", "1" });
			
			rmElements.add(new String[] { "capabilities", "CAPABILITY", "0", "1" });
			rmElements.add(new String[] { "time_validity", "DV_INTERVAL", "0", "1" });
			rmElements.add(new String[] { "performer", "PARTY_REF", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("PARTY_IDENTITY")) {			
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "details", "ITEM_STRUCTURE", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("PARTY_RELATIONSHIP")) {						
			rmElements.add(new String[] { "uid", "HIER_OBJECT_ID", "1", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DvText", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "Archetyped", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FeederAudit", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "Pathable", "0", "1" });
			
			rmElements.add(new String[] { "details", "ITEM_STRUCTURE", "0", "1" });
			rmElements.add(new String[] { "time_validity", "DV_INTERVAL", "0", "1" });
			rmElements.add(new String[] { "source", "PARTY_REF", "1", "1" });
			rmElements.add(new String[] { "target", "PARTY_REF", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("EHR_ACCESS")) {		
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "settings", "ACCESS_CONTROL_SETTINGS", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("EHR_STATUS")) {		
			rmElements.add(new String[] { "uid", "UIDBasedID", "1", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DvText", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "Archetyped", "1", "1" });
			rmElements.add(new String[] { "feeder_audit", "FeederAudit", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "Pathable", "0", "1" });
			
			rmElements.add(new String[] { "subject", "PARTY_SELF", "1", "1" });
			rmElements.add(new String[] { "is_queryable", "boolean", "1", "1" });
			rmElements.add(new String[] { "is_modifiable", "boolean", "1", "1" });
			rmElements.add(new String[] { "other_details", "ITEM_STRUCTURE", "0", "1" });
		}
				
		if (rmClassName.equalsIgnoreCase("DV_TEXT")) {	
			rmElements.add(new String[] { "value", "String", "1", "1" });
			rmElements.add(new String[] { "mappings", "TERM_MAPPING", "0", "1" });				
			rmElements.add(new String[] { "formatting", "String", "0", "1" });
			rmElements.add(new String[] { "hyperlink", "DV_URI", "0", "1" });
			rmElements.add(new String[] { "language", "CODE_PHRASE", "0", "1" });
			rmElements.add(new String[] { "encoding", "CODE_PHRASE", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("CODE_PHRASE")) {	
			rmElements.add(new String[] { "terminology_id", "TERMINOLOGY_ID", "1", "1" });
			rmElements.add(new String[] { "code_string", "String", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("ARCHETYPED")) {	
			rmElements.add(new String[] { "archetype_id", "ARCHETYPE_ID", "1", "1" });
			rmElements.add(new String[] { "template_id", "TEMPLATE_ID", "0", "1" });
			rmElements.add(new String[] { "rm_version", "String", "1", "1" });
		}
				
		if (rmClassName.equalsIgnoreCase("DV_PARSABLE")) {				
			rmElements.add(new String[] { "language", "CODE_PHRASE", "0", "1" });		
			rmElements.add(new String[] { "charset", "CODE_PHRASE", "0", "1" });
			
			rmElements.add(new String[] { "size", "Integer", "1", "1" });
			rmElements.add(new String[] { "value", "String", "1", "1" });
			rmElements.add(new String[] { "formalism", "String", "1", "1" });
		}

		if (rmClassName.equalsIgnoreCase("DV_CODED_TEXT")) {	
			rmElements.add(new String[] { "value", "String", "1", "1" });
			rmElements.add(new String[] { "mappings", "TERM_MAPPING", "0", "1" });				
			rmElements.add(new String[] { "formatting", "String", "0", "1" });
			rmElements.add(new String[] { "hyperlink", "DV_URI", "0", "1" });
			rmElements.add(new String[] { "language", "CODE_PHRASE", "0", "1" });
			rmElements.add(new String[] { "encoding", "CODE_PHRASE", "0", "1" });
			
			rmElements.add(new String[] { "defining_code", "CODE_PHRASE", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_DURATION")) {				
			rmElements.add(new String[] { "other_reference_ranges", "REFERENCE_RANGE", "0", "1" });
			rmElements.add(new String[] { "normal_range", "DV_INTERVAL", "0", "1" });
			rmElements.add(new String[] { "normal_status", "CODE_PHRASE", "0", "1" });
			
			rmElements.add(new String[] { "magnitude_status", "String", "0", "1" });
			rmElements.add(new String[] { "value", "String", "1", "1" });
			rmElements.add(new String[] { "accuracy", "real", "0", "1" });
			rmElements.add(new String[] { "accuracy_is_percent", "boolean", "0", "1" });
			rmElements.add(new String[] { "accuracy_unknown", "Boolean", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("FEEDER_AUDIT")) {	
			rmElements.add(new String[] { "originating_system_audit", "FEEDER_AUDIT_DETAILS", "1", "1" });
			rmElements.add(new String[] { "originating_system_item_ids", "DV_IDENTIFIER", "0", "1" });
			rmElements.add(new String[] { "feeder_system_audit", "FEEDER_AUDIT_DETAILS", "0", "1" });
			rmElements.add(new String[] { "feeder_system_item_ids", "DV_IDENTIFIER", "0", "1" });
			rmElements.add(new String[] { "original_content", "DV_ENCAPSULATED", "0", "1" });
		}

		if (rmClassName.equalsIgnoreCase("FEEDER_AUDIT_DETAILS")) {	
			rmElements.add(new String[] { "system_id", "String", "1", "1" });
			rmElements.add(new String[] { "provider", "PARTY_IDENTIFIED", "0", "1" });
			rmElements.add(new String[] { "location", "PARTY_IDENTIFIED", "0", "1" });
			rmElements.add(new String[] { "time", "DV_DATE_TIME", "0", "1" });
			rmElements.add(new String[] { "subject", "PARTY_PROXY", "0", "1" });
			rmElements.add(new String[] { "version_id", "String", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("ISM_TRANSITION")) {	
			rmElements.add(new String[] { "current_state", "DV_CODED_TEXT", "1", "1" });
			rmElements.add(new String[] { "transition", "DV_CODED_TEXT", "0", "1" });
			rmElements.add(new String[] { "careflowStep", "DV_CODED_TEXT", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_ENCAPSULATED")) {	
			rmElements.add(new String[] { "charset", "CODE_PHRASE", "0", "1" });
			rmElements.add(new String[] { "language", "CODE_PHRASE", "0", "1" });
			rmElements.add(new String[] { "size", "Integer", "1", "1" });
		}

		if (rmClassName.equalsIgnoreCase("PARTY_IDENTIFIED")) {	
			rmElements.add(new String[] { "external_ref", "PARTY_REF", "0", "1" });
			rmElements.add(new String[] { "name", "String", "0", "1" });
			rmElements.add(new String[] { "identifiers", "DV_IDENTIFIER", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("PARTY_REF")) {	
			rmElements.add(new String[] { "id", "OBJECT_ID", "1", "1" });
			rmElements.add(new String[] { "type", "String", "1", "1" });
			rmElements.add(new String[] { "namespace", "String", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("EVENT")) {				
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
			
			rmElements.add(new String[] { "time", "DV_DATE_TIME", "1", "1" });
			rmElements.add(new String[] { "data", type, "1", "1" });
			//rmElements.add(new String[] { "data", "ITEM_STRUCTURE", "1", "1" });
			rmElements.add(new String[] { "state", "ITEM_STRUCTURE", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("ITEM")) {	
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
		}
		
		//TODO: ??? da steht im RM nicht ob die Elemente verpflichtend sind, deswegen einfach verpflichtend gemacht...??
		if (rmClassName.equalsIgnoreCase("DV_INTERVAL")) {	
			//rmElements.add(new String[] { "upper", "DV_ORDERED", "1", "1" });
			rmElements.add(new String[] { "upper", type, "1", "1" });
			//rmElements.add(new String[] { "lower", "DV_ORDERED", "1", "1" });
			rmElements.add(new String[] { "lower", type, "1", "1" });
			rmElements.add(new String[] { "lower_included", "boolean", "1", "1" });
			rmElements.add(new String[] { "upper_included", "boolean", "1", "1" });
			rmElements.add(new String[] { "lower_unbounded", "boolean", "1", "1" });
			rmElements.add(new String[] { "upper_unbounded", "boolean", "1", "1" });			
		}
				
		if (rmClassName.equalsIgnoreCase("DV_ORDERED")) {	
			rmElements.add(new String[] { "other_reference_ranges", "REFERENCE_RANGE", "0", "1" });
			rmElements.add(new String[] { "normal_range", "DV_INTERVAL", "0", "1" });
			rmElements.add(new String[] { "normal_status", "CODE_PHRASE", "0", "1" });			
		}
		
		if (rmClassName.equalsIgnoreCase("DV_URI")) {	
			rmElements.add(new String[] { "value", "String", "1", "1" });		
		}
		
		if (rmClassName.equalsIgnoreCase("INSTRUCTION_DETAILS")) {	
			rmElements.add(new String[] { "instruction_id", "LOCATABLE_REF", "1", "1" });
			rmElements.add(new String[] { "activity_id", "String", "1", "1" });
			rmElements.add(new String[] { "wf_details", "ITEM_STRUCTURE", "0", "1" });		
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });	
		}
		
		if (rmClassName.equalsIgnoreCase("LOCATABLE_REF")) {	
			rmElements.add(new String[] { "id", "OBJECT_VERSION_ID", "1", "1" });
			rmElements.add(new String[] { "path", "String", "0", "1" });
			rmElements.add(new String[] { "namespace", "String", "1", "1" });		
			rmElements.add(new String[] { "type", "String", "1", "1" });	
		}
		
		if (rmClassName.equalsIgnoreCase("OBJECT_REF")) {	
			rmElements.add(new String[] { "id", "OBJECT_ID", "1", "1" });
			rmElements.add(new String[] { "namespace", "String", "1", "1" });		
			rmElements.add(new String[] { "type", "String", "1", "1" });	
		}
		
		if (rmClassName.equalsIgnoreCase("PATHABLE")) {	
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("OBJECT_ID")) {	
			rmElements.add(new String[] { "value", "String", "0", "1" });
		}
		//TODO:????
		if (rmClassName.equalsIgnoreCase("ARCHETYPE_ID")) {	
			rmElements.add(new String[] { "value", "String", "0", "1" });
/*			rmElements.add(new String[] { "qualified_rm_entity", "String", "1", "1" });
			rmElements.add(new String[] { "domain_concept", "String", "1", "1" });		
			rmElements.add(new String[] { "rm_originator", "String", "1", "1" });
			rmElements.add(new String[] { "rm_name", "String", "1", "1" });
			rmElements.add(new String[] { "rm_entity", "String", "1", "1" });		
			rmElements.add(new String[] { "specialisation", "String", "1", "1" });
			rmElements.add(new String[] { "version_id", "String", "1", "1" }); */
		}
		
		if (rmClassName.equalsIgnoreCase("TEMPLATE_ID")) {	
			rmElements.add(new String[] { "value", "String", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("TERMINOLOGY_ID")) {	
			rmElements.add(new String[] { "value", "String", "0", "1" });
			rmElements.add(new String[] { "name", "String", "1", "1" });
			rmElements.add(new String[] { "version_id", "String", "1", "1" });		
		}
		
		if (rmClassName.equalsIgnoreCase("UID_BASED_ID")) {	
			rmElements.add(new String[] { "value", "String", "0", "1" });	
		}
		//TODO:???
		if (rmClassName.equalsIgnoreCase("OBJECT_VERSION_ID")) {	
			rmElements.add(new String[] { "value", "String", "0", "1" });
/*			rmElements.add(new String[] { "object_id", "UID", "1", "1" });
			rmElements.add(new String[] { "version_tree_id", "VERSION_TREE_ID", "1", "1" });
			rmElements.add(new String[] { "creating_system_id", "UID", "1", "1" });*/
		}
		
		if (rmClassName.equalsIgnoreCase("PARTY_PROXY")) {	
			rmElements.add(new String[] { "external_ref", "PARTY_REF", "0", "1" });	
		}
		
		if (rmClassName.equalsIgnoreCase("PARTY_SELF")) {	
			rmElements.add(new String[] { "external_ref", "PARTY_REF", "0", "1" });	
		}
		
		if (rmClassName.equalsIgnoreCase("CONTENT_ITEM")) {	
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });	
		}
		
		if (rmClassName.equalsIgnoreCase("PARTICIPATION")) {	
			rmElements.add(new String[] { "performer", "PARTY_PROXY", "1", "1" });
			rmElements.add(new String[] { "function", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "mode", "DV_CODED_TEXT", "1", "1" });
			rmElements.add(new String[] { "time", "DV_INTERVAL", "0", "1" });	
		}
		
		if (rmClassName.equalsIgnoreCase("EVENT_CONTEXT")) {	
			rmElements.add(new String[] { "health_care_facility", "PARTY_IDENTIFIED", "0", "1" });
			rmElements.add(new String[] { "start_time", "DV_DATE_TIME", "1", "1" });
			rmElements.add(new String[] { "end_time", "DV_DATE_TIME", "0", "1" });
			rmElements.add(new String[] { "participations", "PARTICIPATION", "0", "1" });
			rmElements.add(new String[] { "location", "String", "0", "1" });
			rmElements.add(new String[] { "setting", "DV_CODED_TEXT", "1", "1" });
			rmElements.add(new String[] { "other_context", "ITEM_STRUCTURE", "0", "1" });
			
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("REFERENCE_RANGE")) {	
			rmElements.add(new String[] { "meaning", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "range", "DV_INTERVAL", "1", "1" });	
		}
		
		if (rmClassName.equalsIgnoreCase("TERM_MAPPING")) {	
			rmElements.add(new String[] { "target", "CODE_PHRASE", "1", "1" });
			rmElements.add(new String[] { "match", "Character", "1", "1" });	
			rmElements.add(new String[] { "purpose", "DV_CODED_TEXT", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_BOOLEAN")) {	
			rmElements.add(new String[] { "value", "Boolean", "1", "1" });	
		}
		
		if (rmClassName.equalsIgnoreCase("DV_PARAGRAPH")) {	
			rmElements.add(new String[] { "items", "DV_TEXT", "1", "1" });	
		}
		
		if (rmClassName.equalsIgnoreCase("DV_EHR_URI")) {	
			rmElements.add(new String[] { "value", "String", "1", "1" });	
		}
		
		if (rmClassName.equalsIgnoreCase("DV_STATE")) {	
			rmElements.add(new String[] { "value", "DV_CODED_TEXT", "1", "1" });	
			rmElements.add(new String[] { "is_terminal", "Boolean", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_MULTIMEDIA")) {				
			rmElements.add(new String[] { "charset", "CODE_PHRASE", "0", "1" });
			rmElements.add(new String[] { "language", "CODE_PHRASE", "0", "1" });
			rmElements.add(new String[] { "size", "Integer", "1", "1" });
			
			rmElements.add(new String[] { "alternate_text", "String", "0", "1" });
			rmElements.add(new String[] { "uri", "DV_URI", "0", "1" });
			rmElements.add(new String[] { "data", "Byte", "0", "1" });
			rmElements.add(new String[] { "media_type", "CODE_PHRASE", "1", "1" });
			rmElements.add(new String[] { "compression_algorithm", "CODE_PHRASE", "0", "1" });
			rmElements.add(new String[] { "integrity_check", "Byte", "0", "1" });
			rmElements.add(new String[] { "integrity_check_algorithm", "CODE_PHRASE", "0", "1" });
			rmElements.add(new String[] { "thumbnail", "DV_MULTIMEDIA", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_IDENTIFIER")) {	
			rmElements.add(new String[] { "issuer", "String", "1", "1" });
			rmElements.add(new String[] { "assigner", "String", "1", "1" });
			rmElements.add(new String[] { "id", "String", "1", "1" });
			rmElements.add(new String[] { "type", "String", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_ORDINAL")) {				
			rmElements.add(new String[] { "other_reference_ranges", "REFERENCE_RANGE", "0", "1" });
			rmElements.add(new String[] { "normal_range", "DV_INTERVAL", "0", "1" });
			rmElements.add(new String[] { "normal_status", "CODE_PHRASE", "0", "1" });	
			
			rmElements.add(new String[] { "value", "Integer", "1", "1" });
			rmElements.add(new String[] { "symbol", "DV_CODED_TEXT", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_QUANTIFIED")) {	
			rmElements.add(new String[] { "other_reference_ranges", "REFERENCE_RANGE", "0", "1" });
			rmElements.add(new String[] { "normal_range", "DV_INTERVAL", "0", "1" });
			rmElements.add(new String[] { "normal_status", "CODE_PHRASE", "0", "1" });			
			
			rmElements.add(new String[] { "magnitude_status", "String", "0", "1" });
			rmElements.add(new String[] { "accuracy_unknown", "Boolean", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_TEMPORAL")) {	
			rmElements.add(new String[] { "other_reference_ranges", "REFERENCE_RANGE", "0", "1" });
			rmElements.add(new String[] { "normal_range", "DV_INTERVAL", "0", "1" });
			rmElements.add(new String[] { "normal_status", "CODE_PHRASE", "0", "1" });	
			
			rmElements.add(new String[] { "magnitude_status", "String", "0", "1" });
			rmElements.add(new String[] { "accuracy", "DV_DURATION", "0", "1" });
			rmElements.add(new String[] { "accuracy_unknown", "Boolean", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_AMOUNT")) {	
			rmElements.add(new String[] { "other_reference_ranges", "REFERENCE_RANGE", "0", "1" });
			rmElements.add(new String[] { "normal_range", "DV_INTERVAL", "0", "1" });
			rmElements.add(new String[] { "normal_status", "CODE_PHRASE", "0", "1" });	
			
			rmElements.add(new String[] { "magnitude_status", "String", "0", "1" });
			rmElements.add(new String[] { "accuracy", "real", "0", "1" });
			rmElements.add(new String[] { "accuracy_unknown", "Boolean", "0", "1" });
			rmElements.add(new String[] { "accuracy_is_percent", "Boolean", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_QUANTITY")) {	
			rmElements.add(new String[] { "other_reference_ranges", "REFERENCE_RANGE", "0", "1" });
			rmElements.add(new String[] { "normal_range", "DV_INTERVAL", "0", "1" });
			rmElements.add(new String[] { "normal_status", "CODE_PHRASE", "0", "1" });	
			
			rmElements.add(new String[] { "magnitude_status", "String", "0", "1" });
			rmElements.add(new String[] { "accuracy", "Float", "0", "1" });
			rmElements.add(new String[] { "accuracy_unknown", "Boolean", "0", "1" });
			rmElements.add(new String[] { "accuracy_is_percent", "Boolean", "0", "1" });
						
			rmElements.add(new String[] { "magnitude", "real", "1", "1" });
			rmElements.add(new String[] { "units", "String", "1", "1" });
			rmElements.add(new String[] { "precision", "Integer", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_COUNT")) {	
			rmElements.add(new String[] { "other_reference_ranges", "REFERENCE_RANGE", "0", "1" });
			rmElements.add(new String[] { "normal_range", "DV_INTERVAL", "0", "1" });
			rmElements.add(new String[] { "normal_status", "CODE_PHRASE", "0", "1" });	
			
			rmElements.add(new String[] { "magnitude_status", "String", "0", "1" });
			rmElements.add(new String[] { "accuracy", "Float", "0", "1" });
			rmElements.add(new String[] { "accuracy_unknown", "Boolean", "0", "1" });
			rmElements.add(new String[] { "accuracy_is_percent", "Boolean", "0", "1" });
			
			rmElements.add(new String[] { "magnitude", "Integer", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_PROPORTION")) {	
			rmElements.add(new String[] { "other_reference_ranges", "REFERENCE_RANGE", "0", "1" });
			rmElements.add(new String[] { "normal_range", "DV_INTERVAL", "0", "1" });
			rmElements.add(new String[] { "normal_status", "CODE_PHRASE", "0", "1" });	
			
			rmElements.add(new String[] { "magnitude_status", "String", "0", "1" });
			rmElements.add(new String[] { "accuracy", "Float", "0", "1" });
			rmElements.add(new String[] { "accuracy_unknown", "Boolean", "0", "1" });
			rmElements.add(new String[] { "accuracy_is_percent", "Boolean", "0", "1" });
			
			rmElements.add(new String[] { "numerator", "Real", "1", "1" });
			rmElements.add(new String[] { "denominator", "Real", "1", "1" });
			rmElements.add(new String[] { "type", "Integer", "1", "1" });
			rmElements.add(new String[] { "precision", "Integer", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_DATE_TIME")) {	
			rmElements.add(new String[] { "other_reference_ranges", "REFERENCE_RANGE", "0", "1" });
			rmElements.add(new String[] { "normal_range", "DV_INTERVAL", "0", "1" });
			rmElements.add(new String[] { "normal_status", "CODE_PHRASE", "0", "1" });
			
			rmElements.add(new String[] { "accuracy", "DV_DURATION", "0", "1" });
			rmElements.add(new String[] { "magnitude_status", "String", "0", "1" });			
			rmElements.add(new String[] { "accuracy_unknown", "Boolean", "0", "1" });
			rmElements.add(new String[] { "accuracy_is_percent", "Boolean", "0", "1" });
			
			rmElements.add(new String[] { "value", "String", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_DATE")) {	
			rmElements.add(new String[] { "other_reference_ranges", "REFERENCE_RANGE", "0", "1" });
			rmElements.add(new String[] { "normal_range", "DV_INTERVAL", "0", "1" });
			rmElements.add(new String[] { "normal_status", "CODE_PHRASE", "0", "1" });
			
			rmElements.add(new String[] { "accuracy", "DV_DURATION", "0", "1" });
			rmElements.add(new String[] { "magnitude_status", "String", "0", "1" });			
			rmElements.add(new String[] { "accuracy_unknown", "Boolean", "0", "1" });
			rmElements.add(new String[] { "accuracy_is_percent", "Boolean", "0", "1" });
			
			rmElements.add(new String[] { "value", "String", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_TIME")) {	
			rmElements.add(new String[] { "other_reference_ranges", "REFERENCE_RANGE", "0", "1" });
			rmElements.add(new String[] { "normal_range", "DV_INTERVAL", "0", "1" });
			rmElements.add(new String[] { "normal_status", "CODE_PHRASE", "0", "1" });
			
			rmElements.add(new String[] { "accuracy", "DV_DURATION", "0", "1" });
			rmElements.add(new String[] { "magnitude_status", "String", "0", "1" });			
			rmElements.add(new String[] { "accuracy_unknown", "Boolean", "0", "1" });
			rmElements.add(new String[] { "accuracy_is_percent", "Boolean", "0", "1" });
			
			rmElements.add(new String[] { "value", "String", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_TIME_SPECIFICATION")) {	
			rmElements.add(new String[] { "value", "DV_PARSABLE", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_PERIODIC_TIME_SPECIFICATION")) {	
			rmElements.add(new String[] { "value", "DV_PARSABLE", "1", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("DV_GENERAL_TIME_SPECIFICATION")) {	
			rmElements.add(new String[] { "value", "DV_PARSABLE", "1", "1" });
		}	
		
		if (rmClassName.equalsIgnoreCase("POINT_EVENT")) {						
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });	
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });	
			
			rmElements.add(new String[] { "time", "DV_DATE_TIME", "1", "1" });
			//rmElements.add(new String[] { "data", "ITEM_STRUCTURE", "1", "1" });
			rmElements.add(new String[] { "data", type, "1", "1" });
			rmElements.add(new String[] { "state", "ITEM_STRUCTURE", "0", "1" });
		}
		
		if (rmClassName.equalsIgnoreCase("INTERVAL_EVENT")) {						
			rmElements.add(new String[] { "uid", "UID_BASED_ID", "0", "1" });
			rmElements.add(new String[] { "archetype_node_id", "String", "1", "1" });
			rmElements.add(new String[] { "name", "DV_TEXT", "1", "1" });
			rmElements.add(new String[] { "archetype_details", "ARCHETYPED", "0", "1" });
			rmElements.add(new String[] { "feeder_audit", "FEEDER_AUDIT", "0", "1" });
			rmElements.add(new String[] { "links", "Link", "0", "1" });	
			rmElements.add(new String[] { "parent", "PATHABLE", "0", "1" });	
			
			rmElements.add(new String[] { "time", "DV_DATE_TIME", "1", "1" });
			//rmElements.add(new String[] { "data", "ITEM_STRUCTURE", "1", "1" });
			rmElements.add(new String[] { "data", type, "1", "1" });
			rmElements.add(new String[] { "state", "ITEM_STRUCTURE", "0", "1" });
			
			rmElements.add(new String[] { "width", "DV_DURATION", "1", "1" });
			rmElements.add(new String[] { "math_function", "DV_CODED_TEXT", "1", "1" });
			rmElements.add(new String[] { "sample_count", "Integer", "0", "1" });
		}
		
		if(rmElements.isEmpty()) {
			System.out.println("Class not found in getRMElements():"+rmClassName);
		}
			
		return rmElements;
	}
	
	public static boolean checkIfAbstract(String rmClassName)
	{
		List<String> abstract_elements = Arrays.asList("DV_ENCAPSULATED", "EVENT", "ITEM", "DV_ORDERED",
				"PATHABLE", "OBJECT_ID", "UID_BASED_ID", "PARTY_PROXY", "CONTENT_ITEM", "ACCESS_CONTROL_SETTINGS",
				"DV_QUANTIFIED", "DV_AMOUNT", "DV_TEMPORAL", "DV_TIME_SPECIFICATION", "DV_ABSOLUTE_QUANTITY");	
		
		for(String i : abstract_elements){
			if(i.equalsIgnoreCase(rmClassName))
			{
				//System.out.println(rmClassName+" is abstract, therefore not default object can be created!");
				return true;
			} 
		}	
		return false;

	}
	public static boolean checkIfChildOfLocatable(String rmClassName)
	{
		List<String> locatable_elements = Arrays.asList("FOLDER", "COMPOSITION", "CONTENT_ITEM", "ENTRY",
				"ADMIN_ENTRY", "CARE_ENTRY", "ACTION", "EVALUATION", "INSTRUCTION", "OBSERVATION", "SECTION",
				"GENERIC_ENTRY", "ACTIVITY", "DATA_STRUCTURE", "HISTORY", "ITEM_STRUCTURE", "ITEM_LIST", "ITEM_SINGLE",
				"ITEM_TABLE", "ITEM_TREE", "EVENT", "ITEM", "CLUSTER", "ELEMENT", "ADDRESS", "CAPABILITY",
				"CONTACT", "PARTY", "ACTOR", "AGENT", "GROUP", "ORGANISATION", "PERSON", "ROLE", "PARTY_IDENTITY",
				"PARTY_RELATIONSHIP", "EHR_ACCESS", "EHR_STATUS", "POINT_EVENT", "INTERVAL_EVENT");
			
		for(String i : locatable_elements){
			if(i.equalsIgnoreCase(rmClassName))
			{
				//System.out.println(rmClassName+" is a subclass of LOCATABLE, therefore no default object can be created!");
				return true;
			} 
		}	
		return false;
	}	
		
	/*attribute names are mapped to IDs of openEHR Code sets*/
	private String getCodeSetFromAttributeName(String attributeName) {
		HashMap<String, String> codeSetMapping = new HashMap<String, String>();
		/*name of the attribute, external id of the according code set*/
		codeSetMapping.put("language", "ISO_639-1"); //languages
		codeSetMapping.put("territory", "ISO_3166-1"); //countries
		codeSetMapping.put("encoding", "IANA_character-sets"); 
		codeSetMapping.put("normal_status", "openehr_normal_statuses");
		codeSetMapping.put("media_type", "IANA_media-types");
		codeSetMapping.put("compression_algorithm", "openehr_compression_algorithms");
		codeSetMapping.put("integrity_check_algorithm", "openehr_integrity_check_algorithms");
		codeSetMapping.put("charset", "IANA_character-sets");
				
		for(String attribute:codeSetMapping.keySet())
		{
			if(attribute.equalsIgnoreCase(attributeName))
			{			
				return codeSetMapping.get(attribute);
			}
		}
		return null;
	}
	
	/*for open archetype slots: suitable archetypes are selected from a folder*/
	private ARCHETYPE getArchetype(List<String> atNames) {
		
		File dir = new File("src/main/resources/Archetypes");
		List<File> files=new ArrayList<File>();
		File adl_file;
		
		for(String atId:atNames) {
			//System.out.println(atId);
			if(atId.equalsIgnoreCase(".*.adl")) { 		
				for(File file:dir.listFiles()) {
					files.add(file);
				}
			} else {			
				FileFilter fileFilter = new RegexFileFilter(atId);
				for(File file:dir.listFiles(fileFilter))
				{
					files.add(file);
				}
			}
		}
					 
		if(files.size()>1) { 
			System.out.println("More than 1 suitable archetype has been found for this slot!");
			System.out.println("Please choose one of the following archetypes: ");
			 
			for (File file:files) {
				System.out.println(file.getName());
			}
			
			BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
			String chosen_at = null;
			try {
				chosen_at = console.readLine();
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("You entered: " + chosen_at);
			
			adl_file=files.get(0); //per default wird der Erste genommen
			for (File file:files) {
				if(file.getName().equalsIgnoreCase(chosen_at)) {
					adl_file=file;
				}
			}			 
		} else if(files.size()==0) {
			 System.out.println("No suitable archetype could be found!");
			 return null;
		} else {	 
			System.out.println("chosen archetype: "+files.get(0).getName());
			 adl_file=files.get(0);
		}
		 		
		try{ 
			/*first the archetype is parsed with the ADL parser and then converted into an XML
			 * afterwards the XML is unmarshalled as JAXB tree*/
			ADLParser parser = new ADLParser(adl_file);
			Archetype adl_at=parser.parse();	
			
			/*unfortunately AT_INT_REFs can't be resolved at this point as the nodes don't have parents assigned
			 * additionally there is no possibility to add a cloned node instead of the AT_INT_REF node here*/
			
			XMLSerializer serializer=new XMLSerializer();
			String xml_at=serializer.output(adl_at);
			JAXBContext jc = JAXBContext.newInstance("org.openehr.jaxb"); 
			Unmarshaller unmarshaller = jc.createUnmarshaller();	    	
		    Source source = new StreamSource(new StringReader(xml_at));
		    
		    JAXBElement<ARCHETYPE> slotATRoot = unmarshaller.unmarshal(source, ARCHETYPE.class);
			ARCHETYPE slot_at = slotATRoot.getValue();
			return slot_at;
		} catch (Exception ex) {
			ex.printStackTrace();
			return null;
		}
	}
	
	private void loadATIntRefNodes() { //TODO: not tested yet!
		
		for(ARCHETYPEINTERNALREF entry:this.atIntRefNodes) {

			String fullRefPath=GenerateXForm.getFullPath(entry);
			String targetPath=entry.getTargetPath();	
			/*targetPath only includes the path starting from the last AT root node
			 * the path till this AT root node needs to be added */
			String atPath=fullRefPath.substring(0, fullRefPath.lastIndexOf("[openEHR-EHR-")); 
			targetPath=atPath+targetPath;			
			
			Cloner cloner = new Cloner();
			COBJECT refNode=this.template.node(targetPath);
			if(refNode!=null) {
				COBJECT clonedNode=cloner.deepClone(this.template.node(targetPath)); /*node referenced in the AT_INT_REF node*/
				clonedNode.setParent(entry.getParent());
				entry.getParent().getChildren().add(clonedNode);
				entry.getParent().getChildren().remove(entry);
				this.template.addNewNodeToMap(clonedNode);
			} else {
				System.out.println("AT_internal_ref: Couldn't find referenced node.");
			}
		}
		
	}
	
	/*checks whether a new name has been assigned to the node in the template
	 * if yes, the old term in the term definitions is replaced with the new name*/
	private void checkForNewName(CATTRIBUTE cattribute) {
		
		for(CATTRIBUTE attr1:((CCOMPLEXOBJECT)cattribute.getChildren().get(0)).getAttributes()) {
			if(attr1.getRmAttributeName().equalsIgnoreCase("value")) {
				if(!((CSTRING)((CPRIMITIVEOBJECT)attr1.getChildren().get(0)).getItem()).getList().isEmpty()) {
					String newNodeName=((CSTRING)((CPRIMITIVEOBJECT)attr1.getChildren().get(0)).getItem()).getList().get(0);
					if(newNodeName!=null && !newNodeName.equalsIgnoreCase("") && !newNodeName.equalsIgnoreCase(" ")) {
						String nodeId=cattribute.getParent().getNodeId();
						
						for (ARCHETYPETERM term : template.getDefinition().getTermDefinitions()) {
							if (term.getCode().equalsIgnoreCase(nodeId)) {
								ARCHETYPETERM newTerm=new ARCHETYPETERM();
								newTerm=term;
								template.getDefinition().getTermDefinitions().remove(term);
								
								for (StringDictionaryItem dict_item:term.getItems())
								{
									if(dict_item.getId().equalsIgnoreCase("text"))
									{
										dict_item.setValue(newNodeName);
									}
								}
								template.getDefinition().getTermDefinitions().add(newTerm);
								break;
							}			
						}
					}
				}
			}
		}
	}
}
