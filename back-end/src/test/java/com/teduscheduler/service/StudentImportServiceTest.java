package com.teduscheduler.service;

import com.teduscheduler.dto.ImportResult;
import com.teduscheduler.model.Curriculum;
import com.teduscheduler.model.Student;
import com.teduscheduler.repository.CurriculumRepository;
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
}
