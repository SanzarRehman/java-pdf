# EasyJavaPdf

**EasyJavaPdf** is a lightweight and efficient service designed for handling PDF operations, including merging PDF files and converting HTML content into PDF format. With its simple API endpoints, you can streamline your document processing workflows.

---

## Features

- **HTML to PDF Conversion**: Convert HTML, CSS, and additional assets into high-quality PDFs.
- **PDF Merging**: Combine multiple PDF files and other supported formats (e.g., images) with page range specifications.

---



## Inspiration

This project is inspired by [easy-pdf-rest](https://github.com/ronisaha/easy-pdf-rest). Special thanks to the developers for their amazing contribution and idea-sharing within the open-source community!

---



## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPLv3).  
See the [LICENSE](LICENSE) file for details.

---
##  Usage
```bash
docker run -p 8081:8081 sanzar686/easyjavapdf
```

## API Usage

### 1. **Merge PDF Files**

Merge multiple PDF files and other resources like images into a single PDF document. You can also specify page ranges for each file.

#### **Endpoint**
`POST /api/v1.0/merge`

#### **Parameters**
- `files[]`: List of files (PDFs or supported resources like images) to merge.
- `pages`: (Optional) JSON array defining page ranges for each file.
- `password`: (Optional) Password for protected files.
- `resourceOptimizer`: (Optional) Optimize the merged document. Default: `false`.
##### pages can be passed as JSON

```json
[
  {
    "file": "file1.pdf",
    "range": "0:2"
  },
  {
    "file": "a.jpeg"
  },
  {
    "file": "file1.pdf",
    "range": "2:3"
  },
  {
    "file": "file2.pdf"
  }
]
```
**or string like:**
```code
file1.pdf~0:2 a.jpeg file1.pdf~2:3 file2.pdf
```

#### Range Syntext

| Range | Description                 |     | Range | Description             |
|-------|-----------------------------|-----|-------|-------------------------|
| :     | all pages.                  |     | -1    | last page.              |
| 22    | just the 23rd page.         |     | :-1   | all but the last page.  |
| 0:3   | the first three pages.      |     | -2    | second-to-last page.    |
| :3    | the first three pages.      |     | -2:   | last two pages.         |
| 5:    | from the sixth page onward. |     | -3:-1 | third & second to last. |

##### The third, "stride" or "step" number is also recognized.

| Range  | Description                 |     | Range  | Description      |
|--------|-----------------------------|-----|--------|------------------|
| ::2    | 0 2 4 ... to the end.       |     | 3:0:-1 | 3 2 1 but not 0. |
| 1:10:2 | 1 3 5 7 9                   |     | 2::-1  | 2 1 0.           |
| ::-1   | all pages in reverse order. |     |        |                  |
#### **Example Request**
```bash
curl --location 'http://localhost:8081/api/v1.0/merge' \
--form 'files[]=@"/path/to/file1.pdf"' \
--form 'files[]=@"/path/to/file2.pdf"' \
--form 'files[]=@"/path/to/image.jpg"' \
--form 'pages="[
  { \"file\": \"file1.pdf\", \"range\": \"1:3\" },
  { \"file\": \"image.jpg\" },
  { \"file\": \"file2.pdf\", \"range\": \"2:5\" }
]"' \
--form 'password="yourpassword"' \
--form 'resourceOptimizer="false"'
```

---

### 2. **Convert HTML to PDF**

Convert HTML files into PDF documents, supporting additional styles, fonts, and assets.

#### **Endpoint**
`POST /api/v1.0/print`

#### **Parameters**
- `html`: HTML file to be converted.
- `style`: (Optional) CSS file for styling the PDF.
- `asset[]`: (Optional) List of additional assets such as fonts or images.
- `jsEnable`: (Optional) Enable or disable JavaScript execution during conversion. Default: `false`.

#### Parameters

| Parameter         | Type             | Required          | Description                                                                                                                                                                                                               |
|:------------------|:-----------------|:------------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `html`            | `file or string` | __Semi-Required__ | HTML file to convert. html or url or report one is required. Only either url or html, report should be used.                                                                                                              |
| `url`             | `file or string` | __Semi-Required__ | URL to convert. html or url or report one is required. Only either url or html, report should be used.                                                                                                                    |
| `optimize_images` | `boolean`        | __Optional__      | Whether size of embedded images should be optimized, with no quality loss.                                                                                                                                                |
| `file_name`       | `string`         | __Optional__      | Set response `disposition file_name`. default is `document.pdf`.                                                                                                                                                          |
| `password`        | `string`         | __Optional__      | Password protected PDF                                                                                                                                                                                                    |
| `options`         | `json`           | __Optional__      | Only with driver=`wk` all supported `wkhtmltopdf` options are supported                                                                                                                                                   |
| `style`           | `file or string` | __Optional__      | Style to apply to the `html`. This should only be used if the CSS is not referenced in the html. If it is included via HTML link, it should be passed as `asset`. Only either `style` or `style[]` can be used.           |
| `asset[]`         | `file or file[]` | __Optional__      | Assets which are referenced in the html. This can be images, CSS or fonts. The name must be 1:1 the same as used in the files. 

#### **Example Request**
```bash
curl --location 'http://localhost:8081/api/v1.0/print' \
--form 'html=@"/path/to/page.html"' \
--form 'style=@"/path/to/style.css"' \
--form 'asset[]=@"/path/to/font1.otf"' \
--form 'asset[]=@"/path/to/font2.ttf"' \
--form 'jsEnable="false"'
```

---

## Installation and Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/EasyJavaPdf.git
   cd EasyJavaPdf
   ```

2. Build and run the project:
   ```bash
   mvn clean install
   java -jar target/easy-java-pdf.jar
   ```

3. Access the API:
   ```url
   http://localhost:8081
   ```



## Contributing

Contributions are welcome! Feel free to fork the repository and submit a pull request if you want to add new features or improve the project.


## Note on Dependencies

This project uses external dependencies like **iText** to handle PDF operations. Ensure compliance with their respective licenses when modifying or distributing the project.

---

Enjoy simplified PDF handling with **EasyJavaPdf**!