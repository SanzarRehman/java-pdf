package com.bracits.easyJavaPdf.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PageRange {
  private String file;
  private int startPage;
  private int endPage;
  private int step;

}
