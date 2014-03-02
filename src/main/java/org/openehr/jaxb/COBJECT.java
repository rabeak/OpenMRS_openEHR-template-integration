//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2013.06.06 at 01:43:49 PM CEST 
//


package org.openehr.jaxb;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for C_OBJECT complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="C_OBJECT">
 *   &lt;complexContent>
 *     &lt;extension base="{http://schemas.openehr.org/v1}ARCHETYPE_CONSTRAINT">
 *       &lt;sequence>
 *         &lt;element name="rm_type_name" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="occurrences" type="{http://schemas.openehr.org/v1}IntervalOfInteger"/>
 *         &lt;element name="node_id" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "C_OBJECT", propOrder = {
    "rmTypeName",
    "occurrences",
    "nodeId", 
    "parent"
})
@XmlSeeAlso({
    ARCHETYPEINTERNALREF.class,
    CONSTRAINTREF.class,
    ARCHETYPESLOT.class,
    CDEFINEDOBJECT.class
})
public abstract class COBJECT
    extends ARCHETYPECONSTRAINT
{

    @XmlElement(name = "rm_type_name", required = true)
    protected String rmTypeName;
    @XmlElement(required = true)
    protected IntervalOfInteger occurrences;
    @XmlElement(name = "node_id", required = true)
    protected String nodeId;
    protected CATTRIBUTE parent;

    /**
     * Gets the value of the rmTypeName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getRmTypeName() {
        return rmTypeName;
    }

    /**
     * Sets the value of the rmTypeName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setRmTypeName(String value) {
        this.rmTypeName = value;
    }

    /**
     * Gets the value of the occurrences property.
     * 
     * @return
     *     possible object is
     *     {@link IntervalOfInteger }
     *     
     */
    public IntervalOfInteger getOccurrences() {
        return occurrences;
    }

    /**
     * Sets the value of the occurrences property.
     * 
     * @param value
     *     allowed object is
     *     {@link IntervalOfInteger }
     *     
     */
    public void setOccurrences(IntervalOfInteger value) {
        this.occurrences = value;
    }

    /**
     * Gets the value of the nodeId property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * Sets the value of the nodeId property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setNodeId(String value) {
        this.nodeId = value;
    }
    
    public CATTRIBUTE getParent()
    {
    	return parent;
    }
    
    public void setParent(CATTRIBUTE value)
    {
    	this.parent=value;
    }

}
