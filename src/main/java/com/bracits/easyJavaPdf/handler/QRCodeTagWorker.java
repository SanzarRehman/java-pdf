package com.bracits.easyJavaPdf.handler;

import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.barcodes.qrcode.EncodeHintType;
import com.itextpdf.barcodes.qrcode.ErrorCorrectionLevel;
import com.itextpdf.html2pdf.attach.ITagWorker;
import com.itextpdf.html2pdf.attach.ProcessorContext;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject;
import com.itextpdf.layout.Canvas;
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

  private int qrCodeSize=50;

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

      String sizeAttr = element.getAttribute("size");
      if (sizeAttr != null && !sizeAttr.isEmpty()) {
          try {
              qrCodeSize = Integer.parseInt(sizeAttr);
          } catch (NumberFormatException e) {
              System.err.println("⚠️ Invalid QR size attribute, using default: " + e.getMessage());
          }
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

      // 1️⃣ Create QR code XObject
      PdfFormXObject qrObject = qrCode.createFormXObject(context.getPdfDocument());

      // Wrap QR code XObject in Image for layout
      Image qrImage = new Image(qrObject).setWidth(qrCodeSize).setHeight(qrCodeSize);

      // 2️⃣ Check if logo attribute exists
      String logoPath = element.getAttribute("logo");
      if (logoPath != null && !logoPath.isEmpty()) {
          try {
              // Load logo image
              ImageData logoData = ImageDataFactory.create(logoPath);
              Image logoImage = new Image(logoData);

              // Use QR XObject’s intrinsic dimensions
              float qrWidth = qrObject.getWidth();
              float qrHeight = qrObject.getHeight();

// Logo should cover ~30–40% of QR width for good balance
              float logoScaleRatio = 0.35f;
              float logoWidth = qrWidth * logoScaleRatio;
              float logoHeight = qrHeight * logoScaleRatio;
              logoImage.scaleAbsolute(logoWidth, logoHeight);

// Center logo within the QR XObject
              float centerX = (qrWidth - logoWidth) / 2f;
              float centerY = (qrHeight - logoHeight) / 2f;
              logoImage.setFixedPosition(centerX, centerY);

              // Draw logo directly on QR code XObject using Canvas
              Canvas canvas = new Canvas(
                      new PdfCanvas(qrObject, context.getPdfDocument()),
                      new Rectangle(0, 0, qrObject.getWidth(), qrObject.getHeight())
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