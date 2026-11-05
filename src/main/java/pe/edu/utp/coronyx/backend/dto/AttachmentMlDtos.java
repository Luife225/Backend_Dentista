package pe.edu.utp.coronyx.backend.dto;

import java.util.List;

/**
 * DTOs para la ejecución y revisión clínica del análisis ML sobre radiografías (Fase I).
 * Respeta la regla de representar todos los identificadores como Strings decimales.
 */
public final class AttachmentMlDtos {

    private AttachmentMlDtos() {
    }

    public static class MlBboxDto {
        private Double xMin;
        private Double yMin;
        private Double xMax;
        private Double yMax;

        public MlBboxDto() {
        }

        public MlBboxDto(Double xMin, Double yMin, Double xMax, Double yMax) {
            this.xMin = xMin;
            this.yMin = yMin;
            this.xMax = xMax;
            this.yMax = yMax;
        }

        public Double getxMin() {
            return xMin;
        }

        public void setxMin(Double xMin) {
            this.xMin = xMin;
        }

        public Double getyMin() {
            return yMin;
        }

        public void setyMin(Double yMin) {
            this.yMin = yMin;
        }

        public Double getxMax() {
            return xMax;
        }

        public void setxMax(Double xMax) {
            this.xMax = xMax;
        }

        public Double getyMax() {
            return yMax;
        }

        public void setyMax(Double yMax) {
            this.yMax = yMax;
        }
    }

    public static class MlFindingReviewItemDto {
        private String findingId;
        private String decision;
        private String correctedClassName;
        private MlBboxDto correctedBboxPixels;
        private String clinicalNote;

        public MlFindingReviewItemDto() {
        }

        public MlFindingReviewItemDto(String findingId, String decision, String correctedClassName,
                                      MlBboxDto correctedBboxPixels, String clinicalNote) {
            this.findingId = findingId;
            this.decision = decision;
            this.correctedClassName = correctedClassName;
            this.correctedBboxPixels = correctedBboxPixels;
            this.clinicalNote = clinicalNote;
        }

        public String getFindingId() {
            return findingId;
        }

        public void setFindingId(String findingId) {
            this.findingId = findingId;
        }

        public String getDecision() {
            return decision;
        }

        public void setDecision(String decision) {
            this.decision = decision;
        }

        public String getCorrectedClassName() {
            return correctedClassName;
        }

        public void setCorrectedClassName(String correctedClassName) {
            this.correctedClassName = correctedClassName;
        }

        public MlBboxDto getCorrectedBboxPixels() {
            return correctedBboxPixels;
        }

        public void setCorrectedBboxPixels(MlBboxDto correctedBboxPixels) {
            this.correctedBboxPixels = correctedBboxPixels;
        }

        public String getClinicalNote() {
            return clinicalNote;
        }

        public void setClinicalNote(String clinicalNote) {
            this.clinicalNote = clinicalNote;
        }
    }

    /**
     * Solicitud de revisión clínica de hallazgos ML por parte del odontólogo.
     * Soporta tanto la revisión individual de un hallazgo (findingId + decision)
     * como un lote de hallazgos (reviews) o una decisión global (overallDecision).
     */
    public static class MlReviewRequestDto {
        private String findingId;
        private String decision;
        private String correctedClassName;
        private MlBboxDto correctedBboxPixels;
        private String clinicalNote;
        private List<MlFindingReviewItemDto> reviews;
        private String overallDecision;
        private String overallClinicalNote;

        public MlReviewRequestDto() {
        }

        public String getFindingId() {
            return findingId;
        }

        public void setFindingId(String findingId) {
            this.findingId = findingId;
        }

        public String getDecision() {
            return decision;
        }

        public void setDecision(String decision) {
            this.decision = decision;
        }

        public String getCorrectedClassName() {
            return correctedClassName;
        }

        public void setCorrectedClassName(String correctedClassName) {
            this.correctedClassName = correctedClassName;
        }

        public MlBboxDto getCorrectedBboxPixels() {
            return correctedBboxPixels;
        }

        public void setCorrectedBboxPixels(MlBboxDto correctedBboxPixels) {
            this.correctedBboxPixels = correctedBboxPixels;
        }

        public String getClinicalNote() {
            return clinicalNote;
        }

        public void setClinicalNote(String clinicalNote) {
            this.clinicalNote = clinicalNote;
        }

        public List<MlFindingReviewItemDto> getReviews() {
            return reviews;
        }

        public void setReviews(List<MlFindingReviewItemDto> reviews) {
            this.reviews = reviews;
        }

        public String getOverallDecision() {
            return overallDecision;
        }

        public void setOverallDecision(String overallDecision) {
            this.overallDecision = overallDecision;
        }

        public String getOverallClinicalNote() {
            return overallClinicalNote;
        }

        public void setOverallClinicalNote(String overallClinicalNote) {
            this.overallClinicalNote = overallClinicalNote;
        }
    }
}

