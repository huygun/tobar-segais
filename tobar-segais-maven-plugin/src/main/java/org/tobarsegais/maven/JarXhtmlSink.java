/*
 * Copyright 2013 Stephen Connolly
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

package org.tobarsegais.maven;

import org.apache.maven.doxia.document.DocumentTOC;
import org.apache.maven.doxia.document.DocumentTOCItem;
import org.apache.maven.doxia.module.xhtml.AbstractXhtmlSink;
import org.apache.maven.doxia.module.xhtml.XhtmlSinkFactory;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.SinkEventAttributeSet;
import org.apache.maven.doxia.sink.SinkEventAttributes;
import org.apache.maven.doxia.util.HtmlTools;
import org.codehaus.plexus.util.StringUtils;

import javax.swing.text.html.HTML;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * @author Stephen Connolly
 */
public class JarXhtmlSink extends AbstractXhtmlSink {
    private final JarOutputStream outputStream;
    private final XhtmlSinkFactory factory;
    private Sink delegate;
    private String encoding;
    private StringBuilder textBuffer;

    private boolean sectionHasID;

    private boolean isSectionTitle;

    private boolean isTitle;

    private boolean isHead;

    private boolean isBody;

    private boolean wroteHeader;

    private Set<String> anchorsInSectionTitle;

    private String previousRef;
    private String currentRef;
    private String currentName;
    private String nextRef;

    public JarXhtmlSink(JarOutputStream outputStream, XhtmlSinkFactory factory, String encoding) {
        this.outputStream = outputStream;
        this.factory = factory;
        this.encoding = encoding;
    }

    public JarOutputStream getOutputStream() {
        return outputStream;
    }

    public void file(String name) throws IOException {
        file(name, System.currentTimeMillis());
    }

    public void file(String name, long time) throws IOException {
        file(name, time, null, null);
    }

    public void file(String name, long time, String description, String nextRef) throws IOException {
        if (delegate != null) {
            file_();
        }
        final JarEntry entry = new JarEntry(name);
        entry.setTime(time);
        outputStream.putNextEntry(entry);
        delegate = factory.createSink(new NoCloseOutputStream(outputStream), encoding);
        previousRef = currentRef;
        currentRef = name;
        currentName = description;
        this.nextRef = nextRef;
        isHead = false;
        isSectionTitle = false;
        isTitle = false;
        isBody = false;
        wroteHeader = false;
    }

    public void file_() {
        if (delegate != null) {
            delegate.close();
            delegate = null;
        }
    }

    /**
     * Writes a table of contents. The DocumentModel has to contain a DocumentTOC for this to work.
     */
    public void toc(DocumentTOC toc) {
        delegate.section1();
        delegate.sectionTitle1();
        delegate.text(toc.getName());
        delegate.sectionTitle1_();
        writeTocItems(toc.getItems(), 1);
        delegate.section1_();
    }

    private void writeTocItems(List<DocumentTOCItem> tocItems, int level) {
        final int maxTocLevel = 4;

        if (level < 1 || level > maxTocLevel || tocItems.isEmpty()) {
            return;
        }

        delegate.list(new SinkEventAttributeSet(SinkEventAttributes.STYLE, "list-style-type:none;"));
        for (DocumentTOCItem tocItem : tocItems) {
            delegate.listItem();
            delegate.link(tocItem.getRef());
            delegate.text(tocItem.getName());
            delegate.link_();
            if (tocItem.getItems() != null) {
                writeTocItems(tocItem.getItems(), level + 1);
            }
            delegate.listItem_();
        }
        delegate.list_();
    }

    private void navLinks() {
        SinkEventAttributes style = new SinkEventAttributeSet("width", "100%", "border", "none");
        SinkEventAttributes attributes = new SinkEventAttributeSet();
        attributes.addAttribute(SinkEventAttributes.STYLE, style);
        delegate.table(attributes);
        delegate.tableRow();
        delegate.tableCell(new SinkEventAttributeSet(SinkEventAttributes.WIDTH, "20%", SinkEventAttributeSet.ALIGN, "left"));
        if (previousRef != null) {
            delegate.link(previousRef);
            delegate.text("<<");
            delegate.link_();
        } else {
            text(" ");
        }
        delegate.tableCell_();
        delegate.tableHeaderCell(new SinkEventAttributeSet(SinkEventAttributes.WIDTH, "60%", SinkEventAttributeSet.ALIGN, "center"));
        delegate.text(StringUtils.defaultString(currentName, " "));
        delegate.tableHeaderCell_();
        delegate.tableCell(new SinkEventAttributeSet(SinkEventAttributes.WIDTH, "20%", SinkEventAttributeSet.ALIGN, "right"));
        if (nextRef != null) {
            delegate.link(nextRef);
            delegate.text(">>");
            delegate.link_();
        } else {
            text(" ");
        }
        delegate.tableCell_();
        delegate.table_();
    }

