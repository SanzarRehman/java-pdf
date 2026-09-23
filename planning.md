# Port the `id-card` report from python-pdf into java-pdf

## Context

`easy-pdf` + `python-pdf` serve student ID cards through `POST /api/v1.0/print` (Flask + Jinja2 + WeasyPrint). We want the same capability in `java-pdf` (Spring Boot 3.4.1, Java 21, branch `template-engine`), using Thymeleaf for templating, so the same curl produces an equivalent PDF:

```
curl --location 'http://localhost:8081/api/v1.0/print' \
  --form 'report="admission/id-card.html"' \
  --form 'template="id-card"' \
  --form 'file_name="id-card"' \
  --form 'data="{...}"'
```

No `X-API-KEY`. Scope is the ID card only — no `url`, `data_set`, `password`, `driver` or `options` work.

`readme-python2.md` is the plain-English walkthrough of exactly this request and was used as the behavioral spec; it is cited below where it settles a design question.

**Is it possible? Yes**, and most of the machinery already exists. `report` and `data` are already implemented on `PdfGenerationRequest` with semantics that match Python:

| | Python | java-pdf today | Verdict |
|---|---|---|---|
| `report` | Jinja path under `/data/reports` | classpath name under `/templates`; `.html` suffix stripped, then `<n>.html` / `<n>/index.html` / `<n>/<last>.html` tried | **compatible, no clash** |
| `data` | JSON splatted to top-level Jinja vars | JSON → `Map` used as the Thymeleaf model root, so keys become top-level vars | **compatible, no clash** |
| `template` | separate asset folder under `/data/templates` | **missing** | new field |
| `file_name` | → `Content-Disposition` filename | **missing**; filename hardcoded from `report` | new field |
| `students`, `academicDegree`, `full_name`, `student_id`, `academic_type`, `ribbonColor`, `validity_date`, `blood_type`, `photo_id`, `label` | — | no Java class/field/enum of any of these names | **no clash** |

Report-based requests always Thymeleaf-render (`ReportBasedGenerationStrategy` calls `renderTemplate` directly), so `thymeleafTemplate=true` is *not* needed. The validator already requires `data` when `report` is present.

**Renderer: Chromium** (the repo default, via the warm Node/Puppeteer sidecar). The card's CSS depends on `transform: scaleX(-1)` (3 images), `object-fit: cover`, `display: flex`, and is almost entirely `position: absolute` with top/bottom/left/right offsets. iText/pdfHTML 6.0.0 supports neither `transform` nor `object-fit` and its absolute positioning is unreliable — the card would render unmirrored, stretched and misaligned. Chromium, like WeasyPrint, is CSS-spec-compliant.

## Decisions taken

- `template` mirrors Python exactly: java-pdf gets a real `src/main/resources/reports/` folder (markup) alongside `src/main/resources/templates/` (look), mirroring `easy-pdf/data/reports/` + `easy-pdf/data/templates/` 1:1. `ReportTemplateService` tries `classpath:/reports/` first, falling back to `classpath:/templates/` so `welcome-letter` (co-located with its own assets) keeps resolving exactly as before.
- Zero margins come from auto-honoring `@page { margin }` found in the resolved CSS, so the curl needs no extra parameter.
- Disposition stays `attachment`; only the *filename* is fixed. No `disposition` request field, no change to `PdfResponse`.

## Files

| # | File | Change |
|---|---|---|
| 1 | `src/main/resources/reports/admission/id-card.html` | new — Thymeleaf port of the Jinja report |
| 2 | `src/main/resources/templates/id-card/` (9 files) | new — copied from `easy-pdf/data/templates/id-card/`, 2 small CSS additions |
| 3 | `dto/PdfGenerationRequest.java` | + `template`, `fileName`, `setFile_name` alias setter |
| 4 | `util/PdfGenerationRequestValidator.java` | + `validateTemplateName`, `validateFileName` |
| 5 | `service/ReportTemplateService.java` | + `REPORT_PREFIX` fallback chain, 3-arg `prepareTemplate` overload, `copyTemplateAssetsFlat`, tolerate a missing same-name asset folder |
| 6 | `service/strategy/ReportBasedGenerationStrategy.java` | pass `request.getTemplate()`; use `file_name` for the response name |
| 7 | `service/strategy/PdfGenerationHelper.java` | + `buildResponseFileName` |
| 8 | `service/renderer/ChromiumPdfRenderer.java` | + `parsePageMargin`, honor `@page { margin }` |
| 9 | `src/main/resources/scripts/renderer-server.js` | + `--allow-file-access-from-files` |
| 10 | `src/test/.../dto/PdfGenerationRequestTest.java` | fix the **pre-existing** stale constructor call (was already broken before this work) |

