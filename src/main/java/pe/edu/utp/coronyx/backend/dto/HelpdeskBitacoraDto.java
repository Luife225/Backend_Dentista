package pe.edu.utp.coronyx.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HelpdeskBitacoraDto(
        Long id,
        String ticketId,
        String fechaHora,
        String estadoAnterior,
        String estadoNuevo,
        String usuarioNombre,
        String usuarioRol,
        String accion,
        @JsonAlias({"comentario", "comentarioEvidencia"})
        String comentarioEvidencia
) {
        public String comentario() {
                return comentarioEvidencia;
        }
}
