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
package org.sonar.core.component;

import org.sonar.api.component.Component;

public class ComponentDto implements Component {

  private Long id;
  private String kee;
  private String path;
  private String moduleKey;
  private String name;
  private String longName;
  private String qualifier;

  public Long getId() {
    return id;
  }

  public ComponentDto setId(Long id) {
    this.id = id;
    return this;
  }

  @Override
  public String key() {
    return kee;
  }

  public ComponentDto setKey(String key) {
    this.kee = key;
    return this;
  }

  @Override
  public String path() {
    return path;
  }

  public ComponentDto setPath(String path) {
    this.path = path;
    return this;
  }

  @Override
  public String moduleKey() {
    return moduleKey;
  }

  public ComponentDto setModuleKey(String moduleKey) {
    this.moduleKey = moduleKey;
    return this;
  }

  @Override
  public String name() {
    return name;
  }

  public ComponentDto setName(String name) {
    this.name = name;
    return this;
  }

  @Override
  public String longName() {
    return longName;
  }

  public ComponentDto setLongName(String longName) {
    this.longName = longName;
    return this;
  }

  @Override
  public String qualifier() {
    return qualifier;
  }

  public ComponentDto setQualifier(String qualifier) {
    this.qualifier = qualifier;
    return this;
  }
}
