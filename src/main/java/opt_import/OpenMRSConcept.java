package opt_import;

import org.openehr.jaxb.HIEROBJECTID;

public class OpenMRSConcept {
	private int conceptId;
	private String fullName;
	private String shortName;
	private String nodeId;
	private String conceptDatatype;
	private String xmlDatatype;
	private String description;
	private String xFormBinding;
	private String xFormNodeset;
	private String xPath;
	private boolean repeat=false;
	
    public int getConceptId() {
        return conceptId;
    }

    public void setConceptId(int value) {
        this.conceptId = value;
    }
    
    public String getFullName() {
        return fullName;
    }

    public void setFullName(String value) {
        this.fullName = value;
    }
    
    public String getShortName() {
        return shortName;
    }

    public void setShortName(String value) {
        this.shortName = value;
    }
    
    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String value) {
        this.nodeId = value;
    }

    public String getConceptDatatype() {
        return conceptDatatype;
    }

    public void setConceptDatatype(String value) {
        this.conceptDatatype = value;
    }
    
    public String getXmlDatatype() {
        return xmlDatatype;
    }

    public void setXmlDatatype(String value) {
        this.xmlDatatype = value;
    }
    
    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        this.description = value;
    }
    
    public boolean getRepeat() {
        return repeat;
    }

    public void setRepeat(boolean value) {
        this.repeat = value;
    }
    
    public String getXFormBinding() {
        return xFormBinding;
    }

    public void setXFormBinding(String value) {
        this.xFormBinding = value;
    }
    
    public String getXFormNodeset() {
        return xFormNodeset;
    }

    public void setXFormNodeset(String value) {
        this.xFormNodeset = value;
    }
    
    public String getXPath() {
        return xPath;
    }

    public void setXPath(String value) {
        this.xPath = value;
    }
}
