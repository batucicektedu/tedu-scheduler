package com.teduscheduler.controller;

import com.teduscheduler.config.AppConfig;
import com.teduscheduler.dto.CoursePlanDetailDto;
import com.teduscheduler.dto.CoursePlanImportResult;
import com.teduscheduler.service.CoursePlanImportService;
import com.teduscheduler.service.CoursePlanParser;
import com.teduscheduler.service.CoursePlanQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@CrossOrigin
@RestController
public class CoursePlanController {

    private static final Logger logger = LoggerFactory.getLogger(CoursePlanController.class);

    // eşzamanlı iki içe aktarma aynı planı iki kez oluşturmasın
    private final Object importLock = new Object();

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private CoursePlanImportService importService;

    @Autowired
    private CoursePlanQueryService queryService;

    @PostMapping("/api/course-plans/import")
    public ResponseEntity<?> importPlans(@RequestParam("key") String key,
                                         @RequestParam("files") List<MultipartFile> files,
                                         @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
        if (!key.equals(appConfig.getFetchSecretKey())) {
            String errorMessage = "Wrong Secret Key!";
            logger.error(errorMessage);
            return new ResponseEntity<>(errorMessage, HttpStatus.FORBIDDEN);
        }
        if (files == null || files.isEmpty()) {
            return new ResponseEntity<>("Dosya seçilmedi.", HttpStatus.BAD_REQUEST);
        }
        List<CoursePlanImportService.FileInput> inputs = new ArrayList<>();
        for (MultipartFile file : files) {
            String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
            if (file.isEmpty()) {
                return new ResponseEntity<>(original + ": Dosya boş.", HttpStatus.BAD_REQUEST);
            }
            String lower = original.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
                return new ResponseEntity<>(original + ": Yalnız Excel dosyası (.xlsx, .xls) yüklenebilir.", HttpStatus.BAD_REQUEST);
            }
            try {
                inputs.add(new CoursePlanImportService.FileInput(original, file.getBytes()));
            } catch (IOException e) {
                logger.error("Course plan import failed", e);
                return new ResponseEntity<>(original + ": Dosya okunamadı.", HttpStatus.BAD_REQUEST);
            }
        }
        try {
            CoursePlanImportResult result;
            synchronized (importLock) {
                result = importService.importFiles(inputs, dryRun);
            }
            logger.info("Course plan import finished: dryRun={}, created={}, updated={}, items={}",
                    dryRun, result.getPlansCreated(), result.getPlansUpdated(), result.getItemsSaved());
            return ResponseEntity.ok(result);
        } catch (CoursePlanParser.InvalidFileException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/api/course-plans")
    public ResponseEntity<?> plans() {
        return ResponseEntity.ok(queryService.list());
    }

    @GetMapping("/api/course-plans/{id}")
    public ResponseEntity<?> plan(@PathVariable("id") Long id) {
        CoursePlanDetailDto dto = queryService.detail(id);
        if (dto == null) {
            return new ResponseEntity<>("Plan not found", HttpStatus.NOT_FOUND);
        }
        return ResponseEntity.ok(dto);
    }
}
