package org.tobarsegais.webapp.data;

import com.thoughtworks.xstream.XStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class Toc extends Entry {

    private static final long serialVersionUID = 1L;

    public Toc(String label, String topic, Topic... children) {
        this(label, topic, Arrays.asList(children));
    }

    public Toc(String label, String topic, Collection<Topic> children) {
        super(label, topic, children);
    }

    public static Toc read(URL url) throws XMLStreamException {
        InputStream is = null;
        try {
            is = url.openStream();
            return read(XMLInputFactory.newInstance().createXMLStreamReader(is));
        } catch (IOException e) {
            throw new XMLStreamException(e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    public static Toc read(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext() && !reader.isStartElement()) {
            reader.next();
        }
        if (reader.getEventType() != XMLStreamConstants.START_ELEMENT) {
            throw new IllegalStateException("Expecting a start element");
        }
        if (!"toc".equals(reader.getLocalName())) {
            throw new IllegalStateException("Expecting a <toc> element");
        }
        String label = reader.getAttributeValue(null, "label");
        String topic = reader.getAttributeValue(null, "topic");
        List<Topic> topics = new ArrayList<Topic>();
        outer:
        while (reader.hasNext()) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT:
                    topics.add(Topic.read(reader));
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    break outer;
            }
        }
        return new Toc(label, topic, topics);
    }

    public void write(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("toc");
        writer.writeAttribute("label", getLabel());
        writer.writeAttribute("topic", getHref());
        for (Topic topic: getChildren()) {
           topic.write(writer);
        }
        writer.writeEndElement();
    }

    public static void register(XStream xstream) {
        xstream.aliasAttribute(Toc.class, "href", "topic");
        xstream.useAttributeFor("label", String.class);
        xstream.useAttributeFor(Topic.class, "href");
        xstream.alias("toc", Toc.class);
        xstream.addImplicitCollection(Toc.class, "children", "topic", Topic.class);
        xstream.addImplicitCollection(Topic.class, "children", "topic", Topic.class);
        xstream.addDefaultImplementation(ArrayList.class, List.class);
    }


}
