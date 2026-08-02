package com.leo.careerforgeai.knowledge.infrastructure.document;

import com.leo.careerforgeai.knowledge.domain.CleanedDocument;
import com.leo.careerforgeai.knowledge.domain.DocumentChunk;
import com.leo.careerforgeai.knowledge.domain.SourceDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 串联完整 Markdown Chunking 流程，将一份 CleanedDocument 确定性转换为有序的 DocumentChunk
 */
@Component
public class MarkdownChunker {

    private final ChunkingProperties properties;
    private final StableChunkIdGenerator chunkIdGenerator;
    private final MarkdownSectionParser sectionParser = new MarkdownSectionParser();
    private final MarkdownBlockParser blockParser = new MarkdownBlockParser();
    private final OversizedMarkdownBlockSplitter oversizedBlockSplitter = new OversizedMarkdownBlockSplitter();
    private final MarkdownBlockChunkAssembler chunkAssembler = new MarkdownBlockChunkAssembler();

    public MarkdownChunker(ChunkingProperties properties, StableChunkIdGenerator chunkIdGenerator) {
        this.properties = properties;
        this.chunkIdGenerator = chunkIdGenerator;
    }

    public List<DocumentChunk> chunk(CleanedDocument document) {
        if (document == null) throw new IllegalArgumentException("document 不能为空");

        List<ChunkDraft> drafts = createDrafts(document);
        if (drafts.isEmpty()) throw new DocumentChunkingException("文档没有可生成 Chunk 的正文：" + document.sourceDocument().sourcePath());

        List<DocumentChunk> chunks = new ArrayList<>();
        String chunkerVersion = properties.chunkerVersion();

        for (int chunkIndex = 0; chunkIndex < drafts.size(); chunkIndex++) {
            ChunkDraft draft = drafts.get(chunkIndex);
            chunks.add(createChunk(document, draft, chunkerVersion, chunkIndex));
        }

        return List.copyOf(chunks);
    }

    private List<ChunkDraft> createDrafts(CleanedDocument document) {
        List<ChunkDraft> drafts = new ArrayList<>();
        List<MarkdownSection> sections = sectionParser.parse(document.cleanedContent());

        for (MarkdownSection section : sections) {
            List<MarkdownBlock> blocks = blockParser.parse(section);
            List<MarkdownBlock> normalizedBlocks = oversizedBlockSplitter.split(blocks, properties.getMaxChunkChars());
            drafts.addAll(chunkAssembler.assemble(section, normalizedBlocks, properties.getMaxChunkChars(), properties.getOverlapChars()));
        }

        return drafts;
    }

    private DocumentChunk createChunk(CleanedDocument document, ChunkDraft draft, String chunkerVersion, int chunkIndex) {
        SourceDocument source = document.sourceDocument();
        String chunkId = chunkIdGenerator.generate(document, chunkerVersion, chunkIndex);

        return new DocumentChunk(
                source.knowledgeBaseId(),
                source.documentId(),
                source.documentName(),
                source.documentType(),
                source.sourcePath(),
                source.sourceHash(),
                document.cleaningVersion(),
                chunkerVersion,
                chunkId,
                chunkIndex,
                draft.sectionPath(),
                draft.startOffset(),
                draft.endOffset(),
                draft.content()
        );
    }
}