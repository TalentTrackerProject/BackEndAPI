package com.indexation.cv.controller;

import com.indexation.cv.Exception.TagException;
import com.indexation.cv.data.CVModel;
import com.indexation.cv.data.CVResponse;
import com.indexation.cv.data.DocumentType;
import com.indexation.cv.service.TagsService;
import com.indexation.cv.utils.CVLogger;
import com.indexation.cv.service.CVService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/cv")
@Tag(name = "CV",description = "Search and upload CVs")
public class CVResource {
    @Autowired
    private CVService cvService;
    @Autowired
    private TagsService tagsService;
    @Autowired
    private Environment env;

    /**
     * GET /api/v1/cv : Search single/multiple keyword on the cv
     * @param keyword
     * @param matchAll
     * @return id of CVs that contains a word matching at least one of the keyword
     */
    @Operation(summary = "Get a list of cv ids matching the one or all keywords", description = "To request all cv that contains all keywords, add the `match_all` parameter to `true`. Each keywords must be separated with a **comma**")
    @ApiResponse(responseCode = "200", description = "The list of cv ids", content = { @Content(mediaType = "application/json", schema = @Schema(implementation = CVModel.class))})
    @GetMapping
    public ResponseEntity<List<CVResponse>> searchCv(@RequestParam("keyword") String keyword, @RequestParam(name = "match_all", defaultValue = "false", required = false) boolean matchAll) {
        CVLogger.info("Get CV by keywords: \"" + keyword + "\" with match_all:" + matchAll, env.getActiveProfiles());
        return ResponseEntity.ok(cvService.searchCV(keyword, matchAll));
    }

    /**
     * POST /api/v1/cv : Create an index of the PDF
     * @param file
     * @return Id of the cv created
     * @throws IOException
     */
    @Operation(summary = "Create an index of the CV")
    @ApiResponse(responseCode = "201", description = "Cv indexed", content = { @Content(mediaType = "application/json", schema = @Schema(implementation = CVModel.class))})
    @ApiResponse(responseCode = "415", description = "Unsupported Media type. Only accept: *.doc, *.docx or *.pdf", content = { @Content(mediaType = "application/json", schema = @Schema(implementation = Error.class))})
    @ApiResponse(responseCode = "500", description = "Internal error", content = { @Content(mediaType = "application/json", schema = @Schema(implementation = Error.class))})
    @PostMapping
    public ResponseEntity<CVModel> uploadCv(@RequestParam("file")MultipartFile file, @RequestParam(name = "tags",required = false) List<String> tags) {
        CVLogger.info("[POST] /api/v1/cv : Entering into uploadCv", env.getActiveProfiles());
        try {
            String[] fn = file.getOriginalFilename().split("\\.");
            String ext = fn[fn.length - 1]; // extracting file's extension
            CVModel cv;
            switch(ext.toUpperCase()){
                case "PDF": // PDF file
                    CVLogger.info("uploadCv: Saving pdf file data...", env.getActiveProfiles());
                    cv = cvService.saveCvPDF(file);
                    tagsService.createTags(cv.getId(), tags);
                    return ResponseEntity.status(HttpStatus.CREATED).body(cv);
                case "DOC": case "DOCX": // Word file
                    CVLogger.info("uploadCv: Saving word file data...", env.getActiveProfiles());
                    cv = cvService.saveCvWord(file, ext);
                    tagsService.createTags(cv.getId(), tags);
                    return ResponseEntity.status(HttpStatus.CREATED).body(cv);
                default: // File format not supported
                    CVLogger.error("uploadCv: File extension not allowed", env.getActiveProfiles());
                    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(null);
            }
        } catch (IOException | NullPointerException | InvalidFormatException | TagException e) {
            CVLogger.error("uploadCv: "+e.getMessage(), env.getActiveProfiles());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
