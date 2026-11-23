package com.teduscheduler.controller;

import com.teduscheduler.config.AppConfig;
import com.teduscheduler.dto.ImportResult;
import com.teduscheduler.service.StudentImportService;
import com.teduscheduler.service.StudentQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

@CrossOrigin
@RestController
public class StudentController {

    private static final Logger logger = LoggerFactory.getLogger(StudentController.class);

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private StudentImportService importService;

    @Autowired
    private StudentQueryService queryService;

    @PostMapping("/api/students/import")
    public ResponseEntity<?> importStudents(@RequestParam("key") String key,
                                            @RequestParam("file") MultipartFile file) {
        if (!key.equals(appConfig.getFetchSecretKey())) {
            String errorMessage = "Wrong Secret Key!";
            logger.error(errorMessage);
            return new ResponseEntity<>(errorMessage, HttpStatus.FORBIDDEN);
        }
        if (file.isEmpty()) {
            return new ResponseEntity<>("File is Empty", HttpStatus.BAD_REQUEST);
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            return new ResponseEntity<>("File extension is not correct.", HttpStatus.BAD_REQUEST);
        }
        try (InputStream in = file.getInputStream()) {
            ImportResult result = importService.importStudents(in);
            logger.info("Student import finished: created={}, updated={}, skipped={}",
                    result.getCreated(), result.getUpdated(), result.getSkipped());
            return ResponseEntity.ok(result);
        } catch (StudentImportService.InvalidImportFileException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (IOException e) {
            logger.error("Student import failed", e);
            return new ResponseEntity<>("Could not read file.", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/api/students")
    public ResponseEntity<?> students(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size,
                                      @RequestParam(required = false) String program,
                                      @RequestParam(required = false) Integer entryYear,
                                      @RequestParam(required = false) Long curriculumId,
                                      @RequestParam(required = false) String q) {
        return ResponseEntity.ok(queryService.search(page, size, program, entryYear, curriculumId, q));
    }

    @GetMapping("/api/students/filters")
    public ResponseEntity<?> filters() {
        return ResponseEntity.ok(queryService.filters());
    }

    @GetMapping("/api/curricula")
    public ResponseEntity<?> curricula() {
        return ResponseEntity.ok(queryService.curricula());
    }
}
