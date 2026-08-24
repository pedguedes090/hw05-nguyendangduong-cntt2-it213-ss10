package com.rikkeipay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rikkeipay.dto.EvaluationResult;
import io.langfuse.client.LangfuseClient;
import io.langfuse.client.model.Score;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * AutoEvaluator - hệ thống đánh giá tự động (LLM-as-a-Judge).
 *
 * Luồng hoạt động:
 *   1. Nhận (traceId, input, output) của một hội thoại khách hàng.
 *   2. Gọi ChatClient với JudgePrompt.SYSTEM_PROMPT để LLM chấm điểm.
 *   3. Parse JSON -> EvaluationResult (accuracy/politeness/security + reason).
 *   4. Gửi từng Score ngược lại vào Trace trên Langfuse (đính kèm tên traceId).
 *
 * Trong thực tế bước (1) có thể được kích hoạt tự động bằng Langfuse Webhook
 * khi một trace hoàn tất (endpoint nhận payload trace, trích input/output,
 * gọi Judge, rồi đẩy score về).
 */
@Service
public class AutoEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AutoEvaluator.class);

    private final LangfuseClient langfuseClient;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AutoEvaluator(LangfuseClient langfuseClient, ChatClient chatClient) {
        this.langfuseClient = langfuseClient;
        this.chatClient = chatClient;
    }

    /**
     * Chấm điểm một cặp (input, output) và gửi score về trace trên Langfuse.
     *
     * @param traceId id của trace cần chấm (đã tồn tại trên Langfuse)
     * @param input   câu hỏi/lệnh của khách hàng
     * @param output  câu trả lời của trợ lý
     */
    public void evaluateAndScore(String traceId, String input, String output) {
        String judgeUserPrompt = """
                === INPUT (câu hỏi/lệnh của khách hàng) ===
                %s

                === OUTPUT (câu trả lời của trợ lý) ===
                %s

                Hãy chấm điểm theo cấu trúc JSON đã quy định.
                """.formatted(input, output);

        String judgeResponse = chatClient.prompt()
                .system(JudgePrompt.SYSTEM_PROMPT)
                .user(judgeUserPrompt)
                .call()
                .content();

        log.info("Phản hồi Judge cho trace {}: {}", traceId, judgeResponse);

        EvaluationResult result;
        try {
            result = objectMapper.readValue(judgeResponse, EvaluationResult.class);
        } catch (Exception e) {
            log.error("Judge trả về JSON không hợp lệ: {}", judgeResponse, e);
            return;
        }

        // Gửi 3 score ngược lại vào Trace trên Langfuse
        langfuseClient.score(new Score()
                .traceId(traceId)
                .name("accuracy")
                .value(result.accuracy().score())
                .comment(result.accuracy().reason()));
        langfuseClient.score(new Score()
                .traceId(traceId)
                .name("politeness")
                .value(result.politeness().score())
                .comment(result.politeness().reason()));
        langfuseClient.score(new Score()
                .traceId(traceId)
                .name("security")
                .value(result.security().score())
                .comment(result.security().reason()));

        log.info("Đã ghi score cho trace {}: accuracy={}, politeness={}, security={} (avg={})",
                traceId,
                result.accuracy().score(),
                result.politeness().score(),
                result.security().score(),
                result.average());
    }
}
