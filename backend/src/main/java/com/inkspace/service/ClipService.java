package com.inkspace.service;

import com.inkspace.common.api.ErrorCode;
import com.inkspace.common.exception.BizException;
import com.inkspace.dto.ClipRequest;
import com.inkspace.dto.NoteCreateRequest;
import com.inkspace.vo.NoteVO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * URL 剪藏：抓取网页标题与正文，落成一条"稍后读"笔记。
 */
@Service
public class ClipService {

    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_CONTENT_LENGTH = 20000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; InkSpaceBot/1.0; +https://github.com/ffff-00/InkSpace)";

    private final NoteService noteService;

    public ClipService(NoteService noteService) {
        this.noteService = noteService;
    }

    public NoteVO clip(Long userId, ClipRequest request) {
        String url = request.getUrl().trim();
        Document document = fetch(url);

        String title = extractTitle(document, url);
        String plainText = extractText(document);
        String markdown = "# " + title + "\n\n" + plainText + "\n\n> 来源：" + url;

        NoteCreateRequest create = new NoteCreateRequest();
        create.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        create.setContent(markdown);
        create.setKind("clip");
        create.setStatus("inbox");
        create.setSourceUrl(url);
        return noteService.create(userId, create);
    }

    private Document fetch(String url) {
        try {
            return Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent(USER_AGENT)
                    .followRedirects(true)
                    .get();
        } catch (IOException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.CLIP_FAILED);
        }
    }

    private String extractTitle(Document document, String url) {
        String ogTitle = document.selectFirst("meta[property=og:title]") == null
                ? "" : document.selectFirst("meta[property=og:title]").attr("content");
        if (StringUtils.hasText(ogTitle)) {
            return ogTitle.trim();
        }
        if (StringUtils.hasText(document.title())) {
            return document.title().trim();
        }
        return url;
    }

    private String extractText(Document document) {
        Document cleaned = document.clone();
        for (Element element : cleaned.select("script, style, nav, header, footer, aside, form, noscript")) {
            element.remove();
        }
        String text = cleaned.body() == null ? "" : cleaned.body().text();
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > MAX_CONTENT_LENGTH ? text.substring(0, MAX_CONTENT_LENGTH) : text;
    }
}
