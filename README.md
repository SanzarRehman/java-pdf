# EasyJavaPdf

**EasyJavaPdf** is a lightweight and efficient service designed for handling PDF operations, including merging PDF files and converting HTML content into PDF format. With its simple API endpoints, you can streamline your document processing workflows.

---

## Features

- **HTML to PDF Conversion**: Convert HTML, CSS, and additional assets into high-quality PDFs.
- **PDF Merging**: Combine multiple PDF files and other supported formats (e.g., images) with page range specifications.

---

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

| Parameter      | Type             | Required       | Description |
|:---------------|:-----------------|:---------------|:------------|
| `html`         | file or string   | Semi-Required  | HTML file to convert. `html`, `url`, or `report` must be provided—pick exactly one. |
| `url`          | file or string   | Semi-Required  | URL to convert. `html`, `url`, or `report` must be provided—pick exactly one. |
| `report`       | string           | Semi-Required  | Report template name to render into HTML. `html`, `url`, or `report` must be provided—pick exactly one. |
| `data`         | dict             | Semi-Required  | Variables for rendering a report template. Use when `report` is supplied. Choose either `data` or `data_set`. |
| `data_set`     | dict[]           | Semi-Required  | List of variable dictionaries for rendering multiple reports. Use when `report` is supplied. Choose either `data` or `data_set`. |
| `optimize_images` | boolean       | Optional       | Optimize embedded images without quality loss. |
| `disposition`  | string           | Optional       | Response disposition type (`attachment` or `inline`). Defaults to `inline`. |
| `file_name`    | string           | Optional       | Response filename when `disposition` is set. Defaults to `document.pdf`. |
| `password`     | string           | Optional       | Apply password protection to the generated PDF. |
| `template`     | string           | Optional       | Name of a predefined template to apply. |
| `driver`       | string           | Optional       | Rendering engine (`wk` for wkhtmltopdf, `weasy` default for WeasyPrint). |
| `options`      | json             | Optional       | Additional options; only used when `driver=wk` (passed through to wkhtmltopdf). |
| `style`        | file or string   | Optional       | Inline CSS to apply when not already linked in the HTML. Use either `style` or `style[]`. |
| `style[]`      | file or file[]   | Optional       | Multiple CSS files to apply when not referenced in the HTML. Use either `style` or `style[]`. |
| `asset[]`      | file or file[]   | Optional       | Assets referenced by the HTML (images, CSS, fonts). Filenames must match the references exactly. |
| `renderer`     | string           | Optional       | Rendering engine: `itext` (default) or `chromium`/`puppeteer`. |
| `chunkSizeMb`  | number           | Optional       | Chunk size hint (MB) for large HTML. For Chromium chunked mode and iText chunked mode. Defaults to ~5 MB when chunking applies. |
| `parallelism`  | number           | Optional       | Parallel chunk workers (Chromium only). Defaults to 8 when chunking applies. |

#### Using report templates

Store reusable Thymeleaf themes under `src/main/resources/templates`. The template name becomes the `report` parameter and can live alongside an asset directory:

```
src/main/resources/templates/
├── certificate.html
└── certificate/
    ├── style.css
    └── logo.png
```

Send template variables via the `data` field (single report) or `data_set` (multiple sequential reports). Each entry in `data_set` renders with an automatic page break.

```bash
curl --location 'http://localhost:8081/api/v1.0/print' \
  --form 'report="certificate"' \
  --form 'data="{\"student\":{\"name\":\"Jane Doe\"}}"'
```

All assets inside the matching folder are copied automatically, letting relative URLs and fonts resolve without additional uploads.

#### **Example Request**
```bash
curl --location 'http://localhost:8081/api/v1.0/print' \
--form 'html=@"/path/to/page.html"' \
--form 'style=@"/path/to/style.css"' \
--form 'asset[]=@"/path/to/font1.otf"' \
--form 'asset[]=@"/path/to/font2.ttf"' \
--form 'jsEnable="false"'
```

#### 📑 Working with Table of Contents

- **Headings drive bookmarks**: Any `<h1>`–`<h6>` elements in your HTML automatically become PDF outline entries. Give each heading an `id` to create stable anchors, e.g. `<h2 id="chapter-1">Chapter 1</h2>`.
- **Optional visible TOC**: Add a `<nav>` section with in-document links that point to those heading `id`s so readers can also navigate inside the first pages of the PDF.
- **Bundle all assets**: Upload the HTML file plus the CSS, fonts, and images it references. Use the same filenames that appear in the markup so the converter can resolve them.
- **Disable JS unless required**: Set `jsEnable=false` for faster, deterministic conversions. Only enable it if your HTML relies on runtime scripts to build the headings or links.

Example request that generates a bookmark-enabled table of contents:

```bash
curl --location 'http://localhost:8081/api/v1.0/print' \
--form 'htmlFile=@"test-scenarios/5-table-of-contents.html"' \
--form 'asset[]=@"test-scenarios/2-complex-layout.css"' \
--form 'jsEnable="false"' \
--output table-of-contents.pdf
```

The resulting PDF will render the `<nav>` section as a visible contents page while the bookmarks panel mirrors the heading hierarchy.

#### Renderer and chunking defaults

- Default renderer: `itext` (fast, low-footprint). Set `renderer=chromium` for browser-accurate output.
- Chunked processing (Chromium): triggered automatically when HTML ≥ 10 MB; defaults to `chunkSizeMb≈5` and `parallelism≈8` unless overridden by request params.
- Chunked processing (iText): opt-in via `chunkSizeMb`; splits HTML into pieces and merges PDFs sequentially (parallelism ignored for iText).

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