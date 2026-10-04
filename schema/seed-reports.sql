-- Seed data for pdf_report_templates.
--
-- Only "student-gradesheet" is migrated to the database for now.
-- "id-card" intentionally stays file-based under src/main/resources/reports/ —
-- it simply has no row here, so ReportTemplateService's classpath fallback
-- keeps serving it exactly as it does today. It can be added the same way
-- later if that ever becomes the goal.
--
-- The `key` is the string the client must send in the `report` form field,
-- normalized (leading "/" and trailing ".html" removed):
--   report="student-grade.html" -> key "student-grade"
-- This is shorter than the file's classpath path on purpose - the key is a
-- lookup identifier, not a filesystem mirror. It has no bearing on the
-- `template` field (asset folder), which is resolved independently.
--
-- The body below is copied verbatim from
-- src/main/resources/reports/exam-controller/gradesheet/student-grade.html
-- Its CSS/logo (style.css, logo.png) stay on the classpath under
-- src/main/resources/templates/student-gradesheet/, copied flat next to the
-- rendered HTML at request time, unchanged.
--
-- `variables` documents every ${...}/th: variable path the body actually
-- references (cross-checked against the file with `grep -oE '\$\{[^}]*\}'`)
-- so a person supplying `data`/`data_set` for this report can see every
-- value it needs without reading the Thymeleaf markup. It is display-only —
-- see pgsql.sql's comment. Flattened with "." for nested map keys and "[]"
-- for the two repeating lists (one entry per semester, one per course).

