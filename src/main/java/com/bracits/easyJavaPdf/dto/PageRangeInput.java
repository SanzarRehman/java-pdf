package com.bracits.easyJavaPdf.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PageRangeInput {
  private String file;
  private String range;

  public String getFile() {
    return file;
  }

  public void setFile(String file) {
    this.file = file;
  }

  public String getRange() {
    return range;
  }

  public void setRange(String range) {
    this.range = range;
  }
}

