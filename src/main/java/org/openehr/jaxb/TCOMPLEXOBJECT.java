//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2013.09.17 at 05:12:14 PM CEST 
//


package org.openehr.jaxb;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for T_COMPLEX_OBJECT complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="T_COMPLEX_OBJECT">
 *   &lt;complexContent>
 *     &lt;extension base="{http://schemas.openehr.org/v1}C_COMPLEX_OBJECT">
 *       &lt;sequence>
 *         &lt;element name="default_value" type="{http://schemas.openehr.org/v1}DATA_VALUE" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "T_COMPLEX_OBJECT", propOrder = {
    "defaultValue"
})
public class TCOMPLEXOBJECT
    extends CCOMPLEXOBJECT
{

    @XmlElement(name = "default_value")
    protected DATAVALUE defaultValue;

    /**
     * Gets the value of the defaultValue property.
     * 
     * @return
     *     possible object is
     *     {@link DATAVALUE }
     *     
     */
    public DATAVALUE getDefaultValue() {
        return defaultValue;
    }

    /**
     * Sets the value of the defaultValue property.
     * 
     * @param value
     *     allowed object is
     *     {@link DATAVALUE }
     *     
     */
    public void setDefaultValue(DATAVALUE value) {
        this.defaultValue = value;
    }

}
