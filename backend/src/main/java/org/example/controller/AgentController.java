package org.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * AI Agent 转发控制器
 *
 * 将前端 /api/agent/* 的请求转发到 Python Agent 服务 (:8000)
 * 统一走后端认证（TokenInterceptor），Agent 无需单独处理鉴权
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${agent.base-url:http://localhost:8000}")
    private String agentBaseUrl;

    /**
     * 对话接口 — 转发到 Python Agent
     * POST /api/agent/chat
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, Object> body) {
        String url = agentBaseUrl + "/agent/chat";
        log.debug("🔁 [Agent转发] POST /agent/chat → {}", url);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, Map.class);

            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (Exception e) {
            log.error("❌ [Agent转发] 失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Agent 服务暂不可用，请稍后重试"));
        }
    }

    /**
     * 获取对话历史
     * GET /api/agent/conversations/{convId}/history
     */
    @GetMapping("/conversations/{convId}/history")
    public ResponseEntity<?> getHistory(@PathVariable String convId) {
        String url = agentBaseUrl + "/agent/conversations/" + convId + "/history";
        log.debug("🔁 [Agent转发] GET {}", url);

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("❌ [Agent转发] 获取历史失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Agent 服务暂不可用"));
        }
    }

    /**
     * 清空对话
     * DELETE /api/agent/conversations/{convId}
     */
    @DeleteMapping("/conversations/{convId}")
    public ResponseEntity<?> clearConversation(@PathVariable String convId) {
        String url = agentBaseUrl + "/agent/conversations/" + convId;
        log.debug("🔁 [Agent转发] DELETE {}", url);

        try {
            restTemplate.delete(url);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            log.error("❌ [Agent转发] 清空对话失败: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Agent 服务暂不可用"));
        }
    }

    /**
     * 健康检查
     * GET /api/agent/health
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        String url = agentBaseUrl + "/agent/health";
        log.debug("🔁 [Agent转发] GET {}", url);

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "status", "unavailable",
                    "error", "Agent 服务未启动"
            ));
        }
    }
}
