**1. Emojis in PDF**

To enable the appearance of emojis in the PDF from HTML conversion, *NotoEmoji-VariableFont_wght.ttf* file needs to be added in the asset field. 

*NotoEmoji-VariableFont_wght.ttf* is available on the follwoing location: *test-scenarios/fonts/NotoEmoji-VariableFont_wght.ttf*

**2. QR Scanner in PDF**

The *converterProperties.setTagWorkerFactory* method allows iText to handle multiple custom HTML tags during the HTML-to-PDF conversion. 

When the converter encounters a tag, it first checks if it’s a QR code tag *(\<qr>)* by calling *qrFactory.getCustomTagWorker(tag, context)*. 

If a valid *ITagWorker* is returned, the tag is processed as a QR code and the method returns immediately. 

If it’s not a QR tag, it then checks for a bookmark tag *(<bookmark>)* using *bookmarkFactory.getCustomTagWorker(tag, context)* — if that returns a worker, the tag is processed as a bookmark. 

If neither factory recognizes the tag, the method falls back to iText’s default behavior by calling *super.getCustomTagWorker(tag, context)*, which handles regular HTML tags such as *\<p>*, *\<h1>*, and *\<div>*. 

This approach ensures that both custom tags (QR codes and bookmarks) work seamlessly alongside standard HTML elements in the same PDF generation process.

**3. Embedding a Logo inside the QR Code**

The *processEnd()* method of *QRCodeTagWorker* class handles adding an image logo inside a generated QR code when converting HTML to PDF. It first creates a QR code object *(PdfFormXObject)* and wraps it in an Image so it can be displayed properly in the PDF. Then it checks if the *\<qr>* tag in the HTML includes a logo attribute (for example, *\<qr logo="images/emoji.png">*). If a logo path is provided, it loads that image, scales it down to about 5% of the QR code’s size, and positions it exactly at the center. Using an iText Canvas, the logo is drawn directly on top of the QR code’s area, effectively embedding it inside the QR code graphic. 

**4. How users can generate QR code in PDF from HTML**

The \<qr> tag supports several optional attributes that you can customize according to your needs. The charset attribute specifies the character encoding for the content of the QR code. For example, if you’re using characters from the ISO-8859-1 character set, you would set it as charset="ISO-8859-1". This attribute is especially useful if your QR code contains special characters.

The errorcorrection attribute defines the level of error correction for the QR code. Error correction helps make the QR code scannable even if part of it is damaged. There are four possible levels: L (low, 7% error correction), M (medium, 15% error correction), Q (quality, 25% error correction), and H (high, 30% error correction). The default value is H, which offers the highest error correction.

The size attribute controls the size of the QR code. This is defined in pixels, and adjusting the value will make the QR code larger or smaller. For example, size="50" will create a smaller QR code, while size="200" will make it larger. The default size is typically set to 200.

The logo attribute allows you to add a logo image to the center of the QR code. You need to provide the path to the image file (e.g., logo="/path/to/logo.png") to embed it. If you don’t provide this attribute, the QR code will appear without any logo.

To use the \<qr> tag, simply insert it into your HTML file where you want the QR code to appear. You can adjust the attributes of the tag to suit your needs, such as specifying the charset, error correction level, size, and logo. The content inside the \<qr> tag (e.g., https://www.bracu.ac.bd) will be encoded as a QR code.

\<qr charset="ISO-8859-1"
errorcorrection="H"
size="50"
logo="/path/to/logo.png">
https://www.bracu.ac.bd
\</qr>