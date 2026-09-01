package com.lumalife.assistant;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/assistant")
public class AssistantApi {
  private final AssistantAnswerService service;
  public AssistantApi(AssistantAnswerService service) { this.service = service; }

  @PostMapping("/answer")
  Map<String, String> answer(@RequestBody AssistantAnswerService.AssistantRequest request) {
    return Map.of("answer", service.answer(request));
  }
}
