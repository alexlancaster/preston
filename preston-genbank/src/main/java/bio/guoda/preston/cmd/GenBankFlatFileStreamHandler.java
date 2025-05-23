package bio.guoda.preston.cmd;

import bio.guoda.preston.store.Dereferencer;
import bio.guoda.preston.stream.ContentStreamException;
import bio.guoda.preston.stream.ContentStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.rdf.api.IRI;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.txt.UniversalEncodingDetector;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

public class GenBankFlatFileStreamHandler implements ContentStreamHandler {

    public static final String PREFIX_ACCESSION = "ACCESSION   ";
    public static final String PREFIX_DEFINITION = "DEFINITION  ";
    private static final CharSequence PREFIX_SPECIMEN_VOUCHER = "                     /specimen_voucher=\"";
    private static final CharSequence PREFIX_DB_XREF = "                     /db_xref=\"";
    private static final CharSequence PREFIX_ORGANISM = "                     /organism=\"";
    private static final CharSequence PREFIX_ISOLATION_SOURCE = "                     /isolation_source=\"";
    private static final CharSequence PREFIX_HOST = "                     /host=\"";
    private static final CharSequence PREFIX_COUNTRY = "                     /country=\"";

    private final Dereferencer<InputStream> dereferencer;
    private ContentStreamHandler contentStreamHandler;
    private final OutputStream outputStream;

    public GenBankFlatFileStreamHandler(ContentStreamHandler contentStreamHandler,
                                        Dereferencer<InputStream> inputStreamDereferencer,
                                        OutputStream os) {
        this.contentStreamHandler = contentStreamHandler;
        this.dereferencer = inputStreamDereferencer;
        this.outputStream = os;
    }

    @Override
    public boolean handle(IRI version, InputStream is) throws ContentStreamException {
        AtomicBoolean foundAtLeastOne = new AtomicBoolean(false);
        String iriString = version.getIRIString();
        try {
            Charset charset = new UniversalEncodingDetector().detect(is, new Metadata());
            if (charset != null) {
                int lineStart = -1;
                int lineFinish = -1;
                StringBuilder definition = new StringBuilder();
                AtomicBoolean inDefinition = new AtomicBoolean(false);
                ObjectNode objectNode = new ObjectMapper().createObjectNode();

                BufferedReader reader = new BufferedReader(new InputStreamReader(is, charset));

                for (int lineNumber = 1; contentStreamHandler.shouldKeepProcessing(); ++lineNumber) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    } else {
                        if (StringUtils.startsWith(line, "LOCUS")) {
                            lineStart = lineNumber;
                            objectNode.removeAll();
                            inDefinition.set(false);
                            definition = new StringBuilder();
                        } else if (StringUtils.startsWith(line, "//")) {
                            lineFinish = lineNumber;
                            if (lineFinish > lineStart) {
                                setValue(objectNode, "http://www.w3.org/ns/prov#wasDerivedFrom", "line:" + iriString + "!/L" + lineStart + "-" + "L" + lineFinish);
                                setValue(objectNode, "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "genbank-flatfile");
                            }
                        } else if (StringUtils.startsWith(line, PREFIX_ACCESSION)) {
                            inDefinition.set(false);
                            String value = getValueWithLinePrefix(line, PREFIX_ACCESSION);
                            setValue(objectNode, "accession", StringUtils.split(value, " ")[0]);
                            setValue(objectNode, "http://www.w3.org/2000/01/rdf-schema#seeAlso", String.format("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=nuccore&id=%s&rettype=gb&retmode=text", value));
                            setValue(objectNode, "http://www.w3.org/2000/01/rdf-schema#seeAlso", String.format("https://ncbi.nlm.nih.gov/nuccore/%s", value));
                            setValue(objectNode, definition.toString(), "definition", "");
                        } else if (StringUtils.startsWith(line, PREFIX_DEFINITION)) {
                            inDefinition.set(true);
                            definition.append(StringUtils.substring(line, PREFIX_DEFINITION.length()));
                        } else if (StringUtils.startsWith(line, PREFIX_SPECIMEN_VOUCHER)) {
                            setValueForFeature(objectNode, line, "specimen_voucher", PREFIX_SPECIMEN_VOUCHER);
                        } else if (StringUtils.startsWith(line, PREFIX_HOST)) {
                            setValueForFeature(objectNode, line, "host", PREFIX_HOST);
                        } else if (StringUtils.startsWith(line, PREFIX_DB_XREF)) {
                            setValueForFeature(objectNode, line, "db_xref", PREFIX_DB_XREF);
                        } else if (StringUtils.startsWith(line, PREFIX_COUNTRY)) {
                            setValueForFeature(objectNode, line, "country", PREFIX_COUNTRY);
                        } else if (StringUtils.startsWith(line, PREFIX_ORGANISM)) {
                            setValueForFeature(objectNode, line, "organism", PREFIX_ORGANISM);
                        } else if (StringUtils.startsWith(line, PREFIX_ISOLATION_SOURCE)) {
                            setValueForFeature(objectNode, line, "isolation_source", PREFIX_ISOLATION_SOURCE);
                        } else if (inDefinition.get()) {
                            definition.append(" ").append(StringUtils.trim(line));
                        }

                        if (lineFinish > lineStart && objectNode.has("accession")) {
                            IOUtils.copy(IOUtils.toInputStream(objectNode.toString(), StandardCharsets.UTF_8), outputStream);
                            IOUtils.copy(IOUtils.toInputStream("\n", StandardCharsets.UTF_8), outputStream);
                            lineStart = -1;
                            lineFinish = -1;
                            foundAtLeastOne.set(true);
                        }
                    }
                }
            }

        } catch (IOException e) {
            throw new ContentStreamException("no charset detected");
        }

        return foundAtLeastOne.get();
    }

    private void setValueForFeature(ObjectNode objectNode,
                                    String line,
                                    String key,
                                    CharSequence keyValue) {
        int end = line.length() - 1;
        int start = keyValue.length();
        setValue(objectNode, key, StringUtils.trim(
                StringUtils.substring(line, start, end)));
    }

    private void setValue(ObjectNode objectNode, String key, String value) {
        objectNode.set(key, TextNode.valueOf(value));
    }

    private void setValue(ObjectNode objectNode,
                          String line,
                          String key,
                          CharSequence prefix) {
        String value = getValueWithLinePrefix(line, prefix);
        setValue(objectNode, key, value);
    }

    private static String getValueWithLinePrefix(String line, CharSequence prefix) {
        int start = prefix.length();
        int end = line.length();
        return StringUtils.trim(
                StringUtils.substring(line, start, end));
    }

    @Override
    public boolean shouldKeepProcessing() {
        return contentStreamHandler.shouldKeepProcessing();
    }


}
