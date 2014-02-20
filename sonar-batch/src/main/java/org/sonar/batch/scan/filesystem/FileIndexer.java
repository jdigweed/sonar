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

import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.HiddenFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.BatchComponent;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.InputFileFilter;
import org.sonar.api.resources.Project;
import org.sonar.api.scan.filesystem.PathResolver;
import org.sonar.api.utils.SonarException;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Index input files into {@link InputFileCache}.
 */
public class FileIndexer implements BatchComponent {

  private static final String FILE_IS_NOT_DECLARED_IN_MODULE_BASEDIR = "File '%s' is not declared in module basedir %s";
  private static final IOFileFilter DIR_FILTER = FileFilterUtils.and(HiddenFileFilter.VISIBLE, FileFilterUtils.notFileFilter(FileFilterUtils.prefixFileFilter(".")));
  private static final IOFileFilter FILE_FILTER = HiddenFileFilter.VISIBLE;

  private final PathResolver pathResolver;
  private final List<InputFileFilter> filters;
  private final InputFileCache fileCache;
  private final Project module;
  private final ExclusionFilters exclusionFilters;
  private final InputFileBuilderFactory inputFileBuilderFactory;

  public FileIndexer(List<InputFileFilter> filters, ExclusionFilters exclusionFilters, InputFileBuilderFactory inputFileBuilderFactory,
                     InputFileCache cache, PathResolver pathResolver, Project module) {
    this.filters = filters;
    this.exclusionFilters = exclusionFilters;
    this.inputFileBuilderFactory = inputFileBuilderFactory;
    this.fileCache = cache;
    this.pathResolver = pathResolver;
    this.module = module;
  }

  void index(DefaultModuleFileSystem fileSystem) {
    Logger logger = LoggerFactory.getLogger(FileIndexer.class);
    if (!module.getModules().isEmpty()) {
      // No indexing for an aggregator module
      return;
    }
    logger.info("Index files");
    exclusionFilters.prepare(fileSystem);
    // TODO log configuration too (replace FileSystemLogger)

    Progress progress = new Progress(fileCache.byModule(fileSystem.moduleKey()));

    InputFileBuilder inputFileBuilder = inputFileBuilderFactory.create(fileSystem);
    if (!fileSystem.sourceFiles().isEmpty() || !fileSystem.testFiles().isEmpty()) {
      // Index only provided files
      indexFiles(inputFileBuilder, fileSystem, progress, fileSystem.sourceFiles(), InputFile.Type.MAIN);
      indexFiles(inputFileBuilder, fileSystem, progress, fileSystem.testFiles(), InputFile.Type.TEST);
    } else if (fileSystem.baseDir() != null) {
      // index from basedir
      indexDirectory(inputFileBuilder, fileSystem, progress, fileSystem.baseDir());
    }

    // Remove files that have been removed since previous indexation
    for (InputFile removed : progress.removed) {
      fileCache.remove(fileSystem.moduleKey(), removed);
    }

    logger.info(String.format("%d files indexed", progress.count()));

  }

  private void indexFiles(InputFileBuilder inputFileBuilder, DefaultModuleFileSystem fileSystem, Progress progress, List<File> sourceFiles, InputFile.Type type) {
    for (File sourceFile : sourceFiles) {
      String path = pathResolver.relativePath(fileSystem.baseDir(), sourceFile);
      if (path == null) {
        LoggerFactory.getLogger(getClass()).warn(String.format(
          FILE_IS_NOT_DECLARED_IN_MODULE_BASEDIR, sourceFile.getAbsoluteFile(), fileSystem.baseDir()
          ));
      } else {
        if (exclusionFilters.accept(sourceFile, path, type)) {
          indexFile(inputFileBuilder, fileSystem, progress, sourceFile, path, type);
        }
      }
    }
  }

  private void indexDirectory(InputFileBuilder inputFileBuilder, DefaultModuleFileSystem fileSystem, Progress status, File dirToIndex) {
    Collection<File> files = FileUtils.listFiles(dirToIndex, FILE_FILTER, DIR_FILTER);
    for (File sourceFile : files) {
      String path = pathResolver.relativePath(fileSystem.baseDir(), sourceFile);
      if (path == null) {
        LoggerFactory.getLogger(getClass()).warn(String.format(
          FILE_IS_NOT_DECLARED_IN_MODULE_BASEDIR, sourceFile.getAbsoluteFile(), fileSystem.baseDir()
          ));
      } else {
        if (exclusionFilters.accept(sourceFile, path, InputFile.Type.MAIN)) {
          indexFile(inputFileBuilder, fileSystem, status, sourceFile, path, InputFile.Type.MAIN);
        }
        if (exclusionFilters.accept(sourceFile, path, InputFile.Type.TEST)) {
          indexFile(inputFileBuilder, fileSystem, status, sourceFile, path, InputFile.Type.TEST);
        }
      }
    }
  }

  private void indexFile(InputFileBuilder inputFileBuilder, DefaultModuleFileSystem fs, Progress status, File file, String path, InputFile.Type type) {
    InputFile inputFile = inputFileBuilder.create(file, type);
    if (inputFile != null && accept(inputFile)) {
      fs.add(inputFile);
      status.markAsIndexed(inputFile);
    }
  }

  private boolean accept(InputFile inputFile) {
    // InputFileFilter extensions
    for (InputFileFilter filter : filters) {
      if (!filter.accept(inputFile)) {
        return false;
      }
    }
    return true;
  }

  private static class Progress {
    private final Set<InputFile> removed;
    private final Set<InputFile> indexed;

    Progress(Iterable<InputFile> removed) {
      this.removed = Sets.newHashSet(removed);
      this.indexed = new HashSet<InputFile>();
    }

    void markAsIndexed(InputFile inputFile) {
      if (indexed.contains(inputFile)) {
        throw new SonarException("File " + inputFile + " can't be indexed twice. Please check that inclusion/exclusion patterns produce "
          + "disjoint sets for main and test files");
      }
      removed.remove(inputFile);
      indexed.add(inputFile);
    }

    int count() {
      return indexed.size();
    }
  }

}
