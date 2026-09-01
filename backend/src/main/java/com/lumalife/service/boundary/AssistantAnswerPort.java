package com.lumalife.service.boundary;

import java.util.List;

/** The BFF-facing boundary for AI answers. Provider details stay outside the BFF. */
public interface AssistantAnswerPort {
  String answer(String mode, String question, String context, List<AssistantMessage> history);

  record AssistantMessage(String role, String content) {}
}
