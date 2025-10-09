# EasyJavaPDF Test Scenarios

This directory contains comprehensive test scenarios for the EasyJavaPDF service, covering various real-world use cases and edge cases.

## 📁 Directory Structure

```
test-scenarios/
├── README.md                    # This file
├── run-test.sh                  # Test runner script
├── 1-simple-inline-js.html     # Simple HTML with inline CSS and JavaScript
├── 2-complex-layout.html       # Complex business report layout
├── 2-complex-layout.css        # External CSS for complex layout
├── 3-custom-fonts.html         # Custom fonts demonstration
├── 4-header-footer.html        # Header and footer testing
├── 5-images-logo.html          # Images and logo placement
├── 6-extremely-complex.html    # Extremely complex CSS features
├── 6-extremely-complex.css     # Complex CSS with advanced features
├── assets/
│   ├── header.html             # Sample header file
│   └── footer.html             # Sample footer file
├── fonts/
│   └── README.md               # Font download instructions
├── images/                     # Directory for test images
└── output/                     # Generated PDF output files
```

## 🚀 Quick Start

1. **Start the EasyJavaPDF server:**
   ```bash
   ./gradlew bootRun
   ```

2. **Run all tests:**
   ```bash
   cd test-scenarios
   ./run-test.sh all
   ```

3. **Run a specific test:**
   ```bash
   ./run-test.sh 1    # Simple inline test
   ./run-test.sh 2    # Complex layout test
   ./run-test.sh 3    # Custom fonts test
   ./run-test.sh 4    # Header/footer test
   ./run-test.sh 5    # Images test
   ./run-test.sh 6    # Complex CSS test
   ```

## 📋 Test Scenarios

### 1. Simple HTML with Inline CSS and JavaScript
**File:** `1-simple-inline-js.html`
**Features:**
- Inline CSS styling
- JavaScript execution (jsEnable=true)
- Dynamic content generation
- Table population via JavaScript
- Mathematical calculations
- Timestamp generation

**Test Command:**
```bash
./run-test.sh 1
```

### 2. Complex HTML Layout
**Files:** `2-complex-layout.html`, `2-complex-layout.css`
**Features:**
- External CSS file
- Business report layout
- Grid and flexbox layouts (sanitized)
- Charts and data visualization
- Professional styling
- Multi-section document

**Test Command:**
```bash
./run-test.sh 2
```

### 3. Custom Fonts
**File:** `3-custom-fonts.html`
**Features:**
- Multiple font families
- Font weight variations
- Font size scaling
- Character set testing
- Multilingual support
- Font comparison tables

**Prerequisites:**
- Download fonts to `fonts/` directory (see `fonts/README.md`)

**Test Command:**
```bash
./run-test.sh 3
```

### 4. Header and Footer
**File:** `4-header-footer.html`
**Assets:** `assets/header.html`, `assets/footer.html`
**Features:**
- Multi-page document
- Custom header and footer
- Page numbering
- Professional document structure
- Chapter organization
- Consistent branding

**Test Command:**
```bash
./run-test.sh 4
```

### 5. Images and Logo
**File:** `5-images-logo.html`
**Features:**
- Logo placement examples
- Image placeholders
- Product showcases
- Image galleries
- Infographic elements
- Various image formats

**Test Command:**
```bash
./run-test.sh 5
```

### 6. Extremely Complex CSS
**Files:** `6-extremely-complex.html`, `6-extremely-complex.css`
**Features:**
- Advanced CSS features (most will be sanitized)
- CSS Grid and Flexbox layouts
- Animations and transitions
- Filter effects and blend modes
- Complex typography
- Modern CSS units
- Responsive design patterns

**Test Command:**
```bash
./run-test.sh 6
```

## 🔧 Manual Testing

You can also run tests manually using curl:

### Basic Test
```bash
curl -X POST http://localhost:8081/api/v1.0/print \
  -F 'htmlFile=@1-simple-inline-js.html' \
  -F 'jsEnable=true' \
  --output output/manual-test.pdf
```

