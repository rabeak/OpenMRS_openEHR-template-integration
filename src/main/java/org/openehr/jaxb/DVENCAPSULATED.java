//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2013.06.06 at 01:43:49 PM CEST 
//


package org.openehr.jaxb;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DV_ENCAPSULATED complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DV_ENCAPSULATED">
 *   &lt;complexContent>
 *     &lt;extension base="{http://schemas.openehr.org/v1}DATA_VALUE">
 *       &lt;sequence>
 *         &lt;element name="charset" type="{http://schemas.openehr.org/v1}CODE_PHRASE" minOccurs="0"/>
 *         &lt;element name="language" type="{http://schemas.openehr.org/v1}CODE_PHRASE" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DV_ENCAPSULATED", propOrder = {
    "charset",
    "language"
})
@XmlSeeAlso({
    DVMULTIMEDIA.class,
    DVPARSABLE.class
})
public abstract class DVENCAPSULATED
    extends DATAVALUE
{

    protected CODEPHRASE charset;
    protected CODEPHRASE language;

    /**
     * Gets the value of the charset property.
     * 
     * @return
     *     possible object is
     *     {@link CODEPHRASE }
     *     
     */
    public CODEPHRASE getCharset() {
        return charset;
    }

    /**
     * Sets the value of the charset property.
     * 
     * @param value
     *     allowed object is
     *     {@link CODEPHRASE }
     *     
     */
    public void setCharset(CODEPHRASE value) {
        this.charset = value;
    }

    /**
     * Gets the value of the language property.
     * 
     * @return
     *     possible object is
     *     {@link CODEPHRASE }
     *     
     */
    public CODEPHRASE getLanguage() {
        return language;
    }

    /**
     * Sets the value of the language property.
     * 
     * @param value
     *     allowed object is
     *     {@link CODEPHRASE }
     *     
     */
    public void setLanguage(CODEPHRASE value) {
        this.language = value;
    }

}