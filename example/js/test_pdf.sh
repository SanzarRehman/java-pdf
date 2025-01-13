#!/bin/bash


HTML_FILE="page.html"
FONT_FILE="FiraSans-Bold.otf"
OUTPUT_FILE="output.pdf"
API_URL="http://localhost:8081/api/v1.0/print"


if [ ! -f "$HTML_FILE" ]; then
  echo "HTML file not found at $HTML_FILE"
  exit 1
fi


if [ ! -f "$FONT_FILE" ]; then
  echo "Font file not found at $FONT_FILE"
  exit 1
fi


echo "Sending request to generate PDF..."
curl --location --silent --show-error \
  --form "html=@$HTML_FILE;type=text/html" \
  --form "asset[]=@$FONT_FILE" \
  --form 'jsEnable="true"' \
  --output "$OUTPUT_FILE" \
  "$API_URL"


if [ $? -eq 0 ] && [ -f "$OUTPUT_FILE" ]; then
  echo "PDF successfully generated and saved to $OUTPUT_FILE"
  echo "You can open it with: open $OUTPUT_FILE (macOS) or xdg-open $OUTPUT_FILE (Linux)"
else
  echo "Failed to generate the PDF."
  exit 1
fi
