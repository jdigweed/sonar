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
package org.sonar.api.batch.fs.internal;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile;

import java.io.File;

import static org.fest.assertions.Assertions.assertThat;

public class PathPatternTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void match_relative_path() throws Exception {
    PathPattern pattern = PathPattern.create("**/*Foo.java");
    assertThat(pattern.toString()).isEqualTo("**/*Foo.java");

    File file = new File(temp.newFolder(), "src/main/java/org/MyFoo.java");
    InputFile inputFile = new DefaultInputFile("src/main/java/org/MyFoo.java").setFile(file);
    assertThat(pattern.match(inputFile)).isTrue();

    // case sensitive by default
    file = new File(temp.newFolder(), "src/main/java/org/MyFoo.JAVA");
    inputFile = new DefaultInputFile("src/main/java/org/MyFoo.JAVA").setFile(file);
    assertThat(pattern.match(inputFile)).isFalse();

    file = new File(temp.newFolder(), "src/main/java/org/Other.java");
    inputFile = new DefaultInputFile("src/main/java/org/Other.java").setFile(file);
    assertThat(pattern.match(inputFile)).isFalse();
  }

  @Test
  public void match_relative_path_and_insensitive_file_extension() throws Exception {
    PathPattern pattern = PathPattern.create("**/*Foo.java");

    File file = new File(temp.newFolder(), "src/main/java/org/MyFoo.JAVA");
    InputFile inputFile = new DefaultInputFile("src/main/java/org/MyFoo.JAVA").setFile(file);
    assertThat(pattern.match(inputFile, false)).isTrue();

    file = new File(temp.newFolder(), "src/main/java/org/Other.java");
    inputFile = new DefaultInputFile("src/main/java/org/Other.java").setFile(file);
    assertThat(pattern.match(inputFile, false)).isFalse();
  }

  @Test
  public void match_absolute_path() throws Exception {
    PathPattern pattern = PathPattern.create("file:**/src/main/**Foo.java");
    assertThat(pattern.toString()).isEqualTo("file:**/src/main/**Foo.java");

    File file = new File(temp.newFolder(), "src/main/java/org/MyFoo.java");
    InputFile inputFile = new DefaultInputFile("src/main/java/org/MyFoo.java").setFile(file);
    assertThat(pattern.match(inputFile)).isTrue();

    // case sensitive by default
    file = new File(temp.newFolder(), "src/main/java/org/MyFoo.JAVA");
    inputFile = new DefaultInputFile("src/main/java/org/MyFoo.JAVA").setFile(file);
    assertThat(pattern.match(inputFile)).isFalse();

    file = new File(temp.newFolder(), "src/main/java/org/Other.java");
    inputFile = new DefaultInputFile("src/main/java/org/Other.java").setFile(file);
    assertThat(pattern.match(inputFile)).isFalse();
  }

  @Test
  public void match_absolute_path_and_insensitive_file_extension() throws Exception {
    PathPattern pattern = PathPattern.create("file:**/src/main/**Foo.java");
    assertThat(pattern.toString()).isEqualTo("file:**/src/main/**Foo.java");

    File file = new File(temp.newFolder(), "src/main/java/org/MyFoo.JAVA");
    InputFile inputFile = new DefaultInputFile("src/main/java/org/MyFoo.JAVA").setFile(file);
    assertThat(pattern.match(inputFile, false)).isTrue();

    file = new File(temp.newFolder(), "src/main/java/org/Other.JAVA");
    inputFile = new DefaultInputFile("src/main/java/org/Other.JAVA").setFile(file);
    assertThat(pattern.match(inputFile, false)).isFalse();
  }

  @Test
  public void create_array_of_patterns() throws Exception {
    PathPattern[] patterns = PathPattern.create(new String[]{
      "**/src/main/**Foo.java",
      "file:**/src/main/**Bar.java"
    });
    assertThat(patterns).hasSize(2);
    assertThat(patterns[0].toString()).isEqualTo("**/src/main/**Foo.java");
    assertThat(patterns[1].toString()).isEqualTo("file:**/src/main/**Bar.java");
  }
}
