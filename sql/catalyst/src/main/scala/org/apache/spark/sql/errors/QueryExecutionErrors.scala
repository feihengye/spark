/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.errors

import java.io.{FileNotFoundException, IOException}
import java.net.URISyntaxException
import java.sql.{SQLException, SQLFeatureNotSupportedException}
import java.time.{DateTimeException, LocalDate}
import java.time.temporal.ChronoField

import org.apache.hadoop.fs.{FileStatus, Path}
import org.codehaus.commons.compiler.CompileException
import org.codehaus.janino.InternalCompilerException

import org.apache.spark.{Partition, SparkException, SparkUpgradeException}
import org.apache.spark.executor.CommitDeniedException
import org.apache.spark.memory.SparkOutOfMemoryError
import org.apache.spark.sql.catalyst.analysis.UnresolvedGenerator
import org.apache.spark.sql.catalyst.catalog.CatalogDatabase
import org.apache.spark.sql.catalyst.expressions.{Expression, UnevaluableAggregate}
import org.apache.spark.sql.connector.catalog.CatalogV2Implicits._
import org.apache.spark.sql.connector.catalog.Identifier
import org.apache.spark.sql.connector.expressions.Transform
import org.apache.spark.sql.execution.QueryExecutionException
import org.apache.spark.sql.internal.SQLConf
import org.apache.spark.sql.types.{DataType, Decimal, StructType}
import org.apache.spark.unsafe.array.ByteArrayMethods
import org.apache.spark.unsafe.types.UTF8String

/**
 * Object for grouping error messages from (most) exceptions thrown during query execution.
 * This does not include exceptions thrown during the eager execution of commands, which are
 * grouped into [[QueryCompilationErrors]].
 */
object QueryExecutionErrors {

  def columnChangeUnsupportedError(): Throwable = {
    new UnsupportedOperationException("Please add an implementation for a column change here")
  }

  def logicalHintOperatorNotRemovedDuringAnalysisError(): Throwable = {
    new IllegalStateException(
      "Internal error: logical hint operator should have been removed during analysis")
  }

  def cannotEvaluateExpressionError(expression: Expression): Throwable = {
    new UnsupportedOperationException(s"Cannot evaluate expression: $expression")
  }

  def cannotGenerateCodeForExpressionError(expression: Expression): Throwable = {
    new UnsupportedOperationException(s"Cannot generate code for expression: $expression")
  }

  def cannotTerminateGeneratorError(generator: UnresolvedGenerator): Throwable = {
    new UnsupportedOperationException(s"Cannot terminate expression: $generator")
  }

  def castingCauseOverflowError(t: Any, targetType: String): ArithmeticException = {
    new ArithmeticException(s"Casting $t to $targetType causes overflow")
  }

  def cannotChangeDecimalPrecisionError(
      value: Decimal, decimalPrecision: Int, decimalScale: Int): ArithmeticException = {
    new ArithmeticException(s"${value.toDebugString} cannot be represented as " +
      s"Decimal($decimalPrecision, $decimalScale).")
  }

  def invalidInputSyntaxForNumericError(s: UTF8String): NumberFormatException = {
    new NumberFormatException(s"invalid input syntax for type numeric: $s")
  }

  def cannotCastFromNullTypeError(to: DataType): Throwable = {
    new SparkException(s"should not directly cast from NullType to $to.")
  }

  def cannotCastError(from: DataType, to: DataType): Throwable = {
    new SparkException(s"Cannot cast $from to $to.")
  }

  def cannotParseDecimalError(): Throwable = {
    new IllegalArgumentException("Cannot parse any decimal")
  }

  def simpleStringWithNodeIdUnsupportedError(nodeName: String): Throwable = {
    new UnsupportedOperationException(s"$nodeName does not implement simpleStringWithNodeId")
  }

  def evaluateUnevaluableAggregateUnsupportedError(
      methodName: String, unEvaluable: UnevaluableAggregate): Throwable = {
    new UnsupportedOperationException(s"Cannot evaluate $methodName: $unEvaluable")
  }

  def dataTypeUnsupportedError(dt: DataType): Throwable = {
    new SparkException(s"Unsupported data type $dt")
  }

  def dataTypeUnsupportedError(dataType: String, failure: String): Throwable = {
    new IllegalArgumentException(s"Unsupported dataType: $dataType, $failure")
  }

  def failedExecuteUserDefinedFunctionError(funcCls: String, inputTypes: String,
      outputType: String, e: Throwable): Throwable = {
    new SparkException(
      s"Failed to execute user defined function ($funcCls: ($inputTypes) => $outputType)", e)
  }

