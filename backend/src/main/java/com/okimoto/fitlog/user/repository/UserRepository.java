package com.okimoto.fitlog.user.repository;

import static com.okimoto.fitlog.jooq.Tables.USERS;
import static org.jooq.impl.DSL.lower;

import com.okimoto.fitlog.jooq.tables.records.UsersRecord;
import java.util.Locale;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** ユーザー（users）の読み書き */
@Repository
public class UserRepository {

  private final DSLContext dsl;

  public UserRepository(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** ユーザー名が使われているか（大文字・小文字は区別しない。一意制約の LOWER(username) と同じ判定） */
  public boolean existsByUsernameIgnoreCase(String username) {
    return dsl.fetchExists(USERS, lower(USERS.USERNAME).eq(username.toLowerCase(Locale.ROOT)));
  }

  /** メールアドレスが使われているか（小文字にそろえたものを渡す） */
  public boolean existsByEmail(String email) {
    return dsl.fetchExists(USERS, USERS.EMAIL.eq(email));
  }

  /** ユーザーを保存し、保存した行（ID と既定値を含む）を返す */
  public UsersRecord insert(NewUser user) {
    return dsl.insertInto(USERS)
        .set(USERS.USERNAME, user.username())
        .set(USERS.DISPLAY_NAME, user.displayName())
        .set(USERS.EMAIL, user.email())
        .set(USERS.PASSWORD_HASH, user.passwordHash())
        .set(USERS.CREATED_AT, user.createdAt())
        .set(USERS.UPDATED_AT, user.createdAt())
        .returning()
        .fetchSingle();
  }

  public Optional<UsersRecord> findByEmail(String email) {
    return dsl.selectFrom(USERS).where(USERS.EMAIL.eq(email)).fetchOptional();
  }

  public Optional<UsersRecord> findById(long id) {
    return dsl.selectFrom(USERS).where(USERS.ID.eq(id)).fetchOptional();
  }
}
