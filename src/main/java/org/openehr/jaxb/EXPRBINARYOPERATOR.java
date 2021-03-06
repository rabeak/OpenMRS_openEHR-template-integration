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
 * <p>Java class for EXPR_BINARY_OPERATOR complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="EXPR_BINARY_OPERATOR">
 *   &lt;complexContent>
 *     &lt;extension base="{http://schemas.openehr.org/v1}EXPR_OPERATOR">
 *       &lt;sequence>
 *         &lt;element name="left_operand" type="{http://schemas.openehr.org/v1}EXPR_ITEM"/>
 *         &lt;element name="right_operand" type="{http://schemas.openehr.org/v1}EXPR_ITEM"/>
 *       &lt;/sequence>
 *     &lt;/extension>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "EXPR_BINARY_OPERATOR", propOrder = {
    "leftOperand",
    "rightOperand"
})
public class EXPRBINARYOPERATOR
    extends EXPROPERATOR
{

    @XmlElement(name = "left_operand", required = true)
    protected EXPRITEM leftOperand;
    @XmlElement(name = "right_operand", required = true)
    protected EXPRITEM rightOperand;

    /**
     * Gets the value of the leftOperand property.
     * 
     * @return
     *     possible object is
     *     {@link EXPRITEM }
     *     
     */
    public EXPRITEM getLeftOperand() {
        return leftOperand;
    }

    /**
     * Sets the value of the leftOperand property.
     * 
     * @param value
     *     allowed object is
     *     {@link EXPRITEM }
     *     
     */
    public void setLeftOperand(EXPRITEM value) {
        this.leftOperand = value;
    }

    /**
     * Gets the value of the rightOperand property.
     * 
     * @return
     *     possible object is
     *     {@link EXPRITEM }
     *     
     */
    public EXPRITEM getRightOperand() {
        return rightOperand;
    }

    /**
     * Sets the value of the rightOperand property.
     * 
     * @param value
     *     allowed object is
     *     {@link EXPRITEM }
     *     
     */
    public void setRightOperand(EXPRITEM value) {
        this.rightOperand = value;
    }

}
