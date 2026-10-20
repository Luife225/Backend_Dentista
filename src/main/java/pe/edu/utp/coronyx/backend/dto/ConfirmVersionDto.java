package pe.edu.utp.coronyx.backend.dto;

public class ConfirmVersionDto {
    private Long revision;
    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }


    private Long versionId;
    private Integer numeroVersion;

    public ConfirmVersionDto() {
    }

    public ConfirmVersionDto(Long versionId, Integer numeroVersion) {
        this.versionId = versionId;
        this.numeroVersion = numeroVersion;
    }

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public Integer getNumeroVersion() {
        return numeroVersion;
    }

    public void setNumeroVersion(Integer numeroVersion) {
        this.numeroVersion = numeroVersion;
    }
}