insert into pdf_report_templates (key, body, variables)
values ('student-grade', $html$<!doctype html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="utf-8">
    <link href="./style.css" rel="stylesheet">
    <title>Written Exam Attendance Sheet</title>
    <meta name="description" content="SLM Attendance sheet">
</head>
<body>
<header>
    <div class="w-100 ">
        <div class="w-40 text-right float-left">
            <img class="logo" src="./logo.png" alt="QR" style="width: 60px;"/>
        </div>
        <div class="w-40 float-left text-center fs-10">
            <p class="uni-name fs-16 font-bold m-0 p-0">BRAC University</p>
            <p class="uni-address m-0 p-0 mt-10">Kha 224, Bir Uttam Rafiqul Islam Avenue <br/> Merul Badda, Dhaka 1212.</p>
            <p class="font-bold m-0 p-0 mb-10 mt-10 fs-14">GRADE SHEET</p>
            <p class="font-bold m-0 p-0 mb-10 mt-10">STUDENT'S COPY</p>
        </div>
    </div>

    <div class="header-container w-100 ">
        <div class="student-basic-info float-left w-44 text-left fs-10">
            <table class="font-bold font-italic">
                <tr>
                    <td th:text="'Student ID : ' + ${portfolioInfo['studentId']}">
                        Student ID :
                    </td>
                </tr>
                <tr>
                    <td th:text="'Name : ' + ${portfolioInfo['name']}">Name :</td>
                </tr>
            </table>
        </div>
        <div class="student-other-info w-55 float-left">
            <div class="program-info float-left w-95 text-left">
                <table>
                    <tbody>
                    <tr>
                        <td class="font-bold fs-10 mt-5" colspan="2" th:text="${portfolioInfo['academicType']}"></td>
                    </tr>
                    <tr>
                        <td class="font-bold fs-10 mt-5" style="vertical-align: top">Program : </td>
                        <td class="fs-9 text-upper font-bold" th:text="${portfolioInfo['program']}"></td>
                    </tr>
                    </tbody>
                </table>
            </div>
            <div class="qr-info float-right">
                <img th:src="${qrCodeImage}" alt="QR" style="width: 100px; height: 100px;"/>
            </div>
        </div>
    </div>
</header>
<div class="container w-100 mt--20">
    <div class="content w-100">
        <table class="w-100 m-0 p-0" style="border-collapse: collapse;">
            <thead>
            <tr class="font-bold">
                <td class="w-15 text-left" style="padding-left: 10px;">Course No</td>
                <td class="w-47 text-left">Course Title</td>
                <td class="w-14 text-center">Credit Earned</td>
                <td class="w-10 text-left">Grade</td>
                <td class="w-14 text-center">Grade Points</td>
            </tr>
            </thead>
            <tbody>
            <th:block th:each="sessionWiseData : ${transcriptData}">

            <tr class="bg-light font-bold">
                <td class="w-14 text-left font-italic" style="padding-left: 10px;">SEMESTER:</td>
                <td class="w-86 text-left" colspan="4"
                    th:text="|${sessionWiseData['semesterStanding']['semester']} ${sessionWiseData['semesterStanding']['year']}|">
                </td>
            </tr>

            <tr th:each="courseResult : ${sessionWiseData['courseResults']}">
                <td class="w-15 text-left text-upper" style="text-indent: 10px;" th:text="${courseResult['courseCode']}"></td>
                <td class="w-47 text-left text-upper" th:text="${courseResult['name']}"></td>
                <td class="w-14 text-center" th:text="${courseResult['courseCredit']}"></td>
                <td class="w-10 text-left" th:text="${courseResult['grade']}"></td>
                <td class="w-14 text-center" th:text="${courseResult['gpa']}"></td>
            </tr>
            <tr class="bg-lighter">
                <td colspan="5" class="w-100 m-0 m-0 p-0">
                    <table class="w-100 m-0 m-0 p-0 semester-standing-tbl">
                        <tr>
                            <td class="w-14 text-left" style="padding-left: 10px;">SEMESTER</td>
                            <td class="w-18 text-left" style="padding-left: 8px">Credits Attempted</td>
                            <td class="w-12 text-center" th:text="${sessionWiseData['semesterStanding']['semesterCreditAttempt']}">
                            </td>
                            <td class="w-18 text-left">Credits Earned</td>
                            <td class="w-14 text-center" th:text="${sessionWiseData['semesterStanding']['semesterCreditEarned']}">
                            </td>
                            <td class="w-10 text-right">GPA</td>
                            <td class="w-14 text-center" th:text="${sessionWiseData['semesterStanding']['semesterGpa']}"></td>
                        </tr>

                        <tr>
                            <td class="w-14 text-left" style="padding-left: 10px;">CUMULATIVE</td>
                            <td class="w-18 text-left" style="padding-left: 8px">Credits Attempted</td>
                            <td class="w-12 text-center" th:text="${sessionWiseData['semesterStanding']['cumulativeCreditAttempt']}">
                            </td>
                            <td class="w-18 text-left">Credits Earned</td>
                            <td class="w-14 text-center" th:text="${sessionWiseData['semesterStanding']['cumulativeCreditEarned']}">
                            </td>
                            <td class="w-10 text-right">CGPA</td>
                            <td class="w-14 text-center" th:text="${sessionWiseData['semesterStanding']['cumulativeCgpa']}"></td>
                        </tr>
                    </table>
                </td>
            </tr>

            <tr th:if="${!#strings.isEmpty(sessionWiseData['semesterStanding']['recognitionType'])}">
                <td colspan="5" class="text-center font-bold"
                    th:text="|**${sessionWiseData['semesterStanding']['recognitionType']}**|">
                </td>
            </tr>

            </th:block>

            <tr>
                <td colspan="5" class="font-bold fs-9 pt-30 pb-10"><span class="font-bold"> Academic Standing : </span>
                    <span th:text="${portfolioInfo['academicStanding']}"></span>
                </td>
            </tr>

            </tbody>
        </table>
    </div>
</div>

<footer class="w-100">
    <div class="float-left footer-left w-33">
        <p class="text-left issue-date" th:text="${preparedBy}"></p>
        <p class="issue-date-text text-left border-top font-bold">Prepared By</p>
    </div>
    <div class="float-left footer-left w-33 text-center">
        <p class=" " th:text="${generateDate}"></p>
        <p class="border-top font-bold" style="padding: 2px 30px"> Date</p>
    </div>
    <div class="float-right w-33">
        <p class="text-left issue-date m-0"> &nbsp; </p>
        <p class="text-right signatory border-top float-right mt-0 font-bold ">
            Deputy Controller of Examinations
        </p>
    </div>
</footer>

</body>
</html>
$html$, $json${"portfolioInfo.studentId":"Student ID","portfolioInfo.name":"Student Name","portfolioInfo.academicType":"Academic Type","portfolioInfo.program":"Program","portfolioInfo.academicStanding":"Academic Standing","qrCodeImage":"QR Code Image (URL or base64)","transcriptData":"Transcript Data (list, one entry per semester)","transcriptData[].semesterStanding.semester":"Semester Name","transcriptData[].semesterStanding.year":"Semester Year","transcriptData[].semesterStanding.semesterCreditAttempt":"Semester Credits Attempted","transcriptData[].semesterStanding.semesterCreditEarned":"Semester Credits Earned","transcriptData[].semesterStanding.semesterGpa":"Semester GPA","transcriptData[].semesterStanding.cumulativeCreditAttempt":"Cumulative Credits Attempted","transcriptData[].semesterStanding.cumulativeCreditEarned":"Cumulative Credits Earned","transcriptData[].semesterStanding.cumulativeCgpa":"Cumulative CGPA","transcriptData[].semesterStanding.recognitionType":"Recognition Type (e.g. Dean's List; optional)","transcriptData[].courseResults":"Course Results (list, one entry per course)","transcriptData[].courseResults[].courseCode":"Course Code","transcriptData[].courseResults[].name":"Course Title","transcriptData[].courseResults[].courseCredit":"Credit Earned","transcriptData[].courseResults[].grade":"Grade","transcriptData[].courseResults[].gpa":"Grade Points","preparedBy":"Prepared By","generateDate":"Generated Date"}$json$)
on conflict (key) do update set body = excluded.body, variables = excluded.variables;