  def divideByZeroError(): ArithmeticException = {
    new ArithmeticException("divide by zero")
  }

  def invalidArrayIndexError(index: Int, numElements: Int): ArrayIndexOutOfBoundsException = {
    new ArrayIndexOutOfBoundsException(s"Invalid index: $index, numElements: $numElements")
  }

  def mapKeyNotExistError(key: Any): NoSuchElementException = {
    new NoSuchElementException(s"Key $key does not exist.")
  }

  def rowFromCSVParserNotExpectedError(): Throwable = {
    new IllegalArgumentException("Expected one row from CSV parser.")
  }

  def inputTypeUnsupportedError(dataType: DataType): Throwable = {
    new IllegalArgumentException(s"Unsupported input type ${dataType.catalogString}")
  }

  def invalidFractionOfSecondError(): DateTimeException = {
    new DateTimeException("The fraction of sec must be zero. Valid range is [0, 60].")
  }

  def overflowInSumOfDecimalError(): ArithmeticException = {
    new ArithmeticException("Overflow in sum of decimals.")
  }

  def overflowInIntegralDivideError(): ArithmeticException = {
    new ArithmeticException("Overflow in integral divide.")
  }

  def mapSizeExceedArraySizeWhenZipMapError(size: Int): RuntimeException = {
    new RuntimeException(s"Unsuccessful try to zip maps with $size " +
      "unique keys due to exceeding the array size limit " +
      s"${ByteArrayMethods.MAX_ROUNDED_ARRAY_LENGTH}.")
  }

  def copyNullFieldNotAllowedError(): Throwable = {
    new IllegalStateException("Do not attempt to copy a null field")
  }

  def literalTypeUnsupportedError(v: Any): RuntimeException = {
    new RuntimeException(s"Unsupported literal type ${v.getClass} $v")
  }

  def noDefaultForDataTypeError(dataType: DataType): RuntimeException = {
    new RuntimeException(s"no default for type $dataType")
  }

  def doGenCodeOfAliasShouldNotBeCalledError(): Throwable = {
    new IllegalStateException("Alias.doGenCode should not be called.")
  }

  def orderedOperationUnsupportedByDataTypeError(dataType: DataType): Throwable = {
    new IllegalArgumentException(s"Type $dataType does not support ordered operations")
  }

  def regexGroupIndexLessThanZeroError(): Throwable = {
    new IllegalArgumentException("The specified group index cannot be less than zero")
  }

  def regexGroupIndexExceedGroupCountError(
      groupCount: Int, groupIndex: Int): Throwable = {
    new IllegalArgumentException(
      s"Regex group count is $groupCount, but the specified group index is $groupIndex")
  }

  def invalidUrlError(url: UTF8String, e: URISyntaxException): Throwable = {
    new IllegalArgumentException(s"Find an invaild url string ${url.toString}", e)
  }

  def dataTypeOperationUnsupportedError(): Throwable = {
    new UnsupportedOperationException("dataType")
  }

  def mergeUnsupportedByWindowFunctionError(): Throwable = {
    new UnsupportedOperationException("Window Functions do not support merging.")
  }

  def dataTypeUnexpectedError(dataType: DataType): Throwable = {
    new UnsupportedOperationException(s"Unexpected data type ${dataType.catalogString}")
  }

  def typeUnsupportedError(dataType: DataType): Throwable = {
    new IllegalArgumentException(s"Unexpected type $dataType")
  }

  def negativeValueUnexpectedError(frequencyExpression : Expression): Throwable = {
    new SparkException(s"Negative values found in ${frequencyExpression.sql}")
  }

  def addNewFunctionMismatchedWithFunctionError(funcName: String): Throwable = {
    new IllegalArgumentException(s"$funcName is not matched at addNewFunction")
  }

  def cannotGenerateCodeForUncomparableTypeError(
      codeType: String, dataType: DataType): Throwable = {
    new IllegalArgumentException(
      s"cannot generate $codeType code for un-comparable type: ${dataType.catalogString}")
  }

  def cannotGenerateCodeForUnsupportedTypeError(dataType: DataType): Throwable = {
    new IllegalArgumentException(s"cannot generate code for unsupported type: $dataType")
  }

  def cannotInterpolateClassIntoCodeBlockError(arg: Any): Throwable = {
    new IllegalArgumentException(
      s"Can not interpolate ${arg.getClass.getName} into code block.")
  }

