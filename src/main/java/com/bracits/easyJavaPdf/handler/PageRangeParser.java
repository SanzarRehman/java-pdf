package com.bracits.easyJavaPdf.handler;

import com.bracits.easyJavaPdf.dto.PageRange;
import com.bracits.easyJavaPdf.dto.PageRangeInput;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

public class PageRangeParser {

  /**
   * Parses the pages input and returns a list of PageRange objects.
   *
   * @param pages     The input pages string or JSON.
   * @param tempFiles
   * @return A list of PageRange objects.
   * @throws Exception If parsing fails.
   */
  public static List<PageRange> parse(String pages, String tempFiles) throws Exception {
    List<PageRange> pageRanges = new ArrayList<>();

    if (pages.startsWith("[")) {
      ObjectMapper mapper = new ObjectMapper();
      List<PageRangeInput> inputs = mapper.readValue(pages, new TypeReference<>() {});

      for (PageRangeInput input : inputs) {
        String file = input.getFile();
        String range = input.getRange();
        pageRanges.addAll(parseRange(file, range, tempFiles));
      }
    } else {
      String[] tokens = pages.split(" ");
      for (String token : tokens) {
        if (token.contains("~")) {
          String[] fileAndRange = token.split("~");
          String file = fileAndRange[0];
          String range = fileAndRange.length > 1 ? fileAndRange[1] : null;
          pageRanges.addAll(parseRange(file, range, tempFiles));
        } else {
          pageRanges.addAll(parseRange(token, null,tempFiles));
        }
      }
    }

    return pageRanges;
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


  public static List<PageRange> parseRange(String file,String range,String tempFiles) throws IOException {
    Path parentTempDir = Path.of(tempFiles+"/"+file);
    try {
      List<PageRange> ranges = new ArrayList<>();
      if(isImage(parentTempDir.toString())){

        ranges.add(new PageRange(file, -1, -1, 1));

        return ranges;
      }

      if(range == null){
        ranges.add(new PageRange(file, -1, -1, 1));
        return ranges;
      }

    PDDocument sourceDocument = Loader.loadPDF(parentTempDir.toFile());
    int totalPages =sourceDocument.getNumberOfPages();

    int start, end, step;


    step = (range.contains(":") && range.split(":").length > 2 && !range.split(":")[2].isEmpty())
        ? Integer.parseInt(range.split(":")[2])
        : 1;
    if (step == 0) throw new IllegalArgumentException("Step cannot be zero.");

    String[] parts = range.split(":");


    if (parts.length > 0 && !parts[0].isEmpty()) {
      start = Integer.parseInt(parts[0]);
      if (start < 0) start += totalPages;
    } else {
      start = step > 0 ? 0 : totalPages - 1;
    }

    if (parts.length > 1 && !parts[1].isEmpty()) {
      end = Integer.parseInt(parts[1]);
      if (end < 0) end += totalPages;


    }
    else if (parts[0].isEmpty() && parts[1].isEmpty() && parts[2].equals("-1")) {
      end = 0;
    }
     else {
      end = step > 0 ? totalPages : -1;
    }

    ranges.add(new PageRange(file, start, end, step));

    sourceDocument.close();
      return ranges;
    } catch (Exception e) {
      throw new IOException("File does not exist: " + parentTempDir);
    }
  }

}
