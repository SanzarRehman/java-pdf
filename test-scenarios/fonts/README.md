# Font Installation Instructions

To test different fonts with the PDF generator, download and place the following fonts in this directory:

## Recommended Fonts for Testing

### 1. Google Fonts (Free)
Download from: https://fonts.google.com/

**Popular choices:**
- **Roboto** (Modern, clean): `Roboto-Regular.ttf`, `Roboto-Bold.ttf`
- **Open Sans** (Readable): `OpenSans-Regular.ttf`, `OpenSans-Bold.ttf`
- **Lato** (Professional): `Lato-Regular.ttf`, `Lato-Bold.ttf`
- **Montserrat** (Stylish): `Montserrat-Regular.ttf`, `Montserrat-Bold.ttf`
- **Source Sans Pro** (Adobe): `SourceSansPro-Regular.ttf`, `SourceSansPro-Bold.ttf`

### 2. System Fonts (Already Available)
- Arial
- Times New Roman
- Helvetica
- Georgia

### 3. Specialized Fonts

**For Bengali/Multilingual:**
- **Noto Sans Bengali**: Download from Google Fonts
- **SolaimanLipi**: Free Bengali font

**For Code/Technical:**
- **Fira Code**: Programming font with ligatures
- **Source Code Pro**: Monospace font from Adobe

## Download Instructions

### Method 1: Google Fonts Website
1. Go to https://fonts.google.com/
2. Search for the font name
3. Click "Download family"
4. Extract the ZIP file
5. Copy `.ttf` files to this directory

### Method 2: Direct Download Commands (macOS/Linux)

```bash
# Create fonts directory if it doesn't exist
mkdir -p test-scenarios/fonts

# Download Roboto
curl -L "https://github.com/google/fonts/raw/main/apache/roboto/Roboto-Regular.ttf" -o test-scenarios/fonts/Roboto-Regular.ttf
curl -L "https://github.com/google/fonts/raw/main/apache/roboto/Roboto-Bold.ttf" -o test-scenarios/fonts/Roboto-Bold.ttf

# Download Open Sans
curl -L "https://github.com/google/fonts/raw/main/apache/opensans/OpenSans-Regular.ttf" -o test-scenarios/fonts/OpenSans-Regular.ttf
curl -L "https://github.com/google/fonts/raw/main/apache/opensans/OpenSans-Bold.ttf" -o test-scenarios/fonts/OpenSans-Bold.ttf

# Download Lato
curl -L "https://github.com/google/fonts/raw/main/ofl/lato/Lato-Regular.ttf" -o test-scenarios/fonts/Lato-Regular.ttf
curl -L "https://github.com/google/fonts/raw/main/ofl/lato/Lato-Bold.ttf" -o test-scenarios/fonts/Lato-Bold.ttf
```

### Method 3: Using wget (Linux)

```bash
# Download Roboto
wget -O test-scenarios/fonts/Roboto-Regular.ttf "https://github.com/google/fonts/raw/main/apache/roboto/Roboto-Regular.ttf"
wget -O test-scenarios/fonts/Roboto-Bold.ttf "https://github.com/google/fonts/raw/main/apache/roboto/Roboto-Bold.ttf"
```

## Font Usage in Tests

Once fonts are downloaded, they will be automatically used in:
- `3-custom-fonts.html` - Demonstrates custom font usage
- `4-header-footer.html` - Uses custom fonts in headers/footers
- `6-extremely-complex.html` - Multiple font combinations

## Troubleshooting

**Font not appearing in PDF:**
1. Ensure the font file is in the correct directory
2. Check that the font file is not corrupted
3. Verify the font name matches the CSS font-family declaration
4. Some fonts may have licensing restrictions

**Performance issues:**
- Large font files may slow down PDF generation
- Consider using font subsets for production use
- Limit the number of font variants (regular, bold, italic)

## Font Licensing

- Google Fonts are free and open source
- Always check font licenses before commercial use
- System fonts are generally safe to use
- Some premium fonts may require licensing fees

## File Structure After Download

```
test-scenarios/fonts/
├── README.md (this file)
├── Roboto-Regular.ttf
├── Roboto-Bold.ttf
├── OpenSans-Regular.ttf
├── OpenSans-Bold.ttf
├── Lato-Regular.ttf
├── Lato-Bold.ttf
├── Montserrat-Regular.ttf
├── Montserrat-Bold.ttf
└── ... (other fonts)
```