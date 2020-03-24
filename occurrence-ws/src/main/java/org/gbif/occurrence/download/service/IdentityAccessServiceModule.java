package org.gbif.occurrence.download.service;

import com.google.inject.PrivateModule;
import java.util.Properties;
import org.gbif.api.service.common.IdentityAccessService;
import org.gbif.registry.identity.service.BaseIdentityAccessService;
import org.gbif.registry.persistence.mapper.UserMapper;
import org.gbif.utils.file.properties.PropertiesUtil;

public class IdentityAccessServiceModule extends PrivateModule {

  private final Properties rawProperties;

  public IdentityAccessServiceModule(Properties properties) {
    rawProperties = properties;
  }

  @Override
  protected void configure() {
    install(new IdentityMyBatisModule(
      PropertiesUtil.filterProperties(rawProperties, "registry.db")));
    expose(UserMapper.class);

    try {
      bind(IdentityAccessService.class).toConstructor(
        BaseIdentityAccessService.class.getConstructor(UserMapper.class));
    } catch (NoSuchMethodException e) {
      addError(e);
    }

    expose(IdentityAccessService.class);
  }
}
