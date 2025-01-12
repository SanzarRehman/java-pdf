package com.bracits.easyJavaPdf;

import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.barcodes.qrcode.EncodeHintType;
import com.itextpdf.barcodes.qrcode.ErrorCorrectionLevel;
import com.itextpdf.html2pdf.attach.ITagWorker;
import com.itextpdf.html2pdf.attach.ProcessorContext;
import com.itextpdf.layout.IPropertyContainer;
import com.itextpdf.layout.element.Image;
import com.itextpdf.styledxmlparser.node.IElementNode;
import java.util.HashMap;
import java.util.Map;

 class QRCodeTagWorker implements ITagWorker {

  private static final String[] allowedErrorCorrection = {"L", "M", "Q", "H"};


  private static final String[] allowedCharset = {"Cp437", "Shift_JIS", "ISO-8859-1", "ISO-8859-16"};


  private final BarcodeQRCode qrCode;


  private Image qrCodeAsImage;

  /**
   * Instantiates a new QR code tag worker.
   *
   * @param element the element node
   * @param context the processor context
   */
  public QRCodeTagWorker(IElementNode element, ProcessorContext context) {
    Map<EncodeHintType, Object> hints = new HashMap<>();
    String charset = element.getAttribute("charset");
    if (checkCharacterSet(charset)) {
      hints.put(EncodeHintType.CHARACTER_SET, charset);
    }
    String errorCorrection = element.getAttribute("errorcorrection");
    if (checkErrorCorrectionAllowed(errorCorrection)) {
      ErrorCorrectionLevel errorCorrectionLevel = getErrorCorrectionLevel(errorCorrection);
      hints.put(EncodeHintType.ERROR_CORRECTION, errorCorrectionLevel);
    }
    qrCode = new BarcodeQRCode("placeholder", hints);

  }


  @Override
  public boolean processContent(String content, ProcessorContext context) {
    //Add content to the barcode
    qrCode.setCode(content);
    return true;
  }


  @Override
  public boolean processTagChild(ITagWorker childTagWorker, ProcessorContext context) {
    return false;
  }


  @Override
  public void processEnd(IElementNode element, ProcessorContext context) {
    //Transform barcode into image
    qrCodeAsImage = new Image(qrCode.createFormXObject(context.getPdfDocument()));

  }


  @Override
  public IPropertyContainer getElementResult() {

    return qrCodeAsImage;
  }

  private static boolean checkErrorCorrectionAllowed(String toCheck) {
    for (int i = 0; i < allowedErrorCorrection.length; i++) {
      if (toCheck.toUpperCase().equals(allowedErrorCorrection[i])) {
        return true;
      }
    }
    return false;
  }


  private static boolean checkCharacterSet(String toCheck) {
    for (int i = 0; i < allowedCharset.length; i++) {
      if (toCheck.equals(allowedCharset[i])) {
        return true;
      }
    }
    return false;
  }

  private static ErrorCorrectionLevel getErrorCorrectionLevel(String level) {
    switch (level) {
      case "L":
        return ErrorCorrectionLevel.L;
      case "M":
        return ErrorCorrectionLevel.M;
      case "Q":
        return ErrorCorrectionLevel.Q;
      case "H":
        return ErrorCorrectionLevel.H;
    }
    return null;

  }
}