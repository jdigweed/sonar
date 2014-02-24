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
package org.sonar.server.technicaldebt;

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.CoreProperties;
import org.sonar.api.config.Settings;
import org.sonar.api.technicaldebt.server.Characteristic;
import org.sonar.api.technicaldebt.server.internal.DefaultCharacteristic;
import org.sonar.api.utils.WorkDuration;
import org.sonar.api.utils.WorkDurationFactory;
import org.sonar.core.technicaldebt.DefaultTechnicalDebtManager;
import org.sonar.server.ui.WorkDurationFormatter;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class DebtServiceTest {

  private static final int HOURS_IN_DAY = 8;
  WorkDurationFormatter workDurationFormatter = mock(WorkDurationFormatter.class);
  DefaultTechnicalDebtManager finder = mock(DefaultTechnicalDebtManager.class);

  DebtService service;

  @Before
  public void setUp() throws Exception {
    Settings settings = new Settings();
    settings.setProperty(CoreProperties.HOURS_IN_DAY, HOURS_IN_DAY);
    service = new DebtService(workDurationFormatter, finder, new WorkDurationFactory(settings));
  }

  @Test
  public void format() {
    service.format(10L);
    verify(workDurationFormatter).format(eq(10L), eq(WorkDurationFormatter.Format.SHORT));
  }

  @Test
  public void to_work_duration() {
    assertThat(service.toWorkDuration(HOURS_IN_DAY * 60 * 60L)).isEqualTo(WorkDuration.createFromValueAndUnit(1, WorkDuration.UNIT.DAYS, HOURS_IN_DAY));
  }

  @Test
  public void find_root_characteristics() {
    List<Characteristic> rootCharacteristics = newArrayList();
    when(finder.findRootCharacteristics()).thenReturn(rootCharacteristics);
    assertThat(service.findRootCharacteristics()).isEqualTo(rootCharacteristics);
  }

  @Test
  public void find_requirement_by_rule_id() {
    service.findRequirementByRuleId(1);
    verify(finder).findRequirementByRuleId(1);
  }

  @Test
  public void find_characteristic() {
    Characteristic characteristic = new DefaultCharacteristic();
    when(finder.findCharacteristicById(1)).thenReturn(characteristic);
    assertThat(service.findCharacteristic(1)).isEqualTo(characteristic);
  }

}
