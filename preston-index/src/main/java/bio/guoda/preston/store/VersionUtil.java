package bio.guoda.preston.store;

import bio.guoda.preston.RefNodeFactory;
import bio.guoda.preston.process.StatementListener;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDFTerm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static bio.guoda.preston.RefNodeConstants.HAS_PREVIOUS_VERSION;
import static bio.guoda.preston.RefNodeConstants.HAS_VERSION;
import static bio.guoda.preston.RefNodeConstants.USED_BY;
import static bio.guoda.preston.RefNodeConstants.WAS_DERIVED_FROM;
import static bio.guoda.preston.RefNodeFactory.toIRI;
import static bio.guoda.preston.RefNodeFactory.toStatement;

public class VersionUtil {

    public static final String VERSION_PATTERN = "[^<>]*";
    private static final Pattern PATTERN_SUBJECT_NEWER = Pattern.compile("<(?<subj>" + VERSION_PATTERN + ")> (" + HAS_PREVIOUS_VERSION.toString() + "|" + WAS_DERIVED_FROM.toString() + "|" + USED_BY + ") (.*) [.]$");
    private static final Pattern PATTERN_SUBJECT_NEWER_USED_BY_ONLY = Pattern.compile("<(?<subj>" + VERSION_PATTERN + ")> (" + USED_BY + ") (.*) [.]$");
    private static final Pattern PATTERN_OBJECT_NEWER = Pattern.compile(".* (" + HAS_VERSION.toString() + ") <(?<obj>" + VERSION_PATTERN + ")>(.*) [.]$");
    private static final Pattern PATTERN_VERSION_STATEMENT = Pattern.compile("^<(?<subj>" + VERSION_PATTERN + ")> (" + HAS_VERSION.toString() + ") <(?<obj>" + VERSION_PATTERN + ")>(.*) [.]$");

    public static IRI findMostRecentVersion(IRI provenanceRoot, HexaStore hexastore) throws IOException {
        return findMostRecentVersion(provenanceRoot, hexastore, null);
    }

    static IRI findMostRecentVersion(IRI provenanceRoot, HexaStoreReadOnly statementStore, StatementListener versionListener) throws IOException {
        IRI mostRecentVersion = findVersion(provenanceRoot, statementStore, versionListener);
        if (mostRecentVersion == null) {
            mostRecentVersion = findByPreviousVersion(provenanceRoot, statementStore, versionListener);
        }

        List<IRI> versions = new ArrayList<>();
        versions.add(mostRecentVersion);

        if (mostRecentVersion != null) {
            IRI lastVersionId = mostRecentVersion;
            IRI newerVersionId;

            while ((newerVersionId = findByPreviousVersion(lastVersionId, statementStore, versionListener)) != null) {
                versions.add(mostRecentVersion);
                if (versions.contains(newerVersionId)) {
                    break;
                } else {
                    versions.add(newerVersionId);
                }
                lastVersionId = newerVersionId;
            }
            mostRecentVersion = lastVersionId;
        }

        return mostRecentVersion;
    }

    private static IRI findByPreviousVersion(IRI versionSource, HexaStoreReadOnly statementStore, StatementListener versionListener) throws IOException {
        IRI mostRecentVersion = statementStore.get(Pair.of(HAS_PREVIOUS_VERSION, versionSource));

        if (versionListener != null && mostRecentVersion != null) {
            versionListener.on(toStatement(mostRecentVersion, HAS_PREVIOUS_VERSION, versionSource));
        }
        return mostRecentVersion;
    }

    private static IRI findVersion(IRI provenanceRoot, HexaStoreReadOnly statementStore, StatementListener versionListener) throws IOException {
        IRI mostRecentVersion = statementStore.get(Pair.of(provenanceRoot, HAS_VERSION));

        if (versionListener != null && mostRecentVersion != null) {
            versionListener.on(toStatement(provenanceRoot, HAS_VERSION, mostRecentVersion));
        }
        return mostRecentVersion;
    }

    public static IRI mostRecentVersion(Quad statement) {
        IRI mostRecentVersion = null;
        RDFTerm mostRecentTerm = null;
        if (statement.getPredicate().equals(HAS_PREVIOUS_VERSION)
                || statement.getPredicate().equals(WAS_DERIVED_FROM)
                || statement.getPredicate().equals(USED_BY)) {
            mostRecentTerm = statement.getSubject();
        } else if (statement.getPredicate().equals(HAS_VERSION)) {
            mostRecentTerm = statement.getObject();
        }

        if (mostRecentTerm instanceof IRI) {
            IRI mostRecentIRI = (IRI) mostRecentTerm;
            if (!RefNodeFactory.isBlankOrSkolemizedBlank(mostRecentIRI)) {
                mostRecentVersion = mostRecentIRI;
            }
        }
        return mostRecentVersion;
    }

    static IRI mostRecentVersion(String someStatement) {
        String newerVersionString;
        final Matcher matcherSubject = PATTERN_SUBJECT_NEWER.matcher(someStatement);
        if (matcherSubject.matches()) {
            newerVersionString = matcherSubject.group("subj");
        } else {
            final Matcher matcherObject = PATTERN_OBJECT_NEWER.matcher(someStatement);
            newerVersionString = matcherObject.matches() ? matcherObject.group("obj") : null;
        }

        return StringUtils.isNotBlank(newerVersionString) ?
                RefNodeFactory.toIRI(newerVersionString)
                : null;
    }

    public static IRI mostRecentVersionUsedBy(String someStatement) {
        String newerVersionString;
        final Matcher matcherSubject = PATTERN_SUBJECT_NEWER_USED_BY_ONLY.matcher(someStatement);
        newerVersionString = matcherSubject.matches() ? matcherSubject.group("subj") : null;

        return StringUtils.isNotBlank(newerVersionString) ?
                RefNodeFactory.toIRI(newerVersionString)
                : null;
    }

    public static boolean maybeNotQuad(String line) {
        return !StringUtils.endsWith(line, " .");
    }

    private static IRI getMostRecentContentId(String line) {
        IRI iri = mostRecentVersion(line);
        if (iri == null && maybeNotQuad(line)) {
            try {
                iri = toIRI(StringUtils.trim(line));
            } catch (IllegalArgumentException ex) {
                // ignore invalid IRIs
            }
        }

        return iri != null && HashKeyUtil.isValidHashKey(iri) ? iri : null;
    }

    public static Quad parseAsVersionStatementOrNull(String line) {
        Quad aQuad = null;
        final Matcher versionStatement = PATTERN_VERSION_STATEMENT.matcher(line);
        if (versionStatement.matches()) {
            IRI versionObject = toIRI(versionStatement.group("obj"));
            IRI versionSubject = toIRI(versionStatement.group("subj"));
            if (HashKeyUtil.isValidHashKey(versionObject)) {
                aQuad = toStatement(versionSubject, HAS_VERSION, versionObject);
            }
        }

        if (aQuad == null) {
            IRI contentId = getMostRecentContentId(line);
            if (contentId != null) {
                aQuad = toStatement(RefNodeFactory.toBlank(), HAS_VERSION, contentId);
            }
        }
        return aQuad;
    }
}
