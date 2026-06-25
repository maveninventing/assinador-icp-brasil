package br.com.privacytools.assinador.signing;

import java.awt.geom.Rectangle2D;

public record SignaturePlacement(
        int pageIndex,
        float x,
        float y,
        float width,
        float height
) {
    public Rectangle2D humanRectangle() {
        return new Rectangle2D.Float(x, y, width, height);
    }
}
