// Copyright (C) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License. See LICENSE in project root for information.

package com.microsoft.ml.spark.codegen

import com.microsoft.ml.spark.FileUtilities._
import Config._

import scala.util.matching.Regex
import java.util.regex.Pattern

object CodeGen {

  def copyAllFiles(fromDir: File, rx: Regex, toDir: File): Unit = {
    if (!fromDir.isDirectory) { println(s"'$fromDir' is not a directory"); return }
    allFiles(fromDir, if (rx == null) null else (f => rx.findFirstIn(f.getName) != None))
      .foreach{x => copyFile(x, toDir, overwrite=true)}
  }
  def copyAllFiles(fromDir: File, extension: String, toDir: File): Unit =
      copyAllFiles(fromDir,
                   if (extension == null || extension == "") null
                   else (Pattern.quote("." + extension) + "$").r,
                   toDir)

  def copyAllFilesFromRoots(fromDir: File, roots: List[String], relPath: String,
                            extension: String, toDir: File): Unit = {
    roots.foreach { root =>
      val dir = new File(new File(fromDir, root), relPath)
      if (dir.exists && dir.isDirectory) copyAllFiles(dir, extension, toDir)
    }
  }
  def copyAllFilesFromRoots(fromDir: File, roots: List[String], relPath: String,
                            rx: Regex, toDir: File): Unit = {
    roots.foreach { root =>
      val dir = new File(new File(fromDir, root), relPath)
      if (dir.exists && dir.isDirectory) copyAllFiles(dir, rx, toDir)
    }
  }

  def generateArtifacts(): Unit = {
    println(s"""|Running registration with config:
                |  topDir:    $topDir
                |  srcDir:    $srcDir
                |  outputDir: $outputDir
                |  toZipDir:  $toZipDir
                |  pyTestDir: $pyTestDir""".stripMargin)
    val roots = // note: excludes the toplevel project
      if (!rootsFile.exists) sys.error(s"Could not find roots file at $rootsFile")
      else readFile(rootsFile, _.getLines.toList).filter(_ != ".")
    println("Creating temp folders")
    toZipDir.mkdirs
    pyTestDir.mkdirs
    println("Copy jar files to output directory")
    copyAllFilesFromRoots(srcDir, roots, jarRelPath,
                          (Pattern.quote("-" + mmlVer + ".jar") + "$").r,
                          outputDir)
    println("Copy source python files")
    copyAllFilesFromRoots(srcDir, roots, pyRelPath, "py", toZipDir)
    println("Generate python APIs")
    PySparkWrapperGenerator()
    // build init file
    val importStrings =
      (copyrightLines.mkString("\n") + "\n\n") +:
      allFiles(toZipDir, _.getName.endsWith(".py"))
        .filter(f => !f.getName.startsWith(internalPrefix))
        .map(f => s"from mmlspark.${f.getName.dropRight(3)} import *\n")
    writeFile(new File(toZipDir, "__init__.py"), importStrings.mkString(""))
    // package python zip file
    zipFolder(toZipDir, zipFile)
    // leave the source files there so they will be included in the super-jar
    // if (!delTree(toZipDir)) println(s"Error: could not delete $toZipDir")
  }

  def main(args: Array[String]): Unit = {
    org.apache.log4j.BasicConfigurator.configure(new org.apache.log4j.varia.NullAppender())
    generateArtifacts()
  }

}
