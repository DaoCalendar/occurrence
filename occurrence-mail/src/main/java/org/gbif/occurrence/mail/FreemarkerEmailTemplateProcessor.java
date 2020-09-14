/*
 * Copyright 2020 Global Biodiversity Information Facility (GBIF)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gbif.occurrence.mail;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;

import javax.mail.Address;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

/**
 * Email template processor allows to generate a {@link BaseEmailModel} from a Freemarker template.
 */
public abstract class FreemarkerEmailTemplateProcessor implements EmailTemplateProcessor {

  // shared config among all instances
  private static final Configuration FREEMARKER_CONFIG =
      new Configuration(Configuration.VERSION_2_3_25);

  static {
    FREEMARKER_CONFIG.setDefaultEncoding(StandardCharsets.UTF_8.name());
    FREEMARKER_CONFIG.setLocale(Locale.UK);
    FREEMARKER_CONFIG.setTimeZone(TimeZone.getTimeZone("GMT"));
    FREEMARKER_CONFIG.setNumberFormat("0.####");
    FREEMARKER_CONFIG.setDateFormat("d MMMM yyyy");
    FREEMARKER_CONFIG.setTimeFormat("HH:mm:ss");
    FREEMARKER_CONFIG.setDateTimeFormat("HH:mm:ss d MMMM yyyy");
    FREEMARKER_CONFIG.setClassForTemplateLoading(
        FreemarkerEmailTemplateProcessor.class, "/email/templates");
  }

  /**
   * Build a {@link BaseEmailModel} from
   *
   * @param emailType template type (new user, reset password or welcome)
   * @param emailAddresses email address
   * @param templateDataModel source data
   * @param locale locale
   * @param subjectParams computable params for subject message formatting
   * @return email model to send
   */
  @Override
  public BaseEmailModel buildEmail(
      EmailType emailType,
      List<Address> emailAddresses,
      Object templateDataModel,
      Locale locale,
      String... subjectParams)
      throws IOException, TemplateException {
    return buildEmail(
        emailType, emailAddresses, templateDataModel, locale, Collections.emptyList(), subjectParams);
  }

  /**
   * Build a {@link BaseEmailModel} from
   *
   * @param emailType template type (new user, reset password or welcome)
   * @param emailAddresses email address
   * @param templateDataModel source data
   * @param locale locale
   * @param ccAddresses carbon copy addresses
   * @param subjectParams computable params for subject message formatting
   * @return email model to send
   */
  @Override
  public BaseEmailModel buildEmail(
      EmailType emailType,
      List<Address> emailAddresses,
      Object templateDataModel,
      Locale locale,
      List<String> ccAddresses,
      String... subjectParams)
      throws IOException, TemplateException {
    Objects.requireNonNull(emailAddresses, "emailAddresses shall be provided");
    Objects.requireNonNull(templateDataModel, "templateDataModel shall be provided");
    Objects.requireNonNull(locale, "locale shall be provided");

    // Prepare the E-Mail body text
    StringWriter contentBuffer = new StringWriter();
    FREEMARKER_CONFIG
        .getTemplate(getEmailDataProvider().getTemplate(locale, emailType))
        .process(templateDataModel, contentBuffer);
    return new BaseEmailModel(
        emailAddresses,
        getEmailDataProvider().getSubject(locale, emailType, subjectParams),
        contentBuffer.toString(),
        ccAddresses);
  }

  public abstract EmailDataProvider getEmailDataProvider();
}
