package avrohugger
package tool

import java.io.{File, FilenameFilter, InputStream, PrintStream}
import java.util.{ArrayList, LinkedHashSet, List, Set}

import avrohugger.format.abstractions.SourceFormat
import org.apache.avro.generic.GenericData.StringType
import org.apache.avro.tool.Tool
import avrohugger.filesorter.AvscFileSorter

import scala.collection.JavaConverters._
import scala.util.Try

/**
 * A Tool for generating Scala case classes from schemas 
 * Adapted from https://github.com/apache/avro/blob/branch-1.7/lang/java/tools/src/main/java/org/apache/avro/tool/SpecificCompilerTool.java
 */
class GeneratorTool(sourceFormat: SourceFormat, 
  avroScalaCustomTypes: Map[String, Class[_]] = Map.empty,
  avroScalaCustomNamespace: Map[String, String] = Map.empty) extends Tool {

  val generator = new Generator(sourceFormat, avroScalaCustomTypes, avroScalaCustomNamespace)

  @Override
  def run(in: InputStream, out: PrintStream, err: PrintStream, args: List[String]): Int = {
    if (args.size() < 3) {
      System.err
          .println("Usage: [-string] (schema|protocol|datafile) input... outputdir");
      System.err
          .println(" input - input files or directories");
      System.err
          .println(" outputdir - directory to write generated scala");
      System.err.println(" -string - use java.lang.String instead of Utf8");
      1;
    }

    var stringType: StringType = StringType.CharSequence;

    var arg = 0;
    if ("-string".equals(args.get(arg))) {
      stringType = StringType.String;
      arg+=1;
    }
      
    val method: String = args.get(arg);
    var inputs: List[File] = new ArrayList[File]();
    
    for (i <- (arg + 1) until args.size()) {
      Try {
         inputs.add(new File(args.get(i)));
      }
    }
    
    if ("datafile".equals(method)) {
      for (src: File <- determineInputs(inputs, DATAFILE_FILTER)) {
        generator.fileToFile(src, args.asScala.last)
      }
    } else if ("schema".equals(method)) {
      for (src: File <- determineInputs(inputs, SCHEMA_FILTER)) {
        generator.fileToFile(src, args.asScala.last)
      }
    } 
    else if ("protocol".equals(method)) {
      for (src: File <- determineInputs(inputs, PROTOCOL_FILTER)) {
        generator.fileToFile(src, args.asScala.last)
      }
    } 
    else {
      sys.error("Expected \"datafile\", \"schema\" or \"protocol\".");
      1;
    }
    0;
  }

  @Override
  def getName: String = generator.sourceFormat.toolName;

  @Override
  def getShortDescription: String = generator.sourceFormat.toolShortDescription;

  /**
   * For a List of files or directories, returns a File[] containing each file
   * passed as well as each file with a matching extension found in the directory.
   *
   * @param inputs List of File objects that are files or directories
   * @param filter File extension filter to match on when fetching files from a directory
   * @return Unique array of files
   */
  private def determineInputs(inputs: List[File], filter: FilenameFilter): Array[File] = {
    val fileSet: Set[File] = new LinkedHashSet[File](); // preserve order and uniqueness
    for (file: File <- inputs.asScala) {
      var files = recursiveListFiles(file)
      files = files.filter(x => x.isFile && x.getName.endsWith("avsc"))
      val sortedFiles = AvscFileSorter.sortSchemaFiles(files)
      sortedFiles.foreach( (f:File) => fileSet.add(f))
    }
    if (fileSet.size() > 0) {
      System.err.println("Input files to compile:");
      for (file: File <- fileSet.asScala) {
        System.err.println("  " + file);
      }
    }
    else {
      System.err.println("No input files found.");
    }

    Array[File](fileSet.asScala.toList:_*);
  }

  def recursiveListFiles(f: File): Array[File] = {
    val these = f.listFiles
    these ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
  }

  val SCHEMA_FILTER: FileExtensionFilter  =
    new FileExtensionFilter("avsc");
  val PROTOCOL_FILTER: FileExtensionFilter =
    new FileExtensionFilter("avpr");
  val DATAFILE_FILTER: FileExtensionFilter =
    new FileExtensionFilter("avro");

  case class FileExtensionFilter(extension: String) extends FilenameFilter {
    @Override
    def accept(dir: File, name: String) = {
      name.endsWith(this.extension);
    }
  }
}
