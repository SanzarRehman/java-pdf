package com.bracits.easyJavaPdf.handler;

import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.barcodes.qrcode.EncodeHintType;
import com.itextpdf.barcodes.qrcode.ErrorCorrectionLevel;
import com.itextpdf.html2pdf.attach.ITagWorker;
import com.itextpdf.html2pdf.attach.ProcessorContext;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import com.itextpdf.layout.IPropertyContainer;
import com.itextpdf.layout.element.Image;
import com.itextpdf.styledxmlparser.node.IElementNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class QRCodeTagWorker implements ITagWorker {

  private static final String[] allowedErrorCorrection = {"L", "M", "Q", "H"};


  private static final String[] allowedCharset = {"Cp437", "Shift_JIS", "ISO-8859-1", "ISO-8859-16"};


  private final BarcodeQRCode qrCode;


  private Image qrCodeAsImage;

  private int qrCodeSize;

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

    qrCode.setCode(content);
    return true;
  }


  @Override
  public boolean processTagChild(ITagWorker childTagWorker, ProcessorContext context) {
    return false;
  }


  @Override
  public void processEnd(IElementNode element, ProcessorContext context) {



//    qrCodeAsImage = new Image(qrCode.createFormXObject(context.getPdfDocument()));

      // 1️⃣ Create QR code XObject
      PdfFormXObject qrObject = qrCode.createFormXObject(context.getPdfDocument());

      // Wrap QR code XObject in Image for layout
      qrCodeSize=200;
      Image qrImage = new Image(qrObject).setWidth(qrCodeSize).setHeight(qrCodeSize);

      // 2️⃣ Check if logo attribute exists
      String logoPath = element.getAttribute("logo");
      if (logoPath != null && !logoPath.isEmpty()) {
          try {
              // Load logo image
              ImageData logoData = ImageDataFactory.create(logoPath);
              Image logoImage = new Image(logoData);

              // Scale logo to 25% of QR code size
              float logoWidth = qrCodeSize/20f;
              float logoHeight = qrCodeSize/20f;
              logoImage.scaleAbsolute(logoWidth, logoHeight);

              // Center logo inside QR code
              float centerX = (qrObject.getWidth() - logoWidth) / 2f;
              float centerY = (qrObject.getHeight() - logoHeight) / 2f;
              logoImage.setFixedPosition(centerX, centerY);

              // Draw logo directly on QR code XObject using Canvas
              com.itextpdf.layout.Canvas canvas = new com.itextpdf.layout.Canvas(
                      new PdfCanvas(qrObject, context.getPdfDocument()),
                      new com.itextpdf.kernel.geom.Rectangle(0, 0, qrObject.getWidth(), qrObject.getHeight())
              );
              canvas.add(logoImage);
              canvas.close();

          } catch (IOException e) {
              e.printStackTrace();
          }
      }

      // 3️⃣ Store final QR image for PDF
      this.qrCodeAsImage = qrImage;
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