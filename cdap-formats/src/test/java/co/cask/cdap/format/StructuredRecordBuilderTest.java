/*
 * Copyright © 2017-2018 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.format;

import co.cask.cdap.api.data.format.StructuredRecord;
import co.cask.cdap.api.data.format.UnexpectedFormatException;
import co.cask.cdap.api.data.schema.Schema;
import org.junit.Assert;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

/**
 * Tests conversion logic
 */
public class StructuredRecordBuilderTest {

  @Test
  public void testNullCheck() {
    Schema schema = Schema.recordOf("x", Schema.Field.of("x", Schema.of(Schema.Type.NULL)));
    Assert.assertNull(StructuredRecord.builder(schema).set("x", null).build().get("x"));
  }

  @Test
  public void testUnionNullCheck() {
    Schema schema = Schema.recordOf("x", Schema.Field.of("x", Schema.unionOf(
      Schema.of(Schema.Type.NULL),
      Schema.of(Schema.Type.INT),
      Schema.of(Schema.Type.LONG))));
    Assert.assertNull(StructuredRecord.builder(schema).set("x", null).build().get("x"));
    Assert.assertEquals(5, (int) StructuredRecord.builder(schema).set("x", 5).build().get("x"));
    Assert.assertEquals(5L, (long) StructuredRecord.builder(schema).set("x", 5L).build().get("x"));
  }

