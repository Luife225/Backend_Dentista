package pe.edu.utp.coronyx.backend.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Locale;

/** Verifica contenido y extensión; el MIME enviado por el navegador no concede confianza. */
public final class FileContentValidator {
    private FileContentValidator() {}
    public static String detect(MultipartFile file) {
        byte[] h;
        try (var in=file.getInputStream()) { h=in.readNBytes(132); }
        catch (IOException ex) { throw new IllegalArgumentException("No se pudo leer el contenido del archivo",ex); }
        String name=(file.getOriginalFilename()==null?"":file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf") && starts(h,0,0x25,0x50,0x44,0x46,0x2d)) return "application/pdf";
        if ((name.endsWith(".jpg") || name.endsWith(".jpeg")) && starts(h,0,0xff,0xd8,0xff)) return "image/jpeg";
        if (name.endsWith(".png") && starts(h,0,0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a)) return "image/png";
        if (name.endsWith(".dcm") && starts(h,128,0x44,0x49,0x43,0x4d)) return "application/dicom";
        throw new IllegalArgumentException("Contenido no válido. Se admiten PDF, JPEG, PNG y DICOM (.dcm con encabezado DICM).");
    }
    private static boolean starts(byte[] data, int offset, int... prefix) {
        if (data.length<offset+prefix.length) return false;
        for (int i=0;i<prefix.length;i++) if ((data[offset+i]&0xff)!=prefix[i]) return false;
        return true;
    }
}
