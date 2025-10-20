**1. Emojis in PDF**

To enable the appearance of emojis in the PDF from HTML conversion, *NotoEmoji-VariableFont_wght.ttf* file needs to be added in the asset field. 

*NotoEmoji-VariableFont_wght.ttf* is available on the follwoing location: *test-scenarios/fonts/NotoEmoji-VariableFont_wght.ttf*

**2. QR Scanner in PDF**

The *converterProperties.setTagWorkerFactory* method allows iText to handle multiple custom HTML tags during the HTML-to-PDF conversion. 

When the converter encounters a tag, it first checks if it’s a QR code tag *(<qr>)* by calling *qrFactory.getCustomTagWorker(tag, context)*. 

If a valid *ITagWorker* is returned, the tag is processed as a QR code and the method returns immediately. 

If it’s not a QR tag, it then checks for a bookmark tag *(<bookmark>)* using *bookmarkFactory.getCustomTagWorker(tag, context)* — if that returns a worker, the tag is processed as a bookmark. 

If neither factory recognizes the tag, the method falls back to iText’s default behavior by calling *super.getCustomTagWorker(tag, context)*, which handles regular HTML tags such as *\<p>*, *\<h1>*, and *\<div>*. 

This approach ensures that both custom tags (QR codes and bookmarks) work seamlessly alongside standard HTML elements in the same PDF generation process.

**3. Embedding a Logo inside the QR Code**

The *processEnd()* method of *QRCodeTagWorker* class handles adding an image logo inside a generated QR code when converting HTML to PDF. It first creates a QR code object *(PdfFormXObject)* and wraps it in an Image so it can be displayed properly in the PDF. Then it checks if the *<qr>* tag in the HTML includes a logo attribute (for example, *\<qr logo="images/emoji.png">*). If a logo path is provided, it loads that image, scales it down to about 5% of the QR code’s size, and positions it exactly at the center. Using an iText Canvas, the logo is drawn directly on top of the QR code’s area, effectively embedding it inside the QR code graphic. 