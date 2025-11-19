package com.bracits.easyJavaPdf.handler;

import com.itextpdf.html2pdf.attach.ITagWorker;
import com.itextpdf.html2pdf.attach.ProcessorContext;
import com.itextpdf.html2pdf.attach.impl.DefaultTagWorkerFactory;
import com.itextpdf.styledxmlparser.node.IElementNode;

import java.awt.image.BufferedImage;

public class QRCodeTagWorkerFactory extends DefaultTagWorkerFactory {
    private final BufferedImage logoImage;

    public QRCodeTagWorkerFactory(BufferedImage logoImage) {
        this.logoImage = logoImage;
    }
  @Override
  public ITagWorker getCustomTagWorker(IElementNode tag, ProcessorContext context) {
    if (tag.name().equals("qr")) {
      return new QRCodeTagWorker(tag, context, logoImage);
    }
    return null;
  }
}