    @Override
    public void author_() {
        delegate.author_();
    }

    @Override
    public void body() {
        body(null);
    }

    @Override
    public void body(SinkEventAttributes attributes) {
        delegate.body(attributes);
        navLinks();
        horizontalRule();
    }

    @Override
    public void body_() {
        isBody = false;
        horizontalRule();
        navLinks();
        delegate.body_();
    }

    @Override
    public void date_() {
        delegate.date_();
    }

    @Override
    public void head() {
        head(null);
    }

    @Override
    public void head(SinkEventAttributes attributes) {
        isHead = true;
        delegate.head(attributes);
    }

    @Override
    public void head_() {
        isHead = false;
        delegate.head_();
    }

    @Override
    public void title() {
        title(null);
    }

    @Override
    public void title(SinkEventAttributes attributes) {
        isTitle = true;
        textBuffer = new StringBuilder(128);
        delegate.title(attributes);
    }

    @Override
    public void title_() {
        isTitle = false;
        currentName = textBuffer.toString();
        textBuffer = null;
        delegate.title_();
    }

    @Override
    public void anchor(String name) {
        anchor(name, null);
    }

    @Override
    public void anchor( String name, SinkEventAttributes attributes )
    {
        delegate.anchor( name, attributes );
        if ( isSectionTitle )
        {
            if ( anchorsInSectionTitle == null )
            {
                anchorsInSectionTitle = new HashSet<String>();
            }
            anchorsInSectionTitle.add( name );
        }
    }


    @Override
    public void anchor_() {
        delegate.anchor_();
    }

    @Override
    public void bold() {
        delegate.bold();
    }

    @Override
    public void bold_() {
        delegate.bold_();
    }

    @Override
    public void close() {
        if (delegate != null) {
            delegate.close();
        }
        file_();
    }

    @Override
    public void comment(String comment) {
        delegate.comment(comment);
    }

    @Override
    public void definedTerm() {
        delegate.definedTerm();
    }

    @Override
    public void definedTerm(SinkEventAttributes attributes) {
        delegate.definedTerm(attributes);
    }

    @Override
    public void definedTerm_() {
        delegate.definedTerm_();
    }

    @Override
    public void definition() {
        delegate.definition();
    }

    @Override
    public void definition(SinkEventAttributes attributes) {
        delegate.definition(attributes);
    }

    @Override
    public void definition_() {
        delegate.definition_();
    }

    @Override
    public void definitionList() {
        delegate.definitionList();
    }

    @Override
    public void definitionList(SinkEventAttributes attributes) {
        delegate.definitionList(attributes);
    }

    @Override
    public void definitionList_() {
        delegate.definitionList_();
    }

    @Override
    public void figure() {
        delegate.figure();
    }

    @Override
    public void figure(SinkEventAttributes attributes) {
        delegate.figure(attributes);
    }

    @Override
    public void figure_() {
        delegate.figure_();
    }

    @Override
    public void figureCaption() {
        delegate.figureCaption();
    }

    @Override
    public void figureCaption(SinkEventAttributes attributes) {
        delegate.figureCaption(attributes);
    }

    @Override
    public void figureCaption_() {
        delegate.figureCaption_();
    }

    @Override
    public void figureGraphics(String name) {
        delegate.figureGraphics(name);
    }