  def customCollectionClsNotResolvedError(): Throwable = {
    new UnsupportedOperationException("not resolved")
  }

  def classUnsupportedByMapObjectsError(cls: Class[_]): RuntimeException = {
    new RuntimeException(s"class `${cls.getName}` is not supported by `MapObjects` as " +
      "resulting collection.")
  }

  def nullAsMapKeyNotAllowedError(): RuntimeException = {
    new RuntimeException("Cannot use null as map key!")
  }

  def methodNotDeclaredError(name: String): Throwable = {
    new NoSuchMethodException(s"""A method named "$name" is not declared """ +
      "in any enclosing class nor any supertype")
  }

  def inputExternalRowCannotBeNullError(): RuntimeException = {
    new RuntimeException("The input external row cannot be null.")
  }

  def fieldCannotBeNullMsg(index: Int, fieldName: String): String = {
    s"The ${index}th field '$fieldName' of input row cannot be null."
  }

  def fieldCannotBeNullError(index: Int, fieldName: String): RuntimeException = {
    new RuntimeException(fieldCannotBeNullMsg(index, fieldName))
  }

  def unableToCreateDatabaseAsFailedToCreateDirectoryError(
      dbDefinition: CatalogDatabase, e: IOException): Throwable = {
    new SparkException(s"Unable to create database ${dbDefinition.name} as failed " +
      s"to create its directory ${dbDefinition.locationUri}", e)
  }

  def unableToDropDatabaseAsFailedToDeleteDirectoryError(
      dbDefinition: CatalogDatabase, e: IOException): Throwable = {
    new SparkException(s"Unable to drop database ${dbDefinition.name} as failed " +
      s"to delete its directory ${dbDefinition.locationUri}", e)
  }

  def unableToCreateTableAsFailedToCreateDirectoryError(
      table: String, defaultTableLocation: Path, e: IOException): Throwable = {
    new SparkException(s"Unable to create table $table as failed " +
      s"to create its directory $defaultTableLocation", e)
  }

  def unableToDeletePartitionPathError(partitionPath: Path, e: IOException): Throwable = {
    new SparkException(s"Unable to delete partition path $partitionPath", e)
  }

  def unableToDropTableAsFailedToDeleteDirectoryError(
      table: String, dir: Path, e: IOException): Throwable = {
    new SparkException(s"Unable to drop table $table as failed " +
      s"to delete its directory $dir", e)
  }

  def unableToRenameTableAsFailedToRenameDirectoryError(
      oldName: String, newName: String, oldDir: Path, e: IOException): Throwable = {
    new SparkException(s"Unable to rename table $oldName to $newName as failed " +
      s"to rename its directory $oldDir", e)
  }

  def unableToCreatePartitionPathError(partitionPath: Path, e: IOException): Throwable = {
    new SparkException(s"Unable to create partition path $partitionPath", e)
  }

  def unableToRenamePartitionPathError(oldPartPath: Path, e: IOException): Throwable = {
    new SparkException(s"Unable to rename partition path $oldPartPath", e)
  }

  def methodNotImplementedError(methodName: String): Throwable = {
    new UnsupportedOperationException(s"$methodName is not implemented")
  }

  def tableStatsNotSpecifiedError(): Throwable = {
    new IllegalStateException("table stats must be specified.")
  }

  def unaryMinusCauseOverflowError(originValue: AnyVal): ArithmeticException = {
    new ArithmeticException(s"- $originValue caused overflow.")
  }

  def binaryArithmeticCauseOverflowError(
      eval1: Short, symbol: String, eval2: Short): ArithmeticException = {
    new ArithmeticException(s"$eval1 $symbol $eval2 caused overflow.")
  }

  def failedSplitSubExpressionMsg(length: Int): String = {
    "Failed to split subexpression code into small functions because " +
      s"the parameter length of at least one split function went over the JVM limit: $length"
  }

  def failedSplitSubExpressionError(length: Int): Throwable = {
    new IllegalStateException(failedSplitSubExpressionMsg(length))
  }

  def failedToCompileMsg(e: Exception): String = {
    s"failed to compile: $e"
  }

  def internalCompilerError(e: InternalCompilerException): Throwable = {
    new InternalCompilerException(failedToCompileMsg(e), e)
  }

  def compilerError(e: CompileException): Throwable = {
    new CompileException(failedToCompileMsg(e), e.getLocation)
  }

  def unsupportedTableChangeError(e: IllegalArgumentException): Throwable = {
    new SparkException(s"Unsupported table change: ${e.getMessage}", e)
  }

