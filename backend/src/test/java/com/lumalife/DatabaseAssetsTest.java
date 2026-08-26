package com.lumalife;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DatabaseAssetsTest {
  private static final Path SEED_FILE = Path.of("..", "database", "seeds", "demo-data.sql");

  @Test
  void demoSeedPasswordsUseSpringCompatibleBcryptHashes() throws IOException {
    String sql = Files.readString(SEED_FILE);
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    assertThat(encoder.matches("abc123456", passwordHash(sql, "13800000001"))).isTrue();
    assertThat(encoder.matches("admin123456", passwordHash(sql, "13800000000"))).isTrue();
  }

  @Test
  void demoSeedKeepsIdsRequiredByTheMemoryBaseline() throws IOException {
    String sql = Files.readString(SEED_FILE);

    assertThat(sql).contains("(1001, 1, '藤椒鸡饭'");
    assertThat(sql).contains("(1007, 4, '栗子巴斯克'");
  }

  private String passwordHash(String sql, String phone) {
    Pattern accountRow = Pattern.compile("\\(\\d+, '" + Pattern.quote(phone) + "', '([^']+)'");
    Matcher matcher = accountRow.matcher(sql);
    assertThat(matcher.find()).as("seed account %s exists", phone).isTrue();
    return matcher.group(1);
  }
}
