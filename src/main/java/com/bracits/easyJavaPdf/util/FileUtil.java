package com.bracits.easyJavaPdf.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import org.springframework.web.multipart.MultipartFile;

public class FileUtil {


  public static void deleteDirectory(Path directory) {
    try (Stream<Path> files = Files.walk(directory)) {
      files.sorted(Comparator.reverseOrder())
          .map(java.nio.file.Path::toFile)
          .forEach(File::delete);
    } catch (IOException e) {
      System.err.println("Failed to delete directory: " + directory);
    }
  }


  public static Path saveFileInDirectory(MultipartFile file, Path directory) throws IOException {
    Path filePath = directory.resolve(file.getOriginalFilename());
    Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
    return filePath;
  }
}