  def notADatasourceRDDPartitionError(split: Partition): Throwable = {
    new SparkException(s"[BUG] Not a DataSourceRDDPartition: $split")
  }

  def dataPathNotSpecifiedError(): Throwable = {
    new IllegalArgumentException("'path' is not specified")
  }

  def createStreamingSourceNotSpecifySchemaError(): Throwable = {
    new IllegalArgumentException(
      s"""
         |Schema must be specified when creating a streaming source DataFrame. If some
         |files already exist in the directory, then depending on the file format you
         |may be able to create a static DataFrame on that directory with
         |'spark.read.load(directory)' and infer schema from it.
       """.stripMargin)
  }

  def streamedOperatorUnsupportedByDataSourceError(
      className: String, operator: String): Throwable = {
    new UnsupportedOperationException(
      s"Data source $className does not support streamed $operator")
  }

  def multiplePathsSpecifiedError(allPaths: Seq[String]): Throwable = {
    new IllegalArgumentException("Expected exactly one path to be specified, but " +
      s"got: ${allPaths.mkString(", ")}")
  }

  def failedToFindDataSourceError(provider: String, error: Throwable): Throwable = {
    new ClassNotFoundException(
      s"""
         |Failed to find data source: $provider. Please find packages at
         |http://spark.apache.org/third-party-projects.html
       """.stripMargin, error)
  }

  def removedClassInSpark2Error(className: String, e: Throwable): Throwable = {
    new ClassNotFoundException(s"$className was removed in Spark 2.0. " +
      "Please check if your library is compatible with Spark 2.0", e)
  }

  def incompatibleDataSourceRegisterError(e: Throwable): Throwable = {
    new ClassNotFoundException(
      s"""
         |Detected an incompatible DataSourceRegister. Please remove the incompatible
         |library from classpath or upgrade it. Error: ${e.getMessage}
       """.stripMargin, e)
  }

  def unrecognizedFileFormatError(format: String): Throwable = {
    new IllegalStateException(s"unrecognized format $format")
  }

  def sparkUpgradeInReadingDatesError(
      format: String, config: String, option: String): SparkUpgradeException = {
    new SparkUpgradeException("3.0",
      s"""
         |reading dates before 1582-10-15 or timestamps before 1900-01-01T00:00:00Z from $format
         |files can be ambiguous, as the files may be written by Spark 2.x or legacy versions of
         |Hive, which uses a legacy hybrid calendar that is different from Spark 3.0+'s Proleptic
         |Gregorian calendar. See more details in SPARK-31404. You can set the SQL config
         |'$config' or the datasource option '$option' to 'LEGACY' to rebase the datetime values
         |w.r.t. the calendar difference during reading. To read the datetime values as it is,
         |set the SQL config '$config' or the datasource option '$option' to 'CORRECTED'.
       """.stripMargin, null)
  }

  def sparkUpgradeInWritingDatesError(format: String, config: String): SparkUpgradeException = {
    new SparkUpgradeException("3.0",
      s"""
         |writing dates before 1582-10-15 or timestamps before 1900-01-01T00:00:00Z into $format
         |files can be dangerous, as the files may be read by Spark 2.x or legacy versions of Hive
         |later, which uses a legacy hybrid calendar that is different from Spark 3.0+'s Proleptic
         |Gregorian calendar. See more details in SPARK-31404. You can set $config to 'LEGACY' to
         |rebase the datetime values w.r.t. the calendar difference during writing, to get maximum
         |interoperability. Or set $config to 'CORRECTED' to write the datetime values as it is,
         |if you are 100% sure that the written files will only be read by Spark 3.0+ or other
         |systems that use Proleptic Gregorian calendar.
       """.stripMargin, null)
  }

  def buildReaderUnsupportedForFileFormatError(format: String): Throwable = {
    new UnsupportedOperationException(s"buildReader is not supported for $format")
  }

  def jobAbortedError(cause: Throwable): Throwable = {
    new SparkException("Job aborted.", cause)
  }

  def taskFailedWhileWritingRowsError(cause: Throwable): Throwable = {
    new SparkException("Task failed while writing rows.", cause)
  }

  def readCurrentFileNotFoundError(e: FileNotFoundException): Throwable = {
    new FileNotFoundException(
      s"""
         |${e.getMessage}\n
         |It is possible the underlying files have been updated. You can explicitly invalidate
         |the cache in Spark by running 'REFRESH TABLE tableName' command in SQL or by
         |recreating the Dataset/DataFrame involved.
       """.stripMargin)
  }

