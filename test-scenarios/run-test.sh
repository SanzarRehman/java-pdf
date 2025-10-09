#!/bin/bash

# EasyJavaPDF Test Runner
# This script runs various PDF generation tests with different scenarios

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
API_URL="http://localhost:8081/api/v1.0/print"
OUTPUT_DIR="output"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if server is running
check_server() {
    print_status "Checking if EasyJavaPDF server is running..."
    if curl -s -f "$API_URL" > /dev/null 2>&1; then
        print_success "Server is running"
        return 0
    else
        print_error "Server is not running. Please start the server with: ./gradlew bootRun"
        return 1
    fi
}

# Function to run a test
run_test() {
    local test_name="$1"
    local html_file="$2"
    local css_file="$3"
    local js_enable="$4"
    local additional_params="$5"
    
    print_status "Running test: $test_name"
    
    local output_file="$OUTPUT_DIR/${test_name}.pdf"
    local curl_cmd="curl -X POST $API_URL"
    
    # Add HTML file
    if [[ -f "$html_file" ]]; then
        curl_cmd="$curl_cmd -F 'htmlFile=@$html_file'"
    else
        print_error "HTML file not found: $html_file"
        return 1
    fi
    
    # Add CSS file if provided
    if [[ -n "$css_file" && -f "$css_file" ]]; then
        curl_cmd="$curl_cmd -F 'cssFile=@$css_file'"
    fi
    
    # Add JavaScript enable flag
    curl_cmd="$curl_cmd -F 'jsEnable=$js_enable'"
    
    # Add additional parameters
    if [[ -n "$additional_params" ]]; then
        curl_cmd="$curl_cmd $additional_params"
    fi
    
    # Add output file
    curl_cmd="$curl_cmd --output '$output_file'"
    
    # Execute the curl command
    print_status "Executing: $curl_cmd"
    if eval "$curl_cmd"; then
        if [[ -f "$output_file" && -s "$output_file" ]]; then
            local file_size=$(stat -f%z "$output_file" 2>/dev/null || stat -c%s "$output_file" 2>/dev/null || echo "unknown")
            print_success "Test completed: $test_name (Size: $file_size bytes)"
            return 0
        else
            print_error "Test failed: $test_name - Output file is empty or missing"
            return 1
        fi
    else
        print_error "Test failed: $test_name - Curl command failed"
        return 1
    fi
}

# Function to run test with assets
run_test_with_assets() {
    local test_name="$1"
    local html_file="$2"
    local css_file="$3"
    local js_enable="$4"
    shift 4
    local assets=("$@")
    
    print_status "Running test with assets: $test_name"
    
    local output_file="$OUTPUT_DIR/${test_name}.pdf"
    local curl_cmd="curl -X POST $API_URL"
    
    # Add HTML file
    curl_cmd="$curl_cmd -F 'htmlFile=@$html_file'"
    
    # Add CSS file if provided
    if [[ -n "$css_file" && -f "$css_file" ]]; then
        curl_cmd="$curl_cmd -F 'cssFile=@$css_file'"
    fi
    
    # Add assets
    for asset in "${assets[@]}"; do
        if [[ -f "$asset" ]]; then
            curl_cmd="$curl_cmd -F 'assets=@$asset'"
            print_status "Adding asset: $asset"
        else
            print_warning "Asset file not found: $asset"
        fi
    done
    
    # Add JavaScript enable flag
    curl_cmd="$curl_cmd -F 'jsEnable=$js_enable'"
    
    # Add output file
    curl_cmd="$curl_cmd --output '$output_file'"
    
    # Execute the curl command
    print_status "Executing: $curl_cmd"
    if eval "$curl_cmd"; then
        if [[ -f "$output_file" && -s "$output_file" ]]; then
            local file_size=$(stat -f%z "$output_file" 2>/dev/null || stat -c%s "$output_file" 2>/dev/null || echo "unknown")
            print_success "Test completed: $test_name (Size: $file_size bytes)"
            return 0
        else
            print_error "Test failed: $test_name - Output file is empty or missing"
            return 1
        fi
    else
        print_error "Test failed: $test_name - Curl command failed"
        return 1
    fi
}

# Function to run header/footer test
run_header_footer_test() {
    print_status "Running header/footer test"
    
    local output_file="$OUTPUT_DIR/header-footer-test.pdf"
    local curl_cmd="curl -X POST $API_URL"
    curl_cmd="$curl_cmd -F 'htmlFile=@4-header-footer.html'"
    
    # Add header and footer files if they exist
    if [[ -f "assets/header.html" ]]; then
        curl_cmd="$curl_cmd -F 'headerFile=@assets/header.html'"
        print_status "Adding header file"
    fi
    
    if [[ -f "assets/footer.html" ]]; then
        curl_cmd="$curl_cmd -F 'footerFile=@assets/footer.html'"
        print_status "Adding footer file"
    fi
    
    curl_cmd="$curl_cmd -F 'jsEnable=false'"
    curl_cmd="$curl_cmd --output '$output_file'"
    
    print_status "Executing: $curl_cmd"
    if eval "$curl_cmd"; then
        if [[ -f "$output_file" && -s "$output_file" ]]; then
            local file_size=$(stat -f%z "$output_file" 2>/dev/null || stat -c%s "$output_file" 2>/dev/null || echo "unknown")
            print_success "Header/Footer test completed (Size: $file_size bytes)"
            return 0
        else
            print_error "Header/Footer test failed - Output file is empty or missing"
            return 1
        fi
    else
        print_error "Header/Footer test failed - Curl command failed"
        return 1
    fi
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [test_number|all]"
    echo ""
    echo "Available tests:"
    echo "  1  - Simple HTML with inline CSS and JavaScript"
    echo "  2  - Complex HTML with external CSS"
    echo "  3  - Custom fonts test (requires font files)"
    echo "  4  - Header and footer test"
    echo "  5  - Images and logo test"
    echo "  6  - Extremely complex CSS test"
    echo "  all - Run all tests"
    echo ""
    echo "Examples:"
    echo "  $0 1          # Run simple inline test"
    echo "  $0 all        # Run all tests"
    echo "  $0 3          # Run custom fonts test"
}

