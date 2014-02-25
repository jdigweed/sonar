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
package org.sonar.server.platform;

import org.apache.commons.configuration.BaseConfiguration;
import org.slf4j.LoggerFactory;
import org.sonar.api.config.EmailSettings;
import org.sonar.api.issue.action.Actions;
import org.sonar.api.platform.ComponentContainer;
import org.sonar.api.platform.Server;
import org.sonar.api.profiles.AnnotationProfileParser;
import org.sonar.api.profiles.XMLProfileParser;
import org.sonar.api.profiles.XMLProfileSerializer;
import org.sonar.api.resources.Languages;
import org.sonar.api.resources.ResourceTypes;
import org.sonar.api.rules.AnnotationRuleParser;
import org.sonar.api.rules.XMLRuleParser;
import org.sonar.api.utils.HttpDownloader;
import org.sonar.api.utils.TimeProfiler;
import org.sonar.api.utils.UriReader;
import org.sonar.api.utils.WorkDurationFactory;
import org.sonar.api.utils.internal.TempFolderCleaner;
import org.sonar.core.component.SnapshotPerspectives;
import org.sonar.core.config.Logback;
import org.sonar.core.i18n.DefaultI18n;
import org.sonar.core.i18n.GwtI18n;
import org.sonar.core.i18n.RuleI18nManager;
import org.sonar.core.issue.IssueFilterSerializer;
import org.sonar.core.issue.IssueNotifications;
import org.sonar.core.issue.IssueUpdater;
import org.sonar.core.issue.workflow.FunctionExecutor;
import org.sonar.core.issue.workflow.IssueWorkflow;
import org.sonar.core.measure.MeasureFilterEngine;
import org.sonar.core.measure.MeasureFilterExecutor;
import org.sonar.core.measure.MeasureFilterFactory;
import org.sonar.core.metric.DefaultMetricFinder;
import org.sonar.core.notification.DefaultNotificationManager;
import org.sonar.core.permission.PermissionFacade;
import org.sonar.core.persistence.*;
import org.sonar.core.preview.PreviewCache;
import org.sonar.core.profiling.Profiling;
import org.sonar.core.purge.PurgeProfiler;
import org.sonar.core.qualitygate.db.QualityGateDao;
import org.sonar.core.resource.DefaultResourcePermissions;
import org.sonar.core.rule.DefaultRuleFinder;
import org.sonar.core.technicaldebt.DefaultTechnicalDebtManager;
import org.sonar.core.technicaldebt.TechnicalDebtModelRepository;
import org.sonar.core.technicaldebt.TechnicalDebtModelSynchronizer;
import org.sonar.core.technicaldebt.TechnicalDebtXMLImporter;
import org.sonar.core.test.TestPlanPerspectiveLoader;
import org.sonar.core.test.TestablePerspectiveLoader;
import org.sonar.core.timemachine.Periods;
import org.sonar.core.user.DefaultUserFinder;
import org.sonar.core.user.HibernateUserFinder;
import org.sonar.jpa.dao.MeasuresDao;
import org.sonar.jpa.dao.ProfilesDao;
import org.sonar.jpa.dao.RulesDao;
import org.sonar.jpa.session.DatabaseSessionFactory;
import org.sonar.jpa.session.DatabaseSessionProvider;
import org.sonar.jpa.session.DefaultDatabaseConnector;
import org.sonar.jpa.session.ThreadLocalDatabaseSessionFactory;
import org.sonar.server.charts.ChartFactory;
import org.sonar.server.component.DefaultComponentFinder;
import org.sonar.server.component.DefaultRubyComponentService;
import org.sonar.server.db.EmbeddedDatabaseFactory;
import org.sonar.server.db.migrations.DatabaseMigration;
import org.sonar.server.db.migrations.DatabaseMigrations;
import org.sonar.server.db.migrations.DatabaseMigrator;
import org.sonar.server.es.ESIndex;
import org.sonar.server.es.ESNode;
import org.sonar.server.issue.*;
import org.sonar.server.issue.filter.IssueFilterService;
import org.sonar.server.issue.filter.IssueFilterWs;
import org.sonar.server.issue.ws.IssueShowWsHandler;
import org.sonar.server.issue.ws.IssuesWs;
import org.sonar.server.notifications.NotificationCenter;
import org.sonar.server.notifications.NotificationService;
import org.sonar.server.permission.InternalPermissionService;
import org.sonar.server.permission.InternalPermissionTemplateService;
import org.sonar.server.permission.PermissionFinder;
import org.sonar.server.plugins.*;
import org.sonar.server.qualitygate.QualityGates;
import org.sonar.server.qualitygate.ws.QualityGatesWs;
import org.sonar.server.qualityprofile.*;
import org.sonar.server.rule.*;
import org.sonar.server.rule.ws.*;
import org.sonar.server.source.CodeColorizers;
import org.sonar.server.source.DeprecatedSourceDecorator;
import org.sonar.server.source.HtmlSourceDecorator;
import org.sonar.server.source.SourceService;
import org.sonar.server.source.ws.SourcesShowWsHandler;
import org.sonar.server.source.ws.SourcesWs;
import org.sonar.server.startup.*;
import org.sonar.server.technicaldebt.DebtService;
import org.sonar.server.text.MacroInterpreter;
import org.sonar.server.text.RubyTextService;
import org.sonar.server.ui.*;
import org.sonar.server.user.*;
import org.sonar.server.util.*;
import org.sonar.server.ws.ListingWs;
import org.sonar.server.ws.WebServiceEngine;

