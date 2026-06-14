package org.example.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * AI 聊天控制器 — 将请求转发到 Python Agent 服务
 *
 * POST /api/ai/chat      发送消息（转发到 Agent）
 * POST /api/ai/clear      清空对话
 * GET  /api/ai/health     健康检查（同时检查 Agent）
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${agent.base-url:http://localhost:8000}")
    private String agentBaseUrl;

    /**
     * 对话接口 — 转发到 Python Agent（localhost:8000/agent/chat）
     *
     * Python Agent 提供 RAG 检索、Function Calling、对话管理等能力，
     * 比直接调用 DeepSeek 更智能。后端保留 markdown 清洗以防微信前端显示异常。
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody ChatRequest req) {
        try {
            // 1. 构造转发到 Agent 的请求体
            Map<String, Object> agentBody = new HashMap<>();
            agentBody.put("message", req.getMessage());
            agentBody.put("user_id", req.getUserId());
            // 保留传入的 conversationId（如果没有，Agent 会自己创建）
            if (req.getConversationId() != null && !req.getConversationId().isEmpty()) {
                agentBody.put("conversation_id", req.getConversationId());
            }

            // 2. POST 到 Python Agent
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(agentBody, headers);

            String url = agentBaseUrl + "/agent/chat";
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, Map.class);

            Map<String, Object> agentResp = response.getBody();

            // 3. 提取 reply 并清洗 markdown
            String reply = agentResp != null ? (String) agentResp.get("reply") : null;
            if (reply == null) {
                reply = "抱歉，AI 服务返回异常，请稍后重试。";
            }
            reply = cleanMarkdown(reply);

            // 4. 提取 conversationId（Agent 可能创建了新的）
            String convId = agentResp != null ? (String) agentResp.get("conversation_id") : null;
            if (convId == null) {
                convId = req.getConversationId() != null ? req.getConversationId()
                        : UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("reply", reply);
            result.put("conversation_id", convId);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("AI 对话失败（Agent 转发）: {}", e.getMessage(), e);
            Map<String, Object> err = new HashMap<>();
            err.put("reply", "抱歉，AI 服务暂时不可用，请稍后重试。");
            err.put("conversation_id", req.getConversationId());
            return ResponseEntity.ok(err);
        }
    }

    /**
     * 清空对话 — 转发到 Python Agent
     */
    @PostMapping("/clear")
    public ResponseEntity<Map<String, String>> clearConversation(@RequestBody Map<String, String> body) {
        String convId = body.get("conversation_id");
        if (convId != null) {
            try {
                restTemplate.delete(agentBaseUrl + "/agent/conversations/" + convId);
                log.debug("🗑️ [Agent] 已清空对话: {}", convId);
            } catch (Exception e) {
                log.warn("🗑️ [Agent] 清空对话失败（可能 Agent 未启动）: {}", e.getMessage());
            }
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * 健康检查 — 同时反馈 Agent 状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();

        // 检查 Agent 是否可达
        boolean agentReady = false;
        try {
            ResponseEntity<Map> resp = restTemplate.getForEntity(
                    agentBaseUrl + "/agent/health", Map.class);
            agentReady = resp.getStatusCode().is2xxSuccessful()
                    && "ok".equals(resp.getBody() != null ? resp.getBody().get("status") : null);
        } catch (Exception e) {
            // Agent 未启动
        }

        status.put("status", agentReady ? "ok" : "degraded");
        status.put("agent_ready", agentReady);
        status.put("mode", agentReady ? "agent" : "unavailable");
        return ResponseEntity.ok(status);
    }

    /**
     * 清洗 Markdown 符号，适配微信小程序 <text> 显示
     */
    private String cleanMarkdown(String text) {
        if (text == null) return "";
        // 去掉 **加粗**
        text = text.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        // 去掉 *斜体*
        text = text.replaceAll("(?<![*])\\*([^*]+)\\*(?![*])", "$1");
        // 去掉 `行内代码`
        text = text.replaceAll("`([^`]+)`", "$1");
        // 去掉 ```代码块```
        text = text.replaceAll("```[\\s\\S]*?```", "");
        // 去掉 ###/##/# 标题标记
        text = text.replaceAll("(?m)^#{1,6}\\s+", "");
        // 去掉 - 和 * 列表符号
        text = text.replaceAll("(?m)^[\\s]*[-*]\\s+", "• ");
        // 去掉 > 引用
        text = text.replaceAll("(?m)^>\\s+", "");
        text = text.trim();
        return text;
    }

    // ===== DTO =====

    @Data
    public static class ChatRequest {
        private String message;
        private String userId;
        private String conversationId;
    }
}
