package org.gbif.occurrence.download.service;

import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.gbif.api.model.common.GbifUser;
import org.gbif.api.vocabulary.UserRole;
import org.gbif.mybatis.guice.MyBatisModule;
import org.gbif.mybatis.type.UuidTypeHandler;
import org.gbif.registry.persistence.mapper.UserMapper;
import org.gbif.registry.persistence.mapper.handler.UserRoleTypeHandler;

public class IdentityMyBatisModule extends MyBatisModule {

  public static final String DATASOURCE_BINDING_NAME = "identity";

  public IdentityMyBatisModule(Properties props) {
    super(DATASOURCE_BINDING_NAME, props);
  }

  @Override
  protected void bindManagers() {
    failFast(true);
  }

  @Override
  protected void bindMappers() {
    addMapperClass(UserMapper.class);

    addAlias("GbifUser").to(GbifUser.class);
    addAlias("UserRole").to(UserRole.class);
    addAlias("UUID").to(UUID.class);
  }

  @Override
  protected void bindTypeHandlers() {
    handleType(Set.class).with(UserRoleTypeHandler.class);
    handleType(UUID.class).with(UuidTypeHandler.class);
  }
}