  def unsupportedSaveModeError(saveMode: String, pathExists: Boolean): Throwable = {
    new IllegalStateException(s"unsupported save mode $saveMode ($pathExists)")
  }

  def cannotClearOutputDirectoryError(staticPrefixPath: Path): Throwable = {
    new IOException(s"Unable to clear output directory $staticPrefixPath prior to writing to it")
  }

  def cannotClearPartitionDirectoryError(path: Path): Throwable = {
    new IOException(s"Unable to clear partition directory $path prior to writing to it")
  }

  def failedToCastValueToDataTypeForPartitionColumnError(
      value: String, dataType: DataType, columnName: String): Throwable = {
    new RuntimeException(s"Failed to cast value `$value` to " +
      s"`$dataType` for partition column `$columnName`")
  }

  def endOfStreamError(): Throwable = {
    new NoSuchElementException("End of stream")
  }

  def fallbackV1RelationReportsInconsistentSchemaError(
      v2Schema: StructType, v1Schema: StructType): Throwable = {
    new IllegalArgumentException(
      "The fallback v1 relation reports inconsistent schema:\n" +
        "Schema of v2 scan:     " + v2Schema + "\n" +
        "Schema of v1 relation: " + v1Schema)
  }

  def cannotDropNonemptyNamespaceError(namespace: Seq[String]): Throwable = {
    new SparkException(
      s"Cannot drop a non-empty namespace: ${namespace.quoted}. " +
        "Use CASCADE option to drop a non-empty namespace.")
  }

  def noRecordsFromEmptyDataReaderError(): Throwable = {
    new IOException("No records should be returned from EmptyDataReader")
  }

  def fileNotFoundError(e: FileNotFoundException): Throwable = {
    new FileNotFoundException(
      e.getMessage + "\n" +
        "It is possible the underlying files have been updated. " +
        "You can explicitly invalidate the cache in Spark by " +
        "recreating the Dataset/DataFrame involved.")
  }

  def unsupportedSchemaColumnConvertError(
      filePath: String,
      column: String,
      logicalType: String,
      physicalType: String,
      e: Exception): Throwable = {
    val message = "Parquet column cannot be converted in " +
      s"file $filePath. Column: $column, " +
      s"Expected: $logicalType, Found: $physicalType"
    new QueryExecutionException(message, e)
  }

  def cannotReadParquetFilesError(e: Exception): Throwable = {
    val message = "Encounter error while reading parquet files. " +
      "One possible cause: Parquet column cannot be converted in the " +
      "corresponding files. Details: "
    new QueryExecutionException(message, e)
  }

  def cannotCreateColumnarReaderError(): Throwable = {
    new UnsupportedOperationException("Cannot create columnar reader.")
  }

  def invalidNamespaceNameError(namespace: Array[String]): Throwable = {
    new IllegalArgumentException(s"Invalid namespace name: ${namespace.quoted}")
  }

  def unsupportedPartitionTransformError(transform: Transform): Throwable = {
    new UnsupportedOperationException(
      s"SessionCatalog does not support partition transform: $transform")
  }

  def missingDatabaseLocationError(): Throwable = {
    new IllegalArgumentException("Missing database location")
  }

  def cannotRemoveReservedPropertyError(property: String): Throwable = {
    new UnsupportedOperationException(s"Cannot remove reserved property: $property")
  }

  def namespaceNotEmptyError(namespace: Array[String]): Throwable = {
    new IllegalStateException(s"Namespace ${namespace.quoted} is not empty")
  }

  def writingJobFailedError(cause: Throwable): Throwable = {
    new SparkException("Writing job failed.", cause)
  }

  def writingJobAbortedError(e: Throwable): Throwable = {
    new SparkException("Writing job aborted.", e)
  }

  def commitDeniedError(
      partId: Int, taskId: Long, attemptId: Int, stageId: Int, stageAttempt: Int): Throwable = {
    val message = s"Commit denied for partition $partId (task $taskId, attempt $attemptId, " +
      s"stage $stageId.$stageAttempt)"
    new CommitDeniedException(message, stageId, partId, attemptId)
  }

  def unsupportedTableWritesError(ident: Identifier): Throwable = {
    new SparkException(
      s"Table implementation does not support writes: ${ident.quoted}")
  }

  def cannotCreateJDBCTableWithPartitionsError(): Throwable = {
    new UnsupportedOperationException("Cannot create JDBC table with partition")
  }

