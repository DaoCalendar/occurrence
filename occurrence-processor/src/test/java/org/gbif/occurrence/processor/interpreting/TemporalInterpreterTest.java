package org.gbif.occurrence.processor.interpreting;

import org.gbif.api.model.occurrence.Occurrence;
import org.gbif.api.model.occurrence.VerbatimOccurrence;
import org.gbif.api.vocabulary.OccurrenceIssue;
import org.gbif.common.parsers.core.ParseResult;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.occurrence.processor.interpreting.result.DateYearMonthDay;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.google.common.collect.Range;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TemporalInterpreterTest {

  @Test
  public void testAllDates() {
    VerbatimOccurrence v = new VerbatimOccurrence();
    v.setVerbatimField(DwcTerm.year, "1879");
    v.setVerbatimField(DwcTerm.month, "11 ");
    v.setVerbatimField(DwcTerm.day, "1");
    v.setVerbatimField(DwcTerm.eventDate, "1.11.1879");
    v.setVerbatimField(DwcTerm.dateIdentified, "2012-01-11");
    v.setVerbatimField(DcTerm.modified, "2014-01-11");

    Occurrence o = new Occurrence();
    TemporalInterpreter.interpretTemporal(v, o);

    assertDate("2014-01-11", o.getModified());
    assertDate("2012-01-11", o.getDateIdentified());
    assertDate("1879-11-01", o.getEventDate());
    assertEquals(1879, o.getYear().intValue());
    assertEquals(11, o.getMonth().intValue());
    assertEquals(1, o.getDay().intValue());

    assertEquals(0, o.getIssues().size());
  }

  @Test
  public void testLikelyYearRanges() {
    assertTrue(TemporalInterpreter.VALID_RECORDED_YEAR_RANGE.contains(1900));
    assertTrue(TemporalInterpreter.VALID_RECORDED_YEAR_RANGE.contains(1901));
    assertTrue(TemporalInterpreter.VALID_RECORDED_YEAR_RANGE.contains(2010));
    assertTrue(TemporalInterpreter.VALID_RECORDED_YEAR_RANGE.contains(2014));

    assertFalse(TemporalInterpreter.VALID_RECORDED_YEAR_RANGE.contains(Calendar.getInstance().get(Calendar.YEAR) + 1));
    assertFalse(TemporalInterpreter.VALID_RECORDED_YEAR_RANGE.contains(1580));
    assertFalse(TemporalInterpreter.VALID_RECORDED_YEAR_RANGE.contains(900));
    assertFalse(TemporalInterpreter.VALID_RECORDED_YEAR_RANGE.contains(90));
    assertFalse(TemporalInterpreter.VALID_RECORDED_YEAR_RANGE.contains(0));
    assertFalse(TemporalInterpreter.VALID_RECORDED_YEAR_RANGE.contains(-1900));

    assertFalse(TemporalInterpreter.VALID_RECORDED_YEAR_RANGE.contains(2100));
  }

  @Test
  public void testLikelyIdentified() {
    VerbatimOccurrence v = new VerbatimOccurrence();
    v.setVerbatimField(DwcTerm.year, "1879");
    v.setVerbatimField(DwcTerm.month, "11 ");
    v.setVerbatimField(DwcTerm.day, "1");
    v.setVerbatimField(DwcTerm.eventDate, "1.11.1879");
    v.setVerbatimField(DcTerm.modified, "2014-01-11");
    Occurrence o = new Occurrence();

    v.setVerbatimField(DwcTerm.dateIdentified, "1987-01-31");
    TemporalInterpreter.interpretTemporal(v, o);
    assertEquals(0, o.getIssues().size());

    v.setVerbatimField(DwcTerm.dateIdentified, "1787-03-27");
    TemporalInterpreter.interpretTemporal(v, o);
    assertEquals(0, o.getIssues().size());

    v.setVerbatimField(DwcTerm.dateIdentified, "2014-01-11");
    TemporalInterpreter.interpretTemporal(v, o);
    assertEquals(0, o.getIssues().size());

    Calendar cal = Calendar.getInstance();
    v.setVerbatimField(DwcTerm.dateIdentified, (cal.get(Calendar.YEAR)+1) + "-01-11");
    TemporalInterpreter.interpretTemporal(v, o);
    assertEquals(1, o.getIssues().size());
    assertEquals(OccurrenceIssue.IDENTIFIED_DATE_UNLIKELY, o.getIssues().iterator().next());

    v.setVerbatimField(DwcTerm.dateIdentified, "1599-01-11");
    TemporalInterpreter.interpretTemporal(v, o);
    assertEquals(1, o.getIssues().size());
    assertEquals(OccurrenceIssue.IDENTIFIED_DATE_UNLIKELY, o.getIssues().iterator().next());
  }

  @Test
  public void testLikelyModified() {
    VerbatimOccurrence v = new VerbatimOccurrence();
    v.setVerbatimField(DwcTerm.year, "1879");
    v.setVerbatimField(DwcTerm.month, "11 ");
    v.setVerbatimField(DwcTerm.day, "1");
    v.setVerbatimField(DwcTerm.eventDate, "1.11.1879");
    v.setVerbatimField(DwcTerm.dateIdentified, "1987-01-31");
    Occurrence o = new Occurrence();

    v.setVerbatimField(DcTerm.modified, "2014-01-11");
    TemporalInterpreter.interpretTemporal(v, o);
    assertEquals(0, o.getIssues().size());

    Calendar cal = Calendar.getInstance();
    v.setVerbatimField(DcTerm.modified, (cal.get(Calendar.YEAR) + 1) + "-01-11");
    TemporalInterpreter.interpretTemporal(v, o);
    assertEquals(1, o.getIssues().size());
    assertEquals(OccurrenceIssue.MODIFIED_DATE_UNLIKELY, o.getIssues().iterator().next());

    v.setVerbatimField(DcTerm.modified, "1969-12-31");
    TemporalInterpreter.interpretTemporal(v, o);
    assertEquals(1, o.getIssues().size());
    assertEquals(OccurrenceIssue.MODIFIED_DATE_UNLIKELY, o.getIssues().iterator().next());
  }

  @Test
  public void testLikelyRecorded() {
    VerbatimOccurrence v = new VerbatimOccurrence();
    Calendar cal = Calendar.getInstance();
    v.setVerbatimField(DwcTerm.eventDate, "24.12." + (cal.get(Calendar.YEAR) + 1));

    Occurrence o = new Occurrence();
    TemporalInterpreter.interpretTemporal(v, o);

    assertEquals(1, o.getIssues().size());
    assertEquals(OccurrenceIssue.RECORDED_DATE_UNLIKELY, o.getIssues().iterator().next());
  }

  @Test
  public void testGoodDate() {
    ParseResult<DateYearMonthDay> result = interpretRecordedDate("1984", "3", "22", null);
    assertResult(1984, 3, 22, "1984-03-22", result);
  }

  @Test
  public void testGoodOldDate() {
    ParseResult<DateYearMonthDay> result = interpretRecordedDate("1957", "3", "22", null);
    assertResult(1957, 3, 22, "1957-03-22", result);
  }

  @Test
  public void test0Month() {
    ParseResult<DateYearMonthDay> result = interpretRecordedDate("1984", "0", "22", null);
    assertResult(1984, null, 22, null, result);
  }

  @Test
  public void testOldYear() {
    ParseResult<DateYearMonthDay> result = interpretRecordedDate("1599", "3", "22", null);
    assertNullResult(result);
  }

  @Test
  public void testFutureYear() {
    ParseResult<DateYearMonthDay> result = interpretRecordedDate("2100", "3", "22", null);
    assertNullResult(result);
  }

  @Test
  public void testBadDay() {
    ParseResult<DateYearMonthDay> result = interpretRecordedDate("1984", "3", "32", null);
    assertResult(1984, 3, null, null, result);
  }

  @Test
  public void testStringGood() {
    ParseResult<DateYearMonthDay> result = interpretRecordedDate(null, null, null, "1984-03-22");
    assertResult(1984, 3, 22, "1984-03-22", result);
  }

  @Test
  public void testStringTimestamp() {
    ParseResult<DateYearMonthDay> result = interpretRecordedDate(null, null, null, "1984-03-22T00:00");
    assertResult(1984, 3, 22, "1984-03-22", result);
  }

  @Test
  public void testStringBad() {
    ParseResult<DateYearMonthDay> result = interpretRecordedDate(null, null, null, "22-17-1984");
    assertNullResult(result);
  }

  @Test
  public void testStringWins() {
    ParseResult<DateYearMonthDay> result = interpretRecordedDate("1984", "3", null, "1984-03-22");
    assertResult(1984, 3, 22, "1984-03-22", result);
  }

  @Test
  public void testStringLoses() {
    ParseResult<DateYearMonthDay> result = interpretRecordedDate("1984", "3", null, "22-17-1984");
    assertResult(1984, 3, null, null, result);
  }

  // these two tests demonstrate the problem from POR-2120
//  @Test
//  public void testOnlyYear() {
//    ParseResult<DateYearMonthDay> result = interpretRecordedDate("1984", null, null, null);
//    assertResult(1984, null, null, null, result);
//
//    result = interpretRecordedDate(null, null, null, "1984");
//    assertResult(1984, null, null, null, result);
//
//    result = interpretRecordedDate("1984", null, null, "1984");
//    assertResult(1984, null, null, null, result);
//  }
//
//  @Test
//  public void testYearWithZeros() {
//    ParseResult<DateYearMonthDay> result = interpretRecordedDate("1984", "0", "0", "1984");
//    System.out.println("Got result: " + result.getPayload().getDate());
//    assertResult(1984, null, null, null, result);
//
//    result = interpretRecordedDate(null, null, null, "1984");
//    System.out.println("Got result: " + result.getPayload().getDate());
//    assertResult(1984, null, null, null, result);
//
//    result = interpretRecordedDate("1984", "0", "0", null);
//    assertResult(1984, null, null, null, result);
//
//    result = interpretRecordedDate(null, null, null, "0-0-1984");
//    assertEquals(ParseResult.STATUS.FAIL, result.getStatus());
//    assertNull(result.getPayload());
//  }
//
//  @Test
//  public void testYearMonthNoDay() {
//    ParseResult<DateYearMonthDay> result = interpretRecordedDate("1984", "3", null, null);
//    System.out.println("Got result: " + result.getPayload().getDate());
//    assertResult(1984, 3, null, null, result);
//
//    result = interpretRecordedDate("1984", "3", null, "1984-03");
//    System.out.println("Got result: " + result.getPayload().getDate());
//    assertResult(1984, 3, null, null, result);
//
//    result = interpretRecordedDate(null, null, null, "1984-03");
//    System.out.println("Got result: " + result.getPayload().getDate());
//    assertResult(1984, 3, null, null, result);
//  }

  @Test
  public void testOnlyMonth() {
    ParseResult<DateYearMonthDay> result = interpretRecordedDate(null, "3", null, null);
    assertResult(null, 3, null, null, result);
  }

  @Test
  public void testOnlyDay() {
    ParseResult<DateYearMonthDay> result = interpretRecordedDate(null, null, "23", null);
    assertResult(null, null, 23, null, result);
  }

  /**
   * Tests that a date representing 'now' is interpreted with CONFIDENCE.DEFINITE even after TemporalInterpreter
   * was instantiated. See POR-2860.
   */
  @Test
  public void testNow() {

    // Makes sure the static content is loaded
    ParseResult<DateYearMonthDay> result = interpretEventDate(DateFormatUtils.ISO_DATETIME_FORMAT.format(Calendar.getInstance()));
    assertEquals(ParseResult.CONFIDENCE.DEFINITE, result.getConfidence());

    // Sorry for this Thread.sleep, we need to run the TemporalInterpreter at least 1 second later until
    // we refactor to inject a Calendar of we move to new Java 8 Date/Time API
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      fail(e.getMessage());
    }

    Calendar cal = Calendar.getInstance();
    result = interpretEventDate(DateFormatUtils.ISO_DATETIME_FORMAT.format(cal.getTime()));
    assertEquals(ParseResult.CONFIDENCE.DEFINITE, result.getConfidence());
  }

  @Test
  public void testAllNulls() {
    ParseResult<DateYearMonthDay> result = interpretRecordedDate(null, null, null, null);
    assertNullResult(result);
  }

  @Test
  public void testDateStrings() {
    assertValidDate("1999-07-19", "1999-07-19");
    assertValidDate("1999-07-19", "1999-07-19T00:00:00");
    assertValidDate("1999-07-19", "19-07-1999");
    assertValidDate("1999-07-19", "07-19-1999");
    assertValidDate("1999-09-07", "07-09-1999");
    assertValidDate("1999-06-07", "07-06-1999");
    assertValidDate("1999-07-19", "19/7/1999");
    assertValidDate("1999-07-19", "1999.7.19");
    assertValidDate("1999-07-19", "19.7.1999");
    assertValidDate("1999-07-19", "19.7.1999");
    assertValidDate("1999-07-19", "19990719");
    assertValidDate("2012-05-06", "20120506");
  }

  private void assertValidDate(String expected, String input) {
    assertDate(expected, interpretRecordedDate(null, null, null, input).getPayload());
  }

  /**
   * @param expected expected date in ISO yyyy-MM-dd format
   */
  private void assertDate(String expected, DateYearMonthDay result) {
    if (expected == null) {
      if (result != null) {
        assertNull(result.getDate());
      }
    } else {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      assertNotNull("Missing date", result.getDate());
      assertEquals(expected, sdf.format(result.getDate()));
    }
  }

  /**
   * @param expected expected date in ISO yyyy-MM-dd format
   */
  private void assertDate(String expected, Date result) {
    if (expected == null) {
      assertNull(result);
    } else {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
      assertNotNull("Missing date", result);
      assertEquals(expected, sdf.format(result));
    }
  }

  private void assertInts(Integer expected, Integer x) {
    if (expected == null) {
      assertNull(x);
    } else {
      assertEquals(expected, x);
    }
  }

  private void assertResult(Integer y, Integer m, Integer d, String date, ParseResult<DateYearMonthDay> result) {
    assertNotNull(result);
    assertInts(y, result.getPayload().getYear());
    assertInts(m, result.getPayload().getMonth());
    assertInts(d, result.getPayload().getDay());
    assertDate(date, result.getPayload());
  }

  private void assertNullResult(ParseResult<DateYearMonthDay> result) {
    assertNotNull(result);
    assertNull(result.getPayload());
  }

  private ParseResult<DateYearMonthDay> interpretRecordedDate(String y, String m, String d, String date) {
    VerbatimOccurrence v = new VerbatimOccurrence();
    v.setVerbatimField(DwcTerm.year, y);
    v.setVerbatimField(DwcTerm.month, m);
    v.setVerbatimField(DwcTerm.day, d);
    v.setVerbatimField(DwcTerm.eventDate, date);

    return TemporalInterpreter.interpretRecordedDate(v);
  }

  private ParseResult<DateYearMonthDay> interpretEventDate(String date) {
    VerbatimOccurrence v = new VerbatimOccurrence();
    v.setVerbatimField(DwcTerm.eventDate, date);

    return TemporalInterpreter.interpretRecordedDate(v);
  }

}
