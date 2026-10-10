package com.leo.careerforgeai.interviewflow.application.resume;

import com.leo.careerforgeai.interviewflow.infrastructure.document.ResumeDocumentReader;
import com.leo.careerforgeai.interviewflow.infrastructure.model.ResumeProfileModelContract;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public final class ResumeProfileExtractionEngine {
    private final ResumeDocumentReader documentReader;
    private final ResumeProfileModelContract modelContract;
    private final ProfileDraftValidator validator;

    public ResumeProfileExtractionEngine(ResumeDocumentReader documentReader,
            ResumeProfileModelContract modelContract, ProfileDraftValidator validator) {
        this.documentReader = Objects.requireNonNull(documentReader);
        this.modelContract = Objects.requireNonNull(modelContract);
        this.validator = Objects.requireNonNull(validator);
    }

    public ProfileDraft extract(ResumeUpload upload) {
        ExtractedResumeText extracted = documentReader.extract(upload);
        return validator.validate(modelContract.extract(extracted.text()));
    }
}
