package com.teduscheduler.service;

import com.teduscheduler.model.CoursePlanItemType;
import com.teduscheduler.model.CoursePlanKind;
import org.apache.poi.ss.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TEDÜ müfredat Excel'lerini çözer (Spring'siz). Her "Yarıyıl 1" sütunu bir plan bloğudur;
 * yandal dosyası kalemsiz planlar üretir. Veritabanına dokunmaz.
 */
public final class CoursePlanParser {

    /** Dosyanın kendisi okunamıyor ya da müfredat içermiyor (HTTP 400). */
    public static class InvalidFileException extends RuntimeException {
        public InvalidFileException(String message) {
            super(message);
        }
    }

    public static final String T_TOTAL_MISMATCH = "TOTAL_MISMATCH";
    public static final String T_NO_TOTAL = "NO_TOTAL";
    public static final String T_DUPLICATE_CODE = "DUPLICATE_CODE";
    public static final String T_DUPLICATE_PLAN = "DUPLICATE_PLAN";
    public static final String T_BAD_NUMBER = "BAD_NUMBER";
    public static final String T_UNKNOWN_FACULTY = "UNKNOWN_FACULTY";
    public static final String T_MINOR_NO_YEAR = "MINOR_NO_YEAR";
    public static final String T_BAD_ROW = "BAD_ROW";
    public static final String T_NO_LABEL = "NO_LABEL";
    public static final String T_NO_BLOCK = "NO_BLOCK";

    public static class ParseWarning {
        private final String sheet;
        private final String type;
        private final String message;

        public ParseWarning(String sheet, String type, String message) {
            this.sheet = sheet;
            this.type = type;
            this.message = message;
        }

        public String getSheet() { return sheet; }
        public String getType() { return type; }
        public String getMessage() { return message; }
    }

    public static class ParsedItem {
        public int semester;
        public int orderNo;
        public CoursePlanItemType itemType;
        public String code;
        public String alternativeCode;
        public String name;
        public Integer theory;
        public Integer practice;
        public Integer lab;
        public BigDecimal credits;
        public BigDecimal ects;
    }

    public static class ParsedPlan {
        public String sheetName;
        /** Hücredeki özgün başlık (karşılaştırma ve hata ayıklama için; saklanmaz). */
        public String rawLabel;
        public String faculty;
        public String programCode;
        public String programName;
        public CoursePlanKind kind;
        public String label;
        public Integer year;
        public int semesters;
        public final List<ParsedItem> items = new ArrayList<>();
        public BigDecimal totalCredits = BigDecimal.ZERO;
        public BigDecimal totalEcts = BigDecimal.ZERO;

        public int courseCount() {
            int n = 0;
            for (ParsedItem i : items) if (i.itemType == CoursePlanItemType.COURSE) n++;
            return n;
        }

        public int electiveCount() {
            return items.size() - courseCount();
        }

        /** Aynı plan sayılma anahtarı; yandalda yıl değişse de aynı plan kalır. */
        public String identityKey() {
            if (kind == CoursePlanKind.MINOR) {
                return faculty + "\u0000" + programCode + "\u0000" + kind;
            }
            return faculty + "\u0000" + programCode + "\u0000" + kind + "\u0000" + label;
        }
    }

    public static class ParsedFile {
        public String fileName;
        public String faculty;
        public final List<ParsedPlan> plans = new ArrayList<>();
        public final List<String> skippedSheets = new ArrayList<>();
        public final List<ParseWarning> warnings = new ArrayList<>();
    }

    private static final Pattern WS = Pattern.compile("[\\s\\u00A0]+");
    private static final Pattern SEM = Pattern.compile("^(yariyil|semester)\\s*(\\d{1,3})$");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");
    private static final Pattern MINOR_HEADER = Pattern.compile("mu\\(?f?\\)?redat\\s+yili");
    private static final Pattern COMPACT_CODE = Pattern.compile("^(\\p{L}+)(\\d.*)$");
    private static final Pattern ALT_CODE =
            Pattern.compile("^(\\p{L}+)\\s*(\\d+\\p{L}?)\\s*/\\s*(?:(\\p{L}+)\\s*)?(\\d+\\p{L}?)$");
    private static final Pattern LABEL_TAIL =
            Pattern.compile("(\\s+(tezsiz|tezli|doktora))?(\\s+(mufredat|programi))?\\s*$");

    private static final Set<String> FACULTIES = new HashSet<>(Arrays.asList("EF", "FEF", "MF", "MTF", "LPE", "YANDAL"));

    private static final Map<String, String> PROGRAM_NAMES = new HashMap<>();

