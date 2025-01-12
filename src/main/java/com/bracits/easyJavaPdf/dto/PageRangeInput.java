package com.bracits.easyJavaPdf.dto;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PageRangeInput {
  private String file;
  private String range;
}

