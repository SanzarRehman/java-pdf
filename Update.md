**1. Emojis in PDF**

To enable the appearance of emojis in the PDF from HTML conversion, *NotoEmoji-VariableFont_wght.ttf* file needs to be added in the asset field. 

*NotoEmoji-VariableFont_wght.ttf* is available on the follwoing location: *test-scenarios/fonts/NotoEmoji-VariableFont_wght.ttf*

**2. QR scanner in PDF**

The *converterProperties.setTagWorkerFactory* method allows iText to handle multiple custom HTML tags during the HTML-to-PDF conversion. 

When the converter encounters a tag, it first checks if it’s a QR code tag *(<qr>)* by calling *qrFactory.getCustomTagWorker(tag, context)*. 

If a valid *ITagWorker* is returned, the tag is processed as a QR code and the method returns immediately. 

If it’s not a QR tag, it then checks for a bookmark tag *(<bookmark>)* using *bookmarkFactory.getCustomTagWorker(tag, context)* — if that returns a worker, the tag is processed as a bookmark. 

If neither factory recognizes the tag, the method falls back to iText’s default behavior by calling *super.getCustomTagWorker(tag, context)*, which handles regular HTML tags such as *\<p>*, *\<h1>*, and *\<div>*. 

This approach ensures that both custom tags (QR codes and bookmarks) work seamlessly alongside standard HTML elements in the same PDF generation process.

