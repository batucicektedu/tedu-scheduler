package com.teduscheduler.service;

import com.teduscheduler.dto.CoursePlanImportResult;
import com.teduscheduler.model.CoursePlan;
import com.teduscheduler.model.CoursePlanItem;
import com.teduscheduler.model.CoursePlanKind;
import com.teduscheduler.repository.CoursePlanItemRepository;
import com.teduscheduler.repository.CoursePlanRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.*;

import static com.teduscheduler.service.CoursePlanParserTest.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CoursePlanImportServiceTest {

    @Mock
    private CoursePlanRepository planRepository;

    @Mock
    private CoursePlanItemRepository itemRepository;

    @InjectMocks
    private CoursePlanImportService service;

    private static byte[] mfFile() throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet("CMPE");
        put(sh, 1, 0, "MÜFREDAT - 2017");
        put(sh, 1, 8, "MÜFREDAT-2022");
        int e = sem(sh, 0, 2, 1, row("CMPE 101", "Giriş", 3, 5), row("CC", "Seçmeli", 3, 5));
        grad(sh, 0, e, 6, 10);
        e = sem(sh, 8, 2, 1, row("CMPE 101", "Giriş", 3, 5));
        grad(sh, 8, e, 3, 5);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }

    private static byte[] minorFile(String year) throws Exception {
        Workbook wb = new XSSFWorkbook();
        Sheet sh = wb.createSheet("Sayfa1");
        put(sh, 0, 0, "İİBF");
        put(sh, 0, 1, "MÜ(F)REDAT YILI");
        put(sh, 1, 0, "Ekonomi Yandal");
        put(sh, 1, 1, year);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return out.toByteArray();
    }

    private static List<CoursePlanImportService.FileInput> one(String name, byte[] data) {
        return Collections.singletonList(new CoursePlanImportService.FileInput(name, data));
    }

    @Test
    public void firstImportCreatesPlansAndItems() throws Exception {
        when(planRepository.findAll()).thenReturn(new ArrayList<>());
        when(planRepository.save(any(CoursePlan.class))).thenAnswer(i -> {
            CoursePlan p = i.getArgument(0);
            if (p.getId() == null) p.setId((long) (p.getLabel().hashCode() & 0xffff));
            return p;
        });

        CoursePlanImportResult r = service.importFiles(one("MF-2022 Müfredatlar.xlsx", mfFile()), false);

        assertEquals(2, r.getPlansCreated());
        assertEquals(0, r.getPlansUpdated());
        assertEquals(3, r.getItemsSaved());
        assertEquals("NEW", r.getFiles().get(0).getPlans().get(0).getStatus());
        assertEquals(1, r.getFiles().get(0).getPlans().get(0).getElectives());
        verify(planRepository, times(2)).save(any(CoursePlan.class));
        verify(itemRepository, never()).deleteByPlanId(anyLong());
    }

    @Test
    public void secondImportKeepsIdsAndRewritesItems() throws Exception {
        CoursePlan p17 = existing(11L, "MF", "CMPE", CoursePlanKind.UNDERGRADUATE, "2017");
        CoursePlan p22 = existing(12L, "MF", "CMPE", CoursePlanKind.UNDERGRADUATE, "2022");
        CoursePlan other = existing(13L, "MF", "SENG", CoursePlanKind.UNDERGRADUATE, "2022"); // dosyada yok
        when(planRepository.findAll()).thenReturn(Arrays.asList(p17, p22, other));
        when(planRepository.save(any(CoursePlan.class))).thenAnswer(i -> i.getArgument(0));

        CoursePlanImportResult r = service.importFiles(one("MF-2022 Müfredatlar.xlsx", mfFile()), false);

        assertEquals(0, r.getPlansCreated());
        assertEquals(2, r.getPlansUpdated());
        assertEquals("UPDATED", r.getFiles().get(0).getPlans().get(1).getStatus());
        assertEquals(Long.valueOf(11), p17.getId());
        assertEquals(Long.valueOf(12), p22.getId());
        assertEquals("MF-2022 Müfredatlar.xlsx", p17.getSourceFile());
        assertEquals(0, new BigDecimal("6").compareTo(p17.getTotalCredits()));
        verify(itemRepository).deleteByPlanId(11L);
        verify(itemRepository).deleteByPlanId(12L);
        verify(itemRepository, never()).deleteByPlanId(13L);
        verify(planRepository, never()).delete(any(CoursePlan.class));
        verify(planRepository, never()).deleteAll();
    }

    @Test
    public void dryRunWritesNothing() throws Exception {
        when(planRepository.findAll()).thenReturn(new ArrayList<>());

        CoursePlanImportResult r = service.importFiles(one("MF-2022 Müfredatlar.xlsx", mfFile()), true);

        assertTrue(r.isDryRun());
        assertEquals(2, r.getPlansCreated());
        assertEquals(0, r.getItemsSaved());
        verify(planRepository, never()).save(any(CoursePlan.class));
        verify(itemRepository, never()).saveAll(any());
        verify(itemRepository, never()).deleteByPlanId(anyLong());
    }

    @Test
    public void oneBadFileSavesNothing() throws Exception {
        List<CoursePlanImportService.FileInput> files = new ArrayList<>();
        files.add(new CoursePlanImportService.FileInput("MF-2022 Müfredatlar.xlsx", mfFile()));
        files.add(new CoursePlanImportService.FileInput("bozuk.xlsx", "merhaba".getBytes("UTF-8")));
        try {
            service.importFiles(files, false);
            fail("hata bekleniyordu");
        } catch (CoursePlanParser.InvalidFileException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("bozuk.xlsx"));
        }
        verify(planRepository, never()).save(any(CoursePlan.class));
        verify(itemRepository, never()).saveAll(any());
    }

    @Test
    public void minorYearChangeUpdatesSamePlan() throws Exception {
        CoursePlan old = existing(7L, "İİBF", "Ekonomi Yandal", CoursePlanKind.MINOR, "2019");
        when(planRepository.findAll()).thenReturn(Collections.singletonList(old));
        when(planRepository.save(any(CoursePlan.class))).thenAnswer(i -> i.getArgument(0));

        CoursePlanImportResult r = service.importFiles(one("Yandal Müfredat-Yıl.xlsx", minorFile("2021")), false);

        assertEquals(1, r.getPlansUpdated());
        assertEquals(Long.valueOf(7), old.getId());
        assertEquals("2021", old.getLabel());
        assertEquals(Integer.valueOf(2021), old.getPlanYear());
        verify(itemRepository).deleteByPlanId(7L);
        verify(itemRepository, never()).saveAll(any());
    }

    @Test
    public void samePlanInTwoFilesIsImportedOnce() throws Exception {
        when(planRepository.findAll()).thenReturn(new ArrayList<>());
        when(planRepository.save(any(CoursePlan.class))).thenAnswer(i -> i.getArgument(0));
        List<CoursePlanImportService.FileInput> files = new ArrayList<>();
        files.add(new CoursePlanImportService.FileInput("MF-2022 Müfredatlar.xlsx", mfFile()));
        files.add(new CoursePlanImportService.FileInput("MF-2022 kopya.xlsx", mfFile()));

        CoursePlanImportResult r = service.importFiles(files, false);

        assertEquals(2, r.getPlansCreated());
        assertEquals(0, r.getFiles().get(1).getPlans().size());
        assertEquals(2, r.getFiles().get(1).getWarnings().size());
    }

    private static CoursePlan existing(long id, String faculty, String code, CoursePlanKind kind, String label) {
        CoursePlan p = new CoursePlan();
        p.setId(id);
        p.setFaculty(faculty);
        p.setProgramCode(code);
        p.setKind(kind);
        p.setLabel(label);
        return p;
    }
}