No new dependencies. No renderer swap.

---

## 1. Resource layout — mirrors easy-pdf exactly

```
easy-pdf/data/                              java-pdf/src/main/resources/
  reports/                                    reports/
    admission/                                  admission/
      id-card.html          <----------->          id-card.html
  templates/                                  templates/
    id-card/                                    id-card/
      style.css              <----------->          style.css
      logo.svg                                       logo.svg
      footer.svg                                      footer.svg
      brandBandUg.svg (+Pg, PreUni, Alumni)            brandBandUg.svg (+Pg, PreUni, Alumni)
      Circular-Std-Font.ttf                            Circular-Std-Font.ttf
      CircularStd-Book.otf                             CircularStd-Book.otf
    welcome-letter.html      (pre-existing, untouched)
    welcome-letter/          (pre-existing, untouched)
```

`ReportTemplateService` now has two classpath roots:

```java
private static final String REPORT_PREFIX = "classpath:/reports/";
private static final String TEMPLATE_PREFIX = "classpath:/templates/";
```

`locateTemplate` tries `REPORT_PREFIX` first, then `TEMPLATE_PREFIX` as a fallback — so `report=admission/id-card.html` resolves under the new `reports/` root, while `report=welcome-letter` keeps resolving under `templates/` exactly as before (backward compatible, zero risk to the one existing report).

**Per-request temp dir after `prepareTemplate("admission/id-card.html", "id-card", wd)`:**

```
<wd>/admission/id-card.html        <- the document (copied from reports/admission/id-card.html)
<wd>/admission/style.css           <- flattened from templates/id-card/
<wd>/admission/logo.svg  footer.svg  brandBand*.svg  *.ttf  *.otf
```

`htmlTarget` is `workingDirectory.resolve(relativePath)` = `<wd>/admission/id-card.html`, so **assets must land in `htmlTarget.getParent()`** — not `<wd>/`, not `<wd>/id-card/`. The resolution chain:

1. `ChromiumPdfRenderer.renderFromFile` reads the CSS file picked by `resolveCssFile` and puts its **text** into `config.cssContent`; the CSS file's own location becomes irrelevant.
2. Node does `page.goto("file://" + htmlPath)` → document base URL is `file:///<wd>/admission/`.
3. `<img src="logo.svg">` → `file:///<wd>/admission/logo.svg`. ✔
4. `page.addStyleTag({content})` creates a `<style>` element, which per CSSOM has **no URL of its own** and inherits the *document's* base URL, so `url('Circular-Std-Font.ttf')` → `file:///<wd>/admission/Circular-Std-Font.ttf`. ✔

Invariant: **every relatively-referenced asset — from the HTML or the injected CSS — must be a sibling of the HTML file.** This is exactly Python's flat-key model, as `readme-python2.md` §5 describes it: relative names in the HTML and CSS are really *keys into the template folder*, which is why `brandBandUg.svg` works without a path even though the value came from the JSON. Copying the folder flat beside the document reproduces that with the filesystem instead of an in-memory dict.