### Test with CSS File
```bash
curl -X POST http://localhost:8081/api/v1.0/print \
  -F 'htmlFile=@2-complex-layout.html' \
  -F 'cssFile=@2-complex-layout.css' \
  -F 'jsEnable=false' \
  --output output/complex-layout.pdf
```

### Test with Assets (Fonts)
```bash
curl -X POST http://localhost:8081/api/v1.0/print \
  -F 'htmlFile=@3-custom-fonts.html' \
  -F 'assets=@fonts/Roboto-Regular.ttf' \
  -F 'assets=@fonts/Roboto-Bold.ttf' \
  -F 'jsEnable=false' \
  --output output/custom-fonts.pdf
```

### Test with Header and Footer
```bash
curl -X POST http://localhost:8081/api/v1.0/print \
  -F 'htmlFile=@4-header-footer.html' \
  -F 'headerFile=@assets/header.html' \
  -F 'footerFile=@assets/footer.html' \
  -F 'jsEnable=false' \
  --output output/header-footer.pdf
```

## 🎯 CSS Processing Features

The EasyJavaPDF service includes a sophisticated CSS processor that:

### ✅ Preserves Safe CSS
- Basic typography (font-family, font-size, font-weight)
- Colors and backgrounds
- Margins, padding, and spacing
- Borders and outlines
- Text alignment and decoration
- Safe @font-face declarations

### ❌ Sanitizes Problematic CSS
- Complex @page rules with nested selectors
- CSS animations and keyframes
- Transform and transition properties
- Filter effects and blend modes
- Flexbox and Grid layout properties
- Multi-column layouts
- Background-image url() references
- Pseudo-element content generation
- Import statements

## 📊 Expected Results

After running tests, you should see:

1. **PDF files generated** in the `output/` directory
2. **No FileNotFoundException errors** (CSS processor prevents these)
3. **Clean, professional-looking PDFs** with preserved styling
4. **Consistent layout** across different test scenarios
5. **Proper font rendering** (when font files are provided)

## 🐛 Troubleshooting

### Server Not Running
```
[ERROR] Server is not running. Please start the server with: ./gradlew bootRun
```
**Solution:** Start the server in another terminal

### Font Files Missing
```
[WARNING] Skipping custom fonts test - no font files found in fonts/ directory
```
**Solution:** Download fonts as described in `fonts/README.md`

### Empty PDF Output
```
[ERROR] Test failed: test-name - Output file is empty or missing
```
**Solution:** Check server logs for detailed error messages

### Permission Denied
```
bash: ./run-test.sh: Permission denied
```
**Solution:** Make the script executable: `chmod +x run-test.sh`

## 📈 Performance Notes

- **Simple tests** (1, 5): ~1-2 seconds
- **Complex tests** (2, 6): ~3-5 seconds
- **JavaScript tests** (1): ~2-3 seconds (includes Chrome WebDriver)
- **Font tests** (3): ~2-4 seconds (depends on font file sizes)
- **Header/Footer tests** (4): ~2-3 seconds

## 🔍 Validation

Each test validates:
1. **PDF generation success** (no errors)
2. **File size > 0** (content was generated)
3. **Proper PDF format** (starts with %PDF header)
4. **CSS sanitization** (no FileNotFoundException)
5. **Layout preservation** (visual inspection recommended)

## 📝 Adding New Tests

To add a new test scenario:

1. Create HTML file: `N-test-name.html`
2. Create CSS file (optional): `N-test-name.css`
3. Add test case to `run-test.sh`
4. Update this README
5. Test manually first
6. Add to the `run_all_tests()` function

## 🤝 Contributing

When contributing new test scenarios:
- Follow the existing naming convention
- Include comprehensive comments
- Test edge cases and error conditions
- Document expected behavior
- Ensure cross-platform compatibility