package com.okimoto.fitlog.support;

import static com.okimoto.fitlog.jooq.Tables.USERS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** 結合テストのユーザーを、新規登録の API で作る。DB はテスト全体で共有するので、毎回違うユーザー名にする */
public final class TestAccounts {

  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  private final MockMvc mockMvc;
  private final ObjectMapper objectMapper;
  private final DSLContext dsl;

  public TestAccounts(MockMvc mockMvc, ObjectMapper objectMapper, DSLContext dsl) {
    this.mockMvc = mockMvc;
    this.objectMapper = objectMapper;
    this.dsl = dsl;
  }

  /** 新しいユーザーを登録する */
  public Account signup() throws Exception {
    String username = "t_" + SEQUENCE.incrementAndGet() + "_" + (System.nanoTime() % 100_000);
    String body =
        mockMvc
            .perform(
                post("/api/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new Signup(username, "表示名", username + "@example.com", "password123"))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String token = objectMapper.readTree(body).get("accessToken").asString();
    long id =
        dsl.select(USERS.ID).from(USERS).where(USERS.USERNAME.eq(username)).fetchSingle().value1();
    return new Account(id, username, token);
  }

  /** 鍵アカウントにする（設定の変更の API を作るまでは DB を直接変える） */
  public void makePrivate(Account account) {
    dsl.update(USERS).set(USERS.IS_PRIVATE, true).where(USERS.ID.eq(account.id())).execute();
  }

  /**
   * 登録したユーザー。
   *
   * @param id ユーザーの ID
   * @param username ユーザー名
   * @param token アクセストークン
   */
  public record Account(long id, String username, String token) {
    public String bearer() {
      return "Bearer " + token;
    }
  }

  private record Signup(String username, String displayName, String email, String password) {}
}
