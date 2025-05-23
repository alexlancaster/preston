package bio.guoda.preston.zenodo;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.rdf.api.IRI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ZenodoContext implements ZenodoConfig {

    private final String endpoint;
    private final List<String> communities;
    private final IRI licenseRelations;
    private Long depositId;
    private UUID bucketId;
    private final String accessToken;
    private JsonNode metadata;

    private boolean createNewVersionForExisting = false;
    private boolean restrictedOnly = false;
    private boolean updateMetadataOnly = false;
    private boolean allowEmptyPublicationDate = false;
    private String tmpDir;
    private boolean explicitLicenseOnly = false;

    public ZenodoContext(String accessToken) {
        this(accessToken, "https://sandbox.zenodo.org");
    }

    public ZenodoContext(String accessToken, String endpoint) {
        this(accessToken, endpoint, Collections.emptyList());
    }

    public ZenodoContext(String accessToken, String endpoint, List<String> communities) {
        this(accessToken, endpoint, communities, null);
    }

    public ZenodoContext(String accessToken, String endpoint, List<String> communities, IRI licenseRelations) {
        this.accessToken = accessToken;
        this.endpoint = endpoint;
        this.communities = communities;
        this.licenseRelations = licenseRelations;
    }

    public ZenodoContext(ZenodoConfig config) {
        this.accessToken = config.getAccessToken();
        this.endpoint = config.getEndpoint();
        this.communities = new ArrayList<>(config.getCommunities());
        this.createNewVersionForExisting = config.createNewVersionForExisting();
        this.licenseRelations = config.getLicenseRelations();
    }


    @Override
    public String getAccessToken() {
        return accessToken;
    }

    public UUID getBucketId() {
        return bucketId;
    }

    public void setBucketId(UUID bucketId) {
        this.bucketId = bucketId;
    }

    public Long getDepositId() {
        return depositId;
    }

    public void setDepositId(Long depositId) {
        this.depositId = depositId;
    }

    @Override
    public String getEndpoint() {
        return endpoint;
    }

    @Override
    public List<String> getCommunities() {
        return this.communities;
    }

    @Override
    public List<Pair<String, IRI>> getFileVersions() {
        return new ArrayList<>();
    }

    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
    }

    public JsonNode getMetadata() {
        return metadata;
    }

    @Override
    public void setCreateNewVersionForExisting(Boolean skipOnExisting) {
        this.createNewVersionForExisting = skipOnExisting;
    }

    @Override
    public boolean createNewVersionForExisting() {
        return createNewVersionForExisting;
    }


    @Override
    public void setPublishRestrictedOnly(boolean restrictedOnly) {
        this.restrictedOnly = restrictedOnly;
    }

    @Override
    public boolean shouldPublishRestrictedOnly() {
        return restrictedOnly;
    }

    @Override
    public void setUpdateMetadataOnly(boolean updateMetadataOnly) {
        this.updateMetadataOnly = updateMetadataOnly;
    }
    @Override
    public boolean shouldUpdateMetadataOnly() {
        return updateMetadataOnly;
    }

    @Override
    public void setAllowEmptyPublicationDate(boolean allowEmptyPublicationDate) {
        this.allowEmptyPublicationDate = allowEmptyPublicationDate;
    }

    @Override
    public boolean shouldAllowEmptyPublicationDate() {
        return allowEmptyPublicationDate;
    }

    public IRI getLicenseRelations() {
        return licenseRelations;
    }

    public void setTmpDir(String tmpDir) {
        this.tmpDir = tmpDir;
    }

    public String getTmpDir() {
        return tmpDir;
    }

    @Override
    public void setExplicitLicenseOnly(boolean explicitLicenseOnly) {
        this.explicitLicenseOnly = explicitLicenseOnly;
    }

    @Override
    public boolean shouldAllowExplicitLicenseOnly() {
        return this.explicitLicenseOnly;
    }


}
