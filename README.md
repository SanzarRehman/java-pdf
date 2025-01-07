# PDF Generation Service

This service provides endpoints to generate PDFs from HTML content with optional CSS, fonts, headers, and footers.

## Endpoints

### Generate PDF

**URL:** `/generate`

**Method:** `POST`

**Description:** Generates a PDF from the provided HTML file. Optionally, you can include a CSS file and font files.

**Request Parameters:**

- `file` (MultipartFile): The HTML file to convert to PDF.
- `style` (MultipartFile, optional): The CSS file to style the HTML content.
- `fonts` (MultipartFile[], optional): Array of font files to be used in the PDF.
- `formData` (Map<String, String>): Additional form data.

**Response:**

- `200 OK`: Returns the generated PDF as a byte array.
- `500 Internal Server Error`: Returns an error message if PDF generation fails.

**Example Request:**

```bash
curl -X POST "http://localhost:8080/generate" \
     -F "file=@path/to/your.html" \
     -F "style=@path/to/your.css" \
     -F "fonts=@path/to/font1.ttf" \
     -F "fonts=@path/to/font2.ttf"



### Generate PDF with Header and Footer

**URL:** `/generate-with-header-footer`

**Method:** `POST`

**Description:** Generates a PDF from the provided HTML file with optional CSS, fonts, header, and footer HTML content.

**Request Parameters:**

- `file` (MultipartFile): The HTML file to convert to PDF.
- `style` (MultipartFile, optional): The CSS file to style the HTML content.
- `header` (MultipartFile, optional): The HTML file for the header content.
- `footer` (MultipartFile, optional): The HTML file for the footer content.
- `fonts` (MultipartFile[], optional): Array of font files to be used in the PDF.
- `formData` (Map<String, String>): Additional form data.

**Response:**

- `200 OK`: Returns the generated PDF with header and footer as a byte array.
- `500 Internal Server Error`: Returns an error message if PDF generation fails.

**Example Request:**

```bash
curl -X POST "http://localhost:8080/generate-with-header-footer" \
     -F "file=@path/to/your.html" \
     -F "style=@path/to/your.css" \
     -F "header=@path/to/header.html" \
     -F "footer=@path/to/footer.html" \
     -F "fonts=@path/to/font1.ttf" \
     -F "fonts=@path/to/font2.ttf"
