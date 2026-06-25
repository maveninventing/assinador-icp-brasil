package br.com.privacytools.assinador.util;

import java.nio.file.Path;

public final class FileNameUtils {
    private FileNameUtils() {
    }

    public static Path signedOutput(Path source) {
        String fileName = source.getFileName().toString();
        int extensionIndex = fileName.toLowerCase().lastIndexOf(".pdf");
        String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        return source.resolveSibling(baseName + "_assinado.pdf");
    }
}