import javax.annotation.Nullable;
import javax.servlet.ServletContext;

/**
 * @since 2.2
 */
public final class Platform {

  private static final Platform INSTANCE = new Platform();
  // level 1 : only database connectors
  private ComponentContainer rootContainer;
  // level 2 : level 1 + core components
  private ComponentContainer coreContainer;
  // level 3 : level 2 + plugin extensions + core components that depend on plugin extensions
  private ComponentContainer servicesContainer;
  private boolean connected = false;
  private boolean started = false;

  private Platform() {
  }

  public static Platform getInstance() {
    return INSTANCE;
  }

  /**
   * shortcut for ruby code
   */
  public static Server getServer() {
    return (Server) getInstance().getComponent(Server.class);
  }

  /**
   * Used by ruby code
   */
  @Nullable
  public static <T> T component(Class<T> type) {
    if (INSTANCE.started) {
      return INSTANCE.getContainer().getComponentByType(type);
    }
    return null;
  }

  public void init(ServletContext servletContext) {
    if (!connected) {
      startDatabaseConnectors(servletContext);
      connected = true;
    }
  }

  public void start() {
    if (!started && getDatabaseStatus() == DatabaseVersion.Status.UP_TO_DATE) {
      TimeProfiler profiler = new TimeProfiler().start("Start components");
      startCoreComponents();
      startServiceComponents();
      executeStartupTasks();
      started = true;
      profiler.stop();
    }
  }

  private void startDatabaseConnectors(ServletContext servletContext) {
    rootContainer = new ComponentContainer();
    rootContainer.addSingleton(servletContext);
    rootContainer.addSingleton(new BaseConfiguration());
    rootContainer.addSingleton(ServerSettings.class);
    rootContainer.addSingleton(ServerImpl.class);
    rootContainer.addSingleton(Logback.class);
    rootContainer.addSingleton(Profiling.class);
    rootContainer.addSingleton(JRubyProfiling.class);
    rootContainer.addSingleton(EmbeddedDatabaseFactory.class);
    rootContainer.addSingleton(DefaultDatabase.class);
    rootContainer.addSingleton(MyBatis.class);
    rootContainer.addSingleton(DefaultServerUpgradeStatus.class);
    rootContainer.addSingleton(DatabaseServerCompatibility.class);
    for (Class<? extends DatabaseMigration> migrationClass : DatabaseMigrations.CLASSES) {
      rootContainer.addSingleton(migrationClass);
    }
    rootContainer.addSingleton(DatabaseMigrator.class);
    rootContainer.addSingleton(DatabaseVersion.class);
    for (Class daoClass : DaoUtils.getDaoClasses()) {
      rootContainer.addSingleton(daoClass);
    }
    rootContainer.addSingleton(PurgeProfiler.class);
    rootContainer.addSingleton(PluginDeployer.class);
    rootContainer.addSingleton(ServerPluginInstaller.class);
    rootContainer.addSingleton(InstalledPluginReferentialFactory.class);
    rootContainer.addSingleton(DefaultServerPluginRepository.class);

    rootContainer.addSingleton(DefaultServerFileSystem.class);
    rootContainer.addSingleton(ApplicationDeployer.class);
    rootContainer.addSingleton(JRubyI18n.class);
    rootContainer.addSingleton(DefaultI18n.class);
    rootContainer.addSingleton(RuleI18nManager.class);
    rootContainer.addSingleton(GwtI18n.class);
    rootContainer.addSingleton(PreviewDatabaseFactory.class);
    rootContainer.addSingleton(SemaphoreUpdater.class);
    rootContainer.addSingleton(SemaphoresImpl.class);
    rootContainer.addPicoAdapter(new TempFolderProvider());
    rootContainer.addSingleton(TempFolderCleaner.class);
    rootContainer.startComponents();
  }