    @Override
    public void figureGraphics(String src,
                               SinkEventAttributes attributes) {
        delegate.figureGraphics(src, attributes);
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public void horizontalRule() {
        delegate.horizontalRule();
    }

    @Override
    public void horizontalRule(SinkEventAttributes attributes) {
        delegate.horizontalRule(attributes);
    }

    @Override
    public void italic() {
        delegate.italic();
    }

    @Override
    public void italic_() {
        delegate.italic_();
    }

    @Override
    public void lineBreak() {
        delegate.lineBreak();
    }

    @Override
    public void lineBreak(SinkEventAttributes attributes) {
        delegate.lineBreak(attributes);
    }

    @Override
    public void link(String name) {
        delegate.link(name);
    }

    @Override
    public void link(String name, SinkEventAttributes attributes) {
        delegate.link(name, attributes);
    }

    @Override
    public void link_() {
        delegate.link_();
    }

    @Override
    public void list() {
        delegate.list();
    }

    @Override
    public void list(SinkEventAttributes attributes) {
        delegate.list(attributes);
    }

    @Override
    public void list_() {
        delegate.list_();
    }

    @Override
    public void listItem() {
        delegate.listItem();
    }

    @Override
    public void listItem(SinkEventAttributes attributes) {
        delegate.listItem(attributes);
    }

    @Override
    public void listItem_() {
        delegate.listItem_();
    }

    @Override
    public void monospaced() {
        delegate.monospaced();
    }

    @Override
    public void monospaced_() {
        delegate.monospaced_();
    }

    @Override
    public void nonBreakingSpace() {
        delegate.nonBreakingSpace();
    }

    @Override
    public void numberedList(int numbering) {
        delegate.numberedList(numbering);
    }

    @Override
    public void numberedList(int numbering, SinkEventAttributes attributes) {
        delegate.numberedList(numbering, attributes);
    }

    @Override
    public void numberedList_() {
        delegate.numberedList_();
    }

    @Override
    public void numberedListItem() {
        delegate.numberedListItem();
    }

    @Override
    public void numberedListItem(SinkEventAttributes attributes) {
        delegate.numberedListItem(attributes);
    }

    @Override
    public void numberedListItem_() {
        delegate.numberedListItem_();
    }

    @Override
    public void pageBreak() {
        delegate.pageBreak();
    }

    @Override
    public void paragraph() {
        delegate.paragraph();
    }

    @Override
    public void paragraph(SinkEventAttributes attributes) {
        delegate.paragraph(attributes);
    }

    @Override
    public void paragraph_() {
        delegate.paragraph_();
    }

    @Override
    public void rawText(String text) {
        delegate.rawText(text);
    }

    @Override
    public void section(int level, SinkEventAttributes attributes) {
        delegate.section(level, attributes);
    }

    @Override
    public void section1() {
        delegate.section1();
    }

    @Override
    public void section1_() {
        delegate.section1_();
    }

    @Override
    public void section2() {
        delegate.section2();
    }

    @Override
    public void section2_() {
        delegate.section2_();
    }

    @Override
    public void section3() {
        delegate.section3();
    }

    @Override
    public void section3_() {
        delegate.section3_();
    }

    @Override
    public void section4() {
        delegate.section4();
    }

    @Override
    public void section4_() {
        delegate.section4_();
    }

    @Override
    public void section5() {
        delegate.section5();
    }

    @Override
    public void section5_() {
        delegate.section5_();
    }

    @Override
    public void section_(int level) {
        delegate.section_(level);
    }

    /** {@inheritDoc} */
    protected void onSectionTitle( int depth, SinkEventAttributes attributes )
    {
        this.textBuffer = new StringBuilder();
        sectionHasID = ( attributes != null && attributes.isDefined ( HTML.Attribute.ID.toString() ) );
        isSectionTitle = true;
    }

    /** {@inheritDoc} */
    protected void onSectionTitle_( int depth )
    {
        String sectionTitle = textBuffer.toString();
        this.textBuffer = null;

        if ( !sectionHasID && !StringUtils.isEmpty(sectionTitle) )
        {
            String id = HtmlTools.encodeId(sectionTitle);
            if ( ( anchorsInSectionTitle == null ) || (! anchorsInSectionTitle.contains( id ) ) )
            {
                anchor( id );
                anchor_();
            }
        }
        else
        {
            sectionHasID = false;
        }

        this.isSectionTitle = false;
        anchorsInSectionTitle = null;
    }

    @Override
    public void sectionTitle() {
        delegate.sectionTitle();
        onSectionTitle(0, null);
    }

    @Override
    public void sectionTitle_() {
        delegate.sectionTitle_();
        onSectionTitle_(0);
    }

    @Override
    public void sectionTitle(int level, SinkEventAttributes attributes) {
        delegate.sectionTitle(level, attributes);
        onSectionTitle(level, attributes);
    }

    @Override
    public void sectionTitle1() {
        delegate.sectionTitle1();
        onSectionTitle(1, null);
    }

    @Override
    public void sectionTitle1_() {
        delegate.sectionTitle1_();
        onSectionTitle_(1);
    }

    @Override
    public void sectionTitle2() {
        delegate.sectionTitle2();
        onSectionTitle(2, null);
    }

    @Override
    public void sectionTitle2_() {
        delegate.sectionTitle2_();
        onSectionTitle_(2);
    }

    @Override
    public void sectionTitle3() {
        delegate.sectionTitle3();
        onSectionTitle(3, null);
    }

    @Override
    public void sectionTitle3_() {
        delegate.sectionTitle3_();
        onSectionTitle_(3);
    }

    @Override
    public void sectionTitle4() {
        delegate.sectionTitle4();
        onSectionTitle(4, null);
    }

    @Override
    public void sectionTitle4_() {
        delegate.sectionTitle4_();
        onSectionTitle_(4);
    }

    @Override
    public void sectionTitle5() {
        delegate.sectionTitle5();
        onSectionTitle(5, null);
    }

    @Override
    public void sectionTitle5_() {
        delegate.sectionTitle5_();
        onSectionTitle_(5);
    }

    @Override
    public void sectionTitle_(int level) {
        delegate.sectionTitle_(level);
        onSectionTitle_(level);
    }

    @Override
    public void table() {
        delegate.table();
    }

    @Override
    public void table(SinkEventAttributes attributes) {
        delegate.table(attributes);
    }

    @Override
    public void table_() {
        delegate.table_();
    }

    @Override
    public void tableCaption() {
        delegate.tableCaption();
    }

    @Override
    public void tableCaption(SinkEventAttributes attributes) {
        delegate.tableCaption(attributes);
    }

    @Override
    public void tableCaption_() {
        delegate.tableCaption_();
    }

    @Override
    public void tableCell() {
        delegate.tableCell();
    }

    @Override
    public void tableCell(SinkEventAttributes attributes) {
        delegate.tableCell(attributes);
    }

    @Override
    public void tableCell(String width) {
        delegate.tableCell(width);
    }

    @Override
    public void tableCell_() {
        delegate.tableCell_();
    }

    @Override
    public void tableHeaderCell() {
        delegate.tableHeaderCell();
    }

    @Override
    public void tableHeaderCell(SinkEventAttributes attributes) {
        delegate.tableHeaderCell(attributes);
    }

    @Override
    public void tableHeaderCell(String width) {
        delegate.tableHeaderCell(width);
    }

    @Override
    public void tableHeaderCell_() {
        delegate.tableHeaderCell_();
    }

    @Override
    public void tableRow() {
        delegate.tableRow();
    }

    @Override
    public void tableRow(SinkEventAttributes attributes) {
        delegate.tableRow(attributes);
    }

    @Override
    public void tableRow_() {
        delegate.tableRow_();
    }

    @Override
    public void tableRows(int[] justification, boolean grid) {
        delegate.tableRows(justification, grid);
    }

    @Override
    public void tableRows_() {
        delegate.tableRows_();
    }

    @Override
    public void text(String text) {
        text(text, null);
    }

    @Override
    public void text(String text, SinkEventAttributes attributes) {
        if ( textBuffer != null )
        {
            // this implies we're interested in collecting text events
            textBuffer.append(text);
        }
        delegate.text(text, attributes);
    }

    @Override
    public void unknown(String name, Object[] requiredParams,
                        SinkEventAttributes attributes) {
        delegate.unknown(name, requiredParams, attributes);
    }

    @Override
    public void verbatim(SinkEventAttributes attributes) {
        delegate.verbatim(attributes);
    }

    @Override
    public void verbatim(boolean boxed) {
        delegate.verbatim(boxed);
    }

    @Override
    public void verbatim_() {
        delegate.verbatim_();
    }

    @Override
    public void author() {
        delegate.author();
    }

    @Override
    public void author(SinkEventAttributes attributes) {
        delegate.author(attributes);
    }

    @Override
    public void date() {
        delegate.date();
    }

    @Override
    public void date(SinkEventAttributes attributes) {
        delegate.date(attributes);
    }

    @Override
    public void definitionListItem() {
        delegate.definitionListItem();
    }

    @Override
    public void definitionListItem(SinkEventAttributes attributes) {
        delegate.definitionListItem(attributes);
    }

    @Override
    public void definitionListItem_() {
        delegate.definitionListItem_();
    }

    private static class NoCloseOutputStream extends OutputStream {
        private final OutputStream delegate;

        private NoCloseOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() throws IOException {
            delegate.flush();
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void write(byte[] b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }
    }
}