    static {
        String[][] p = {
                {"CMPE", "Bilgisayar Mühendisliği"}, {"SENG", "Yazılım Mühendisliği"},
                {"CE", "İnşaat Mühendisliği"}, {"ME", "Makine Mühendisliği"},
                {"EE", "Elektrik-Elektronik Mühendisliği"}, {"IE", "Endüstri Mühendisliği"},
                {"MATH", "Matematik"}, {"ELL", "İngiliz Dili ve Edebiyatı"}, {"PSY", "Psikoloji"},
                {"SOC", "Sosyoloji"}, {"ARCH", "Mimarlık"}, {"CITY", "Şehir ve Bölge Planlama"},
                {"ID", "Endüstriyel Tasarım"}, {"TINT", "İç Mimarlık ve Çevre Tasarımı"},
                {"VCODE", "Görsel İletişim Tasarımı"},
                {"PSCG", "Rehberlik ve Psikolojik Danışmanlık"}, {"PRED", "Sınıf Öğretmenliği"},
                {"PECE", "Okul Öncesi Öğretmenliği"}, {"ELT", "İngilizce Öğretmenliği"},
                {"EMT", "İlköğretim Matematik Öğretmenliği"}
        };
        for (String[] e : p) PROGRAM_NAMES.put(e[0], e[1]);
    }

    private final String fileName;
    private final ParsedFile out = new ParsedFile();

    private CoursePlanParser(String fileName) {
        this.fileName = fileName;
        out.fileName = fileName;
    }

    /** Dosyayı çözer; okunamazsa ya da hiç plan yoksa InvalidFileException. */
    public static ParsedFile parse(InputStream in, String fileName) {
        String name = fileName == null ? "" : Normalizer.normalize(fileName, Normalizer.Form.NFC);
        CoursePlanParser parser = new CoursePlanParser(name);
        Workbook workbook;
        try {
            workbook = WorkbookFactory.create(in);
        } catch (Exception e) {
            throw new InvalidFileException(name + ": Excel dosyası okunamadı.");
        }
        try {
            parser.run(workbook);
        } finally {
            try {
                workbook.close();
            } catch (IOException ignored) {
                // kapatma hatası sonucu etkilemez
            }
        }
        if (parser.out.plans.isEmpty()) {
            throw new InvalidFileException(name + ": Dosyada müfredat bulunamadı.");
        }
        return parser.out;
    }

    // ---------- genel yardımcılar ----------

    /** NFC + her türlü boşluk dizisi (NBSP dahil) tek boşluk + kırpma. */
    static String norm(String s) {
        if (s == null) return "";
        return WS.matcher(Normalizer.normalize(s, Normalizer.Form.NFC)).replaceAll(" ").trim();
    }

