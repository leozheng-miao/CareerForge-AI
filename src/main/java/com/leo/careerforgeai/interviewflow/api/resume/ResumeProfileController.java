package com.leo.careerforgeai.interviewflow.api.resume;

import com.leo.careerforgeai.interviewflow.application.resume.ResumeProfileExtractionEngine;
import com.leo.careerforgeai.interviewflow.application.resume.ResumeUpload;
import com.leo.careerforgeai.interviewflow.infrastructure.document.ResumeDocumentReader.ResumeParsingException;
import com.leo.careerforgeai.shared.exception.ErrorCode;
import com.leo.careerforgeai.shared.web.BaseResponse;
import com.leo.careerforgeai.shared.web.ResultUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;

@RestController
@RequestMapping("/api/interviews/resume")
public final class ResumeProfileController {
    private final ResumeProfileExtractionEngine engine;

    public ResumeProfileController(ResumeProfileExtractionEngine engine) {
        this.engine = Objects.requireNonNull(engine);
    }

    @PostMapping(value = "/parse", consumes = "multipart/form-data")
    public BaseResponse<ProfileDraftResponse> parse(@RequestPart("file") MultipartFile file)
            throws IOException {
        ResumeUpload upload = new ResumeUpload(file.getOriginalFilename(),
                file.getContentType(), file.getBytes());
        return ResultUtils.success(ProfileDraftResponse.from(engine.extract(upload)));
    }

    @RestControllerAdvice(assignableTypes = ResumeProfileController.class)
    public static final class ResumeAdvice {
        @ExceptionHandler(ResumeParsingException.class)
        ResponseEntity<BaseResponse<?>> parsing(ResumeParsingException exception) {
            return ResponseEntity.badRequest().body(
                    ResultUtils.error(ErrorCode.PARAMS_ERROR, exception.getMessage()));
        }

        @ExceptionHandler(IOException.class)
        ResponseEntity<BaseResponse<?>> io(IOException exception) {
            return ResponseEntity.badRequest().body(
                    ResultUtils.error(ErrorCode.PARAMS_ERROR, "简历文件读取失败"));
        }
    }
}
