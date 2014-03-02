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
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for C_DURATION complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="C_DURATION">
 *   &lt;complexContent>
 *     &lt;extension base="{http://schemas.openehr.org/v1}C_PRIMITIVE">
 *       &lt;sequence>
 *         &lt;element name="pattern" type="{http://schemas.openehr.org/v1}DurationConstraintPattern" minOccurs="0"/>
 *         &lt;element name="range" type="{http://schemas.openehr.org/v1}IntervalOfDuration" minOccurs="0"/>
 *         &lt;element name="assumed_value" type="{http://schemas.openehr.org/v1}Iso8601Duration" minOccurs="0"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "C_DURATION", propOrder = {
    "pattern",
    "range",
    "assumedValue"
})
public class CDURATION
    extends CPRIMITIVE
{

    protected String pattern;
    protected IntervalOfDuration range;
    @XmlElement(name = "assumed_value")
    protected String assumedValue;

    /**
     * Gets the value of the pattern property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * Sets the value of the pattern property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPattern(String value) {
        this.pattern = value;
    }

    /**
     * Gets the value of the range property.
     * 
     * @return
     *     possible object is
     *     {@link IntervalOfDuration }
     *     
     */
    public IntervalOfDuration getRange() {
        return range;
    }

    /**
     * Sets the value of the range property.
     * 
     * @param value
     *     allowed object is
     *     {@link IntervalOfDuration }
     *     
     */
    public void setRange(IntervalOfDuration value) {
        this.range = value;
    }

    /**
     * Gets the value of the assumedValue property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getAssumedValue() {
        return assumedValue;
    }

    /**
     * Sets the value of the assumedValue property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setAssumedValue(String value) {
        this.assumedValue = value;
    }

}