  private DatabaseVersion.Status getDatabaseStatus() {
    DatabaseVersion version = getContainer().getComponentByType(DatabaseVersion.class);
    return version.getStatus();
  }

  private void startCoreComponents() {
    coreContainer = rootContainer.createChild();
    coreContainer.addSingleton(PersistentSettings.class);
    coreContainer.addSingleton(DefaultDatabaseConnector.class);
    coreContainer.addSingleton(ServerExtensionInstaller.class);
    coreContainer.addSingleton(ThreadLocalDatabaseSessionFactory.class);
    coreContainer.addPicoAdapter(new DatabaseSessionProvider());
    coreContainer.addSingleton(ServerMetadataPersister.class);
    coreContainer.addSingleton(ESNode.class);
    coreContainer.startComponents();
  }

  /**
   * plugin extensions + all the components that depend on plugin extensions
   */
  private void startServiceComponents() {
    servicesContainer = coreContainer.createChild();

    servicesContainer.addSingleton(ESIndex.class);
    servicesContainer.addSingleton(HttpDownloader.class);
    servicesContainer.addSingleton(UriReader.class);
    servicesContainer.addSingleton(UpdateCenterClient.class);
    servicesContainer.addSingleton(UpdateCenterMatrixFactory.class);
    servicesContainer.addSingleton(PluginDownloader.class);
    servicesContainer.addSingleton(ServerIdGenerator.class);
    // depends on plugins
    servicesContainer.addSingleton(ChartFactory.class);
    servicesContainer.addSingleton(Languages.class);
    servicesContainer.addSingleton(Views.class);
    servicesContainer.addSingleton(CodeColorizers.class);
    servicesContainer.addComponent(RulesDao.class, false);
    servicesContainer.addComponent(MeasuresDao.class, false);
    servicesContainer.addComponent(ProfilesDao.class, false);
    servicesContainer.addComponent(ProfilesManager.class, false);
    servicesContainer.addSingleton(SecurityRealmFactory.class);
    servicesContainer.addSingleton(ServerLifecycleNotifier.class);
    servicesContainer.addSingleton(AnnotationProfileParser.class);
    servicesContainer.addSingleton(XMLProfileParser.class);
    servicesContainer.addSingleton(XMLProfileSerializer.class);
    servicesContainer.addSingleton(AnnotationRuleParser.class);
    servicesContainer.addSingleton(XMLRuleParser.class);
    servicesContainer.addSingleton(DefaultRuleFinder.class);
    servicesContainer.addSingleton(DefaultMetricFinder.class);
    servicesContainer.addSingleton(ResourceTypes.class);
    servicesContainer.addSingleton(SettingsChangeNotifier.class);
    servicesContainer.addSingleton(PageDecorations.class);
    servicesContainer.addSingleton(MeasureFilterFactory.class);
    servicesContainer.addSingleton(MeasureFilterExecutor.class);
    servicesContainer.addSingleton(MeasureFilterEngine.class);
    servicesContainer.addSingleton(PreviewCache.class);
    servicesContainer.addSingleton(DefaultResourcePermissions.class);
    servicesContainer.addSingleton(Periods.class);

    // web services
    servicesContainer.addSingleton(WebServiceEngine.class);
    servicesContainer.addSingleton(ListingWs.class);

    // quality profiles
    servicesContainer.addSingleton(QProfileRuleLookup.class);
    servicesContainer.addSingleton(QProfiles.class);
    servicesContainer.addSingleton(QProfileLookup.class);
    servicesContainer.addSingleton(QProfileOperations.class);
    servicesContainer.addSingleton(QProfileActiveRuleOperations.class);
    servicesContainer.addSingleton(QProfileProjectOperations.class);
    servicesContainer.addSingleton(QProfileProjectLookup.class);
    servicesContainer.addSingleton(QProfileBackup.class);
    servicesContainer.addSingleton(QProfileRepositoryExporter.class);
    servicesContainer.addSingleton(ESActiveRule.class);

    // quality gates
    servicesContainer.addSingleton(QualityGateDao.class);
    servicesContainer.addSingleton(QualityGates.class);
    servicesContainer.addSingleton(QualityGatesWs.class);

    // users
    servicesContainer.addSingleton(HibernateUserFinder.class);
    servicesContainer.addSingleton(NewUserNotifier.class);
    servicesContainer.addSingleton(DefaultUserFinder.class);
    servicesContainer.addSingleton(DefaultUserService.class);

    // groups
    servicesContainer.addSingleton(GroupMembershipService.class);
    servicesContainer.addSingleton(GroupMembershipFinder.class);

    // permissions
    servicesContainer.addSingleton(PermissionFacade.class);
    servicesContainer.addSingleton(InternalPermissionService.class);
    servicesContainer.addSingleton(InternalPermissionTemplateService.class);
    servicesContainer.addSingleton(PermissionFinder.class);

    // components
    servicesContainer.addSingleton(DefaultComponentFinder.class);
    servicesContainer.addSingleton(DefaultRubyComponentService.class);

    // issues
    servicesContainer.addSingleton(ServerIssueStorage.class);
    servicesContainer.addSingleton(IssueUpdater.class);
    servicesContainer.addSingleton(FunctionExecutor.class);
    servicesContainer.addSingleton(IssueWorkflow.class);
    servicesContainer.addSingleton(IssueService.class);
    servicesContainer.addSingleton(IssueCommentService.class);
    servicesContainer.addSingleton(DefaultIssueFinder.class);
    servicesContainer.addSingleton(IssueStatsFinder.class);
    servicesContainer.addSingleton(PublicRubyIssueService.class);
    servicesContainer.addSingleton(InternalRubyIssueService.class);
    servicesContainer.addSingleton(ActionPlanService.class);
    servicesContainer.addSingleton(IssueChangelogService.class);
    servicesContainer.addSingleton(IssueNotifications.class);
    servicesContainer.addSingleton(ActionService.class);
    servicesContainer.addSingleton(Actions.class);
    servicesContainer.addSingleton(IssueFilterSerializer.class);
    servicesContainer.addSingleton(IssueFilterService.class);
    servicesContainer.addSingleton(IssueBulkChangeService.class);
    servicesContainer.addSingleton(IssueChangelogFormatter.class);
    servicesContainer.addSingleton(IssueFilterWs.class);
    servicesContainer.addSingleton(IssueShowWsHandler.class);
    servicesContainer.addSingleton(IssuesWs.class);

    // issues actions
    servicesContainer.addSingleton(AssignAction.class);
    servicesContainer.addSingleton(PlanAction.class);
    servicesContainer.addSingleton(SetSeverityAction.class);
    servicesContainer.addSingleton(CommentAction.class);
    servicesContainer.addSingleton(TransitionAction.class);

    // rules
    servicesContainer.addSingleton(Rules.class);
    servicesContainer.addSingleton(RuleOperations.class);
    servicesContainer.addSingleton(RuleRegistry.class);
    servicesContainer.addSingleton(RubyRuleService.class);
    servicesContainer.addSingleton(RuleRepositories.class);
    servicesContainer.addSingleton(RulesWs.class);
    servicesContainer.addSingleton(RuleShowWsHandler.class);
    servicesContainer.addSingleton(AddTagsWsHandler.class);
    servicesContainer.addSingleton(RemoveTagsWsHandler.class);

    // rule tags
    servicesContainer.addSingleton(ESRuleTags.class);
    servicesContainer.addSingleton(RuleTagLookup.class);
    servicesContainer.addSingleton(RuleTagOperations.class);
    servicesContainer.addSingleton(RuleTags.class);
    servicesContainer.addSingleton(RuleTagsWs.class);

    // technical debt
    servicesContainer.addSingleton(DebtService.class);
    servicesContainer.addSingleton(TechnicalDebtModelSynchronizer.class);
    servicesContainer.addSingleton(TechnicalDebtModelRepository.class);
    servicesContainer.addSingleton(TechnicalDebtXMLImporter.class);
    servicesContainer.addSingleton(WorkDurationFormatter.class);
    servicesContainer.addSingleton(DefaultTechnicalDebtManager.class);
    servicesContainer.addSingleton(WorkDurationFactory.class);

    // source
    servicesContainer.addSingleton(HtmlSourceDecorator.class);
    servicesContainer.addSingleton(DeprecatedSourceDecorator.class);
    servicesContainer.addSingleton(SourceService.class);
    servicesContainer.addSingleton(SourcesWs.class);
    servicesContainer.addSingleton(SourcesShowWsHandler.class);

    // text
    servicesContainer.addSingleton(MacroInterpreter.class);
    servicesContainer.addSingleton(RubyTextService.class);

    // Notifications
    servicesContainer.addSingleton(EmailSettings.class);
    servicesContainer.addSingleton(NotificationService.class);
    servicesContainer.addSingleton(NotificationCenter.class);
    servicesContainer.addSingleton(DefaultNotificationManager.class);

    // graphs and perspective related classes
    servicesContainer.addSingleton(TestablePerspectiveLoader.class);
    servicesContainer.addSingleton(TestPlanPerspectiveLoader.class);
    servicesContainer.addSingleton(SnapshotPerspectives.class);

    // Type validation
    servicesContainer.addSingleton(TypeValidations.class);
    servicesContainer.addSingleton(IntegerTypeValidation.class);
    servicesContainer.addSingleton(FloatTypeValidation.class);
    servicesContainer.addSingleton(BooleanTypeValidation.class);
    servicesContainer.addSingleton(TextTypeValidation.class);
    servicesContainer.addSingleton(StringTypeValidation.class);
    servicesContainer.addSingleton(StringListTypeValidation.class);

    ServerExtensionInstaller extensionRegistrar = servicesContainer.getComponentByType(ServerExtensionInstaller.class);
    extensionRegistrar.registerExtensions(servicesContainer);

    servicesContainer.startComponents();
  }

