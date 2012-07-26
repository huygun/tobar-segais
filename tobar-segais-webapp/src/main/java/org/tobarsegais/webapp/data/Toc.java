/*
 * Copyright 2011 Stephen Connolly
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tobarsegais.webapp.data;

import org.apache.commons.io.IOUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class Toc extends TocEntry {

    private static final long serialVersionUID = 1L;

    public Toc(String label, String topic, Topic... children) {
        this(label, topic, Arrays.asList(children));
    }

    public Toc(String label, String topic, Collection<Topic> children) {
        super(label, topic, children);
    }

    public static Toc read(InputStream inputStream) throws XMLStreamException {
        try {
            return read(XMLInputFactory.newInstance().createXMLStreamReader(inputStream));
        } finally {
            IOUtils.closeQuietly(inputStream);
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
        int depth = 0;
        while (reader.hasNext() && depth >= 0) {
            switch (reader.next()) {
                case XMLStreamConstants.START_ELEMENT:
                    if (depth == 0 && "topic".equals(reader.getLocalName())) {
                        topics.add(Topic.read(reader));
                    } else {
                        depth++;
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    depth--;
                    break;
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

}
