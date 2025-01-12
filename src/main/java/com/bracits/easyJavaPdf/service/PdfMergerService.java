package com.bracits.easyJavaPdf.service;

import com.bracits.easyJavaPdf.dto.PageRange;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;

import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;


@Service
public class PdfMergerService {


  public byte[] mergePdfs(List<Path> tempFiles, List<PageRange> pageRanges, String password, Path tempLoc, String optimizer) throws IOException {
    if (tempFiles == null || tempFiles.isEmpty()) {
      throw new IllegalArgumentException("The list of temporary files cannot be null or empty.");
    }
    if (pageRanges == null || pageRanges.isEmpty()) {
      throw new IllegalArgumentException("The list of page ranges cannot be null or empty.");
    }


    try {
      PDFMergerUtility pdfmerge = new PDFMergerUtility();

      if(optimizer.equals("true")){
        pdfmerge.setDocumentMergeMode(PDFMergerUtility.DocumentMergeMode.OPTIMIZE_RESOURCES_MODE);
      }else{
        pdfmerge.setDocumentMergeMode(PDFMergerUtility.DocumentMergeMode.PDFBOX_LEGACY_MODE);
      }

      
      
      for (PageRange pageRange : pageRanges) {
        Path inputPath = tempFiles.stream()
            .filter(file -> file.getFileName().toString().equals(pageRange.getFile() + ".pdf") || file.getFileName().toString().equals(pageRange.getFile()))
            .findFirst()
            .orElseThrow(() -> new FileNotFoundException("File not found: " + pageRange.getFile() + ".pdf"));

        if (Files.notExists(inputPath)) {
          throw new FileNotFoundException("File does not exist: " + inputPath);
        }

        if (inputPath.toString().toLowerCase().endsWith(".pdf")) {

          try (PDDocument sourceDocument = Loader.loadPDF(inputPath.toFile())) {
            PDDocument newDocument = new PDDocument();


            int start = Math.max(0, pageRange.getStartPage());
            int end;
            end = Math.min(pageRange.getEndPage(), sourceDocument.getNumberOfPages() - 1);

            if(start == 0 && end == -1){
              end = sourceDocument.getNumberOfPages() - 1;
            }

            if (start <= end) {
              for (int i = start; i <= end; i += pageRange.getStep()) {
                newDocument.addPage(sourceDocument.getPage(i));
              }
            } else {
              for (int i = start; i >= end; i += pageRange.getStep()) {
                newDocument.addPage(sourceDocument.getPage(i));
              }
            }


            String tempPdf = tempLoc.toString()+"/"+UUID.randomUUID().toString()+".pdf";
            newDocument.save(tempPdf);

            pdfmerge.addSource(tempPdf);
            newDocument.close();


          }
          catch (IOException e) {
            throw new RuntimeException("Failed to merge PDFs", e);
          }





        } else if (isImage(inputPath.toString())) {
          PDDocument imageDocument = convertImageToPDF(inputPath.toString());
          String tempPdf = tempLoc.toString()+"/"+UUID.randomUUID().toString()+".pdf";
          imageDocument.save(tempPdf);
         pdfmerge.addSource(tempPdf);
        } else {
          throw new IllegalArgumentException("Unsupported file format: " + inputPath.getFileName());
        }
      }

      try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

        pdfmerge.setDestinationStream(outputStream);
        CompressParameters compressParameters = new CompressParameters();
        compressParameters.isCompress();


        pdfmerge.mergeDocuments(null, compressParameters);


        return outputStream.toByteArray();
      } catch (IOException e) {
        throw new RuntimeException("Error occurred while merging PDF documents", e);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to merge PDFs", e);

    } finally {



    }
  }


  private static boolean isImage(String filepath) {
    File file = new File(filepath);
    String[] supportedExtensions = {"jpeg", "jpg", "png", "bmp", "gif"};
    String fileName = file.getName().toLowerCase();
    for (String ext : supportedExtensions) {
      if (fileName.endsWith(ext)) {
        return true;
      }
    }
    return false;
  }

  private static PDDocument convertImageToPDF(String imageFilePath) throws IOException {
    File imageFile = new File(imageFilePath);
    PDDocument document = new PDDocument();
    PDImageXObject image = PDImageXObject.createFromFile(imageFile.getAbsolutePath(), document);
    PDRectangle pageSize = PDRectangle.A4;
    float imageWidth = image.getWidth();
    float imageHeight = image.getHeight();
    float pageWidth = pageSize.getWidth();
    float pageHeight = pageSize.getHeight();
    float widthScale = pageWidth / imageWidth;
    float heightScale = pageHeight / imageHeight;
    float scale = Math.min(widthScale, heightScale);
    float scaledWidth = imageWidth * scale;
    float scaledHeight = imageHeight * scale;
    float xOffset = (pageWidth - scaledWidth) / 2;
    float yOffset = (pageHeight - scaledHeight) / 2;
    PDPage page = new PDPage(pageSize);
    document.addPage(page);
    try (var contentStream = new PDPageContentStream(document, page)) {
      contentStream.drawImage(image, xOffset, yOffset, scaledWidth, scaledHeight);
    }
    return document;
  }
}