  @Test
  public void testDateConversion() {
    long ts = 0L;
    Date date = new Date(ts);
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    Schema schema = Schema.recordOf("x1",
                                    Schema.Field.of("ts", Schema.of(Schema.Type.LONG)),
                                    Schema.Field.of("date1", Schema.of(Schema.Type.STRING)),
                                    Schema.Field.of("date2", Schema.of(Schema.Type.STRING)));

    StructuredRecord expected = StructuredRecord.builder(schema)
      .set("ts", 0L)
      .set("date1", "1970-01-01T00:00:00 UTC")
      .set("date2", "1970-01-01")
      .build();

    StructuredRecord actual = StructuredRecord.builder(schema)
      .convertAndSet("ts", date)
      .convertAndSet("date1", date)
      .convertAndSet("date2", date, dateFormat)
      .build();

    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testDateSupport() {
    Schema schema = Schema.recordOf("test", Schema.Field.of("id", Schema.of(Schema.Type.INT)),
                                    Schema.Field.of("name", Schema.of(Schema.Type.STRING)),
                                    Schema.Field.of("date", Schema.of(Schema.LogicalType.DATE)));

    LocalDate expected = LocalDate.of(2002, 11, 18);
    StructuredRecord record = StructuredRecord.builder(schema)
      .set("id", 1)
      .set("name", "test")
      .setDate("date", expected).build();

    LocalDate actual = record.getDate("date");

    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testTimeSupport() {
    Schema schema = Schema.recordOf("test", Schema.Field.of("id", Schema.of(Schema.Type.INT)),
                                    Schema.Field.of("name", Schema.of(Schema.Type.STRING)),
                                    Schema.Field.of("time", Schema.of(Schema.LogicalType.TIME_MILLIS)));

    LocalTime expected = LocalTime.now();
    StructuredRecord record = StructuredRecord.builder(schema)
      .set("id", 1)
      .set("name", "test")
      .setTime("time", expected).build();

    LocalTime actual = record.getTime("time");

    Assert.assertEquals(expected, actual);

    schema = Schema.recordOf("test", Schema.Field.of("id", Schema.of(Schema.Type.INT)),
                             Schema.Field.of("name", Schema.of(Schema.Type.STRING)),
                             Schema.Field.of("time", Schema.of(Schema.LogicalType.TIME_MICROS)));
    record = StructuredRecord.builder(schema)
      .set("id", 1)
      .set("name", "test")
      .setTime("time", expected).build();

    actual = record.getTime("time");

    Assert.assertEquals(expected, actual);
  }

  @Test
  public void testTimestampSupport() {
    Schema schema = Schema.recordOf("test", Schema.Field.of("id", Schema.of(Schema.Type.INT)),
                                    Schema.Field.of("name", Schema.of(Schema.Type.STRING)),
                                    Schema.Field.of("timestamp", Schema.of(Schema.LogicalType.TIMESTAMP_MILLIS)));

    ZonedDateTime expectedMillis = ZonedDateTime.of(2018, 11, 11, 11, 11, 11, 123 * 1000 * 1000,
                                                    ZoneId.ofOffset("UTC", ZoneOffset.UTC));
    StructuredRecord record = StructuredRecord.builder(schema)
      .set("id", 1)
      .set("name", "test")
      .setTimestamp("timestamp", expectedMillis).build();

    ZonedDateTime actual = record.getTimestamp("timestamp", ZoneId.ofOffset("UTC", ZoneOffset.UTC));
    Assert.assertEquals(expectedMillis, actual);

    ZonedDateTime expectedMicros = ZonedDateTime.of(2018, 11, 11, 11, 11, 11, 123456 * 1000,
                                                    ZoneId.ofOffset("UTC", ZoneOffset.UTC));
    schema = Schema.recordOf("test", Schema.Field.of("id", Schema.of(Schema.Type.INT)),
                             Schema.Field.of("name", Schema.of(Schema.Type.STRING)),
                             Schema.Field.of("timestamp", Schema.of(Schema.LogicalType.TIMESTAMP_MICROS)));
    record = StructuredRecord.builder(schema)
      .set("id", 1)
      .set("name", "test")
      .setTimestamp("timestamp", expectedMicros).build();
    actual = record.getTimestamp("timestamp");
    Assert.assertEquals(expectedMicros, actual);
  }

  @Test
  public void testNullLogicalType() {
    Schema schema = Schema.recordOf("test", Schema.Field.of("id", Schema.of(Schema.Type.INT)),
                                    Schema.Field.of("name", Schema.of(Schema.Type.STRING)),
                                    Schema.Field.of("date", Schema.nullableOf(Schema.of(Schema.LogicalType.DATE))));

    StructuredRecord record = StructuredRecord.builder(schema)
      .set("id", 1)
      .set("name", "test")
      .setDate("date", null).build();

    LocalDate actual = record.getDate("date");

    Assert.assertNull(actual);
  }

  @Test
  public void testUnionSchemaType() {
    Schema schema = Schema.recordOf("x", Schema.Field.of("x", Schema.unionOf(
      Schema.unionOf(Schema.nullableOf(Schema.of(Schema.LogicalType.TIMESTAMP_MILLIS)),
                     Schema.of(Schema.Type.STRING)),
      Schema.of(Schema.Type.NULL),
      Schema.of(Schema.Type.INT),
      Schema.of(Schema.Type.LONG),
      Schema.of(Schema.LogicalType.DATE),
      Schema.nullableOf(Schema.of(Schema.LogicalType.TIME_MILLIS)))));

    LocalDate date = LocalDate.of(2018, 11, 11);
    ZonedDateTime zonedDateTime = ZonedDateTime.of(2018, 11, 11, 11, 11, 11, 0, ZoneId.ofOffset("UTC", ZoneOffset.UTC));
    LocalTime time = LocalTime.of(21, 1, 1);
    Assert.assertEquals(date, StructuredRecord.builder(schema).setDate("x", date).build().getDate("x"));
    StructuredRecord x = StructuredRecord.builder(schema).setTimestamp("x", zonedDateTime).build();
    Assert.assertEquals(zonedDateTime, x.getTimestamp("x"));
    Assert.assertEquals(time, StructuredRecord.builder(schema).setTime("x", time).build().getTime("x"));
    Assert.assertNull(StructuredRecord.builder(schema).setTime("x", null).build().getTime("x"));
  }

  @Test
  public void testGetNonExistentField() {
    Schema schema = Schema.recordOf("record", Schema.Field.of("x", Schema.of(Schema.LogicalType.DATE)));
    LocalDate date = LocalDate.of(2018, 11, 11);
    Assert.assertNull(StructuredRecord.builder(schema).setDate("x", date).build().getDate("y"));
  }

  @Test(expected = UnexpectedFormatException.class)
  public void testSetNonExistentField() {
    Schema schema = Schema.recordOf("record", Schema.Field.of("x", Schema.of(Schema.LogicalType.DATE)));
    LocalDate date = LocalDate.of(2018, 11, 11);
    StructuredRecord.builder(schema).setDate("y", date).build();
  }

  @Test(expected = UnexpectedFormatException.class)
  public void testInvalidNestedUnionSchemaType() {
    Schema schema = Schema.recordOf("x", Schema.Field.of("x", Schema.unionOf(
      Schema.unionOf(Schema.nullableOf(Schema.of(Schema.LogicalType.TIMESTAMP_MILLIS)),
                     Schema.of(Schema.Type.STRING)),
      Schema.of(Schema.Type.NULL),
      Schema.of(Schema.Type.INT),
      Schema.of(Schema.Type.LONG),
      Schema.nullableOf(Schema.of(Schema.LogicalType.TIME_MILLIS)))));

    LocalDate date = LocalDate.of(2018, 11, 11);
    Assert.assertEquals(date, StructuredRecord.builder(schema).setDate("x", date).build().getDate("x"));
  }

  @Test(expected = UnexpectedFormatException.class)
  public void testInvalidDateLogicalType() {
    Schema schema = Schema.recordOf("test", Schema.Field.of("id", Schema.of(Schema.Type.INT)),
                                    Schema.Field.of("name", Schema.of(Schema.Type.STRING)),
                                    Schema.Field.of("date", Schema.of(Schema.LogicalType.DATE)));

    LocalTime expected = LocalTime.now();
    StructuredRecord.builder(schema).set("id", 1).set("name", "test").setTime("date", expected).build();
  }

  @Test(expected = UnexpectedFormatException.class)
  public void testInvalidTimeLogicalType() {
    Schema schema = Schema.recordOf("test", Schema.Field.of("id", Schema.of(Schema.Type.INT)),
                                    Schema.Field.of("name", Schema.of(Schema.Type.STRING)),
                                    Schema.Field.of("time", Schema.of(Schema.LogicalType.TIME_MILLIS)));

    LocalDate expected = LocalDate.now();
    StructuredRecord.builder(schema).set("id", 1).set("name", "test").setDate("time", expected).build();
  }

  @Test(expected = UnexpectedFormatException.class)
  public void testInvalidTimestampLogicalType() {
    Schema schema = Schema.recordOf("test", Schema.Field.of("id", Schema.of(Schema.Type.INT)),
                                    Schema.Field.of("name", Schema.of(Schema.Type.STRING)),
                                    Schema.Field.of("timestamp", Schema.of(Schema.LogicalType.TIMESTAMP_MILLIS)));

    LocalDate expected = LocalDate.now();
    StructuredRecord.builder(schema).set("id", 1).set("name", "test").setDate("timestamp", expected).build();
  }
}
