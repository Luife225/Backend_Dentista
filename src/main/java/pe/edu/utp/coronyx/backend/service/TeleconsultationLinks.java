package pe.edu.utp.coronyx.backend.service;
import java.net.URI;

public final class TeleconsultationLinks {
    private TeleconsultationLinks() {}
    public static String validate(String value) {
        if (value==null || value.isBlank()) return null;
        try {
            URI uri=URI.create(value.trim()); String host=uri.getHost();
            boolean meet="meet.google.com".equalsIgnoreCase(host) && uri.getPath().matches("/[a-z]{3}-[a-z]{4}-[a-z]{3}");
            boolean zoom=host!=null && (host.equalsIgnoreCase("zoom.us") || host.toLowerCase(java.util.Locale.ROOT).endsWith(".zoom.us")) && uri.getPath().matches("/j/[0-9]+");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo()!=null || uri.getPort()!=-1 || (!meet && !zoom))
                throw new IllegalArgumentException();
            return uri.toString();
        } catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Ingrese un enlace HTTPS de una reunión de Google Meet o Zoom válida"); }
    }
}
