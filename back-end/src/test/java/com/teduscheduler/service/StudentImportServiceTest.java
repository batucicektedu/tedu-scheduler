package com.teduscheduler.service;

import com.teduscheduler.dto.ImportResult;
import com.teduscheduler.model.Curriculum;
import com.teduscheduler.model.ProgramType;
import com.teduscheduler.model.Student;
import com.teduscheduler.model.StudentProgram;
import com.teduscheduler.repository.CurriculumRepository;
import com.teduscheduler.repository.StudentProgramRepository;
import com.teduscheduler.repository.StudentRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StudentImportServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CurriculumRepository curriculumRepository;

    @Mock
    private StudentProgramRepository studentProgramRepository;

    @InjectMocks
    private StudentImportService service;

    private static byte[] workbook(String[] headers, String[][] rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");
            Row h = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null) row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            // numeric year + date cell on the first data row
            Row first = sheet.getRow(1);
            first.createCell(headers.length - 1).setCellValue(java.sql.Date.valueOf("2024-09-02"));
            CellStyle style = wb.createCellStyle();
            style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("dd.mm.yyyy"));
            first.getCell(headers.length - 1).setCellStyle(style);
            first.createCell(3).setCellValue(2024);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static final String[] HEADERS = {"Öğrenci No", "Ad", "Soyad", "Giriş Yılı", "Program", "Katalog",
            "Katalog (Tanım)", "Versiyon", "Kayıt Tarihi"};

    @Test
    public void importCreatesUpdatesAndSkips() throws Exception {
        Student existing = new Student();
        existing.setStudentNo("222");
        existing.setFirstName("Old");
        when(studentRepository.findAll()).thenReturn(new ArrayList<>(Collections.singletonList(existing)));
        when(curriculumRepository.findAll()).thenReturn(new ArrayList<>());
        when(curriculumRepository.save(any(Curriculum.class))).thenAnswer(i -> i.getArgument(0));

        byte[] data = workbook(HEADERS, new String[][]{
                {" 111 ", "Ali", "Veli", "2024", "Mimarlık Doktora Programı", "0040", "Doktora müfredatı", "2023", null},
                {"", "Blank", "No", "2024", "X", "1", "n", "2023", null},
                {"222", "New", "Name", "2023", "Mimarlık Doktora Programı", "0040", "Doktora müfredatı", "2023", null},
                {"333", "No", "Catalog", "abc", "Mimarlık Doktora Programı", "", "", "", null},
                {"444", "No", "Program", "2024", "", "0040", "x", "2023", null},
        });

        ImportResult res = service.importStudents(new ByteArrayInputStream(data));

        assertEquals(4, res.getTotalRows());
        assertEquals(2, res.getCreated());
        assertEquals(1, res.getWithoutCurriculum());
        assertEquals(1, res.getUpdated());
        assertEquals(2, res.getSkipped());
        assertEquals(1, res.getCurriculaCreated());
        assertEquals(1, res.getErrors().size());
        assertEquals(6, res.getErrors().get(0).getRow());
        assertEquals("New", existing.getFirstName());
        assertEquals(Integer.valueOf(2023), existing.getEntryYear());

        verify(studentRepository).saveAll(argThat(it -> {
            List<Student> l = new ArrayList<>();
            it.forEach(l::add);
            return l.size() == 3 && l.stream().anyMatch(s -> "111".equals(s.getStudentNo())
                    && s.getEntryYear() == 2024 && LocalDate.of(2024, 9, 2).equals(s.getRegistrationDate()));
        }));
    }

    @Test
    public void missingRequiredHeaderFails() throws Exception {
        byte[] data = workbook(new String[]{"Öğrenci No", "Ad", "Program", "Katalog", "Versiyon"}, new String[][]{{"1", "a", "p", "c", "v"}});
        try {
            service.importStudents(new ByteArrayInputStream(data));
            fail();
        } catch (StudentImportService.InvalidImportFileException e) {
            assertTrue(e.getMessage().contains("Giriş Yılı"));
        }
    }

    // ---------------------------------------------------------------- multi-programme tests

    private static final String MAIN_PROG = "Örnek Mühendisliği";
    private static final String MINOR_PROG = "Örnek Sanat Tarihi Yan Dal";
    private static final String OTHER_PROG = "Örnek Felsefe";

    private List<Student> savedStudents;
    private List<StudentProgram> savedPrograms;
    private List<StudentProgram> deletedPrograms;

    private static String[] row(String no, String first, String last, String program, String code, String date) {
        return new String[]{no, first, last, "2022", program, code, "Müfredat " + code, "2021", date};
    }

    private static byte[] plain(String[]... rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");
            Row h = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) h.createCell(i).setCellValue(HEADERS[i]);
            for (int r = 0; r < rows.length; r++) {
                Row rw = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null) rw.createCell(c).setCellValue(rows[r][c]);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private ImportResult run(byte[] data, List<Student> students, List<StudentProgram> programs) throws Exception {
        savedStudents = new ArrayList<>();
        savedPrograms = new ArrayList<>();
        deletedPrograms = new ArrayList<>();
        lenient().when(studentRepository.findAll()).thenReturn(new ArrayList<>(students));
        lenient().when(studentProgramRepository.findAllWithStudent()).thenReturn(new ArrayList<>(programs));
        lenient().when(curriculumRepository.findAll()).thenReturn(new ArrayList<>());
        lenient().when(curriculumRepository.save(any(Curriculum.class))).thenAnswer(i -> i.getArgument(0));
        lenient().doAnswer(i -> {
            ((Iterable<Student>) i.getArgument(0)).forEach(savedStudents::add);
            return null;
        }).when(studentRepository).saveAll(any());
        lenient().doAnswer(i -> {
            ((Iterable<StudentProgram>) i.getArgument(0)).forEach(savedPrograms::add);
            return null;
        }).when(studentProgramRepository).saveAll(any());
        lenient().doAnswer(i -> {
            ((Iterable<StudentProgram>) i.getArgument(0)).forEach(deletedPrograms::add);
            return null;
        }).when(studentProgramRepository).deleteAll(any());
        return service.importStudents(new ByteArrayInputStream(data));
    }

    private StudentProgram saved(String program) {
        for (StudentProgram sp : savedPrograms) {
            if (program.equals(sp.getProgram())) return sp;
        }
        throw new AssertionError("programme not saved: " + program);
    }

    @Test
    public void mainAndMinorAreKeptAndStudentMirrorsMainEvenWhenMinorComesFirst() throws Exception {
        ImportResult res = run(plain(
                row("500", "Ada", "Minor", MINOR_PROG, "0050", "01.09.2022"),
                row("500", "Ada", "Main", MAIN_PROG, "0010", "01.09.2021")), new ArrayList<>(), new ArrayList<>());

        assertEquals(2, res.getTotalRows());
        assertEquals(1, res.getCreated());
        assertEquals(2, res.getProgramsCreated());
        assertEquals(1, res.getMultiProgramStudents());
        assertEquals(0, res.getErrors().size());
        assertEquals(2, savedPrograms.size());
        assertEquals(ProgramType.MAIN, saved(MAIN_PROG).getProgramType());
        assertTrue(saved(MAIN_PROG).isPrimaryProgram());
        assertEquals(ProgramType.MINOR, saved(MINOR_PROG).getProgramType());
        assertFalse(saved(MINOR_PROG).isPrimaryProgram());
        Student s = savedStudents.get(0);
        assertEquals(1, savedStudents.size());
        assertEquals(MAIN_PROG, s.getProgram());
        assertEquals("Main", s.getLastName());
        assertEquals(LocalDate.of(2021, 9, 1), s.getRegistrationDate());
        assertSame(s, saved(MAIN_PROG).getStudent());
    }

    @Test
    public void minorIsDetectedByNameEvenWithCatalog0020() throws Exception {
        run(plain(
                row("501", "Bora", "Test", MINOR_PROG, "0020", "01.09.2020"),
                row("501", "Bora", "Test", MAIN_PROG, "0020", "01.09.2021")), new ArrayList<>(), new ArrayList<>());

        assertEquals(ProgramType.MINOR, saved(MINOR_PROG).getProgramType());
        assertEquals(ProgramType.MAIN, saved(MAIN_PROG).getProgramType());
        assertTrue(saved(MAIN_PROG).isPrimaryProgram());
        assertEquals(MAIN_PROG, savedStudents.get(0).getProgram());
    }

    @Test
    public void doubleMajorWhenBothCatalogsAre0020() throws Exception {
        run(plain(
                row("502", "Cem", "Test", MAIN_PROG, "0020", "01.09.2020"),
                row("502", "Cem", "Test", OTHER_PROG, "0020", "01.09.2021")), new ArrayList<>(), new ArrayList<>());

        assertEquals(ProgramType.MAIN, saved(MAIN_PROG).getProgramType());
        assertEquals(ProgramType.DOUBLE_MAJOR, saved(OTHER_PROG).getProgramType());
        assertFalse(saved(OTHER_PROG).isPrimaryProgram());
    }

    @Test
    public void differentCatalogCodesGiveOther() throws Exception {
        run(plain(
                row("503", "Deniz", "Test", MAIN_PROG, "0020", "01.09.2020"),
                row("503", "Deniz", "Test", OTHER_PROG, "0030", "01.09.2021")), new ArrayList<>(), new ArrayList<>());

        assertEquals(ProgramType.MAIN, saved(MAIN_PROG).getProgramType());
        assertEquals(ProgramType.OTHER, saved(OTHER_PROG).getProgramType());
    }

    @Test
    public void allMinorStudentGetsEarliestAsPrimary() throws Exception {
        run(plain(
                row("504", "Ece", "Test", MINOR_PROG, "0050", "01.09.2022"),
                row("504", "Ece", "Test", "Örnek Müzik Yandal", "0020", "01.09.2021")), new ArrayList<>(), new ArrayList<>());

        assertEquals(ProgramType.MINOR, saved(MINOR_PROG).getProgramType());
        assertEquals(ProgramType.MINOR, saved("Örnek Müzik Yandal").getProgramType());
        assertTrue(saved("Örnek Müzik Yandal").isPrimaryProgram());
        assertFalse(saved(MINOR_PROG).isPrimaryProgram());
        assertEquals("Örnek Müzik Yandal", savedStudents.get(0).getProgram());
    }

    @Test
    public void reimportIsIdempotent() throws Exception {
        byte[] data = plain(
                row("505", "Fatma", "Test", MAIN_PROG, "0010", "01.09.2021"),
                row("505", "Fatma", "Test", MINOR_PROG, "0050", "01.09.2022"));
        run(data, new ArrayList<>(), new ArrayList<>());
        Student student = savedStudents.get(0);
        List<StudentProgram> first = new ArrayList<>(savedPrograms);

        ImportResult res = run(data, Collections.singletonList(student), first);

        assertEquals(0, res.getCreated());
        assertEquals(1, res.getUpdated());
        assertEquals(0, res.getProgramsCreated());
        assertEquals(2, res.getProgramsUpdated());
        assertEquals(0, res.getProgramsRemoved());
        assertTrue(deletedPrograms.isEmpty());
        assertEquals(2, savedPrograms.size());
        assertSame(first.get(0), savedPrograms.get(0));
    }

    @Test
    public void programmeMissingFromFileIsRemoved() throws Exception {
        run(plain(
                row("506", "Gül", "Test", MAIN_PROG, "0010", "01.09.2021"),
                row("506", "Gül", "Test", MINOR_PROG, "0050", "01.09.2022")), new ArrayList<>(), new ArrayList<>());
        Student student = savedStudents.get(0);
        List<StudentProgram> first = new ArrayList<>(savedPrograms);

        ImportResult res = run(plain(row("506", "Gül", "Test", MAIN_PROG, "0010", "01.09.2021")),
                Collections.singletonList(student), first);

        assertEquals(1, res.getProgramsRemoved());
        assertEquals(1, deletedPrograms.size());
        assertEquals(MINOR_PROG, deletedPrograms.get(0).getProgram());
        assertEquals(1, savedPrograms.size());
        assertTrue(savedPrograms.get(0).isPrimaryProgram());
    }

    @Test
    public void nothingIsRemovedWhenAnotherRowOfTheStudentHadAnError() throws Exception {
        run(plain(
                row("507", "Hakan", "Test", MAIN_PROG, "0010", "01.09.2021"),
                row("507", "Hakan", "Test", MINOR_PROG, "0050", "01.09.2022")), new ArrayList<>(), new ArrayList<>());
        Student student = savedStudents.get(0);
        List<StudentProgram> first = new ArrayList<>(savedPrograms);

        ImportResult res = run(plain(
                row("507", "Hakan", "Test", OTHER_PROG, "0030", "01.09.2023"),
                row("507", "Hakan", "Test", "", "0010", "01.09.2021")),
                Collections.singletonList(student), first);

        assertEquals(1, res.getErrors().size());
        assertEquals(0, res.getProgramsRemoved());
        assertTrue(deletedPrograms.isEmpty());
        assertEquals(1, res.getProgramsCreated());
        // 3 programmes retained/created, exactly one primary (the earliest non-minor: existing main)
        assertEquals(3, savedPrograms.size());
        int primaries = 0;
        for (StudentProgram sp : savedPrograms) if (sp.isPrimaryProgram()) primaries++;
        assertEquals(1, primaries);
        assertTrue(saved(MAIN_PROG).isPrimaryProgram());
        assertEquals(ProgramType.OTHER, saved(OTHER_PROG).getProgramType());
        assertEquals(MAIN_PROG, student.getProgram());
    }

    @Test
    public void duplicateStudentAndProgramRowIsReported() throws Exception {
        ImportResult res = run(plain(
                row("508", "Irmak", "First", MAIN_PROG, "0010", "01.09.2021"),
                row("508", "Irmak", "Second", MAIN_PROG, "0010", "01.09.2020")), new ArrayList<>(), new ArrayList<>());

        assertEquals(2, res.getTotalRows());
        assertEquals(1, res.getSkipped());
        assertEquals(1, res.getErrors().size());
        assertEquals(3, res.getErrors().get(0).getRow());
        assertEquals("Aynı öğrenci ve program dosyada tekrar ediyor", res.getErrors().get(0).getMessage());
        assertEquals(1, savedPrograms.size());
        assertEquals("First", savedStudents.get(0).getLastName());
        assertEquals(LocalDate.of(2021, 9, 1), saved(MAIN_PROG).getRegistrationDate());
        assertEquals(0, res.getMultiProgramStudents());
    }
}