  def unsupportedUserSpecifiedSchemaError(): Throwable = {
    new UnsupportedOperationException("user-specified schema")
  }

  def writeUnsupportedForBinaryFileDataSourceError(): Throwable = {
    new UnsupportedOperationException("Write is not supported for binary file data source")
  }

  def fileLengthExceedsMaxLengthError(status: FileStatus, maxLength: Int): Throwable = {
    new SparkException(
      s"The length of ${status.getPath} is ${status.getLen}, " +
        s"which exceeds the max length allowed: ${maxLength}.")
  }

  def unsupportedFieldNameError(fieldName: String): Throwable = {
    new RuntimeException(s"Unsupported field name: ${fieldName}")
  }

  def cannotSpecifyBothJdbcTableNameAndQueryError(
      jdbcTableName: String, jdbcQueryString: String): Throwable = {
    new IllegalArgumentException(
      s"Both '$jdbcTableName' and '$jdbcQueryString' can not be specified at the same time.")
  }

  def missingJdbcTableNameAndQueryError(
      jdbcTableName: String, jdbcQueryString: String): Throwable = {
    new IllegalArgumentException(
      s"Option '$jdbcTableName' or '$jdbcQueryString' is required."
    )
  }

  def emptyOptionError(optionName: String): Throwable = {
    new IllegalArgumentException(s"Option `$optionName` can not be empty.")
  }

  def invalidJdbcTxnIsolationLevelError(jdbcTxnIsolationLevel: String, value: String): Throwable = {
    new IllegalArgumentException(
      s"Invalid value `$value` for parameter `$jdbcTxnIsolationLevel`. This can be " +
        "`NONE`, `READ_UNCOMMITTED`, `READ_COMMITTED`, `REPEATABLE_READ` or `SERIALIZABLE`.")
  }

  def cannotGetJdbcTypeError(dt: DataType): Throwable = {
    new IllegalArgumentException(s"Can't get JDBC type for ${dt.catalogString}")
  }

  def unrecognizedSqlTypeError(sqlType: Int): Throwable = {
    new SQLException(s"Unrecognized SQL type $sqlType")
  }

  def unsupportedJdbcTypeError(content: String): Throwable = {
    new SQLException(s"Unsupported type $content")
  }

  def unsupportedArrayElementTypeBasedOnBinaryError(dt: DataType): Throwable = {
    new IllegalArgumentException(s"Unsupported array element " +
      s"type ${dt.catalogString} based on binary")
  }

  def nestedArraysUnsupportedError(): Throwable = {
    new IllegalArgumentException("Nested arrays unsupported")
  }

  def cannotTranslateNonNullValueForFieldError(pos: Int): Throwable = {
    new IllegalArgumentException(s"Can't translate non-null value for field $pos")
  }

  def invalidJdbcNumPartitionsError(n: Int, jdbcNumPartitions: String): Throwable = {
    new IllegalArgumentException(
      s"Invalid value `$n` for parameter `$jdbcNumPartitions` in table writing " +
        "via JDBC. The minimum value is 1.")
  }

  def transactionUnsupportedByJdbcServerError(): Throwable = {
    new SQLFeatureNotSupportedException("The target JDBC server does not support " +
      "transaction and can only support ALTER TABLE with a single action.")
  }

  def dataTypeUnsupportedYetError(dataType: DataType): Throwable = {
    new UnsupportedOperationException(s"$dataType is not supported yet.")
  }

  def unsupportedOperationForDataTypeError(dataType: DataType): Throwable = {
    new UnsupportedOperationException(s"DataType: ${dataType.catalogString}")
  }

  def inputFilterNotFullyConvertibleError(owner: String): Throwable = {
    new SparkException(s"The input filter of $owner should be fully convertible.")
  }

  def cannotReadFooterForFileError(file: Path, e: IOException): Throwable = {
    new SparkException(s"Could not read footer for file: $file", e)
  }

  def cannotReadFooterForFileError(file: FileStatus, e: RuntimeException): Throwable = {
    new IOException(s"Could not read footer for file: $file", e)
  }

  def foundDuplicateFieldInCaseInsensitiveModeError(
      requiredFieldName: String, matchedOrcFields: String): Throwable = {
    new RuntimeException(
      s"""
         |Found duplicate field(s) "$requiredFieldName": $matchedOrcFields
         |in case-insensitive mode
       """.stripMargin.replaceAll("\n", " "))
  }

  def failedToMergeIncompatibleSchemasError(
      left: StructType, right: StructType, e: Throwable): Throwable = {
    new SparkException(s"Failed to merge incompatible schemas $left and $right", e)
  }