**Bug found and fixed while wiring this up:** the pre-existing `copyAssetTree` (used for a report's own same-name asset folder, e.g. `welcome-letter.html` + `welcome-letter/`) throws when that same-name folder simply doesn't exist — a case `welcome-letter` never hit, since its assets happen to share its name. Since `admission/id-card.html`'s assets live in the independently-named `id-card/` folder (via `template`), not in a `reports/admission/id-card/` folder, this surfaced immediately. Fixed by catching `FileNotFoundException` specifically and returning an empty asset list — absence of a same-named folder is not an error, it just means "no extra co-located assets," which is exactly the id-card case.

## 2. `template` parameter

**DTO** — append `template` and `fileName` **at the end of `PdfGenerationRequest`** (see §7 risk E), plus the alias setter:

```java
private String template;   // asset folder under classpath:/templates, copied flat beside the HTML
private String fileName;   // response file name (Python's file_name)

/** Binding alias: Spring matches form field names to JavaBean property names,
 *  so a field literally named "file_name" needs a "file_name" write property. */
public void setFile_name(String fileName) { this.fileName = fileName; }
```

**Validation** — add `validateTemplateName(request.getTemplate())` and `validateFileName(request.getFileName())` to `validate(...)`, mirroring the existing `validateReportName`: reject `..`, `\`, leading `/` or `.`, >255 chars, and anything outside `^[a-zA-Z0-9_./-]+$`.

**Asset copy** — keep the existing 2-arg `prepareTemplate` as a delegating one-liner so current behavior and tests are byte-identical, and add a 3-arg overload that also calls `copyTemplateAssetsFlat` when `templateName` is provided.

`copyTemplateAssetsFlat` globs `classpath:/templates/<template>/**` via the existing `resourcePatternResolver`, skips unreadable entries (directories), flattens each to its basename (matching Python, whose `Template.assets` is keyed by `FileStorage.filename`), copies into the report's own document directory, and throws `ValidationException` if nothing matched.

**Deliberate divergence — fail loud on an unknown `template`.** `readme-python2.md` §8 lists "a typo in `template` is silent" as its first gotcha: Python finds no stylesheets and returns a plain, unstyled PDF, so a wrong name looks like a CSS bug. Throwing `ValidationException` instead turns that into an immediate 400. Worth keeping, and worth a README line.

**Known limitation — one stylesheet per template.** `readme-python2.md` §3 notes Python reads *every* `.css` in the folder as a stylesheet; `resolveCssFile` picks only the **first**. Irrelevant for `id-card` (a single `style.css`) but it would silently drop a stylesheet for a future multi-CSS template such as Python's `attendance/` or `certificate/` folders. Out of scope here — note it, don't fix it.

Call site is one line in `ReportBasedGenerationStrategy.generate`:

```java
ReportTemplateDescriptor descriptor = reportTemplateService.prepareTemplate(
        request.getReport(), request.getTemplate(), requestTempDir);
```

`resolveCssFile` needs no change — `reports/admission/id-card/` does not exist, so `style.css` (from `templates/id-card/`) is the only `.css` in `copiedResources`.

## 3. `file_name` parameter

**Why the alias setter:** Spring's `ServletModelAttributeMethodProcessor` binds through `WebDataBinder`/`BeanWrapperImpl`, matching parameter names against JavaBean property names, and `setIgnoreUnknownFields` defaults to `true` — so a form field named `file_name` is *silently dropped* today. `Introspector.decapitalize("File_name")` is `"file_name"`, so `setFile_name` creates a write-only property with that exact name. One line, co-located with the field, no MVC configuration, works for multipart and query params alike.

Rejected: `@InitBinder` (Spring has no alias API — would need a `ServletRequestDataBinder` subclass rewriting `MutablePropertyValues`), `WebMvcConfigurer` (same work, applies app-wide), and a second `@RequestParam("file_name")` controller argument (splits one logical request in two and bypasses `PdfGenerationRequestValidator`).

The same latent gap exists for `dataSet` (`@JsonAlias("data_set")` is a *Jackson* annotation and does nothing for form binding). Out of scope; note it in the README.

**Extension handling** — mirror Python's `os.path.splitext` + `".pdf"`. Add to `PdfGenerationHelper` beside `buildReportFileName`:

```java
public String buildResponseFileName(String fileName, String report) {
    if (!StringUtils.hasText(fileName)) return buildReportFileName(report);   // unchanged fallback
    // strip any path, strip a trailing extension only when dot > 0 (".bashrc" keeps its name),
    // sanitize to [a-zA-Z0-9_.-], clamp to 60 chars, append ".pdf"
}
```

`id-card` → `id-card.pdf`; `id-card.pdf` → `id-card.pdf`; `report.v2` → `report.pdf`.

## 4. Response

`Content-Type` is already `application/pdf` (set in `HtmlToPdfController`). **`PdfResponse` is not touched** — `getContentDispositionHeader()` keeps returning `disposition + "; filename=" + fileName`, and `ReportBasedGenerationStrategy` keeps `.disposition("attachment")`. Only the filename changes:

```java
.fileName(helper.buildResponseFileName(request.getFileName(), request.getReport()))
```

Result: `Content-Disposition: attachment; filename=id-card.pdf`. Without `file_name`, behavior is byte-identical to today, so `welcome-letter` does not regress.

## 5. Zero-margin page

`ChromiumPdfRenderer.buildConfig` unconditionally sets `margin` to 20px on all four sides, and Puppeteer applies `margin` **independently of `preferCSSPageSize`**, overriding CSS. On a 205.98 × 325.04 px page that leaves a 165.98 × 285.04 px content box — each card overflows into extra pages and everything shifts 20px right and down. This must be fixed.

**Fix inside `ChromiumPdfRenderer`**, where the CSS text is already in hand — no DTO field, no `RendererTuning` change, no signature changes anywhere. Right after `config.put("cssContent", cssContent)`:

```java
Map<String, String> cssMargin = parsePageMargin(cssContent);
if (cssMargin != null) {
    logger.info("Honoring @page margin from stylesheet: {}", cssMargin);
    config.put("margin", cssMargin);       // replaces buildConfig's 20px default
}
```

Mirror the same three lines in `render(String htmlContent, String cssContent, ...)` for the content path.

`parsePageMargin(String css)`: match `@page\s*\{([^}]*)}` (deliberately *not* `@page :first {`), then `margin\s*:\s*([^;}]+)` inside it, expand the 1-to-4-value CSS shorthand, and convert bare `0`/`auto` to `"0px"` since Puppeteer wants unit-bearing strings. Returns `null` when there is no `@page` or no margin in it, leaving the 20px default intact.

**Regression surface is one file:** only `src/main/resources/more/style.css` declares `@page { margin: 1in }`, and it is a hand-testing sample referenced from no Java code, README example or test. `templates/welcome-letter/style.css` has no `@page` rule at all, so the only existing report template is provably unaffected.

Rejected: an explicit `margin` request param (Python has none, so the curl stops being drop-in) and threading it through `RendererTuning` (`buildTuning` returns `null` unless one of `chunkSizeMb`/`parallelism`/`fitToWidth`/`scale` is set, so the margin would be silently dropped — a real foot-gun — and it would fix only the report path).

`preferCSSPageSize: true` is already set and needs no change.

## 6. The Thymeleaf template

Port `easy-pdf/data/reports/admission/id-card.html` verbatim in structure — back card (`.card-body`) first, then front card (`.id-card`, which carries `page-break-after: always`), both inside the student loop, giving 2 pages per student.

| Jinja | Thymeleaf |
|---|---|
| `{% for student in students %}` | `<th:block th:each="student : ${students}">` (emits no markup) |
| `{{ student.full_name }}` | `th:text="${student['full_name']}"` — bracket access, matching `welcome-letter.html`, since the model root is a `Map` |
| `src="brandBand{{student.ribbonColor}}.svg"` | `th:src="'brandBand' + ${student['ribbonColor']} + '.svg'"`, static `src` kept as a design-time fallback |
| `{% if student.academic_type in academicDegree %}` | `th:if="${academicDegree != null and #lists.contains(academicDegree, student['academic_type'])}"` — the null guard is required, `#lists.contains(null, ...)` NPEs |
| `{% if student.validity_date %}` / `blood_type` / `barcode` | `th:if="${!#strings.isEmpty(student['...'])}"` — **not** bare `th:if="${x}"`: Thymeleaf treats `""` as truthy while Jinja does not, and `#strings.isEmpty(null)` is `true`, so this reproduces Jinja exactly |

Deliberate, parity-safe deviations: add `<!DOCTYPE html>` (the Python file has none; Chromium would use quirks mode, WeasyPrint never does), self-close `<br/>`/`<img/>`, and keep the technically-invalid `<h2><p>STUDENT</p></h2>` verbatim — HTML5 parsing keeps the `<p>` inside the `<h2>` in both engines, so margins match. No `th:href`/`@{}` link expressions; plain relative URLs already resolve correctly per §1.

**Two additions to the copied `templates/id-card/style.css`:**

```css
.card-body {
    /* ...existing... */
    position: relative;        /* ADDED — correctness, see risk A */
    page-break-after: always;  /* ADDED — make the back/front split explicit */
}
```

## 7. Ordering and risks

1. Copy the 9 asset files, write `admission/id-card.html` under the new `reports/` root.
2. `ReportTemplateService` `REPORT_PREFIX`/`TEMPLATE_PREFIX` fallback chain + overload + `copyTemplateAssetsFlat` + the `FileNotFoundException` tolerance fix; unit-test the flat layout first — this is where silent breakage hides.
3. DTO fields + `setFile_name` + validator + strategy call site + `buildResponseFileName`. Curl now returns a PDF with wrong margins.
4. `parsePageMargin` in `ChromiumPdfRenderer`. Page geometry now correct.
5. `--allow-file-access-from-files` in `renderer-server.js` (risk B).
6. Visual diff against the Python output; iterate on A/D if page count or multi-student layout is off.

**Risk A (highest) — `.card-body` has no `position: relative`.** `style.css` sets it on `.id-card` but not `.card-body`, so the back card's `.back-footer`, `.brand-band` and `.barcode` resolve against the initial containing block. WeasyPrint's ICB is the page area, so `bottom: 0` lands correctly per page; in Chromium print the ICB is the **first page's** area, so with 2+ students those boxes land on the wrong page or off-page. The single-student curl will *not* expose this. Fix is the `position: relative` above — geometrically identical for one card in both engines. **Test with a 2-student payload**: expect 4 pages, band/footer correct on all.

**Risk B (high) — `@font-face` may be blocked on the warm sidecar.** `puppeteer-pdf.js` launches with `--allow-file-access-from-files` and `--disable-web-security`; `renderer-server.js` launched with **neither** — and `run.sh` makes the sidecar the default path. `<img src>` is not a CORS request and works either way, but `@font-face` fetches are always CORS-mode and cross-`file://` subresources are opaque-origin by default. Symptom: layout is fine but CircularStd silently falls back and every text run has different metrics. Fix: add `'--allow-file-access-from-files'` to that `args` array. Diagnose with `pdffonts`.

