package com.inkspace.common.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Prompt 模板库：从 classpath:prompts/*.md 加载，按文件名（去扩展名）取用。
 * 模板变量用 {{name}} 占位，避免字符串拼接导致的提示词散落各处。
 */
@Component
public class PromptLibrary {

    private static final String LOCATION = "classpath*:prompts/*.md";
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{(\\w+)}}");

    private final Map<String, String> templates = new HashMap<>();

    public PromptLibrary() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) {
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                templates.put(filename.replace(".md", ""),
                        new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    public String get(String name) {
        String template = templates.get(name);
        if (template == null) {
            throw new IllegalStateException("prompt 模板不存在: " + name);
        }
        return template;
    }

    public String render(String name, Map<String, String> variables) {
        String template = get(name);
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String value = variables.getOrDefault(matcher.group(1), "");
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public List<String> names() {
        return templates.keySet().stream().sorted().toList();
    }
}