# Function to check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    # Check if curl is available
    if ! command -v curl &> /dev/null; then
        print_error "curl is required but not installed"
        return 1
    fi
    
    # Check if we're in the right directory
    if [[ ! -f "1-simple-inline-js.html" ]]; then
        print_error "Please run this script from the test-scenarios directory"
        return 1
    fi
    
    print_success "Prerequisites check passed"
    return 0
}

# Function to run all tests
run_all_tests() {
    print_status "Running all tests..."
    
    local failed_tests=0
    local total_tests=6
    
    # Test 1: Simple inline with JavaScript
    if run_test "1-simple-inline-js" "1-simple-inline-js.html" "" "true"; then
        ((total_tests++))
    else
        ((failed_tests++))
    fi
    
    # Test 2: Complex layout
    if run_test "2-complex-layout" "2-complex-layout.html" "2-complex-layout.css" "false"; then
        ((total_tests++))
    else
        ((failed_tests++))
    fi
    
    # Test 3: Custom fonts (check if font files exist)
    local font_files=()
    for font in fonts/*.ttf fonts/*.otf fonts/*.woff fonts/*.woff2; do
        if [[ -f "$font" ]]; then
            font_files+=("$font")
        fi
    done
    
    if [[ ${#font_files[@]} -gt 0 ]]; then
        if run_test_with_assets "3-custom-fonts" "3-custom-fonts.html" "" "false" "${font_files[@]}"; then
            ((total_tests++))
        else
            ((failed_tests++))
        fi
    else
        print_warning "Skipping custom fonts test - no font files found in fonts/ directory"
        print_warning "See fonts/README.md for download instructions"
    fi
    
    # Test 4: Header and footer
    if run_header_footer_test; then
        ((total_tests++))
    else
        ((failed_tests++))
    fi
    
    # Test 5: Images and logo
    if run_test "5-images-logo" "5-images-logo.html" "" "false"; then
        ((total_tests++))
    else
        ((failed_tests++))
    fi
    
    # Test 6: Extremely complex CSS
    if run_test "6-extremely-complex" "6-extremely-complex.html" "6-extremely-complex.css" "false"; then
        ((total_tests++))
    else
        ((failed_tests++))
    fi
    
    # Summary
    echo ""
    echo "========================================="
    echo "           TEST SUMMARY"
    echo "========================================="
    echo "Total tests run: $total_tests"
    echo "Failed tests: $failed_tests"
    echo "Success rate: $(( (total_tests - failed_tests) * 100 / total_tests ))%"
    echo ""
    
    if [[ $failed_tests -eq 0 ]]; then
        print_success "All tests passed!"
    else
        print_error "$failed_tests test(s) failed"
    fi
    
    print_status "Output files are in the '$OUTPUT_DIR' directory"
}

# Main script logic
main() {
    echo "========================================="
    echo "        EasyJavaPDF Test Runner"
    echo "========================================="
    echo ""
    
    # Check prerequisites
    if ! check_prerequisites; then
        exit 1
    fi
    
    # Check if server is running
    if ! check_server; then
        exit 1
    fi
    
    # Parse command line arguments
    case "${1:-}" in
        1)
            run_test "1-simple-inline-js" "1-simple-inline-js.html" "" "true"
            ;;
        2)
            run_test "2-complex-layout" "2-complex-layout.html" "2-complex-layout.css" "false"
            ;;
        3)
            # Check for font files
            local font_files=()
            for font in fonts/*.ttf fonts/*.otf fonts/*.woff fonts/*.woff2; do
                if [[ -f "$font" ]]; then
                    font_files+=("$font")
                fi
            done
            
            if [[ ${#font_files[@]} -gt 0 ]]; then
                run_test_with_assets "3-custom-fonts" "3-custom-fonts.html" "" "false" "${font_files[@]}"
            else
                print_error "No font files found in fonts/ directory"
                print_warning "See fonts/README.md for download instructions"
                exit 1
            fi
            ;;
        4)
            run_header_footer_test
            ;;
        5)
            run_test "5-images-logo" "5-images-logo.html" "" "false"
            ;;
        6)
            run_test "6-extremely-complex" "6-extremely-complex.html" "6-extremely-complex.css" "false"
            ;;
        all)
            run_all_tests
            ;;
        -h|--help|help)
            show_usage
            ;;
        "")
            show_usage
            exit 1
            ;;
        *)
            print_error "Unknown test: $1"
            show_usage
            exit 1
            ;;
    esac
}

# Change to script directory
cd "$SCRIPT_DIR"

# Run main function
main "$@"