**Risk C (downgraded — the default path is already correct).** `readme-python2.md` §5 confirms Python blocks every outside URL unless it matches `ALLOWED_URL_PATTERN`, which in `easy-pdf/docker-compose.yml` permits only `google.com` — so `https://example.com/photo.jpg` is **not** downloaded there either; the card renders with an empty photo box and a log warning. `renderer-server.js` aborts all non-`file://`/`data:` URLs unconditionally, which is **the same behavior**. Since `run.sh` makes the sidecar the default path, the out-of-the-box result matches Python. For real photos, Python's own advice applies: send a base64 `data:` URI in `photo_id`. Do not add network-fetch plumbing or an allow-list.

**Risk D (medium, confirmed in practice) — fractional page height adds a blank page.** The cards are `325.03937008px` tall inside a `325.03937008px` box; Chromium's `@page` size conversion truncates sub-pixel, so the box ends up a hair taller than the printable area, fragmenting a near-blank extra page and clipping bottom-anchored children (e.g. `.back-footer`). Observed directly: a live single-student render came back as **3 pages instead of 2**, with the middle page holding just the tail of the ribbon graphic, and the mirrored footer glyphs missing off the bottom of both real pages. Remedy: clamp `.card-body, .id-card { height: 325px }` (from `325.03937008px`) — shifts the `bottom: 0` absolutes by 1/25 of a pixel, invisible at print resolution. Applied to `templates/id-card/style.css`. **Verify with `pdfinfo` after any resource change — this requires killing and restarting the running app (`./run.sh`); `bootRun`'s classpath resources do not hot-reload,** so a stale build can make a real fix look like it did nothing.

