package com.legalrag.util;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PromptBuilder {

    /* =========================================================
     * SYSTEM PROMPT
     * ========================================================= */
    public String buildSystemPrompt() {
        return """
                Bạn là trợ lý pháp lý AI chuyên nghiệp, chuyên về pháp luật Việt Nam.

                Nguyên tắc trả lời:
                1. CHÍNH XÁC: Dựa vào văn bản pháp luật được cung cấp
                2. RÕ RÀNG: Giải thích dễ hiểu, có cấu trúc
                3. ĐẦY ĐỦ: Trả lời toàn diện các khía cạnh
                4. TRÍCH DẪN: Luôn dẫn chiếu điều luật cụ thể
                5. THẬN TRỌNG: Nếu không chắc chắn, hãy nói rõ

                Luôn trả lời bằng tiếng Việt.
                """;
    }

    /* =========================================================
     * STANDARD RAG PROMPT
     * ========================================================= */
    public String buildStandardPrompt(
            String question,
            String context,
            String chatHistory) {

        StringBuilder prompt = new StringBuilder();

        prompt.append("### NHIỆM VỤ\n");
        prompt.append("Trả lời câu hỏi pháp lý dựa trên các văn bản được cung cấp.\n\n");

        if (chatHistory != null && !chatHistory.isBlank()) {
            prompt.append("### LỊCH SỬ HỘI THOẠI\n");
            prompt.append(chatHistory).append("\n\n");
        }

        prompt.append("### VĂN BẢN PHÁP LUẬT LIÊN QUAN\n");
        prompt.append(context).append("\n\n");

        prompt.append("### CÂU HỎI\n");
        prompt.append(question).append("\n\n");

        prompt.append("### YÊU CẦU TRẢ LỜI\n");
        prompt.append("- Trả lời ngắn gọn, đúng trọng tâm\n");
        prompt.append("- Dẫn chiếu rõ Điều / Khoản / Văn bản\n");
        prompt.append("- Giải thích logic pháp lý\n\n");

        prompt.append("### TRẢ LỜI");

        return prompt.toString();
    }

  

    /* =========================================================
     * DEEP THINKING
     * ========================================================= */
    public String buildDeepThinkingPrompt(
            String question,
            Map<String, Object> thinkingProcess,
            String multiContext,
            String chatHistory) {

        @SuppressWarnings("unchecked")
        List<String> keywords = (List<String>) thinkingProcess.get("keywords");

        String reasoning = (String) thinkingProcess.get("reasoning");
        Integer totalDocs = (Integer) thinkingProcess.get("total_docs_found");

        StringBuilder prompt = new StringBuilder();

        prompt.append("### PHÂN TÍCH CHUYÊN SÂU\n\n");
        prompt.append("Câu hỏi: ").append(question).append("\n\n");
        prompt.append("- Keywords: ").append(String.join(", ", keywords)).append("\n");
        prompt.append("- Lý do phân tích: ").append(reasoning).append("\n");
        prompt.append("- Tổng tài liệu: ").append(totalDocs).append("\n\n");

        if (chatHistory != null && !chatHistory.isBlank()) {
            prompt.append("### LỊCH SỬ HỘI THOẠI\n");
            prompt.append(chatHistory).append("\n\n");
        }

        prompt.append("### NGỮ CẢNH TỔNG HỢP\n");
        prompt.append(multiContext).append("\n\n");

        prompt.append("### YÊU CẦU\n");
        prompt.append("- Tổng hợp đa chiều\n");
        prompt.append("- Phân tích sâu, logic\n");
        prompt.append("- Trích dẫn đầy đủ\n\n");

        prompt.append("### TRẢ LỜI TOÀN DIỆN");

        return prompt.toString();
    }

 

    /* =========================================================
     * KEYWORD EXTRACTION
     * ========================================================= */
    public String buildKeywordExtractionPrompt(String query) {
        return """
                Phân tích câu hỏi pháp lý sau và trích xuất keywords.

                Câu hỏi: "%s"

                Trả về JSON hợp lệ, KHÔNG thêm text khác.
                """.formatted(query);
    }

    /* =========================================================
     * CONTEXT FORMAT
     * ========================================================= */
    public String formatContextWithScores(
            List<Map<String, Object>> results,
            boolean showScores) {

        StringBuilder context = new StringBuilder();

        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> r = results.get(i);

            context.append("[VĂN BẢN ").append(i + 1).append("]");

            if (showScores && r.containsKey("score")) {
                context.append(" (Score: ").append(r.get("score")).append(")");
            }

            context.append("\n");
            context.append(r.get("text")).append("\n");
            context.append("-".repeat(60)).append("\n");
        }

        return context.toString();
    }
}
