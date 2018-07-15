package com.wokesolutions.ignes.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class SVG {

	private static final Logger LOG = Logger.getLogger(SVG.class.getName());

	private static final String PATH = "img/stats/portugal.svg";

	public static String createPortugalSVG(Map<String, String> colors, int total) {
		InputStream is = Storage.getImageIs(PATH);

		try {
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			Document doc = docBuilder.parse(is);

			// Get the staff element by tag name directly
			NodeList nodes = doc.getElementsByTagName("path");

			for(int i = 0; i < nodes.getLength(); i++) {
				Node node = nodes.item(i);
				NamedNodeMap attr = node.getAttributes();
				Node id = attr.getNamedItem("id");
				String district = id.getTextContent();
				LOG.info(district);

				if(colors.containsKey(district)) {
					Node fill = attr.getNamedItem("fill");
					
					fill.setTextContent(colors.get(district));
				}
			}

			nodes = doc.getElementsByTagName("text");
			
			for(int i = 0; i < nodes.getLength(); i++) {
				Node node = nodes.item(i);
				if(node.getTextContent().equals("maxreports"))
					node.setTextContent(Integer.toString(total));
			}

			StringWriter sw = new StringWriter();
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
			transformer.setOutputProperty(OutputKeys.METHOD, "xml");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

			transformer.transform(new DOMSource(doc), new StreamResult(sw));
			return sw.toString();
		} catch (ParserConfigurationException e) {
			LOG.info(e.getMessage());
			LOG.info(e.toString());
		} catch (IOException e) {
			LOG.info(e.getMessage());
			LOG.info(e.toString());
		} catch (SAXException e) {
			LOG.info(e.getMessage());
			LOG.info(e.toString());
		} catch (TransformerException e) {
			LOG.info(e.getMessage());
			LOG.info(e.toString());
		}

		return null;
	}
}
