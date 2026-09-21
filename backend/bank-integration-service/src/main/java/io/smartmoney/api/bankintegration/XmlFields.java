package io.smartmoney.api.bankintegration;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the fields of an XML notification into a flat map.
 *
 * Several of the banks post a SOAP envelope and the element names carry a
 * namespace prefix such as soapenv. The document is parsed without namespace
 * processing and every name is reduced to the part after the last colon, so
 * <User> and <ns:User> are the same field. Entity expansion and doctypes are
 * switched off, because a notification is untrusted input and must not be able
 * to read a file from this machine or expand without limit.
 */
public final class XmlFields {

    private XmlFields() {
    }

    /**
     * @return the fields of the notification body, or null when the text is not
     *         well formed XML.
     */
    public static Map<String, String> read(String xml) {
        if (xml == null || xml.isBlank()) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            document.getDocumentElement().normalize();

            Element start = innermostBody(document);
            Map<String, String> fields = new LinkedHashMap<>();
            collect(start, fields);
            return fields;
        } catch (Exception error) {
            return null;
        }
    }

    /**
     * Prefers the bank payload element over the SOAP envelope, so an envelope
     * that happens to contain an element with the same name cannot shadow it.
     */
    private static Element innermostBody(Document document) {
        List<String> wrappers = List.of(
                "NCBAPaymentNotificationRequest", "NCBAPaymentNotificationResult");
        for (String wrapper : wrappers) {
            Element found = firstWithName(document.getDocumentElement(), wrapper);
            if (found != null) {
                return found;
            }
        }
        return document.getDocumentElement();
    }

    private static void collect(Element element, Map<String, String> fields) {
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element childElement = (Element) child;
            String name = localName(childElement.getNodeName());
            if (hasElementChildren(childElement)) {
                collect(childElement, fields);
            } else {
                fields.putIfAbsent(name, collapse(childElement.getTextContent()));
            }
        }
    }

    private static boolean hasElementChildren(Element element) {
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index).getNodeType() == Node.ELEMENT_NODE) {
                return true;
            }
        }
        return false;
    }

    private static Element firstWithName(Element element, String wanted) {
        if (wanted.equalsIgnoreCase(localName(element.getNodeName()))) {
            return element;
        }
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index).getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element found = firstWithName((Element) children.item(index), wanted);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Strips any namespace prefix, so soapenv:User becomes User. */
    public static String localName(String nodeName) {
        int colon = nodeName.lastIndexOf(':');
        return colon < 0 ? nodeName : nodeName.substring(colon + 1);
    }

    /** Collapses runs of whitespace, which is what the banks expect in a value. */
    public static String collapse(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    /**
     * First value present under any of the given names. Banks rename fields
     * between revisions, so each field is looked up under its aliases.
     */
    public static String first(Map<String, String> fields, String... names) {
        if (fields == null) {
            return null;
        }
        for (String name : names) {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isBlank()) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static final List<DateTimeFormatter> TIMESTAMP_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyMMddHHmm"),
            DateTimeFormatter.ofPattern("yyMMddHHmmss"),
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    /**
     * Bank timestamps arrive in several shapes. NCBA sends YYMMDDhhmm with no
     * zone, so an unzoned value is read as East Africa Time.
     */
    public static Instant timestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = collapse(value);
        try {
            return Instant.parse(cleaned);
        } catch (Exception ignored) {
            // Not an ISO instant. Try the bank layouts below.
        }
        for (DateTimeFormatter format : TIMESTAMP_FORMATS) {
            try {
                return LocalDateTime.parse(cleaned, format)
                        .toInstant(ZoneOffset.ofHours(3));
            } catch (Exception ignored) {
                // Try the next layout.
            }
        }
        return null;
    }
}
