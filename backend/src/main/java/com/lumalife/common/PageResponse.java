package com.lumalife.common;

import java.util.List;

public record PageResponse<T>(List<T> records, int page, int size, long total, long pages) {
  public static <T> PageResponse<T> of(List<T> records, int page, int size, long total) {
    long pages = size <= 0 ? 0 : (long) Math.ceil(total * 1.0 / size);
    return new PageResponse<>(records, page, size, total, pages);
  }
}