  private void executeStartupTasks() {
    ComponentContainer startupContainer = servicesContainer.createChild();
    startupContainer.addSingleton(GwtPublisher.class);
    startupContainer.addSingleton(RegisterMetrics.class);
    startupContainer.addSingleton(DeprecatedRuleDefinitions.class);
    startupContainer.addSingleton(RuleDefinitionsLoader.class);
    startupContainer.addSingleton(RuleRegistration.class);
    startupContainer.addSingleton(RegisterNewProfiles.class);
    startupContainer.addSingleton(JdbcDriverDeployer.class);
    startupContainer.addSingleton(RegisterTechnicalDebtModel.class);
    startupContainer.addSingleton(DeleteDeprecatedMeasures.class);
    startupContainer.addSingleton(GeneratePluginIndex.class);
    startupContainer.addSingleton(GenerateBootstrapIndex.class);
    startupContainer.addSingleton(RegisterNewMeasureFilters.class);
    startupContainer.addSingleton(RegisterNewDashboards.class);
    startupContainer.addSingleton(RegisterPermissionTemplates.class);
    startupContainer.addSingleton(RenameDeprecatedPropertyKeys.class);
    startupContainer.addSingleton(LogServerId.class);
    startupContainer.addSingleton(RegisterServletFilters.class);
    startupContainer.addSingleton(CleanPreviewAnalysisCache.class);
    startupContainer.startComponents();

    startupContainer.getComponentByType(ServerLifecycleNotifier.class).notifyStart();

    // Do not put the following statements in a finally block.
    // It would hide the possible exception raised during startup
    // See SONAR-3107
    startupContainer.stopComponents();
    servicesContainer.removeChild();
    servicesContainer.getComponentByType(DatabaseSessionFactory.class).clear();
  }

  public void stop() {
    if (rootContainer != null) {
      try {
        rootContainer.stopComponents();
        rootContainer = null;
        connected = false;
        started = false;
      } catch (Exception e) {
        LoggerFactory.getLogger(getClass()).debug("Fail to stop server - ignored", e);
      }
    }
  }

  public ComponentContainer getContainer() {
    if (servicesContainer != null) {
      return servicesContainer;
    }
    if (coreContainer != null) {
      return coreContainer;
    }
    return rootContainer;
  }

  public Object getComponent(Object key) {
    return getContainer().getComponentByKey(key);
  }
}
