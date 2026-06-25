package br.com.privacytools.assinador.ui;

import br.com.privacytools.assinador.signing.SignaturePlacement;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

public final class PdfPreviewPanel extends JPanel implements Closeable {
    private static final int RENDER_DPI = 120;
    private static final int OUTER_MARGIN = 16;

    private PDDocument document;
    private PDFRenderer renderer;
    private BufferedImage renderedPage;
    private int currentPageIndex;
    private Rectangle2D.Float selectionInImage;
    private Rectangle displayedImageBounds = new Rectangle();
    private double displayedImageScale = 1.0;
    private double signatureWidthMm = 70.0;
    private double signatureHeightMm = 25.0;
    private Point dragOffset;

    public PdfPreviewPanel() {
        setBackground(new Color(55, 58, 64));
        setPreferredSize(new Dimension(850, 620));
        setFocusable(true);
        installMouseHandling();
    }

    public void load(Path pdfFile) throws IOException {
        close();
        document = Loader.loadPDF(pdfFile.toFile());
        renderer = new PDFRenderer(document);
        currentPageIndex = 0;
        renderCurrentPage(true);
    }

    public boolean hasDocument() {
        return document != null;
    }

    public int getPageCount() {
        return document == null ? 0 : document.getNumberOfPages();
    }

    public int getCurrentPageNumber() {
        return hasDocument() ? currentPageIndex + 1 : 0;
    }

    public void previousPage() throws IOException {
        if (document != null && currentPageIndex > 0) {
            currentPageIndex--;
            renderCurrentPage(true);
        }
    }

    public void nextPage() throws IOException {
        if (document != null && currentPageIndex < document.getNumberOfPages() - 1) {
            currentPageIndex++;
            renderCurrentPage(true);
        }
    }

    public void setSignatureSizeMm(double widthMm, double heightMm) {
        signatureWidthMm = widthMm;
        signatureHeightMm = heightMm;
        if (renderedPage != null) {
            resizeSelectionKeepingCenter();
            repaint();
        }
    }

    public SignaturePlacement getPlacement() {
        if (document == null || renderedPage == null || selectionInImage == null) {
            return null;
        }

        PDPage page = document.getPage(currentPageIndex);
        float cropWidth = page.getCropBox().getWidth();
        float cropHeight = page.getCropBox().getHeight();
        int rotation = normalizedRotation(page.getRotation());
        float displayedPageWidth = rotation == 90 || rotation == 270 ? cropHeight : cropWidth;
        float displayedPageHeight = rotation == 90 || rotation == 270 ? cropWidth : cropHeight;

        float x = (float) (selectionInImage.x / renderedPage.getWidth() * displayedPageWidth);
        float y = (float) (selectionInImage.y / renderedPage.getHeight() * displayedPageHeight);
        float width = (float) (selectionInImage.width / renderedPage.getWidth() * displayedPageWidth);
        float height = (float) (selectionInImage.height / renderedPage.getHeight() * displayedPageHeight);

        return new SignaturePlacement(currentPageIndex, x, y, width, height);
    }

    private void renderCurrentPage(boolean resetSelection) throws IOException {
        renderedPage = renderer.renderImageWithDPI(currentPageIndex, RENDER_DPI, ImageType.RGB);
        if (resetSelection || selectionInImage == null) {
            createDefaultSelection();
        } else {
            clampSelection();
        }
        revalidate();
        repaint();
        firePropertyChange("page", null, getCurrentPageNumber());
    }

    private void createDefaultSelection() {
        Dimension size = signatureSizeInRenderedPixels();
        float x = Math.max(0, renderedPage.getWidth() - size.width - 35);
        float y = Math.max(0, renderedPage.getHeight() - size.height - 35);
        selectionInImage = new Rectangle2D.Float(x, y, size.width, size.height);
        clampSelection();
    }

    private void resizeSelectionKeepingCenter() {
        if (selectionInImage == null) {
            createDefaultSelection();
            return;
        }
        Dimension size = signatureSizeInRenderedPixels();
        float centerX = selectionInImage.x + selectionInImage.width / 2f;
        float centerY = selectionInImage.y + selectionInImage.height / 2f;
        selectionInImage.setRect(
                centerX - size.width / 2f,
                centerY - size.height / 2f,
                size.width,
                size.height
        );
        clampSelection();
    }

    private Dimension signatureSizeInRenderedPixels() {
        if (document == null || renderedPage == null) {
            return new Dimension(250, 90);
        }
        PDPage page = document.getPage(currentPageIndex);
        int rotation = normalizedRotation(page.getRotation());
        float displayedWidthPoints = (rotation == 90 || rotation == 270)
                ? page.getCropBox().getHeight()
                : page.getCropBox().getWidth();
        float displayedHeightPoints = (rotation == 90 || rotation == 270)
                ? page.getCropBox().getWidth()
                : page.getCropBox().getHeight();

        double widthPoints = signatureWidthMm * 72.0 / 25.4;
        double heightPoints = signatureHeightMm * 72.0 / 25.4;
        int widthPixels = (int) Math.round(widthPoints / displayedWidthPoints * renderedPage.getWidth());
        int heightPixels = (int) Math.round(heightPoints / displayedHeightPoints * renderedPage.getHeight());

        widthPixels = Math.max(80, Math.min(widthPixels, renderedPage.getWidth()));
        heightPixels = Math.max(35, Math.min(heightPixels, renderedPage.getHeight()));
        return new Dimension(widthPixels, heightPixels);
    }