  def ddlUnsupportedTemporarilyError(ddl: String): Throwable = {
    new UnsupportedOperationException(s"$ddl is not supported temporarily.")
  }

  def operatingOnCanonicalizationPlanError(): Throwable = {
    new IllegalStateException("operating on canonicalization plan")
  }

  def executeBroadcastTimeoutError(timeout: Long): Throwable = {
    new SparkException(
      s"""
         |Could not execute broadcast in $timeout secs. You can increase the timeout
         |for broadcasts via ${SQLConf.BROADCAST_TIMEOUT.key} or disable broadcast join
         |by setting ${SQLConf.AUTO_BROADCASTJOIN_THRESHOLD.key} to -1
       """.stripMargin.replaceAll("\n", " "))
  }

  def cannotCompareCostWithTargetCostError(cost: String): Throwable = {
    new IllegalArgumentException(s"Could not compare cost with $cost")
  }

  def unsupportedDataTypeError(dt: DataType): Throwable = {
    new UnsupportedOperationException(s"Unsupported data type: ${dt.catalogString}")
  }

  def notSupportTypeError(dataType: DataType): Throwable = {
    new Exception(s"not support type: $dataType")
  }

  def notSupportNonPrimitiveTypeError(): Throwable = {
    new RuntimeException("Not support non-primitive type now")
  }

  def unsupportedTypeError(dataType: DataType): Throwable = {
    new Exception(s"Unsupported type: ${dataType.catalogString}")
  }

  def useDictionaryEncodingWhenDictionaryOverflowError(): Throwable = {
    new IllegalStateException(
      "Dictionary encoding should not be used because of dictionary overflow.")
  }

  def endOfIteratorError(): Throwable = {
    new NoSuchElementException("End of the iterator")
  }

  def cannotAllocateMemoryToGrowBytesToBytesMapError(): Throwable = {
    new IOException("Could not allocate memory to grow BytesToBytesMap")
  }

  def cannotAcquireMemoryToBuildLongHashedRelationError(size: Long, got: Long): Throwable = {
    new SparkException(s"Can't acquire $size bytes memory to build hash relation, " +
      s"got $got bytes")
  }

  def cannotAcquireMemoryToBuildUnsafeHashedRelationError(): Throwable = {
    new SparkOutOfMemoryError("There is not enough memory to build hash map")
  }

  def rowLargerThan256MUnsupportedError(): Throwable = {
    new UnsupportedOperationException("Does not support row that is larger than 256M")
  }

  def cannotBuildHashedRelationWithUniqueKeysExceededError(): Throwable = {
    new UnsupportedOperationException(
      "Cannot build HashedRelation with more than 1/3 billions unique keys")
  }

  def cannotBuildHashedRelationLargerThan8GError(): Throwable = {
    new UnsupportedOperationException(
      "Can not build a HashedRelation that is larger than 8G")
  }

  def failedToPushRowIntoRowQueueError(rowQueue: String): Throwable = {
    new SparkException(s"failed to push a row into $rowQueue")
  }

  def unexpectedWindowFunctionFrameError(frame: String): Throwable = {
    new RuntimeException(s"Unexpected window function frame $frame.")
  }

  def cannotParseStatisticAsPercentileError(
      stats: String, e: NumberFormatException): Throwable = {
    new IllegalArgumentException(s"Unable to parse $stats as a percentile", e)
  }

  def statisticNotRecognizedError(stats: String): Throwable = {
    new IllegalArgumentException(s"$stats is not a recognised statistic")
  }

  def unknownColumnError(unknownColumn: String): Throwable = {
    new IllegalArgumentException(s"Unknown column: $unknownColumn")
  }

  def unexpectedAccumulableUpdateValueError(o: Any): Throwable = {
    new IllegalArgumentException(s"Unexpected: $o")
  }

  def unscaledValueTooLargeForPrecisionError(): Throwable = {
    new ArithmeticException("Unscaled value too large for precision")
  }

  def decimalPrecisionExceedsMaxPrecisionError(precision: Int, maxPrecision: Int): Throwable = {
    new ArithmeticException(
      s"Decimal precision $precision exceeds max precision $maxPrecision")
  }

  def outOfDecimalTypeRangeError(str: UTF8String): Throwable = {
    new ArithmeticException(s"out of decimal type range: $str")
  }

  def unsupportedArrayTypeError(clazz: Class[_]): Throwable = {
    new RuntimeException(s"Do not support array of type $clazz.")
  }

