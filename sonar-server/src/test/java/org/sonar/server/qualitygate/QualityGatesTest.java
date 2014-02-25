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
package org.sonar.server.qualitygate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.Metric.ValueType;
import org.sonar.api.measures.MetricFinder;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.core.properties.PropertiesDao;
import org.sonar.core.properties.PropertyDto;
import org.sonar.core.qualitygate.db.QualityGateConditionDao;
import org.sonar.core.qualitygate.db.QualityGateConditionDto;
import org.sonar.core.qualitygate.db.QualityGateDao;
import org.sonar.core.qualitygate.db.QualityGateDto;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.user.MockUserSession;
import org.sonar.server.user.UserSession;
import org.sonar.server.user.UserSessionTestUtils;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class QualityGatesTest {

  @Mock
  private QualityGateDao dao;

  @Mock
  private QualityGateConditionDao conditionDao;

  @Mock
  private MetricFinder metricFinder;

  @Mock
  private PropertiesDao propertiesDao;

  private QualityGates qGates;

  UserSession authorizedUserSession = MockUserSession.create().setLogin("gaudol").setName("Olivier").setGlobalPermissions(GlobalPermissions.QUALITY_PROFILE_ADMIN);
  UserSession unauthenticatedUserSession = MockUserSession.create();
  UserSession unauthorizedUserSession = MockUserSession.create().setLogin("polop").setName("Polop");

  @Before
  public void initialize() {
    qGates = new QualityGates(dao, conditionDao, metricFinder, propertiesDao);
    UserSessionTestUtils.setUserSession(authorizedUserSession);
  }

  @Test
  public void should_list_qgates() throws Exception {
    List<QualityGateDto> allQgates = Lists.newArrayList(new QualityGateDto().setName("Gate One"), new QualityGateDto().setName("Gate Two"));
    when(dao.selectAll()).thenReturn(allQgates);
    assertThat(qGates.list()).isEqualTo(allQgates);
  }

  @Test
  public void should_create_qgate() throws Exception {
    String name = "SG-1";
    QualityGateDto sg1 = qGates.create(name);
    assertThat(sg1.getName()).isEqualTo(name);
    verify(dao).selectByName(name);
    verify(dao).insert(sg1);
  }

  @Test(expected = UnauthorizedException.class)
  public void should_fail_create_on_anonymous() throws Exception {
    UserSessionTestUtils.setUserSession(unauthenticatedUserSession);
    qGates.create("polop");
  }

  @Test(expected = ForbiddenException.class)
  public void should_fail_create_on_missing_permission() throws Exception {
    UserSessionTestUtils.setUserSession(unauthorizedUserSession);
    qGates.create("polop");
  }

  @Test(expected = BadRequestException.class)
  public void should_fail_create_on_empty_name() throws Exception {
    qGates.create("");
  }

  @Test(expected = BadRequestException.class)
  public void should_fail_create_on_duplicate_name() throws Exception {
    String name = "SG-1";
    when(dao.selectByName(name)).thenReturn(new QualityGateDto().setName(name).setId(42L));
    qGates.create(name);
  }

  @Test
  public void should_get_qgate() throws Exception {
    long id = 42L;
    final String name = "Golden";
    QualityGateDto existing = new QualityGateDto().setId(id).setName(name);
    when(dao.selectById(id)).thenReturn(existing);
    assertThat(qGates.get(id)).isEqualTo(existing);
    verify(dao).selectById(id);
  }

  @Test
  public void should_rename_qgate() throws Exception {
    long id = 42L;
    String name = "SG-1";
    QualityGateDto existing = new QualityGateDto().setId(id).setName("Golden");
    when(dao.selectById(id)).thenReturn(existing);
    QualityGateDto sg1 = qGates.rename(id, name);
    assertThat(sg1.getName()).isEqualTo(name);
    verify(dao).selectById(id);
    verify(dao).selectByName(name);
    verify(dao).update(sg1);
  }

  @Test
  public void should_allow_rename_with_same_name() throws Exception {
    long id = 42L;
    String name = "SG-1";
    QualityGateDto existing = new QualityGateDto().setId(id).setName(name);
    when(dao.selectById(id)).thenReturn(existing);
    QualityGateDto sg1 = qGates.rename(id, name);
    assertThat(sg1.getName()).isEqualTo(name);
    verify(dao).selectById(id);
    verify(dao).selectByName(name);
    verify(dao).update(sg1);
  }

  @Test(expected = NotFoundException.class)
  public void should_fail_rename_on_inexistent_qgate() throws Exception {
    qGates.rename(42L, "Unknown");
  }

  @Test(expected = BadRequestException.class)
  public void should_fail_rename_on_duplicate_name() throws Exception {
    long id = 42L;
    String name = "SG-1";
    QualityGateDto existing = new QualityGateDto().setId(id).setName("Golden");
    when(dao.selectById(id)).thenReturn(existing);
    when(dao.selectByName(name)).thenReturn(new QualityGateDto().setId(666L).setName(name));
    qGates.rename(id, name);
  }

  @Test
  public void should_select_default_qgate() throws Exception {
    long defaultId = 42L;
    String defaultName = "Default Name";
    when(dao.selectById(defaultId)).thenReturn(new QualityGateDto().setId(defaultId).setName(defaultName ));
    qGates.setDefault(defaultId);
    verify(dao).selectById(defaultId);
    ArgumentCaptor<PropertyDto> propertyCaptor = ArgumentCaptor.forClass(PropertyDto.class);
    verify(propertiesDao).setProperty(propertyCaptor.capture());
    assertThat(propertyCaptor.getValue().getKey()).isEqualTo("sonar.qualitygate");
    assertThat(propertyCaptor.getValue().getValue()).isEqualTo(defaultName);
  }

  @Test
  public void should_unset_default_qgate() throws Exception {
    qGates.setDefault(null);
    verify(propertiesDao).deleteGlobalProperty("sonar.qualitygate");
  }

  @Test
  public void should_delete_qgate() throws Exception {
    long idToDelete = 42L;
    QualityGateDto toDelete = new QualityGateDto().setId(idToDelete).setName("To Delete");
    when(dao.selectById(idToDelete)).thenReturn(toDelete);
    qGates.delete(idToDelete);
    verify(dao).selectById(idToDelete);
    verify(dao).delete(toDelete);
  }

  @Test
  public void should_delete_qgate_if_non_default() throws Exception {
    long idToDelete = 42L;
    QualityGateDto toDelete = new QualityGateDto().setId(idToDelete).setName("To Delete");
    when(dao.selectById(idToDelete)).thenReturn(toDelete);
    when(propertiesDao.selectGlobalProperty("sonar.qualitygate")).thenReturn(new PropertyDto().setValue("Other Qgate"));
    qGates.delete(idToDelete);
    verify(dao).selectById(idToDelete);
    verify(dao).delete(toDelete);
  }

  @Test(expected = BadRequestException.class)
  public void should_not_delete_qgate_if_default() throws Exception {
    long idToDelete = 42L;
    String name = "To Delete";
    QualityGateDto toDelete = new QualityGateDto().setId(idToDelete).setName(name);
    when(dao.selectById(idToDelete)).thenReturn(toDelete);
    when(propertiesDao.selectGlobalProperty("sonar.qualitygate")).thenReturn(new PropertyDto().setValue(name));
    qGates.delete(idToDelete);
  }

  @Test
  public void should_return_default_qgate_if_set() throws Exception {
    String defaultName = "Sonar way";
    when(propertiesDao.selectGlobalProperty("sonar.qualitygate")).thenReturn(new PropertyDto().setValue(defaultName));
    QualityGateDto defaultQgate = new QualityGateDto().setId(42L).setName(defaultName);
    when(dao.selectByName(defaultName)).thenReturn(defaultQgate);
    assertThat(qGates.getDefault()).isEqualTo(defaultQgate);
  }

  @Test
  public void should_return_null_default_qgate_if_unset() throws Exception {
    when(propertiesDao.selectGlobalProperty("sonar.qualitygate")).thenReturn(new PropertyDto().setValue(""));
    assertThat(qGates.getDefault()).isNull();
  }

  @Test
  public void should_create_warning_condition_without_period() {
    long qGateId = 42L;
    String metricKey = "coverage";
    String operator = "LT";
    String warningThreshold = "90";
    when(dao.selectById(qGateId)).thenReturn(new QualityGateDto().setId(qGateId));
    Integer metricId = 10;
    Metric coverage = Mockito.spy(CoreMetrics.COVERAGE);
    when(coverage.getId()).thenReturn(metricId);
    when(metricFinder.findByKey(metricKey)).thenReturn(coverage);

    QualityGateConditionDto newCondition = qGates.createCondition(qGateId, metricKey, operator, warningThreshold, null, null);
    assertThat(newCondition.getQualityGateId()).isEqualTo(qGateId);
    assertThat(newCondition.getMetricId()).isEqualTo(metricId);
    assertThat(newCondition.getOperator()).isEqualTo(operator);
    assertThat(newCondition.getWarningThreshold()).isEqualTo(warningThreshold);
    assertThat(newCondition.getErrorThreshold()).isNull();
    assertThat(newCondition.getPeriod()).isNull();
    verify(conditionDao).insert(newCondition);
  }

  @Test
  public void should_create_error_condition_with_period() {
    long qGateId = 42L;
    String metricKey = "new_coverage";
    String operator = "LT";
    String errorThreshold = "80";
    when(dao.selectById(qGateId)).thenReturn(new QualityGateDto().setId(qGateId));
    Integer metricId = 10;
    Metric newCoverage = Mockito.spy(CoreMetrics.NEW_COVERAGE);
    when(newCoverage.getId()).thenReturn(metricId);
    when(metricFinder.findByKey(metricKey)).thenReturn(newCoverage);
    int period = 2;

    QualityGateConditionDto newCondition = qGates.createCondition(qGateId, metricKey, operator, null, errorThreshold, period);
    assertThat(newCondition.getQualityGateId()).isEqualTo(qGateId);
    assertThat(newCondition.getMetricId()).isEqualTo(metricId);
    assertThat(newCondition.getMetricKey()).isEqualTo(metricKey);
    assertThat(newCondition.getOperator()).isEqualTo(operator);
    assertThat(newCondition.getWarningThreshold()).isNull();
    assertThat(newCondition.getErrorThreshold()).isEqualTo(errorThreshold);
    assertThat(newCondition.getPeriod()).isEqualTo(period);
    verify(conditionDao).insert(newCondition);
  }

  @Test(expected = NotFoundException.class)
  public void should_fail_create_condition_on_missing_metric() {
    long qGateId = 42L;
    when(dao.selectById(qGateId)).thenReturn(new QualityGateDto().setId(qGateId));
    qGates.createCondition(qGateId, "new_coverage", "LT", null, "80", 2);
  }

  @Test(expected = BadRequestException.class)
  public void should_fail_create_condition_on_alert_metric() {
    long qGateId = 42L;
    when(metricFinder.findByKey(anyString())).thenReturn(CoreMetrics.ALERT_STATUS);
    when(dao.selectById(qGateId)).thenReturn(new QualityGateDto().setId(qGateId));
    qGates.createCondition(qGateId, "alert_status", "EQ", null, "80", 2);
  }

  @Test(expected = BadRequestException.class)
  public void should_fail_create_condition_on_non_data_metric() {
    long qGateId = 42L;
    final Metric metric = mock(Metric.class);
    when(metric.getType()).thenReturn(ValueType.DATA);
    when(metricFinder.findByKey(anyString())).thenReturn(metric);
    when(dao.selectById(qGateId)).thenReturn(new QualityGateDto().setId(qGateId));
    qGates.createCondition(qGateId, "alert_status", "LT", null, "80", 2);
  }

  @Test(expected = BadRequestException.class)
  public void should_fail_create_condition_on_hidden_metric() {
    long qGateId = 42L;
    final Metric metric = mock(Metric.class);
    when(metric.isHidden()).thenReturn(true);
    when(metric.getType()).thenReturn(ValueType.INT);
    when(metricFinder.findByKey(anyString())).thenReturn(metric);
    when(dao.selectById(qGateId)).thenReturn(new QualityGateDto().setId(qGateId));
    qGates.createCondition(qGateId, "alert_status", "LT", null, "80", 2);
  }

  @Test(expected = BadRequestException.class)
  public void should_fail_create_condition_on_rating_metric() {
    long qGateId = 42L;
    final Metric metric = mock(Metric.class);
    when(metric.getType()).thenReturn(ValueType.RATING);
    when(metricFinder.findByKey(anyString())).thenReturn(metric);
    when(dao.selectById(qGateId)).thenReturn(new QualityGateDto().setId(qGateId));
    qGates.createCondition(qGateId, "alert_status", "LT", null, "80", 2);
  }

  @Test(expected = BadRequestException.class)
  public void should_fail_create_condition_on_unallowed_operator() {
    long qGateId = 42L;
    final Metric metric = mock(Metric.class);
    when(metric.getType()).thenReturn(ValueType.BOOL);
    when(metricFinder.findByKey(anyString())).thenReturn(metric);
    when(dao.selectById(qGateId)).thenReturn(new QualityGateDto().setId(qGateId));
    qGates.createCondition(qGateId, "alert_status", "LT", null, "80", 2);
  }

  @Test(expected = BadRequestException.class)
  public void should_fail_create_condition_on_missing_thresholds() {
    long qGateId = 42L;
    final Metric metric = mock(Metric.class);
    when(metric.getType()).thenReturn(ValueType.BOOL);
    when(metricFinder.findByKey(anyString())).thenReturn(metric);
    when(dao.selectById(qGateId)).thenReturn(new QualityGateDto().setId(qGateId));
    qGates.createCondition(qGateId, "alert_status", "EQ", null, null, 2);
  }

  @Test(expected = BadRequestException.class)
  public void should_fail_create_condition_on_missing_period() {
    long qGateId = 42L;
    final Metric metric = mock(Metric.class);
    when(metric.getKey()).thenReturn("new_coverage");
    when(metric.getType()).thenReturn(ValueType.BOOL);
    when(metricFinder.findByKey(anyString())).thenReturn(metric);
    when(dao.selectById(qGateId)).thenReturn(new QualityGateDto().setId(qGateId));
    qGates.createCondition(qGateId, "alert_status", "EQ", null, "90", null);
  }

  @Test(expected = BadRequestException.class)
  public void should_fail_create_condition_on_invalid_period() {
    long qGateId = 42L;
    final Metric metric = mock(Metric.class);
    when(metric.getKey()).thenReturn("new_coverage");
    when(metric.getType()).thenReturn(ValueType.BOOL);
    when(metricFinder.findByKey(anyString())).thenReturn(metric);
    when(dao.selectById(qGateId)).thenReturn(new QualityGateDto().setId(qGateId));
    qGates.createCondition(qGateId, "alert_status", "EQ", null, "90", 4);
  }

  @Test
  public void should_update_condition() {
    long condId = 42L;
    String metricKey = "new_coverage";
    String operator = "LT";
    String errorThreshold = "80";
    final QualityGateConditionDto condition = new QualityGateConditionDto().setId(condId)
      .setMetricId(666L).setOperator("GT").setWarningThreshold("123");
    when(conditionDao.selectById(condId)).thenReturn(condition);
    Integer metricId = 10;
    Metric newCoverage = Mockito.spy(CoreMetrics.NEW_COVERAGE);
    when(newCoverage.getId()).thenReturn(metricId);
    when(metricFinder.findByKey(metricKey)).thenReturn(newCoverage);
    int period = 2;

    assertThat(qGates.updateCondition(condId, metricKey, operator, null, errorThreshold, period)).isEqualTo(condition);
    assertThat(condition.getId()).isEqualTo(condId);
    assertThat(condition.getMetricId()).isEqualTo(metricId);
    assertThat(condition.getMetricKey()).isEqualTo(metricKey);
    assertThat(condition.getOperator()).isEqualTo(operator);
    assertThat(condition.getWarningThreshold()).isNull();
    assertThat(condition.getErrorThreshold()).isEqualTo(errorThreshold);
    assertThat(condition.getPeriod()).isEqualTo(period);
    verify(conditionDao).update(condition);
  }

  @Test
  public void should_list_conditions() throws Exception {
    long qGateId = 42L;
    long metric1Id = 1L;
    String metric1Key = "polop";
    long metric2Id = 2L;
    String metric2Key = "palap";
    QualityGateConditionDto cond1 = new QualityGateConditionDto().setMetricId(metric1Id);
    QualityGateConditionDto cond2 = new QualityGateConditionDto().setMetricId(metric2Id);
    Collection<QualityGateConditionDto> conditions = ImmutableList.of(cond1, cond2);
    when(conditionDao.selectForQualityGate(qGateId)).thenReturn(conditions );
    Metric metric1 = mock(Metric.class);
    when(metric1.getKey()).thenReturn(metric1Key);
    when(metricFinder.findById((int) metric1Id)).thenReturn(metric1);
    Metric metric2 = mock(Metric.class);
    when(metric2.getKey()).thenReturn(metric2Key);
    when(metricFinder.findById((int) metric2Id)).thenReturn(metric2);
    assertThat(qGates.listConditions(qGateId)).isEqualTo(conditions);
    Iterator<QualityGateConditionDto> iterator = conditions.iterator();
    assertThat(iterator.next().getMetricKey()).isEqualTo(metric1Key);
    assertThat(iterator.next().getMetricKey()).isEqualTo(metric2Key);
  }

  @Test
  public void should_delete_condition() throws Exception {
    long idToDelete = 42L;
    QualityGateConditionDto toDelete = new QualityGateConditionDto().setId(idToDelete);
    when(conditionDao.selectById(idToDelete)).thenReturn(toDelete);
    qGates.deleteCondition(idToDelete);
    verify(conditionDao).selectById(idToDelete);
    verify(conditionDao).delete(toDelete);
  }

  @Test(expected = NotFoundException.class)
  public void should_fail_delete_condition() throws Exception {
    qGates.deleteCondition(42L);
  }
}