    private void clampSelection() {
        if (selectionInImage == null || renderedPage == null) {
            return;
        }
        selectionInImage.width = Math.min(selectionInImage.width, renderedPage.getWidth());
        selectionInImage.height = Math.min(selectionInImage.height, renderedPage.getHeight());
        selectionInImage.x = Math.max(0, Math.min(selectionInImage.x, renderedPage.getWidth() - selectionInImage.width));
        selectionInImage.y = Math.max(0, Math.min(selectionInImage.y, renderedPage.getHeight() - selectionInImage.height));
    }

    private void installMouseHandling() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || renderedPage == null) {
                    return;
                }
                Point imagePoint = componentToImage(event.getPoint());
                if (imagePoint == null) {
                    return;
                }

                if (selectionInImage.contains(imagePoint)) {
                    dragOffset = new Point(
                            Math.round(imagePoint.x - selectionInImage.x),
                            Math.round(imagePoint.y - selectionInImage.y)
                    );
                } else {
                    selectionInImage.x = imagePoint.x - selectionInImage.width / 2f;
                    selectionInImage.y = imagePoint.y - selectionInImage.height / 2f;
                    dragOffset = new Point(
                            Math.round(selectionInImage.width / 2f),
                            Math.round(selectionInImage.height / 2f)
                    );
                    clampSelection();
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (dragOffset == null || renderedPage == null) {
                    return;
                }
                Point imagePoint = componentToImage(event.getPoint());
                if (imagePoint == null) {
                    return;
                }
                selectionInImage.x = imagePoint.x - dragOffset.x;
                selectionInImage.y = imagePoint.y - dragOffset.y;
                clampSelection();
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                dragOffset = null;
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                Point imagePoint = componentToImage(event.getPoint());
                boolean overSelection = imagePoint != null
                        && selectionInImage != null
                        && selectionInImage.contains(imagePoint);
                setCursor(overSelection
                        ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                        : Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
    }

    private Point componentToImage(Point componentPoint) {
        if (!displayedImageBounds.contains(componentPoint) || displayedImageScale <= 0) {
            return null;
        }
        int x = (int) Math.round((componentPoint.x - displayedImageBounds.x) / displayedImageScale);
        int y = (int) Math.round((componentPoint.y - displayedImageBounds.y) / displayedImageScale);
        return new Point(
                Math.max(0, Math.min(x, renderedPage.getWidth() - 1)),
                Math.max(0, Math.min(y, renderedPage.getHeight() - 1))
        );
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (renderedPage == null) {
                g2.setColor(Color.WHITE);
                g2.setFont(getFont().deriveFont(Font.PLAIN, 18f));
                String text = "Selecione um documento PDF";
                int x = Math.max(20, (getWidth() - g2.getFontMetrics().stringWidth(text)) / 2);
                int y = getHeight() / 2;
                g2.drawString(text, x, y);
                return;
            }

            double availableWidth = Math.max(1, getWidth() - 2.0 * OUTER_MARGIN);
            double availableHeight = Math.max(1, getHeight() - 2.0 * OUTER_MARGIN);
            displayedImageScale = Math.min(
                    availableWidth / renderedPage.getWidth(),
                    availableHeight / renderedPage.getHeight()
            );
            int drawWidth = Math.max(1, (int) Math.round(renderedPage.getWidth() * displayedImageScale));
            int drawHeight = Math.max(1, (int) Math.round(renderedPage.getHeight() * displayedImageScale));
            int drawX = (getWidth() - drawWidth) / 2;
            int drawY = (getHeight() - drawHeight) / 2;
            displayedImageBounds = new Rectangle(drawX, drawY, drawWidth, drawHeight);

            g2.setColor(Color.BLACK);
            g2.fillRect(drawX - 2, drawY - 2, drawWidth + 4, drawHeight + 4);
            g2.drawImage(renderedPage, drawX, drawY, drawWidth, drawHeight, null);

            if (selectionInImage != null) {
                int x = drawX + (int) Math.round(selectionInImage.x * displayedImageScale);
                int y = drawY + (int) Math.round(selectionInImage.y * displayedImageScale);
                int width = Math.max(1, (int) Math.round(selectionInImage.width * displayedImageScale));
                int height = Math.max(1, (int) Math.round(selectionInImage.height * displayedImageScale));

                g2.setColor(new Color(30, 105, 180, 55));
                g2.fillRect(x, y, width, height);
                g2.setColor(new Color(15, 75, 145));
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(x, y, width, height);
                g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
                g2.drawString("ASSINATURA", x + 7, y + Math.min(height - 6, 18));
            }
        } finally {
            g2.dispose();
        }
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

    @Override
    public void close() throws IOException {
        renderedPage = null;
        renderer = null;
        selectionInImage = null;
        if (document != null) {
            document.close();
            document = null;
        }
        repaint();
    }
}