    /** Türkçe harfleri ASCII'ye indirip küçültür (İ/ı tuzağından kaçınmak için); uzunluk değişmez. */
    static String fold(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case 'İ': case 'I': case 'ı': case 'i': sb.append('i'); break;
                case 'Ü': case 'ü': sb.append('u'); break;
                case 'Ö': case 'ö': sb.append('o'); break;
                case 'Ş': case 'ş': sb.append('s'); break;
                case 'Ç': case 'ç': sb.append('c'); break;
                case 'Ğ': case 'ğ': sb.append('g'); break;
                default: sb.append(Character.toLowerCase(ch));
            }
        }
        return sb.toString();
    }

    private static String cellText(Cell cell) {
        if (cell == null) return "";
        CellType t = cell.getCellTypeEnum();
        if (t == CellType.FORMULA) t = cell.getCachedFormulaResultTypeEnum();
        switch (t) {
            case STRING:
                return norm(cell.getRichStringCellValue().getString());
            case NUMERIC:
                double d = cell.getNumericCellValue();
                if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
                return BigDecimal.valueOf(d).toPlainString();
            default:
                return "";
        }
    }

    private static String text(Row row, int col) {
        return row == null || col < 0 ? "" : cellText(row.getCell(col));
    }

    private static String plain(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0).toPlainString() : s.toPlainString();
    }

    /** Boş hücre null; okunamayan metinde bad[0] = true ve null. */
    private static BigDecimal number(Row row, int col, boolean[] bad) {
        String t = text(row, col);
        if (t.isEmpty() || t.equals("-") || t.equals("–") || t.equals("—")) return null;
        try {
            return new BigDecimal(t.replace(',', '.'));
        } catch (NumberFormatException e) {
            bad[0] = true;
            return null;
        }
    }

    private static Integer integer(Row row, int col, boolean[] bad) {
        BigDecimal v = number(row, col, bad);
        if (v == null) return null;
        try {
            return v.intValueExact();
        } catch (ArithmeticException e) {
            bad[0] = true;
            return null;
        }
    }

    private void warn(String sheet, String type, String message) {
        out.warnings.add(new ParseWarning(sheet, type, message));
    }

    // ---------- dosya ----------

    private void run(Workbook wb) {
        String faculty = facultyOf(fileName);
        if (faculty == null) {
            faculty = "DIGER";
            warn("", T_UNKNOWN_FACULTY, "Dosya adından fakülte anlaşılamadı; fakülte \"DIGER\" olarak kaydedilecek.");
        }
        out.faculty = faculty;
        boolean lpe = "LPE".equals(faculty);

        Set<String> keys = new HashSet<>();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            Sheet sh = wb.getSheetAt(i);
            String sheetName = norm(sh.getSheetName());
            String f = fold(sheetName);
            if (f.equals("servis dersleri") || f.equals("programlar")) {
                out.skippedSheets.add(sheetName);
                continue;
            }
            List<ParsedPlan> plans = new ArrayList<>();
            int minorHeader = findMinorHeader(sh);
            if (minorHeader >= 0) {
                parseMinorSheet(sh, sheetName, minorHeader, plans);
            } else {
                parseCurriculumSheet(sh, sheetName, lpe, faculty, plans);
            }
            for (ParsedPlan p : plans) {
                if (keys.add(p.identityKey())) {
                    out.plans.add(p);
                } else {
                    warn(sheetName, T_DUPLICATE_PLAN, "\"" + p.programName + " / " + p.label
                            + "\" planı dosyada daha önce geçtiği için atlandı.");
                }
            }
        }
    }

    /** Dosya adından fakülte: ilk kelime / tire öncesi; tanınmazsa null. */
    static String facultyOf(String fileName) {
        String n = norm(fileName);
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) n = n.substring(slash + 1);
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        String[] parts = n.split("[\\s\\-_.]+");
        if (parts.length == 0) return null;
        String first = parts[0].toUpperCase(Locale.ROOT);
        return FACULTIES.contains(first) ? first : null;
    }

    // ---------- yandal ----------

    private int findMinorHeader(Sheet sh) {
        int last = Math.min(sh.getLastRowNum(), 2);
        for (int r = Math.max(sh.getFirstRowNum(), 0); r <= last; r++) {
            if (MINOR_HEADER.matcher(fold(text(sh.getRow(r), 1))).find()) return r;
        }
        return -1;
    }

    private void parseMinorSheet(Sheet sh, String sheetName, int headerRow, List<ParsedPlan> plans) {
        String heading = null;
        for (int r = headerRow; r <= sh.getLastRowNum(); r++) {
            Row row = sh.getRow(r);
            String a = text(row, 0);
            if (r == headerRow) {
                if (!a.isEmpty()) heading = a;
                continue;
            }
            if (a.isEmpty()) continue;
            if (!fold(a).contains("yandal")) {
                heading = a;
                continue;
            }
            String yearText = text(row, 1);
            ParsedPlan p = new ParsedPlan();
            p.sheetName = sheetName;
            p.rawLabel = yearText;
            p.faculty = heading == null ? "DIGER" : heading;
            p.programCode = a;
            p.programName = a;
            p.kind = CoursePlanKind.MINOR;
            Matcher m = YEAR.matcher(yearText);
            if (yearText.isEmpty()) {
                p.label = "Yıl belirtilmemiş";
                warn(sheetName, T_MINOR_NO_YEAR, "\"" + a + "\" yandalının müfredat yılı boş.");
            } else {
                p.label = yearText;
                if (m.find()) p.year = Integer.valueOf(m.group(1));
            }
            plans.add(p);
        }
    }

    // ---------- lisans / lisansüstü ----------

    private void parseCurriculumSheet(Sheet sh, String sheetName, boolean lpe, String faculty, List<ParsedPlan> plans) {
        int firstRow = Math.max(sh.getFirstRowNum(), 0);
        int scanEnd = Math.min(sh.getLastRowNum(), 11);
        Set<Integer> usedCols = new HashSet<>();
        List<int[]> blocks = new ArrayList<>(); // {sütun, "Yarıyıl 1" satırı}
        for (int r = firstRow; r <= scanEnd; r++) {
            Row row = sh.getRow(r);
            if (row == null || row.getFirstCellNum() < 0) continue;
            for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
                Matcher m = SEM.matcher(fold(text(row, c)));
                if (m.matches() && Integer.parseInt(m.group(2)) == 1 && usedCols.add(c)) {
                    blocks.add(new int[]{c, r});
                }
            }
        }
        if (blocks.isEmpty()) {
            warn(sheetName, T_NO_BLOCK, "Sayfada \"Yarıyıl 1\" ile başlayan müfredat bloğu bulunamadı.");
            return;
        }
        for (int[] b : blocks) {
            ParsedPlan p = parseBlock(sh, sheetName, b[0], b[1], lpe, faculty);
            plans.add(p);
        }
    }

    private ParsedPlan parseBlock(Sheet sh, String sheetName, int c, int semRow, boolean lpe, String faculty) {
        // etiket: aynı sütunda yukarıdaki en yakın MÜFREDAT/DOKTORA hücresi
        String raw = "";
        for (int r = semRow - 1; r >= 0; r--) {
            String t = text(sh.getRow(r), c);
            String f = fold(t);
            if ((f.contains("mufredat") || f.contains("doktora")) && !f.contains("degisikligi")) {
                raw = t;
                break;
            }
        }
        String f = fold(raw);

        ParsedPlan p = new ParsedPlan();
        p.sheetName = sheetName;
        p.rawLabel = raw;
        p.faculty = faculty;
        if (lpe) {
            p.programCode = sheetName.split("\\s+")[0];
            p.label = "Müfredat";
            if (f.contains("tezsiz")) p.kind = CoursePlanKind.MASTER_NON_THESIS;
            else if (f.contains("tezli")) p.kind = CoursePlanKind.MASTER_THESIS;
            else if (f.contains("doktora")) p.kind = CoursePlanKind.PHD;
            else {
                p.kind = CoursePlanKind.MASTER_THESIS;
                warn(sheetName, T_NO_LABEL, "Sütun " + (c + 1) + ": program türü (tezli/tezsiz/doktora) başlıktan anlaşılamadı.");
            }
            if (raw.isEmpty()) {
                p.programName = p.programCode;
            } else {
                Matcher m = LABEL_TAIL.matcher(f);
                int cut = m.find() ? m.start() : raw.length();
                String nm = raw.substring(0, cut).trim();
                p.programName = nm.isEmpty() ? raw : nm;
            }
        } else {
            p.kind = CoursePlanKind.UNDERGRADUATE;
            p.programCode = sheetName;
            String upper = sheetName.toUpperCase(Locale.ROOT);
            p.programName = PROGRAM_NAMES.containsKey(upper) ? PROGRAM_NAMES.get(upper) : sheetName;
            if (f.contains("mevcut")) {
                p.label = "Mevcut Müfredat";
            } else if (f.contains("guncellen")) {
                p.label = "Güncellenen Müfredat";
            } else {
                Matcher y = YEAR.matcher(raw);
                if (y.find()) {
                    p.year = Integer.valueOf(y.group(1));
                    p.label = y.group(1);
                } else {
                    p.label = "Müfredat";
                }
            }
            if (raw.isEmpty()) {
                warn(sheetName, T_NO_LABEL, "Sütun " + (c + 1) + ": müfredat başlığı bulunamadı; etiket \"Müfredat\" yapıldı.");
            }
        }
        if (p.programName == null) p.programName = p.programCode;

        readBlock(sh, sheetName, c, semRow, p);
        return p;
    }

    private void readBlock(Sheet sh, String sheetName, int c, int semRow, ParsedPlan p) {
        String where = sheetName + " / " + p.label + (lpeSuffix(p));
        Set<Integer> seen = new LinkedHashSet<>();
        Map<Integer, BigDecimal[]> declared = new HashMap<>();
        Map<Integer, BigDecimal[]> sums = new HashMap<>();
        Set<String> codes = new HashSet<>();
        BigDecimal[] grad = null;
        int sem = 0;
        int order = 0;
        boolean[] bad = new boolean[1];

        for (int r = semRow; r <= sh.getLastRowNum(); r++) {
            Row row = sh.getRow(r);
            String code = text(row, c);
            String name = text(row, c + 1);
            String fc = fold(code);
            String fn = fold(name);

            Matcher m = SEM.matcher(fc);
            if (m.matches()) {
                int n = Integer.parseInt(m.group(2));
                if (!seen.add(n)) break; // İngilizce çeviri bloğu
                sem = n;
                continue;
            }
            boolean toplam = fc.contains("toplam") || fn.contains("toplam");
            if (toplam && (fc.contains("mezuniyet") || fn.contains("mezuniyet")
                    || fc.contains("genel") || fn.contains("genel"))) {
                bad[0] = false;
                grad = new BigDecimal[]{number(row, c + 5, bad), number(row, c + 6, bad)};
                break;
            }
            if (fc.contains("yariyil kredi") || fn.contains("yariyil kredi")
                    || fc.contains("semester credit") || fn.contains("semester credit")) {
                bad[0] = false;
                declared.put(sem, new BigDecimal[]{number(row, c + 5, bad), number(row, c + 6, bad)});
                continue;
            }
            if (fc.equals("kod") || fc.equals("code") || fc.equals("ders kodu")) continue;
            if (code.isEmpty() && name.isEmpty()) {
                boolean other = false;
                for (int k = 2; k <= 6; k++) if (!text(row, c + k).isEmpty()) other = true;
                if (other) {
                    warn(sheetName, T_BAD_ROW, where + ": " + (r + 1) + ". satırda kod ve ad boş olduğu için satır atlandı.");
                }
                continue;
            }

            ParsedItem it = new ParsedItem();
            it.semester = sem;
            it.orderNo = ++order;
            it.name = name.isEmpty() ? code : name;
            boolean hasDigit = false;
            for (int i = 0; i < code.length(); i++) if (Character.isDigit(code.charAt(i))) hasDigit = true;
            it.itemType = hasDigit ? CoursePlanItemType.COURSE : CoursePlanItemType.ELECTIVE;
            it.code = code.isEmpty() ? null : code;
            if (hasDigit) normalizeCode(it);
            bad[0] = false;
            it.theory = integer(row, c + 2, bad);
            it.practice = integer(row, c + 3, bad);
            it.lab = integer(row, c + 4, bad);
            it.credits = number(row, c + 5, bad);
            it.ects = number(row, c + 6, bad);
            if (bad[0]) {
                warn(sheetName, T_BAD_NUMBER, where + ": " + (r + 1) + ". satırdaki \"" + code
                        + "\" kaleminde okunamayan sayı var; ilgili alan boş bırakıldı.");
            }
            if (it.itemType == CoursePlanItemType.COURSE && !codes.add(it.code)) {
                warn(sheetName, T_DUPLICATE_CODE, where + ": \"" + it.code + "\" kodu planda birden fazla geçiyor.");
            }
            BigDecimal[] s = sums.get(sem);
            if (s == null) {
                s = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
                sums.put(sem, s);
            }
            if (it.credits != null) {
                s[0] = s[0].add(it.credits);
                p.totalCredits = p.totalCredits.add(it.credits);
            }
            if (it.ects != null) {
                s[1] = s[1].add(it.ects);
                p.totalEcts = p.totalEcts.add(it.ects);
            }
            p.items.add(it);
        }
        p.semesters = seen.size();

        for (Map.Entry<Integer, BigDecimal[]> e : declared.entrySet()) {
            BigDecimal[] s = sums.get(e.getKey());
            if (s == null) s = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
            compare(sheetName, where, "Yarıyıl " + e.getKey(), e.getValue(), s);
        }
        if (grad == null) {
            warn(sheetName, T_NO_TOTAL, where + ": mezuniyet toplamı satırı bulunamadı.");
        } else {
            compare(sheetName, where, "Mezuniyet toplamı", grad, new BigDecimal[]{p.totalCredits, p.totalEcts});
        }
    }

    private static String lpeSuffix(ParsedPlan p) {
        return p.kind == CoursePlanKind.UNDERGRADUATE || p.kind == CoursePlanKind.MINOR ? "" : " (" + p.programName + ")";
    }

    private void compare(String sheetName, String where, String what, BigDecimal[] declared, BigDecimal[] actual) {
        String[] names = {"kredi (K)", "AKTS"};
        for (int i = 0; i < 2; i++) {
            if (declared[i] != null && declared[i].compareTo(actual[i]) != 0) {
                warn(sheetName, T_TOTAL_MISMATCH, where + ": " + what + " " + names[i] + " dosyada " + plain(declared[i])
                        + ", kalemlerin toplamı " + plain(actual[i]) + ".");
            }
        }
    }

    /** "PSY444" -> "PSY 444"; "TUR 101/150" -> kod "TUR 101", alternatif "TUR 150". */
    private static void normalizeCode(ParsedItem it) {
        String code = it.code;
        Matcher alt = ALT_CODE.matcher(code);
        if (alt.matches()) {
            String prefix2 = alt.group(3) != null ? alt.group(3) : alt.group(1);
            it.code = alt.group(1) + " " + alt.group(2);
            it.alternativeCode = prefix2 + " " + alt.group(4);
            return;
        }
        if (code.indexOf(' ') < 0) {
            Matcher m = COMPACT_CODE.matcher(code);
            if (m.matches()) it.code = m.group(1) + " " + m.group(2);
        }
    }
}
