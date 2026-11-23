package com.teduscheduler.service;

import com.teduscheduler.model.CoursePlanItemType;
import com.teduscheduler.model.CoursePlanKind;
import com.teduscheduler.service.CoursePlanParser.ParsedFile;
import com.teduscheduler.service.CoursePlanParser.ParsedItem;
import com.teduscheduler.service.CoursePlanParser.ParsedPlan;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class CoursePlanParserTest {

    // ---------- bellekte çalışma kitabı kurma yardımcıları ----------

    static void put(Sheet sh, int r, int c, Object v) {
        Row row = sh.getRow(r) != null ? sh.getRow(r) : sh.createRow(r);
        if (v == null) return;
        Cell cell = row.createCell(c);
        if (v instanceof Number) cell.setCellValue(((Number) v).doubleValue());
        else cell.setCellValue(v.toString());
    }

    /** Yarıyıl başlığı + satırlar + "Yarıyıl Kredileri" satırı; sonraki boş satırı döndürür. */
    static int sem(Sheet sh, int c, int r, int n, Object[]... rows) {
        put(sh, r++, c, "Yarıyıl " + n);
        put(sh, r++, c, "Kod");
        double k = 0, e = 0;
        for (Object[] row : rows) {
            for (int i = 0; i < row.length; i++) put(sh, r, c + i, row[i]);
            if (row.length > 5 && row[5] instanceof Number) k += ((Number) row[5]).doubleValue();
            if (row.length > 6 && row[6] instanceof Number) e += ((Number) row[6]).doubleValue();
            r++;
        }
        put(sh, r, c + 1, "Yarıyıl Kredileri");
        put(sh, r, c + 5, k);
        put(sh, r, c + 6, e);
        return r + 1;
    }

    static int grad(Sheet sh, int c, int r, double k, double e) {
        put(sh, r, c + 1, "Toplam Mezuniyet Kredisi");
        put(sh, r, c + 5, k);
        put(sh, r, c + 6, e);
        return r + 1;
    }

    static ByteArrayInputStream bytes(Workbook wb) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new ByteArrayInputStream(out.toByteArray());
    }

    static Object[] row(String code, String name, double k, double e) {
        return new Object[]{code, name, 3, 0, 0, k, e};
    }

    // ---------- testler ----------

    @Test
    public void twoCatalogsSideBySideWithEnglishTranslationSkipped() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet("CMPE");
        put(sh, 1, 0, "MÜFREDAT - 2017");
        put(sh, 1, 8, "MÜFREDAT-2022");
        int end = sem(sh, 0, 2, 1, row("CMPE101", "Giriş", 3, 5), row("MATH 101", "Matematik", 4, 6));
        end = sem(sh, 0, end, 2, row("CMPE102", "İleri", 3, 5));
        end = grad(sh, 0, end, 10, 16);
        // alttaki İngilizce çeviri ikinci kez okunmamalı
        sem(sh, 0, end + 1, 1, row("CMPE101", "Intro", 3, 5), row("MATH 101", "Math", 4, 6));
        int e2 = sem(sh, 8, 2, 1, row("CMPE 101", "Giriş", 3, 5));
        grad(sh, 8, e2, 3, 5);

        ParsedFile f = CoursePlanParser.parse(bytes(wb), "MF-2022 Müfredatlar.xlsx");
        assertEquals("MF", f.faculty);
        assertEquals(2, f.plans.size());
        ParsedPlan a = f.plans.get(0);
        assertEquals("2017", a.label);
        assertEquals(Integer.valueOf(2017), a.year);
        assertEquals(CoursePlanKind.UNDERGRADUATE, a.kind);
        assertEquals("CMPE", a.programCode);
        assertEquals("Bilgisayar Mühendisliği", a.programName);
        assertEquals(3, a.items.size());
        assertEquals(2, a.semesters);
        assertEquals(0, new BigDecimal("10").compareTo(a.totalCredits));
        assertEquals(0, new BigDecimal("16").compareTo(a.totalEcts));
        assertEquals("2022", f.plans.get(1).label);
        assertEquals(1, f.plans.get(1).items.size());
        assertTrue(f.warnings.toString(), f.warnings.isEmpty());
    }

    @Test
    public void englishTranslationEndsAtRepeatedSemesterWithoutTotal() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet("ARCH");
        put(sh, 1, 0, "MEVCUT MÜFREDAT");
        int end = sem(sh, 0, 2, 1, row("ARCH 101", "Stüdyo", 4, 8));
        sem(sh, 0, end, 1, row("ARCH 101", "Studio", 4, 8));

        ParsedFile f = CoursePlanParser.parse(bytes(wb), "MTF-2022 Müfredatlar.xlsx");
        ParsedPlan p = f.plans.get(0);
        assertEquals("Mevcut Müfredat", p.label);
        assertNull(p.year);
        assertEquals(1, p.items.size());
        assertEquals(1, countType(f, CoursePlanParser.T_NO_TOTAL));
    }

    @Test
    public void electivesAndEmptyCodesAreKept() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet("PSCG");
        put(sh, 1, 0, "GÜNCELLENEN  MÜFREDAT");
        int end = sem(sh, 0, 2, 1,
                row("PSCG 101", "Giriş", 3, 5),
                row("CC", "Çekirdek Seçmeli", 3, 5),
                row("FREE", "Serbest Seçmeli", 3, 5),
                new Object[]{null, "Bölüm Seçmeli Dersi", 3, 0, 0, 3, 5});
        grad(sh, 0, end, 12, 20);

        ParsedPlan p = CoursePlanParser.parse(bytes(wb), "EF-2022 Müfredatlar.xlsx").plans.get(0);
        assertEquals("Güncellenen Müfredat", p.label);
        assertEquals("Rehberlik ve Psikolojik Danışmanlık", p.programName);
        assertEquals(4, p.items.size());
        assertEquals(1, p.courseCount());
        assertEquals(3, p.electiveCount());
        assertEquals(CoursePlanItemType.ELECTIVE, p.items.get(3).itemType);
        assertNull(p.items.get(3).code);
        assertEquals("Bölüm Seçmeli Dersi", p.items.get(3).name);
        assertEquals(0, new BigDecimal("12").compareTo(p.totalCredits));
    }

    @Test
    public void alternativeCodesStayOneRow() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet("SOC");
        put(sh, 1, 0, "MEVCUT MÜFREDAT");
        int end = sem(sh, 0, 2, 1,
                row("TUR 101/150", "Türkçe", 2, 2),
                row("HIST 101/HIST 150", "Tarih", 2, 2),
                row("PSY444", "Seminer", 3, 5),
                row("TINT 101-O", "Orta", 3, 5));
        grad(sh, 0, end, 10, 14);

        ParsedFile f = CoursePlanParser.parse(bytes(wb), "FEF-2022 Müfredatlar.xlsx");
        List<ParsedItem> it = f.plans.get(0).items;
        assertEquals(4, it.size());
        assertEquals("TUR 101", it.get(0).code);
        assertEquals("TUR 150", it.get(0).alternativeCode);
        assertEquals("HIST 101", it.get(1).code);
        assertEquals("HIST 150", it.get(1).alternativeCode);
        assertEquals("PSY 444", it.get(2).code);
        assertEquals("TINT 101-O", it.get(3).code);
        assertEquals(0, countType(f, CoursePlanParser.T_TOTAL_MISMATCH));
    }

    @Test
    public void fractionalEctsIsKept() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet("ADS Tezsiz-Tezli Program");
        put(sh, 0, 0, "UYGULAMALI VERİ BİLİMİ TEZSİZ MÜFREDAT");
        int end = sem(sh, 0, 1, 1, row("ADS 501", "Veri", 3, 7.5));
        grad(sh, 0, end, 3, 7.5);

        ParsedPlan p = CoursePlanParser.parse(bytes(wb), "LPE Müfredatlar.xlsx").plans.get(0);
        assertEquals(0, new BigDecimal("7.5").compareTo(p.items.get(0).ects));
        assertEquals(0, new BigDecimal("7.5").compareTo(p.totalEcts));
    }

    @Test
    public void lpeSheetWithThreeBlocks() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet("ARCH PhD-Tezsiz-Tezli Program");
        put(sh, 0, 0, "MİMARİ TASARIM TEZSİZ MÜFREDAT");
        put(sh, 0, 8, "MİMARİ TASARIM TEZLİ MÜFREDAT");
        put(sh, 0, 16, "MİMARLIK DOKTORA PROGRAMI");
        int e1 = sem(sh, 0, 1, 1, row("ARCH 501", "A", 3, 10));
        grad(sh, 0, e1, 3, 10);
        int e2 = sem(sh, 8, 1, 1, row("ARCH 601", "B", 3, 12));
        grad(sh, 8, e2, 3, 12);
        int e3 = sem(sh, 16, 1, 1, row("ARCH 701", "C", 3, 30));
        grad(sh, 16, e3, 3, 30);

        ParsedFile f = CoursePlanParser.parse(bytes(wb), "LPE Müfredatlar.xlsx");
        assertEquals(3, f.plans.size());
        assertEquals(CoursePlanKind.MASTER_NON_THESIS, f.plans.get(0).kind);
        assertEquals(CoursePlanKind.MASTER_THESIS, f.plans.get(1).kind);
        assertEquals(CoursePlanKind.PHD, f.plans.get(2).kind);
        assertEquals("ARCH", f.plans.get(0).programCode);
        assertEquals("MİMARİ TASARIM", f.plans.get(0).programName);
        assertEquals("MİMARLIK", f.plans.get(2).programName);
        assertEquals("Müfredat", f.plans.get(2).label);
        assertNull(f.plans.get(2).year);
        assertEquals("ARCH 701", f.plans.get(2).items.get(0).code);
        assertTrue(f.warnings.toString(), f.warnings.isEmpty());
    }

    @Test
    public void totalMismatchProducesWarning() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet("ME");
        put(sh, 1, 0, "MÜFREDAT-2021");
        int end = sem(sh, 0, 2, 1, row("ME 101", "Statik", 3, 5));
        grad(sh, 0, end, 99, 5);

        ParsedFile f = CoursePlanParser.parse(bytes(wb), "MF-2022 Müfredatlar.xlsx");
        assertEquals(1, countType(f, CoursePlanParser.T_TOTAL_MISMATCH));
        assertEquals(1, f.plans.get(0).items.size());
    }

    @Test
    public void duplicateCodeWarnsAndKeepsBoth() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet("ME");
        put(sh, 1, 0, "MÜFREDAT-2021");
        int end = sem(sh, 0, 2, 1, row("ENGR 490", "Proje", 3, 5), row("ENGR 490", "Proje", 3, 5));
        grad(sh, 0, end, 6, 10);

        ParsedFile f = CoursePlanParser.parse(bytes(wb), "MF-2022 Müfredatlar.xlsx");
        assertEquals(2, f.plans.get(0).items.size());
        assertEquals(1, countType(f, CoursePlanParser.T_DUPLICATE_CODE));
    }

    @Test
    public void formulaCellsUseCachedValueAndDersKoduHeaderIsSkipped() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet("IE");
        put(sh, 1, 0, "MÜFREDAT-2022");
        put(sh, 2, 0, "Yarıyıl 1");
        put(sh, 3, 0, "Ders Kodu");
        put(sh, 4, 0, "IE 101");
        put(sh, 4, 1, "Giriş");
        put(sh, 4, 5, 3);
        Cell f = sh.getRow(4).createCell(6);
        f.setCellFormula("3+4");
        wb.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(f);
        Cell code = sh.createRow(5).createCell(0);
        code.setCellFormula("\"IE\"&\" 102\"");
        wb.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(code);
        put(sh, 5, 1, "Devam");

        ParsedPlan p = CoursePlanParser.parse(bytes(wb), "MF-2022 Müfredatlar.xlsx").plans.get(0);
        assertEquals(2, p.items.size());
        assertEquals(0, new BigDecimal("7").compareTo(p.items.get(0).ects));
        assertEquals("IE 102", p.items.get(1).code);
        assertEquals(CoursePlanItemType.COURSE, p.items.get(1).itemType);
    }

    @Test
    public void unreadableNumberKeepsItemAndWarns() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet("EE");
        put(sh, 1, 0, "MÜFREDAT-2022");
        int end = sem(sh, 0, 2, 1, new Object[]{"EE 101", "Devre", 3, 0, 0, 3, "yedi"});
        ParsedFile f = CoursePlanParser.parse(bytes(wb), "MF-2022 Müfredatlar.xlsx");
        assertEquals(1, f.plans.get(0).items.size());
        assertNull(f.plans.get(0).items.get(0).ects);
        assertEquals(1, countType(f, CoursePlanParser.T_BAD_NUMBER));
    }

    @Test
    public void minorFileProducesItemlessPlans() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet("Sayfa1");
        put(sh, 0, 0, "İİBF");
        put(sh, 0, 1, "MÜ(F)REDAT YILI");
        put(sh, 1, 0, "Ekonomi Yandal");
        put(sh, 1, 1, 2021);
        put(sh, 2, 0, "EF");
        put(sh, 3, 0, "Matematik Yandal (MATH)");
        put(sh, 3, 1, null);
        put(sh, 4, 0, "Psikoloji Yandal");
        put(sh, 4, 1, "2019");

        ParsedFile f = CoursePlanParser.parse(bytes(wb), "Yandal Müfredat-Yıl.xlsx");
        assertEquals("YANDAL", f.faculty);
        assertEquals(3, f.plans.size());
        ParsedPlan a = f.plans.get(0);
        assertEquals(CoursePlanKind.MINOR, a.kind);
        assertEquals("İİBF", a.faculty);
        assertEquals("Ekonomi Yandal", a.programName);
        assertEquals("Ekonomi Yandal", a.programCode);
        assertEquals("2021", a.label);
        assertEquals(Integer.valueOf(2021), a.year);
        assertTrue(a.items.isEmpty());
        assertEquals("EF", f.plans.get(1).faculty);
        assertEquals("Yıl belirtilmemiş", f.plans.get(1).label);
        assertNull(f.plans.get(1).year);
        assertEquals(1, countType(f, CoursePlanParser.T_MINOR_NO_YEAR));
        assertEquals(Integer.valueOf(2019), f.plans.get(2).year);
    }

    @Test
    public void skippedSheetsAndUnknownFaculty() throws Exception {
        Workbook wb = new XSSFWorkbook();
        wb.createSheet("Servis Dersleri");
        wb.createSheet("Programlar");
        Sheet sh = wb.createSheet("CE");
        put(sh, 1, 0, "MÜFREDAT-2022");
        sem(sh, 0, 2, 1, row("CE 101", "Statik", 3, 5));

        ParsedFile f = CoursePlanParser.parse(bytes(wb), "bilinmeyen dosya.xlsx");
        assertEquals("DIGER", f.faculty);
        assertEquals(2, f.skippedSheets.size());
        assertEquals(1, countType(f, CoursePlanParser.T_UNKNOWN_FACULTY));
    }

    @Test
    public void nonCurriculumWorkbookIsRejected() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet("Liste");
        put(sh, 0, 0, "Öğrenci No");
        final ByteArrayInputStream in = bytes(wb);
        org.junit.jupiter.api.Assertions.assertThrows(CoursePlanParser.InvalidFileException.class,
                () -> CoursePlanParser.parse(in, "ogrenciler.xlsx"));
    }

    @Test
    public void garbageBytesAreRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(CoursePlanParser.InvalidFileException.class,
                () -> CoursePlanParser.parse(new ByteArrayInputStream("merhaba".getBytes(StandardCharsets.UTF_8)), "x.xlsx"));
    }

    static int countType(ParsedFile f, String type) {
        int n = 0;
        for (CoursePlanParser.ParseWarning w : f.warnings) if (type.equals(w.getType())) n++;
        return n;
    }

    // ---------- gerçek dosyalarla doğrulama (yalnız -Dmufredat.klasor=... verilirse) ----------

    @Test
    public void realFilesMatchExpectedList() throws Exception {
        String dir = System.getProperty("mufredat.klasor", System.getenv("MUFREDAT_KLASOR"));
        String exp = System.getProperty("mufredat.beklenen", System.getenv("MUFREDAT_BEKLENEN"));
        assumeTrue(dir != null && !dir.isEmpty(), "mufredat.klasor verilmedi");
        assumeTrue(exp != null && !exp.isEmpty(), "mufredat.beklenen verilmedi");

        List<String> actual = new ArrayList<>();
        int minors = 0;
        Map<String, Integer> warnCounts = new TreeMap<>();
        List<String> warnTexts = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(Paths.get(dir), "*.xlsx")) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                ParsedFile f;
                try (java.io.InputStream in = Files.newInputStream(p)) {
                    f = CoursePlanParser.parse(in, name);
                }
                for (CoursePlanParser.ParseWarning w : f.warnings) {
                    Integer n = warnCounts.get(w.getType());
                    warnCounts.put(w.getType(), n == null ? 1 : n + 1);
                    warnTexts.add(name + " | " + w.getType() + " | " + w.getMessage());
                }
                for (ParsedPlan pl : f.plans) {
                    if (pl.kind == CoursePlanKind.MINOR) {
                        minors++;
                        continue;
                    }
                    actual.add(f.fileName.split("[\\s]+")[0] + "|" + pl.sheetName + "|" + pl.rawLabel + "|"
                            + pl.semesters + "|" + pl.items.size() + "|" + pl.courseCount() + "|"
                            + plain(pl.totalCredits) + "|" + plain(pl.totalEcts));
                }
            }
        }
        List<String> expected = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(exp), StandardCharsets.UTF_8)) {
            if (!line.trim().isEmpty()) expected.add(line.trim());
        }
        Collections.sort(actual);
        Collections.sort(expected);
        System.out.println("GERCEK-DOSYA plan=" + actual.size() + " yandal=" + minors + " uyari=" + warnCounts);
        for (String w : warnTexts) System.out.println("UYARI " + w);
        List<String> missing = new ArrayList<>(expected);
        missing.removeAll(actual);
        List<String> extra = new ArrayList<>(actual);
        extra.removeAll(expected);
        for (String s : missing) System.out.println("EKSIK " + s);
        for (String s : extra) System.out.println("FAZLA " + s);
        assertEquals("beklenen listeden eksik/fazla", 0, missing.size() + extra.size());
        assertEquals(90, actual.size());
        assertEquals(21, minors);
        assertNull("toplam uyusmazligi", warnCounts.get(CoursePlanParser.T_TOTAL_MISMATCH));
        assertEquals(Integer.valueOf(2), warnCounts.get(CoursePlanParser.T_DUPLICATE_CODE));
        assertEquals(Integer.valueOf(1), warnCounts.get(CoursePlanParser.T_MINOR_NO_YEAR));
    }

    private static String plain(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0).toPlainString() : s.toPlainString();
    }
}
