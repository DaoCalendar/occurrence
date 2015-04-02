package org.gbif.occurrence.processor.interpreting;

import org.gbif.api.vocabulary.Country;
import org.gbif.api.vocabulary.OccurrenceIssue;
import org.gbif.common.parsers.core.OccurrenceParseResult;
import org.gbif.common.parsers.core.ParseResult;
import org.gbif.occurrence.processor.guice.ApiClientConfiguration;
import org.gbif.occurrence.processor.interpreting.result.CoordinateResult;

import java.net.URI;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Ignore("requires live geo lookup webservice")
public class CoordinateInterpreterTest {

  static final ApiClientConfiguration cfg = new ApiClientConfiguration();;
  static final CoordinateInterpreter interpreter;
  static {
    cfg.url = URI.create("http://api.gbif-uat.org/v1/");
    interpreter = new CoordinateInterpreter(cfg.newApiClient());
  }

  private void assertCoordinate(ParseResult<CoordinateResult> result, double lat, double lng) {
    assertEquals(lat, result.getPayload().getLatitude().doubleValue(), 0.00001);
    assertEquals(lng, result.getPayload().getLongitude().doubleValue(), 0.00001);
  }

  @Test
  public void testCoordinate() {
    Double lat = 43.0;
    Double lng = 79.0;
    OccurrenceParseResult<CoordinateResult> result =
      interpreter.interpretCoordinate(lat.toString(), lng.toString(), null, null);

    assertCoordinate(result, lat, lng);
    assertTrue(result.getIssues().contains(OccurrenceIssue.COUNTRY_DERIVED_FROM_COORDINATES));
  }

  @Test
  public void testNull() {
    ParseResult<CoordinateResult> result = interpreter.interpretCoordinate(null, null, null, null);
    assertNotNull(result);
    assertEquals(ParseResult.STATUS.FAIL, result.getStatus());

    result = interpreter.interpretCoordinate(null, null, null, Country.CANADA);
    assertNotNull(result);
    assertEquals(ParseResult.STATUS.FAIL, result.getStatus());

    result = interpreter.interpretCoordinate("10.123", "40.567", null, null);
    assertNotNull(result);
    assertEquals(ParseResult.STATUS.SUCCESS, result.getStatus());

    assertCoordinate(result, 10.123, 40.567);
  }

  @Test
  public void testLookupCountryMatch() {
    Double lat = 43.65;
    Double lng = -79.40;
    Country country = Country.CANADA;
    OccurrenceParseResult<CoordinateResult> result =
      interpreter.interpretCoordinate(lat.toString(), lng.toString(), null, country);

    assertCoordinate(result, lat, lng);
    assertEquals(country, result.getPayload().getCountry());
    assertEquals(1, result.getIssues().size());
    assertTrue(result.getIssues().contains(OccurrenceIssue.GEODETIC_DATUM_ASSUMED_WGS84));
  }

  @Test
  public void testLookupCountryEmpty() {
    Double lat = -37.78;
    Double lng = 144.97;
    OccurrenceParseResult<CoordinateResult> result =
      interpreter.interpretCoordinate(lat.toString(), lng.toString(), null, null);
    assertCoordinate(result, lat, lng);
    assertEquals(Country.AUSTRALIA, result.getPayload().getCountry());
    assertTrue(result.getIssues().contains(OccurrenceIssue.COUNTRY_DERIVED_FROM_COORDINATES));
  }

  @Test
  public void testLookupCountryMisMatch() {
    Double lat = 55.68;
    Double lng = 12.57;
    Country country = Country.SWEDEN;
    OccurrenceParseResult<CoordinateResult> result =
      interpreter.interpretCoordinate(lat.toString(), lng.toString(), null, country);
    assertCoordinate(result, lat, lng);
    assertEquals(country, result.getPayload().getCountry());
    assertTrue(result.getIssues().contains(OccurrenceIssue.COUNTRY_COORDINATE_MISMATCH));
  }

  @Test
  public void testLookupIllegalCoord() {
    Double lat = 200d;
    Double lng = 200d;
    Country country = null; // "asdf"
    OccurrenceParseResult<CoordinateResult> result =
      interpreter.interpretCoordinate(lat.toString(), lng.toString(), null, country);
    assertNull(result.getPayload());
    assertFalse(result.isSuccessful());
    assertTrue(result.getIssues().contains(OccurrenceIssue.COORDINATE_OUT_OF_RANGE));
  }

