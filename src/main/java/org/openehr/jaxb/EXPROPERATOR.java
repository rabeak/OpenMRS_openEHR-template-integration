//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2013.06.06 at 01:43:49 PM CEST 
//


package org.openehr.jaxb;

import java.math.BigInteger;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for EXPR_OPERATOR complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="EXPR_OPERATOR">
 *   &lt;complexContent>
 *     &lt;extension base="{http://schemas.openehr.org/v1}EXPR_ITEM">
 *       &lt;sequence>
 *         &lt;element name="operator" type="{http://schemas.openehr.org/v1}OPERATOR_KIND"/>
 *         &lt;element name="precedence_overridden" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "EXPR_OPERATOR", propOrder = {
    "operator",
    "precedenceOverridden"
})
@XmlSeeAlso({
    EXPRBINARYOPERATOR.class,
    EXPRUNARYOPERATOR.class
})
public abstract class EXPROPERATOR
    extends EXPRITEM
{

    @XmlElement(required = true)
    protected BigInteger operator;
    @XmlElement(name = "precedence_overridden")
    protected boolean precedenceOverridden;

    /**
     * Gets the value of the operator property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getOperator() {
        return operator;
    }

    /**
     * Sets the value of the operator property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setOperator(BigInteger value) {
        this.operator = value;
    }

    /**
     * Gets the value of the precedenceOverridden property.
     * 
     */
    public boolean isPrecedenceOverridden() {
        return precedenceOverridden;
    }

    /**
     * Sets the value of the precedenceOverridden property.
     * 
     */
    public void setPrecedenceOverridden(boolean value) {
        this.precedenceOverridden = value;
    }

}