**Risk E (confirmed, will bite at build time) — `src/test` was already red before this work.** `./gradlew compileTestJava` failed on `template-engine` before any of these changes: `PdfGenerationRequestTest.java` passed 23 args to a 24-field `@AllArgsConstructor` (`mode` was added without updating the test). Appending `template` and `fileName` **at the end of the class** makes the fix a 3-argument append rather than a re-shuffle — do not insert the new fields mid-class.

**Risk F (low) — SVG intrinsic sizing.** `logo.svg` has a `viewBox` but no `width`/`height`; every rule using it sets both dimensions or a width inside a fixed-width parent, so Chromium derives height from the viewBox ratio exactly as WeasyPrint does. Don't "tidy" the SVGs.

**Risk G (low) — README drift.** `README.md` already documents `template`, `file_name` and `disposition` as if they exist. After this change two of the three are real; note that `disposition` is merge-only, and that `data_set` must be sent as `dataSet`.

## 8. Verification

```bash
cd /home/sakibuihaque/Projects/dummy/pdf-service/java-pdf
JAVA_HOME=~/.sdkman/candidates/java/21.0.2-open ./gradlew compileJava compileTestJava -q   # the system default JDK (25) breaks Gradle's own report-container reflection; use 21
JAVA_HOME=~/.sdkman/candidates/java/21.0.2-open ./run.sh                                   # renderer-server:3001, then bootRun on :8081
```

`run.sh` binds **8081** — stop any previously running instance first (`kill` the `run.sh`/gradle-wrapper/java/node PIDs — plain `pkill -f bootRun` does not reliably match) or the new process dies with "Port 8081 was already in use". It exports `RENDERER_SERVER_URL`, routing renders to the **warm sidecar**, not `puppeteer-pdf.js` (matters for risks B and C).

