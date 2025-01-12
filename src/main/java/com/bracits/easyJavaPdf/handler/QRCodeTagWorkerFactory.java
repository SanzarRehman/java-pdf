package com.bracits.easyJavaPdf.handler;

import com.itextpdf.html2pdf.attach.ITagWorker;
import com.itextpdf.html2pdf.attach.ProcessorContext;
import com.itextpdf.html2pdf.attach.impl.DefaultTagWorkerFactory;
import com.itextpdf.styledxmlparser.node.IElementNode;

public class QRCodeTagWorkerFactory extends DefaultTagWorkerFactory {
  @Override
  public ITagWorker getCustomTagWorker(IElementNode tag, ProcessorContext context) {
    if (tag.name().equals("qr")) {
      return new QRCodeTagWorker(tag, context);
    }
    return null;
  }
}
