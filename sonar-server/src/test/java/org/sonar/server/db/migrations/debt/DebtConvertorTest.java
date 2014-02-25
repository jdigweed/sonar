/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2013 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.db.migrations.debt;

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.config.Settings;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;

public class DebtConvertorTest {

  static final int HOURS_IN_DAY = 8;

  static final Long ONE_MINUTE = 1L;
  static final Long ONE_HOUR_IN_MINUTES = ONE_MINUTE * 60;
  static final Long ONE_DAY_IN_MINUTES = ONE_HOUR_IN_MINUTES * HOURS_IN_DAY;

  Settings settings = new Settings();

  DebtConvertor convertor;

  @Before
  public void setUp() throws Exception {
    convertor = new DebtConvertor(settings);
  }

  @Test
  public void convert_from_long() throws Exception {
    settings.setProperty(DebtConvertor.HOURS_IN_DAY_PROPERTY, HOURS_IN_DAY);

    assertThat(convertor.createFromLong(1)).isEqualTo(ONE_MINUTE);
    assertThat(convertor.createFromLong(100)).isEqualTo(ONE_HOUR_IN_MINUTES);
    assertThat(convertor.createFromLong(10000)).isEqualTo(ONE_DAY_IN_MINUTES);
    assertThat(convertor.createFromLong(10101)).isEqualTo(ONE_DAY_IN_MINUTES + ONE_HOUR_IN_MINUTES + ONE_MINUTE);
  }

  @Test
  public void convert_from_long_use_default_value_for_hours_in_day_when_no_property() throws Exception {
    assertThat(convertor.createFromLong(1)).isEqualTo(ONE_MINUTE);
  }

  @Test
  public void fail_convert_from_long_on_bad_hours_in_day_property() throws Exception {
    try {
      settings.setProperty(DebtConvertor.HOURS_IN_DAY_PROPERTY, -2);
      convertor.createFromLong(1);
      fail();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  public void convert_from_days() throws Exception {
    settings.setProperty(DebtConvertor.HOURS_IN_DAY_PROPERTY, HOURS_IN_DAY);

    assertThat(convertor.createFromDays(1.0)).isEqualTo(ONE_DAY_IN_MINUTES);
    assertThat(convertor.createFromDays(0.1)).isEqualTo(48L);

    // Should be 4.8 but as it's a long it's truncated after comma
    assertThat(convertor.createFromDays(0.01)).isEqualTo(4L);
  }

}