```bash
curl -sS -D /tmp/id-card.headers -o /tmp/id-card.pdf \
  --location 'http://localhost:8081/api/v1.0/print' \
  --form 'report="admission/id-card.html"' \
  --form 'template="id-card"' \
  --form 'file_name="id-card"' \
  --form 'data="{\"academicDegree\":[\"UG\",\"PG\"],\"students\":[{\"full_name\":\"Sakib Ul Haque\",\"student_id\":\"23341128\",\"academic_type\":\"UG\",\"label\":\"Computer Science\",\"ribbonColor\":\"Ug\",\"validity_date\":\"Dec 2027\",\"blood_type\":\"A+\",\"photo_id\":\"https://example.com/photo.jpg\"}]}"'

grep -i 'content-type\|content-disposition' /tmp/id-card.headers
#   Content-Type: application/pdf
#   Content-Disposition: attachment; filename=id-card.pdf

pdfinfo /tmp/id-card.pdf
#   Pages:     2                      <- exactly 2 per student (verify after the Risk D fix)
#   Page size: 154.49 x 243.78 pts    <- 205.98 x 325.04 px @96dpi = 54.5 x 86 mm (CR80)

pdffonts /tmp/id-card.pdf            # expect embedded CircularStd + CircularStd-Book;
                                      # a DejaVu/Liberation fallback means risk B
pdftoppm -r 150 -png /tmp/id-card.pdf /tmp/id-card   # visual diff vs the Python output
```

`pdfinfo`/`pdffonts`/`pdftoppm` are already at `/usr/bin/`.

Visual checklist vs the Python PDF — page 1 (back): logo centred ~26px from top, brand band at `top: 120.98px` spanning the full width, address block centred, footer glyph bottom-**right** and **mirrored**. Page 2 (front): logo top-left, photo top-right cropped by `object-fit: cover` to 98.1 × 131.2, brand band **mirrored**, name at `top: 155px` right-aligned, "STUDENT" + "Computer Science" at `top: 210px`, 3-row detail table bottom-right, footer glyph bottom-**left**, **not** mirrored. Nothing inset 20px from any edge — that's the margin bug.

**Tests added** — `src/test/java/com/bracits/easyJavaPdf/service/strategy/IdCardReportStrategyTest.java`, modelled on the existing `WelcomeLetterReportStrategyTest` (real `ReportTemplateService` + `TemplateRenderingService`, mocked `PdfGenerator`/`TempFileManager`, `@TempDir`). Asserts on the *inputs to the renderer*, so no browser is needed:

- `prepareTemplate("admission/id-card.html", "id-card", wd)` yields `wd/admission/style.css`, `wd/admission/logo.svg`, `wd/admission/Circular-Std-Font.ttf` as **flat siblings** of `wd/admission/id-card.html` — the regression test for §1;
- the rendered HTML contains `brandBandUg.svg`, `Sakib Ul Haque`, `23341128`, `>STUDENT<`, `Dec 2027`;
- `PdfResponse.getFileName()` is `id-card.pdf` and the disposition header is `attachment; filename=id-card.pdf`.

Plus `src/test/java/com/bracits/easyJavaPdf/service/renderer/ChromiumPdfRendererTest.java` for `parsePageMargin`: `@page{margin:0}` → all `"0px"`; `@page{margin:1in}` → all `"1in"`; `@page :first{margin-top:2in}` alone → `null`; no `@page` → `null` (the welcome-letter non-regression).

A pdfbox end-to-end check (`getNumberOfPages() == 2`, `getPage(0).getMediaBox()` ≈ 154.49 × 243.78 pt) is worth adding — `pdfbox:3.0.3` is already a dependency — but gate it behind `@EnabledIfEnvironmentVariable` since it needs Node + Chromium.

## 9. Status as of last verification

Implemented and live-tested via the curl above: HTTP 200, correct `Content-Type`/`Content-Disposition`, fonts embedded (`pdffonts` shows CircularStd + CircularStd-Book, not a fallback). Still open: the last live run came back as **3 pages instead of 2** for one student — the Risk D fix (`height: 325px`) is in the source CSS, but the running app needs a full stop/restart (not just a resource edit) before re-testing, since `bootRun` does not hot-reload `src/main/resources`. Re-run §8's verification after restart to confirm page count drops to 2 before calling this done.
