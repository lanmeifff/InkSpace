package com.inkspace.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.inkspace.common.ai.AiClientConfig;
import com.inkspace.domain.entity.UserAiConfig;
import com.inkspace.mapper.UserAiConfigMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 接入的手工诊断（不写断言，只打印，跑完看控制台）。
 *
 * 用途：遇到「AI 服务调用失败」时，一次性看清
 *   1) 系统实际解析出的生效配置（url / model / Key 有没有、长度对不对）
 *   2) 上游到底支持哪些模型
 *   3) 用真实配置打一次上游，打印上游返回的原文错误
 *
 * 跑法：mvn -o test -f backend/pom.xml -Dtest=AiConfigProbeTest
 * 库里没有用户配置过 AI 时自动跳过，不影响 mvn test 全绿。
 */
@SpringBootTest
class AiConfigProbeTest {

    @Autowired
    private AiConfigService aiConfigService;

    @Autowired
    private UserAiConfigMapper userAiConfigMapper;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @Test
    void dumpEffectiveConfigAndProbeUpstream() throws Exception {
        UserAiConfig stored = userAiConfigMapper.selectOne(
                new LambdaQueryWrapper<UserAiConfig>().orderByDesc(UserAiConfig::getUpdatedAt).last("LIMIT 1"));
        Assumptions.assumeTrue(stored != null, "库里还没有用户配置过 AI，跳过诊断");

        Long userId = stored.getUserId();
        AiClientConfig config = aiConfigService.resolve(userId);
        System.out.println("\n========== 生效配置（userId=" + userId + "）==========");
        System.out.println("providerName = " + config.providerName());
        System.out.println("url          = " + config.url());
        System.out.println("model        = " + config.model());
        System.out.println("apiKey       = " + mask(config.apiKey()));

        System.out.println("\n========== 上游支持的模型 ==========");
        for (String model : listModels(config)) {
            System.out.println("  - " + model);
        }

        System.out.println("\n========== 按你保存的原样调一次 ==========");
        chat(config.url(), config.model(), config.apiKey());

        System.out.println("\n========== 怎么读上面的结果 ==========");
        System.out.println("· HTTP 200        → 配置可用");
        System.out.println("· HTTP 404        → url 少了路径，OpenAI 兼容接口一般是 …/v1/chat/completions");
        System.out.println("· HTTP 400 + model→ 模型名不在上面的清单里");
        System.out.println("· HTTP 401        → Key 无效");
        System.out.println("· HTTP 402/403    → 余额或额度问题");
    }

    private List<String> listModels(AiClientConfig config) {
        List<String> ids = new ArrayList<>();
        try {
            String base = config.url().replaceAll("/+$", "");
            if (base.endsWith("/chat/completions")) {
                base = base.substring(0, base.length() - "/chat/completions".length());
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/models"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            for (JsonNode node : root.path("data")) {
                ids.add(node.path("id").asText("?") + "   " + node.path("name").asText(""));
            }
            if (ids.isEmpty()) {
                ids.add("(解析不到，HTTP " + response.statusCode() + ") " + trim(response.body()));
            }
        } catch (Exception e) {
            ids.add("异常：" + e.getMessage());
        }
        return ids;
    }

    private void chat(String url, String model, String apiKey) {
        System.out.println("POST " + url + "   model=" + model);
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", 16);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "user").put("content", "ping");
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("HTTP " + response.statusCode());
            System.out.println(trim(response.body()));
        } catch (Exception e) {
            System.out.println("请求异常：" + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    /** 只显示前后几位，避免把完整 Key 打进日志 */
    private static String mask(String key) {
        if (key == null || key.isBlank()) {
            return "(空)";
        }
        return key.length() <= 12 ? "(太短，长度=" + key.length() + ")"
                : key.substring(0, 7) + "****" + key.substring(key.length() - 4) + "  长度=" + key.length();
    }

    private static String trim(String body) {
        if (body == null) {
            return "(空响应)";
        }
        String one = body.replaceAll("\\s+", " ").trim();
        return one.length() > 500 ? one.substring(0, 500) + "…" : one;
    }
}
