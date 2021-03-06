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
package org.sonar.batch.scan;

import org.sonar.api.BatchComponent;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.MessageException;

public class UnsupportedProperties implements BatchComponent {
  private final Settings settings;

  public UnsupportedProperties(Settings settings) {
    this.settings = settings;
  }

  public void start() {
    verify("sonar.light", "The property 'sonar.light' is no longer supported. Please use 'sonar.dynamicAnalysis'");
  }

  private void verify(String key, String message) {
    if (settings.hasKey(key)) {
      throw MessageException.of(message);
    }
  }
}