  @Test
  public void testLookupLatLngReversed() {
    Double lat = 43.65;
    Double lng = 79.40;
    Country country = Country.CANADA;
    OccurrenceParseResult<CoordinateResult> result =
      interpreter.interpretCoordinate(lat.toString(), lng.toString(), null, country);

    System.out.println(result.getIssues());

    assertCoordinate(result, lat, -1*lng);
    assertEquals(country, result.getPayload().getCountry());
    assertTrue(result.getIssues().contains(OccurrenceIssue.PRESUMED_NEGATED_LONGITUDE));
  }

  @Test
  public void testLookupNulls() {
    String lat = null;
    String lng = null;
    Country country = null;
    OccurrenceParseResult<CoordinateResult> result = interpreter.interpretCoordinate(lat, lng, null, country);
    Assert.assertNotNull(result);
    assertNull(result.getPayload());
    assertFalse(result.isSuccessful());
    assertTrue(result.getIssues().isEmpty());
  }

  @Test
  public void testLookupOneNull() {
    String lat = "45";
    String lng = null;
    Country country = null;
    OccurrenceParseResult<CoordinateResult> result = interpreter.interpretCoordinate(lat, lng, null, country);
    Assert.assertNotNull(result);
    assertNull(result.getPayload());
    assertFalse(result.isSuccessful());
    assertTrue(result.getIssues().isEmpty());
  }

  @Test
  public void testRoundPrecision() {
    Double lat = 43.6512345;
    Double lng = -79.4056789;
    // expect max 5 decimals
    double roundedLat = Math.round(lat * Math.pow(10, 5)) / Math.pow(10, 5);
    double roundedLng = Math.round(lng * Math.pow(10, 5)) / Math.pow(10, 5);
    Country country = Country.CANADA;
    OccurrenceParseResult<CoordinateResult> result =
      interpreter.interpretCoordinate(lat.toString(), lng.toString(), null, country);

    assertCoordinate(result, roundedLat, roundedLng);
    assertEquals(country, result.getPayload().getCountry());

    assertEquals(2, result.getIssues().size());
    assertTrue(result.getIssues().contains(OccurrenceIssue.COORDINATE_ROUNDED));
    assertTrue(result.getIssues().contains(OccurrenceIssue.GEODETIC_DATUM_ASSUMED_WGS84));
  }

  @Test
  public void testNotNumbers() {
    ParseResult<CoordinateResult> result = interpreter.interpretCoordinate("asdf", "qwer", null, null);
    Assert.assertNotNull(result);
  }

  @Test
  public void testFuzzyCountry() {
    // Belfast is UK not IE
    Double lat = 54.597;
    Double lng = -5.93;
    Country country = Country.IRELAND;
    OccurrenceParseResult<CoordinateResult> result =
      interpreter.interpretCoordinate(lat.toString(), lng.toString(), null, country);
    assertEquals(Country.UNITED_KINGDOM, result.getPayload().getCountry());
    assertEquals(2, result.getIssues().size());
    assertTrue(result.getIssues().contains(OccurrenceIssue.COUNTRY_DERIVED_FROM_COORDINATES));
    assertTrue(result.getIssues().contains(OccurrenceIssue.GEODETIC_DATUM_ASSUMED_WGS84));

    // Isle of Man is IM not UK
    lat = 54.25;
    lng = -4.5;
    country = Country.UNITED_KINGDOM;
    result = interpreter.interpretCoordinate(lat.toString(), lng.toString(), null, country);
    assertEquals(Country.ISLE_OF_MAN, result.getPayload().getCountry());
    assertEquals(2, result.getIssues().size());
    assertTrue(result.getIssues().contains(OccurrenceIssue.COUNTRY_DERIVED_FROM_COORDINATES));
    assertTrue(result.getIssues().contains(OccurrenceIssue.GEODETIC_DATUM_ASSUMED_WGS84));
  }

  @Test
  public void testCountryLoopedupIssue() {
    // Belfast is UK
    Double lat = 54.597;
    Double lng = -5.93;
    OccurrenceParseResult<CoordinateResult> result = interpreter.interpretCoordinate(lat.toString(),
                                                                                     lng.toString(),
                                                                                     null,
                                                                                     null);
    assertEquals(Country.UNITED_KINGDOM, result.getPayload().getCountry());
    assertTrue(result.getIssues().contains(OccurrenceIssue.COUNTRY_DERIVED_FROM_COORDINATES));
  }
}
