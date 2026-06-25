package br.com.privacytools.assinador.signing;

import br.com.privacytools.assinador.certificate.CertificateEntry;
import br.com.privacytools.assinador.certificate.CertificateNameUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.List;

public final class PdfSignerService {
    private static final int PREFERRED_SIGNATURE_SIZE = 65_536;
    private static final DateTimeFormatter VISIBLE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public void sign(
            Path input,
            Path output,
            CertificateEntry certificateEntry,
            SignaturePlacement placement,
            String reason
    ) throws Exception {
        if (input == null || !Files.isRegularFile(input)) {
            throw new IOException("O PDF de origem não foi encontrado.");
        }
        if (placement == null) {
            throw new IllegalArgumentException("Posicione a assinatura no documento.");
        }

        PrivateKey privateKey = certificateEntry.loadPrivateKey();
        if (privateKey == null) {
            throw new IllegalStateException("A chave privada do certificado não está disponível.");
        }
        X509Certificate[] chain = certificateEntry.loadCertificateChain();
        chain[0].checkValidity();

        Files.deleteIfExists(output);

        try (PDDocument document = Loader.loadPDF(input.toFile());
             OutputStream outputStream = Files.newOutputStream(
                     output,
                     StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE
             );
             SignatureOptions options = new SignatureOptions()) {

            if (document.isEncrypted()) {
                throw new IOException("PDF protegido por senha ou criptografado não é suportado nesta versão.");
            }
            if (placement.pageIndex() < 0 || placement.pageIndex() >= document.getNumberOfPages()) {
                throw new IllegalArgumentException("A página selecionada não existe no PDF.");
            }

            Calendar signingTime = Calendar.getInstance();
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ETSI_CADES_DETACHED);
            signature.setName(CertificateNameUtils.commonName(chain[0]));
            signature.setReason(normalizeReason(reason));
            signature.setLocation("Brasil");
            signature.setSignDate(signingTime);

            PDRectangle signatureRectangle = createSignatureRectangle(
                    document.getPage(placement.pageIndex()),
                    placement.humanRectangle()
            );

            byte[] visualTemplate = createVisualSignatureTemplate(
                    document,
                    placement.pageIndex(),
                    signatureRectangle,
                    chain[0],
                    signingTime,
                    normalizeReason(reason)
            );

            try (InputStream visualStream = new ByteArrayInputStream(visualTemplate)) {
                options.setVisualSignature(visualStream);
                options.setPage(placement.pageIndex());
                options.setPreferredSignatureSize(PREFERRED_SIGNATURE_SIZE);

                CmsSignature cmsSignature = new CmsSignature(privateKey, chain, certificateEntry.provider());
                document.addSignature(signature, cmsSignature, options);
                document.saveIncremental(outputStream);
            }
        } catch (Exception error) {
            Files.deleteIfExists(output);
            throw error;
        }
    }

    private PDRectangle createSignatureRectangle(PDPage page, Rectangle2D humanRectangle) {
        float x = (float) humanRectangle.getX();
        float y = (float) humanRectangle.getY();
        float width = (float) humanRectangle.getWidth();
        float height = (float) humanRectangle.getHeight();
        PDRectangle crop = page.getCropBox();
        float llx = crop.getLowerLeftX();
        float lly = crop.getLowerLeftY();
        float pageWidth = crop.getWidth();
        float pageHeight = crop.getHeight();

        PDRectangle rectangle = new PDRectangle();
        switch (normalizedRotation(page.getRotation())) {
            case 90 -> {
                rectangle.setLowerLeftY(lly + x);
                rectangle.setUpperRightY(lly + x + width);
                rectangle.setLowerLeftX(llx + y);
                rectangle.setUpperRightX(llx + y + height);
            }
            case 180 -> {
                rectangle.setUpperRightX(llx + pageWidth - x);
                rectangle.setLowerLeftX(llx + pageWidth - x - width);
                rectangle.setLowerLeftY(lly + y);
                rectangle.setUpperRightY(lly + y + height);
            }
            case 270 -> {
                rectangle.setLowerLeftY(lly + pageHeight - x - width);
                rectangle.setUpperRightY(lly + pageHeight - x);
                rectangle.setLowerLeftX(llx + pageWidth - y - height);
                rectangle.setUpperRightX(llx + pageWidth - y);
            }
            default -> {
                rectangle.setLowerLeftX(llx + x);
                rectangle.setUpperRightX(llx + x + width);
                rectangle.setLowerLeftY(lly + pageHeight - y - height);
                rectangle.setUpperRightY(lly + pageHeight - y);
            }
        }
        return rectangle;
    }

    private byte[] createVisualSignatureTemplate(
            PDDocument sourceDocument,
            int pageIndex,
            PDRectangle rectangle,
            X509Certificate signerCertificate,
            Calendar signingTime,
            String reason
    ) throws IOException {
        try (PDDocument template = new PDDocument()) {
            PDPage sourcePage = sourceDocument.getPage(pageIndex);
            PDPage page = new PDPage(sourcePage.getMediaBox());
            template.addPage(page);

            PDAcroForm acroForm = new PDAcroForm(template);
            template.getDocumentCatalog().setAcroForm(acroForm);
            acroForm.setSignaturesExist(true);
            acroForm.setAppendOnly(true);
            acroForm.getCOSObject().setDirect(true);

            PDSignatureField signatureField = new PDSignatureField(acroForm);
            List<org.apache.pdfbox.pdmodel.interactive.form.PDField> fields = acroForm.getFields();
            fields.add(signatureField);

            PDAnnotationWidget widget = signatureField.getWidgets().get(0);
            widget.setRectangle(rectangle);

            PDStream stream = new PDStream(template);
            PDFormXObject form = new PDFormXObject(stream);
            PDResources resources = new PDResources();
            form.setResources(resources);
            form.setFormType(1);

            PDRectangle boundingBox = new PDRectangle(rectangle.getWidth(), rectangle.getHeight());
            float contentHeight = boundingBox.getHeight();
            Matrix initialScale = null;
            switch (normalizedRotation(sourcePage.getRotation())) {
                case 90 -> {
                    form.setMatrix(AffineTransform.getQuadrantRotateInstance(1));
                    initialScale = Matrix.getScaleInstance(
                            boundingBox.getWidth() / boundingBox.getHeight(),
                            boundingBox.getHeight() / boundingBox.getWidth()
                    );
                    contentHeight = boundingBox.getWidth();
                }
                case 180 -> form.setMatrix(AffineTransform.getQuadrantRotateInstance(2));
                case 270 -> {
                    form.setMatrix(AffineTransform.getQuadrantRotateInstance(3));
                    initialScale = Matrix.getScaleInstance(
                            boundingBox.getWidth() / boundingBox.getHeight(),
                            boundingBox.getHeight() / boundingBox.getWidth()
                    );
                    contentHeight = boundingBox.getWidth();
                }
                default -> {
                    // Sem rotação.
                }
            }
            form.setBBox(boundingBox);

            PDAppearanceDictionary appearance = new PDAppearanceDictionary();
            appearance.getCOSObject().setDirect(true);
            PDAppearanceStream appearanceStream = new PDAppearanceStream(form.getCOSObject());
            appearance.setNormalAppearance(appearanceStream);
            widget.setAppearance(appearance);

            PDFont regularFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            try (PDPageContentStream canvas = new PDPageContentStream(template, appearanceStream)) {
                if (initialScale != null) {
                    canvas.transform(initialScale);
                }

                float width = boundingBox.getWidth();
                float height = contentHeight;
                canvas.setNonStrokingColor(new Color(248, 250, 252));
                canvas.addRect(0, 0, width, height);
                canvas.fill();
                canvas.setStrokingColor(new Color(45, 80, 120));
                canvas.setLineWidth(1.2f);
                canvas.addRect(1, 1, Math.max(1, width - 2), Math.max(1, height - 2));
                canvas.stroke();

                float margin = Math.max(5f, Math.min(10f, width * 0.04f));
                float availableWidth = Math.max(20f, width - (2 * margin));
                float y = height - margin - 10f;

                y = drawLine(canvas, boldFont, "ASSINADO DIGITALMENTE", margin, y, availableWidth, 10f, 7f);
                y = drawLine(
                        canvas,
                        boldFont,
                        CertificateNameUtils.commonName(signerCertificate),
                        margin,
                        y - 2f,
                        availableWidth,
                        9f,
                        6f
                );

                LocalDateTime dateTime = LocalDateTime.ofInstant(
                        signingTime.toInstant(),
                        ZoneId.systemDefault()
                );
                y = drawLine(
                        canvas,
                        regularFont,
                        "Data: " + VISIBLE_DATE_FORMAT.format(dateTime),
                        margin,
                        y - 2f,
                        availableWidth,
                        8f,
                        5.5f
                );

                if (reason != null && !reason.isBlank() && y > 8f) {
                    drawLine(
                            canvas,
                            regularFont,
                            "Motivo: " + reason,
                            margin,
                            y - 2f,
                            availableWidth,
                            7f,
                            5f
                    );
                }
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            template.save(output);
            return output.toByteArray();
        }
    }

    private float drawLine(
            PDPageContentStream canvas,
            PDFont font,
            String originalText,
            float x,
            float y,
            float maxWidth,
            float preferredSize,
            float minimumSize
    ) throws IOException {
        String text = sanitizeForStandardFont(originalText);
        float fontSize = preferredSize;
        while (fontSize > minimumSize && textWidth(font, text, fontSize) > maxWidth) {
            fontSize -= 0.5f;
        }

        while (text.length() > 4 && textWidth(font, text, fontSize) > maxWidth) {
            text = text.substring(0, text.length() - 2).stripTrailing() + "...";
        }

        canvas.beginText();
        canvas.setFont(font, fontSize);
        canvas.setNonStrokingColor(Color.BLACK);
        canvas.newLineAtOffset(x, Math.max(2f, y));
        canvas.showText(text);
        canvas.endText();
        return y - (fontSize + 2f);
    }

    private float textWidth(PDFont font, String text, float fontSize) throws IOException {
        return font.getStringWidth(text) / 1000f * fontSize;
    }

    private String sanitizeForStandardFont(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\\t]]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Assinatura digital do documento";
        }
        return reason.trim();
    }

    private int normalizedRotation(int rotation) {
        int normalized = rotation % 360;
        if (normalized < 0) {
            normalized += 360;
        }
        return switch (normalized) {
            case 90, 180, 270 -> normalized;
            default -> 0;
        };
    }
}
