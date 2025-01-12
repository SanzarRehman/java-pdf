package com.bracits.easyJavaPdf.PdfMerger.Service;

public class PageRange {
  private String file;
  private int startPage;
  private int endPage;
  private int step;

  public PageRange(String file, int startPage, int endPage, int step) {
    this.file = file;
    this.startPage = startPage;
    this.endPage = endPage;
    this.step = step;
  }

  public String getFile() {
    return file;
  }

  public int getStartPage() {
    return startPage;
  }

  public int getEndPage() {
    return endPage;
  }

  public int getStep() {
    return step;
  }
}
