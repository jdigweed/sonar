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
package org.sonar.batch.scan.filesystem;

import org.junit.Test;
import org.mockito.Mockito;
import org.sonar.api.resources.Project;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.batch.bootstrap.AnalysisMode;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class InputFileBuilderFactoryTest {
  @Test
  public void create_builder() throws Exception {
    PathResolver pathResolver = new PathResolver();
    Project project = new Project("struts");
    LanguageDetectionFactory langDetectionFactory = mock(LanguageDetectionFactory.class, Mockito.RETURNS_MOCKS);
    StatusDetectionFactory statusDetectionFactory = mock(StatusDetectionFactory.class, Mockito.RETURNS_MOCKS);
    DefaultModuleFileSystem fs = mock(DefaultModuleFileSystem.class);
    AnalysisMode analysisMode = mock(AnalysisMode.class);

    InputFileBuilderFactory factory = new InputFileBuilderFactory(
      project, pathResolver, langDetectionFactory,
      statusDetectionFactory, analysisMode);
    InputFileBuilder builder = factory.create(fs);

    assertThat(builder.langDetection()).isNotNull();
    assertThat(builder.statusDetection()).isNotNull();
    assertThat(builder.pathResolver()).isSameAs(pathResolver);
    assertThat(builder.fs()).isSameAs(fs);
    assertThat(builder.moduleKey()).isEqualTo("struts");
  }
}
