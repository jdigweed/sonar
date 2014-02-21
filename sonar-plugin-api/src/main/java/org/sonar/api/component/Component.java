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
package org.sonar.api.component;

import javax.annotation.CheckForNull;

public interface Component<C extends Component> {
  String key();

  /**
   * Path of the component relative to basedir of the parent module.
   * @return null if this component is not a child of a module
   * @since 4.2
   */
  @CheckForNull
  String path();

  /**
   * Key of the module this component belong to.
   * @return null for components that are module themself
   * @since 4.2
   */
  @CheckForNull
  String moduleKey();

  String name();

  String longName();

  String qualifier();
}
