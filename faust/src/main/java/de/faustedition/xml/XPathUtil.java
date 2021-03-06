package de.faustedition.xml;

import com.google.common.base.Throwables;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class XPathUtil {

	public static XPathExpression xpath(String expression, NamespaceContext namespaceContext) {
		try {
			XPath xpath = XPathFactory.newInstance().newXPath();
			if (namespaceContext != null) {
				xpath.setNamespaceContext(namespaceContext);
			}
			return xpath.compile(expression);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(String.format("XPath error while compiling '%s'", expression), e);
		}
	}

	public static XPathExpression xpath(String expr) {
		return xpath(expr, CustomNamespaceContext.INSTANCE);
	}

}
