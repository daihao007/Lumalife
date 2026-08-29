package com.lumalife.service;

import java.util.Optional;

/** Persistent source of truth for the monolith business state. */
public interface BusinessStateRepository {
  Optional<String> load();

  void save(String payload);
}
