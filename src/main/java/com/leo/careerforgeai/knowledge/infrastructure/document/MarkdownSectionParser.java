package com.leo.careerforgeai.knowledge.infrastructure.document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @program: CareerForge-AI
 * @description: 识别 Markdown 标题层级，忽略代码围栏中的伪标题，并生成带精确 Offset 的 Section
 * @author: Miao Zheng
 * @date: 2026-07-31 17:28
 **/
final class MarkdownSectionParser {

    private static final Pattern CHINESE_NUMBERED_HEADING = Pattern.compile("^[一二三四五六七八九十百]+、\\s*\\S.*$");

    List<MarkdownSection> parse(String markdown) {
        if (markdown == null || markdown.isBlank()) throw new IllegalArgumentException("markdown 不能为空");

        List<MarkdownSection> sections = new ArrayList<>();
        String[] headings = new String[6];
        String fenceMarker = null;
        int bodyStart = 0;
        int lineStart = 0;

        while (lineStart <= markdown.length()) {
            int newlineIndex = markdown.indexOf('\n', lineStart);
            int lineEnd = newlineIndex >= 0 ? newlineIndex : markdown.length();
            String line = markdown.substring(lineStart, lineEnd);

            if (fenceMarker != null) {
                if (isClosingFence(line, fenceMarker)) fenceMarker = null;
            } else {
                String openingFence = detectFenceMarker(line);
                if (openingFence != null) {
                    fenceMarker = openingFence;
                } else {
                    Heading heading = parseHeading(line);
                    if (heading == null) heading = parseChineseNumberedHeading(line);
                    if (heading != null) {
                        addSection(sections, markdown, bodyStart, lineStart, currentPath(headings));
                        updateHeadings(headings, heading);
                        bodyStart = newlineIndex >= 0 ? lineEnd + 1 : lineEnd;
                    }
                }
            }

            if (newlineIndex < 0) break;
            lineStart = lineEnd + 1;
        }

        addSection(sections, markdown, bodyStart, markdown.length(), currentPath(headings));
        return List.copyOf(sections);
    }

    private Heading parseHeading(String line) {
        int indentation = 0;
        while (indentation < line.length() && line.charAt(indentation) == ' ' && indentation < 4) indentation++;
        if (indentation > 3) return null;

        int level = 0;
        while (indentation + level < line.length() && line.charAt(indentation + level) == '#') level++;
        if (level == 0 || level > 6) return null;

        int titleStart = indentation + level;
        if (titleStart < line.length() && !Character.isWhitespace(line.charAt(titleStart))) return null;

        String title = line.substring(titleStart).strip();
        title = title.replaceFirst("[ \\t]+#+[ \\t]*$", "").strip();
        return new Heading(level, title);
    }

    private void updateHeadings(String[] headings, Heading heading) {
        Arrays.fill(headings, heading.level() - 1, headings.length, null);
        if (!heading.title().isBlank()) headings[heading.level() - 1] = heading.title();
    }

    private List<String> currentPath(String[] headings) {
        return Arrays.stream(headings).filter(value -> value != null).toList();
    }

    private void addSection(List<MarkdownSection> sections, String markdown, int startOffset, int endOffset, List<String> sectionPath) {
        int contentStart = skipLeadingBlankLines(markdown, startOffset, endOffset);
        int contentEnd = trimTrailingBlankLines(markdown, contentStart, endOffset);
        if (contentStart >= contentEnd) return;
        sections.add(new MarkdownSection(sectionPath, contentStart, contentEnd, markdown.substring(contentStart, contentEnd)));
    }

    private int skipLeadingBlankLines(String markdown, int startOffset, int endOffset) {
        int current = startOffset;
        while (current < endOffset) {
            int newlineIndex = markdown.indexOf('\n', current);
            int lineEnd = newlineIndex >= 0 && newlineIndex < endOffset ? newlineIndex : endOffset;
            if (!markdown.substring(current, lineEnd).isBlank()) break;
            current = lineEnd < endOffset ? lineEnd + 1 : endOffset;
        }
        return current;
    }

    private int trimTrailingBlankLines(String markdown, int startOffset, int endOffset) {
        int current = endOffset;
        while (current > startOffset) {
            int lineEnd = current;
            if (markdown.charAt(lineEnd - 1) == '\n') lineEnd--;
            int lineStart = markdown.lastIndexOf('\n', lineEnd - 1) + 1;
            if (markdown.substring(lineStart, lineEnd).isBlank()) {
                current = lineStart;
            } else {
                return lineEnd;
            }
        }
        return current;
    }

    private String detectFenceMarker(String line) {
        String candidate = line.stripLeading();
        if (candidate.isEmpty()) return null;

        char marker = candidate.charAt(0);
        if (marker != '`' && marker != '~') return null;

        int length = 0;
        while (length < candidate.length() && candidate.charAt(length) == marker) length++;
        return length >= 3 ? candidate.substring(0, length) : null;
    }

    private boolean isClosingFence(String line, String openingFence) {
        String candidate = line.stripLeading();
        char marker = openingFence.charAt(0);
        int length = 0;
        while (length < candidate.length() && candidate.charAt(length) == marker) length++;
        return length >= openingFence.length() && candidate.substring(length).isBlank();
    }

    private Heading parseChineseNumberedHeading(String line) {
        int indentation = 0;
        while (indentation < line.length() && line.charAt(indentation) == ' ') indentation++;
        if (indentation > 3) return null;

        String candidate = line.substring(indentation).stripTrailing();
        return CHINESE_NUMBERED_HEADING.matcher(candidate).matches() ? new Heading(3, candidate) : null;
    }

    private record Heading(int level, String title) {
    }
}