  def unsupportedJavaTypeError(clazz: Class[_]): Throwable = {
    new RuntimeException(s"Do not support type $clazz.")
  }

  def failedParsingStructTypeError(raw: String): Throwable = {
    new RuntimeException(s"Failed parsing ${StructType.simpleString}: $raw")
  }

  def failedMergingFieldsError(leftName: String, rightName: String, e: Throwable): Throwable = {
    new SparkException(s"Failed to merge fields '$leftName' and '$rightName'. ${e.getMessage}")
  }

  def cannotMergeDecimalTypesWithIncompatiblePrecisionAndScaleError(
      leftPrecision: Int, rightPrecision: Int, leftScale: Int, rightScale: Int): Throwable = {
    new SparkException("Failed to merge decimal types with incompatible " +
      s"precision $leftPrecision and $rightPrecision & scale $leftScale and $rightScale")
  }

  def cannotMergeDecimalTypesWithIncompatiblePrecisionError(
      leftPrecision: Int, rightPrecision: Int): Throwable = {
    new SparkException("Failed to merge decimal types with incompatible " +
      s"precision $leftPrecision and $rightPrecision")
  }

  def cannotMergeDecimalTypesWithIncompatibleScaleError(
      leftScale: Int, rightScale: Int): Throwable = {
    new SparkException("Failed to merge decimal types with incompatible " +
      s"scala $leftScale and $rightScale")
  }

  def cannotMergeIncompatibleDataTypesError(left: DataType, right: DataType): Throwable = {
    new SparkException(s"Failed to merge incompatible data types ${left.catalogString}" +
      s" and ${right.catalogString}")
  }

  def exceedMapSizeLimitError(size: Int): Throwable = {
    new RuntimeException(s"Unsuccessful attempt to build maps with $size elements " +
      s"due to exceeding the map size limit ${ByteArrayMethods.MAX_ROUNDED_ARRAY_LENGTH}.")
  }

  def duplicateMapKeyFoundError(key: Any): Throwable = {
    new RuntimeException(s"Duplicate map key $key was found, please check the input " +
      "data. If you want to remove the duplicated keys, you can set " +
      s"${SQLConf.MAP_KEY_DEDUP_POLICY.key} to ${SQLConf.MapKeyDedupPolicy.LAST_WIN} so that " +
      "the key inserted at last takes precedence.")
  }

  def mapDataKeyArrayLengthDiffersFromValueArrayLengthError(): Throwable = {
    new RuntimeException("The key array and value array of MapData must have the same length.")
  }

  def fieldDiffersFromDerivedLocalDateError(
      field: ChronoField, actual: Int, expected: Int, candidate: LocalDate): Throwable = {
    new DateTimeException(s"Conflict found: Field $field $actual differs from" +
      s" $field $expected derived from $candidate")
  }

  def failToParseDateTimeInNewParserError(s: String, e: Throwable): Throwable = {
    new SparkUpgradeException("3.0", s"Fail to parse '$s' in the new parser. You can " +
      s"set ${SQLConf.LEGACY_TIME_PARSER_POLICY.key} to LEGACY to restore the behavior " +
      s"before Spark 3.0, or set to CORRECTED and treat it as an invalid datetime string.", e)
  }

  def failToFormatDateTimeInNewFormatterError(
      resultCandidate: String, e: Throwable): Throwable = {
    new SparkUpgradeException("3.0",
      s"""
         |Fail to format it to '$resultCandidate' in the new formatter. You can set
         |${SQLConf.LEGACY_TIME_PARSER_POLICY.key} to LEGACY to restore the behavior before
         |Spark 3.0, or set to CORRECTED and treat it as an invalid datetime string.
       """.stripMargin.replaceAll("\n", " "), e)
  }

  def failToRecognizePatternInDateTimeFormatterError(
      pattern: String, e: Throwable): Throwable = {
    new SparkUpgradeException("3.0", s"Fail to recognize '$pattern' pattern in the" +
      s" DateTimeFormatter. 1) You can set ${SQLConf.LEGACY_TIME_PARSER_POLICY.key} to LEGACY" +
      s" to restore the behavior before Spark 3.0. 2) You can form a valid datetime pattern" +
      s" with the guide from https://spark.apache.org/docs/latest/sql-ref-datetime-pattern.html",
      e)
  }

  def cannotCastUTF8StringToDataTypeError(s: UTF8String, to: DataType): Throwable = {
    new DateTimeException(s"Cannot cast $s to $to.")
  }
}
