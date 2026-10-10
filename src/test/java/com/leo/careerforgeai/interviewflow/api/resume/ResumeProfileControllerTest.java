package com.leo.careerforgeai.interviewflow.api.resume;

import com.leo.careerforgeai.interviewflow.application.resume.ProfileDraft;
import com.leo.careerforgeai.interviewflow.application.resume.ResumeProfileExtractionEngine;
import com.leo.careerforgeai.interviewflow.infrastructure.document.ResumeDocumentReader;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResumeProfileControllerTest {

    @Test
    void returnsEditableDraftWithoutRawTextOrServerFileIdentifier() throws Exception {
        ResumeProfileExtractionEngine engine = mock(ResumeProfileExtractionEngine.class);
        when(engine.extract(any())).thenReturn(new ProfileDraft(
                "Java Agent开发者", List.of("Java", "Spring AI"),
                List.of(), List.of(), List.of()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ResumeProfileController(engine)).build();
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", "%PDF-test".getBytes());

        mvc.perform(multipart("/api/interviews/resume/parse").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personalSummary").value("Java Agent开发者"))
                .andExpect(jsonPath("$.data.coreSkills[0]").value("Java"))
                .andExpect(jsonPath("$.data.rawText").doesNotExist())
                .andExpect(jsonPath("$.data.fileId").doesNotExist())
                .andExpect(jsonPath("$.data.filePath").doesNotExist());
    }

    @Test
    void returnsClearBadRequestForUnsupportedDocument() throws Exception {
        ResumeProfileExtractionEngine engine = mock(ResumeProfileExtractionEngine.class);
        when(engine.extract(any())).thenThrow(new ResumeDocumentReader.ResumeParsingException(
                ResumeDocumentReader.FailureReason.UNSUPPORTED_TYPE, "仅支持PDF或DOCX简历"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new ResumeProfileController(engine))
                .setControllerAdvice(new ResumeProfileController.ResumeAdvice())
                .build();

        mvc.perform(multipart("/api/interviews/resume/parse").file(
                        new MockMultipartFile("file", "resume.png", "image/png", new byte[]{1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("仅支持PDF或DOCX简历"));
    }
}
